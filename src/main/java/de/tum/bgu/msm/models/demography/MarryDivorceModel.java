/*
 * Copyright  2005 PB Consult Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package de.tum.bgu.msm.models.demography;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.container.SiloModelContainer;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.events.EventManager;
import de.tum.bgu.msm.events.EventRules;
import de.tum.bgu.msm.events.EventTypes;
import de.tum.bgu.msm.events.IssueCounter;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.relocation.InOutMigration;
import de.tum.bgu.msm.models.relocation.MovesModelI;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.syntheticPopulationGenerator.CreateCarOwnershipModel;
import org.apache.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * Simulates marriage and divorce
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 31 December 2009 in Cologne
 * Revised on 5 March 2015 in Wheaton, MD
 **/

public class MarryDivorceModel extends AbstractModel {

    private static Logger logger = Logger.getLogger(MarryDivorceModel.class);
    private final MovesModelI movesModel;
    private final InOutMigration iomig;
    private final CreateCarOwnershipModel carOwnership;

    private MarryDivorceJSCalculator calculator;

    private final static int AGE_OFFSET = 10;
    // ageOffset is the range of ages above and below a persons age that are considered for marriage
    // needs to cover -9 to +9 to reach one person type above and one person type below
    // (e.g., for 25-old person consider partners from 20 to 34). ageOffset is 10 and not 9 to
    // capture if potential partner has celebrated BIRTHDAY already (i.e. turned 35). To improve
    // performance, the person type of this person in the marriage market is not updated.

    public MarryDivorceModel(SiloDataContainer dataContainer, MovesModelI movesModel,
                             InOutMigration iomig, CreateCarOwnershipModel carOwnership) {
        super(dataContainer);
        this.movesModel = movesModel;
        this.iomig = iomig;
        this.carOwnership = carOwnership;
        setupModel();
    }

    private void setupModel() {

        // localMarriageAdjuster serves to adjust from national marriage rates to local conditions
        double scale = Properties.get().demographics.localMarriageAdjuster;

        Reader reader;
        if(Properties.get().main.implementation == Implementation.MUNICH) {
            reader = new InputStreamReader(this.getClass().getResourceAsStream("MarryDivorceCalcMuc"));
        } else {
            reader = new InputStreamReader(this.getClass().getResourceAsStream("MarryDivorceCalcMstm"));
        }
        calculator = new MarryDivorceJSCalculator(reader, scale);
    }

    public List<int[]> selectCouplesToGetMarriedThisYear() {
        // select singles that will get married during this coming year
        if (!EventRules.runMarriages()) {
            return Collections.emptyList();
        }
        logger.info("  Selecting couples to get married this year");
        List<int[]> couplesToMarryThisYear = new ArrayList<>();

        // create HashMap with men and women by age
        Map<String, List<Integer>> ppByAgeAndGender = new HashMap<>();

        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (Person pp : householdData.getPersons()) {
            if (EventRules.ruleGetMarried(pp) && pp.getAge() < 100) {
                int size = pp.getHh().getHhSize();
                // put only every fifth person into marriage market, emphasize single-person households
                if (size == 1 && SiloUtil.getRandomNumberAsFloat() > 0.1 * Properties.get().demographics.onePersonHhMarriageBias) {
                    continue;
                }
                if (size != 1 && SiloUtil.getRandomNumberAsFloat() > 0.1) {
                    continue;
                }
                // Store persons by age and gender
                String token = pp.getAge() + "_" + pp.getGender();
                if (ppByAgeAndGender.containsKey(token)) {
                    List<Integer> al = ppByAgeAndGender.get(token);
                    al.add(pp.getId());
                } else {
                    ppByAgeAndGender.put(token, Lists.newArrayList(pp.getId()));
                }
            }
        }

        // create couples
        int highestId = householdData.getHighestPersonIdInUse();
        boolean[] personSelectedForMarriage = SiloUtil.createArrayWithValue(highestId + 1, false);
        float interRacialMarriageShare = Properties.get().demographics.interracialMarriageShare;
        for (Person pp : householdData.getPersons()) {
            if (EventRules.ruleGetMarried(pp) && pp.getAge() < 100 && !personSelectedForMarriage[pp.getId()]) {
                double marryProb = calculator.calculateMarriageProbability(pp);   // raw marriage probability for this age/gender group
                // to keep things simple, emphasize prop to initialize marriage for people from single-person households.
                // Single-person household has no influence on whether someone is selected by the marriage initializer
                if (pp.getHh().getHhSize() == 1) {
                    marryProb *= Properties.get().demographics.onePersonHhMarriageBias;
                }
                if (SiloUtil.getRandomNumberAsDouble() >= marryProb){
                    continue;
                }
                // person was selected to find a partner
                personSelectedForMarriage[pp.getId()] = true;

                // First, select interracial or monoracial marriage
                boolean sameRace = true;
                if (SiloUtil.getRandomNumberAsFloat() <= interRacialMarriageShare) {
                    sameRace = false;
                }

                // Second, select age of new partner
                double[] ageProb = new double[AGE_OFFSET * 2 + 1];
                for (int ageDiff = -AGE_OFFSET; ageDiff <= AGE_OFFSET; ageDiff++) {
                    ageProb[ageDiff + AGE_OFFSET] = getAgeDependentProbabilities(pp.getGender(), ageDiff);
                    int thisAge = pp.getAge() + ageDiff;
                    if (pp.getGender() == 1) {
                        if (ppByAgeAndGender.containsKey(thisAge + "_" + 2)) {    // man looking for women
                            ageProb[ageDiff + AGE_OFFSET] *= ppByAgeAndGender.get(thisAge + "_" + 2).size();
                        } else {
                            ageProb[ageDiff + AGE_OFFSET] = 0;
                        }
                    } else {                                                     // woman looking for men
                        if (ppByAgeAndGender.containsKey(thisAge + "_" + 1)) {
                            ageProb[ageDiff + AGE_OFFSET] *= ppByAgeAndGender.get(thisAge + "_" + 1).size();
                        } else {
                            ageProb[ageDiff + AGE_OFFSET] = 0;
                        }
                    }
                }
                if (SiloUtil.getSum(ageProb) == 0) {
                    logger.warn("Marriage market ran empty, increase share of persons. Age: " + pp.getAge());
                    break;
                }
                int selectedAge = SiloUtil.select(ageProb) - AGE_OFFSET + pp.getAge();

                // Third, select partner
                List<Integer> possiblePartners;
                if (pp.getGender() == 1) {   // Look for woman
                    possiblePartners = ppByAgeAndGender.get(selectedAge + "_" + 2);
                } else {                     // Look for man
                    possiblePartners = ppByAgeAndGender.get(selectedAge + "_" + 1);
                }
                float[] partnerProb = SiloUtil.createArrayWithValue(possiblePartners.size(), 0f);
                for (int per = 0; per < possiblePartners.size(); per++) {
                    int personId = possiblePartners.get(per);
                    if (personSelectedForMarriage[personId])
                        continue;  // this person was already selected to get married
                    Race personRace = householdData.getPersonFromId(personId).getRace();
                    if ((sameRace && pp.getRace() == personRace) || (!sameRace && pp.getRace() != personRace)) {
                        partnerProb[per] = 10000f;
                    } else {
                        partnerProb[per] = 0.001f;  // set probability to small non-zero value to ensure that model works when marriage market runs almost empty
                    }
                }
                int selectedPartner = possiblePartners.get(SiloUtil.select(partnerProb));
                personSelectedForMarriage[selectedPartner] = true;
                couplesToMarryThisYear.add(new int[]{pp.getId(), selectedPartner});
                if (pp.getId() == SiloUtil.trackPp) SiloUtil.trackWriter.println("Person " + pp.getId() + " chose " +
                        "person " + selectedPartner + " to marry and they were scheduled as a couple to marry this year.");
                if (selectedPartner == SiloUtil.trackPp)
                    SiloUtil.trackWriter.println("Person " + selectedPartner + " was chosen " +
                            "by person " + pp.getId() + " to get married and they were scheduled as a couple to marry this year.");
            }
        }
        return couplesToMarryThisYear;
    }

    private double getAgeDependentProbabilities(int gender, int ageDiff) {
        double marryAbsAgeDiff = Properties.get().demographics.marryAbsAgeDiff;
        double marryAgeSpreadFac = Properties.get().demographics.marryAgeSpreadFac;
        if(gender == 1) {
            return 1 / Math.exp(Math.pow(ageDiff + marryAbsAgeDiff, 2) * marryAgeSpreadFac);  // man searches woman
        } else if(gender ==2) {
            return 1 / Math.exp(Math.pow(ageDiff - marryAbsAgeDiff, 2) * marryAgeSpreadFac);  // woman searches man
        } else {
            throw new IllegalArgumentException("Unknwon gender " + gender);
        }
    }


    public void marryCouple(int[] couple) {
        final HouseholdDataManager householdData = dataContainer.getHouseholdData();
        final Person partner1 = householdData.getPersonFromId(couple[0]);
        if (!EventRules.ruleGetMarried(partner1)) {
            return;  // Person got already married this simulation period or died or moved away
        }
        final Person partner2 = householdData.getPersonFromId(couple[1]);
        if (!EventRules.ruleGetMarried(partner2)) {
            return;  // Person got already married this simulation period or died or moved away
        }

        final Household hhOfPartner1 = partner1.getHh();
        final Household hhOfPartner2 = partner2.getHh();

        final Household moveTo = chooseRelocationTarget(partner1, partner2, hhOfPartner1, hhOfPartner2);

        final boolean success = moveTogether(partner1, partner2, moveTo);

        if(success) {
            partner1.setRole(PersonRole.MARRIED);
            partner2.setRole(PersonRole.MARRIED);
            EventManager.countEvent(EventTypes.CHECK_MARRIAGE);
            householdData.addHouseholdThatChanged(hhOfPartner1);
            householdData.addHouseholdThatChanged(hhOfPartner2);
        } else {
            if (partner1.getId() == SiloUtil.trackPp
                    || partner2.getId() == SiloUtil.trackPp
                    || moveTo.getId() == SiloUtil.trackHh) {
                SiloUtil.trackWriter.println("Person " + partner1.getId()
                        + " and person " + partner2.getId()
                        + " of household " + moveTo.getId()
                        + " got married but could not find an appropriate vacant dwelling. "
                        + "Household outmigrated.");
                IssueCounter.countLackOfDwellingFailedMarriage();
            }
        }
    }

    private Household chooseRelocationTarget(Person partner1, Person partner2, Household household1, Household household2) {
        final int hhSize1 = household1.getHhSize();
        final int hhSize2 = household2.getHhSize();
        final PersonRole role1 = partner1.getRole();
        final PersonRole role2 = partner2.getRole();

        Household moveTo = household1;
        if (role1.equals(PersonRole.CHILD) && !role2.equals(PersonRole.CHILD)) {
            moveTo = household2; // if one is not a child, move into that household
        } else if (!role1.equals(PersonRole.CHILD) && role2.equals(PersonRole.CHILD)) {
            moveTo = household1;
        } else if (role1 == role2) {
            // if both are/areNot children, move into smaller hh size
            if (hhSize1 > hhSize2) {
                moveTo = household2;
            } else if (hhSize1 == hhSize2) {
                // if hhSize is identical, move into larger dwelling
                Dwelling dwelling1 = dataContainer.getRealEstateData().getDwelling(household1.getDwellingId());
                Dwelling dwelling2 = dataContainer.getRealEstateData().getDwelling(household2.getDwellingId());
                if (dwelling1.getBedrooms() < dwelling2.getBedrooms()) {
                    moveTo = household2;
                }
            }
        }
        // if household is already crowded, move couple into new household
        if (moveTo.getHhSize() > 3) {
            final int newHhId = dataContainer.getHouseholdData().getNextHouseholdId();
            moveTo = dataContainer.getHouseholdData().createHousehold(newHhId, -1, 0);
        }
        return moveTo;
    }

    private boolean moveTogether(Person person1, Person person2, Household moveTo) {

        movePerson(person1, moveTo);
        movePerson(person2, moveTo);

        if (person1.getId() == SiloUtil.trackPp
                || person2.getId() == SiloUtil.trackPp
                || person1.getHh().getId() == SiloUtil.trackHh
                || person2.getHh().getId() == SiloUtil.trackHh) {
            SiloUtil.trackWriter.println("Person " + person1.getId() +
                    " and person " + person2.getId() + " got married and moved into household "
                    + moveTo.getId() + ".");
        }

        if(moveTo.getDwellingId() == -1) {
            final int newDwellingId = movesModel.searchForNewDwelling(ImmutableList.of(person1 , person2));
            if (newDwellingId < 0) {
                iomig.outMigrateHh(moveTo.getId(), true);
                return false;
            } else {
                movesModel.moveHousehold(moveTo, -1, newDwellingId);
                if (Properties.get().main.implementation == Implementation.MUNICH) {
                    carOwnership.simulateCarOwnership(moveTo); // set initial car ownership of new household
                }
            }
        }
        return true;
    }

    private void movePerson(Person person1, Household moveTo) {
        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        final Household household1 = person1.getHh();
        if(!moveTo.equals(household1)) {
            householdData.removePersonFromHousehold(person1);
            householdData.addPersonToHousehold(person1, moveTo);
            if(household1.checkIfOnlyChildrenRemaining()) {
                moveRemainingChildren(household1, moveTo);
            }
        }
    }

    private void moveRemainingChildren(Household oldHh, Household newHh) {
        List<Person> remainingPersons = new ArrayList<>(oldHh.getPersons());
        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (Person person : remainingPersons) {
            householdData.removePersonFromHousehold(person);
            householdData.addPersonToHousehold(person, newHh);
            if (person.getId() == SiloUtil.trackPp || oldHh.getId() == SiloUtil.trackHh ||
                    newHh.getId() == SiloUtil.trackHh) {
                SiloUtil.trackWriter.println("Person " +
                        person.getId() + " was moved from household " + oldHh.getId() + " to household " + newHh.getId() +
                        " as remaining child.");
            }
        }
    }


    public void chooseDivorce(int perId) {
        // select if person gets divorced/leaves joint dwelling

        final HouseholdDataManager householdData = dataContainer.getHouseholdData();
        Person per = householdData.getPersonFromId(perId);
        if (!EventRules.ruleGetDivorced(per)) {
            return;
        }
        double probability = calculator.calculateDivorceProbability(per.getType().ordinal()) / 2;
        if (SiloUtil.getRandomNumberAsDouble() < probability) {
            // check if vacant dwelling is available
            int newDwellingId = movesModel.searchForNewDwelling(Collections.singletonList(per));
            if (newDwellingId < 0) {
                if (perId == SiloUtil.trackPp || per.getHh().getId() == SiloUtil.trackHh) {
                    SiloUtil.trackWriter.println(
                            "Person " + perId + " wanted to but could not divorce from household " + per.getHh().getId() +
                                    " because no appropriate vacant dwelling was found.");
                }
                IssueCounter.countLackOfDwellingFailedDivorce();
                return;
            }

            // divorce
            Household oldHh = per.getHh();
            Person divorcedPerson = HouseholdDataManager.findMostLikelyPartner(per, oldHh);
            divorcedPerson.setRole(PersonRole.SINGLE);
            per.setRole(PersonRole.SINGLE);
            householdData.removePersonFromHousehold(per);
            oldHh.determineHouseholdRace();
            oldHh.setType();

            int newHhId = householdData.getNextHouseholdId();
            Household newHh = householdData.createHousehold(newHhId, -1, 0);
            householdData.addPersonToHousehold(per, newHh);
            newHh.setType();
            newHh.determineHouseholdRace();
            // move divorced person into new dwelling
            movesModel.moveHousehold(newHh, -1, newDwellingId);
            if (perId == SiloUtil.trackPp || newHh.getId() == SiloUtil.trackHh ||
                    oldHh.getId() == SiloUtil.trackHh) SiloUtil.trackWriter.println("Person " + perId +
                    " has divorced from household " + oldHh + " and established the new household " +
                    newHhId + ".");
            EventManager.countEvent(EventTypes.CHECK_DIVORCE);
            householdData.addHouseholdThatChanged(oldHh); // consider original household for update in car ownership
            if (Properties.get().main.implementation == Implementation.MUNICH) {
                carOwnership.simulateCarOwnership(newHh); // set initial car ownership of new household
            }
        }
    }
}