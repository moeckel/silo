package edu.umd.ncsg.data;

import edu.umd.ncsg.SiloUtil;
import edu.umd.ncsg.events.IssueCounter;
import org.apache.log4j.Logger;
import com.pb.common.util.ResourceUtil;
import com.pb.common.datafile.TableDataSet;

import java.io.*;
import java.util.*;

/**
 * Keeps data of dwellings and non-residential floorspace
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 7 January 2010 in Rhede
 **/

public class RealEstateDataManager {
    static Logger logger = Logger.getLogger(RealEstateDataManager.class);

    protected static final String PROPERTIES_DD_FILE_ASCII  = "dwelling.file.ascii";
    protected static final String PROPERTIES_READ_BIN_FILE  = "read.binary.dd.file";
    protected static final String PROPERTIES_DD_FILE_BIN    = "dwellings.file.bin";
    protected static final String PROPERTIES_LAND_USE_AREA  = "land.use.area.by.taz";
    protected static final String PROPERTIES_DEVELOPABLE    = "developable.lu.category";
    protected static final String PROPERTIES_ACRES_BY_DD    = "developer.acres.per.dwelling.by.type";
    protected static final String PROPERTIES_DEVELOPM_RESTR = "development.restrictions";
    protected static final String PROPERTIES_MAX_NUM_VAC_DD = "vacant.dd.by.reg.array";
    protected static final String PROPERTIES_USE_CAPACITY   = "use.growth.capacity.data";
    protected static final String PROPERTIES_CAPACITY_FILE  = "growth.capacity.file";

    private ResourceBundle rb;
    private static TableDataSet landUse;
    private int[] developableLUtypes;
    private static TableDataSet developmentRestrictions;
    private TableDataSet developmentCapacity;
    private boolean useCapacityAsNumberOfDwellings;
    private HashMap<DwellingType, Float> acresByDwellingType;
    public static int largestNoBedrooms;
    public static int[] dwellingsByQuality;
    private static double[] initialQualityShares;
    private static int highestDwellingIdInUse;
    public static int rentCategories;
    private static HashMap<Integer, float[]> ddPriceByHhType;
    private static int[] dwellingsByRegion;
    private static int[][] vacDwellingsByRegion;
    private static int[] vacDwellingsByRegionPos;
    private static int numberOfStoredVacantDD;
    private double[] avePrice;
    private double[] aveVac;
    private static float[] medianRent;

    public RealEstateDataManager(ResourceBundle rb) {
        // constructor
        this.rb = rb;
    }


    public void readDwellings () {
        // read population
        boolean readBin = ResourceUtil.getBooleanProperty(rb, PROPERTIES_READ_BIN_FILE, false);
        if (readBin) {
            readBinaryDwellingDataObjects();
        } else {
            readDwellingData();
        }
    }


    private void readDwellingData() {
        // read dwelling micro data from ascii file

        logger.info("Reading dwelling micro data from ascii file");
        int year = SiloUtil.getStartYear();
        String fileName = SiloUtil.baseDirectory + ResourceUtil.getProperty(rb, PROPERTIES_DD_FILE_ASCII) + "_" + year + ".csv";

        String recString = "";
        int recCount = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId      = SiloUtil.findPositionInArray("id", header);
            int posZone    = SiloUtil.findPositionInArray("zone",header);
            int posHh      = SiloUtil.findPositionInArray("hhId",header);
            int posType    = SiloUtil.findPositionInArray("type",header);
            int posRooms   = SiloUtil.findPositionInArray("bedrooms",header);
            int posQuality = SiloUtil.findPositionInArray("quality",header);
            int posCosts   = SiloUtil.findPositionInArray("monthlyCost",header);
            int posRestr   = SiloUtil.findPositionInArray("restriction",header);
            int posYear    = SiloUtil.findPositionInArray("yearBuilt",header);

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id        = Integer.parseInt(lineElements[posId]);
                int zone      = Integer.parseInt(lineElements[posZone]);
                int hhId      = Integer.parseInt(lineElements[posHh]);
                String tp     = lineElements[posType].replace("\"", "");
                DwellingType type = DwellingType.valueOf(tp);
                int price     = Integer.parseInt(lineElements[posCosts]);
                int area      = Integer.parseInt(lineElements[posRooms]);
                int quality   = Integer.parseInt(lineElements[posQuality]);
                float restrict  = Float.parseFloat(lineElements[posRestr]);
                int yearBuilt = Integer.parseInt(lineElements[posYear]);
                new Dwelling(id, zone, hhId, type, area, quality, price, restrict, yearBuilt);   // this automatically puts it in id->dwelling map in Dwelling class
                if (id == SiloUtil.trackDd) {
                    SiloUtil.trackWriter.println("Read dwelling with following attributes from " + fileName);
                    Dwelling.getDwellingFromId(id).logAttributes(SiloUtil.trackWriter);
                }
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop dwelling file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("Finished reading " + recCount + " dwellings.");
    }


    public void identifyVacantDwellings() {
        // walk through all dwellings and identify vacant dwellings (one-time task at beginning of model run only)

        int highestRegion = SiloUtil.getHighestVal(geoData.getRegionList());
        numberOfStoredVacantDD = ResourceUtil.getIntegerProperty(rb, PROPERTIES_MAX_NUM_VAC_DD);
        dwellingsByRegion = new int[highestRegion + 1];
        vacDwellingsByRegion = new int[highestRegion + 1][numberOfStoredVacantDD + 1];
        vacDwellingsByRegion = SiloUtil.setArrayToValue(vacDwellingsByRegion, 0);
        vacDwellingsByRegionPos = new int[highestRegion + 1];
        vacDwellingsByRegionPos = SiloUtil.setArrayToValue(vacDwellingsByRegionPos, 0);

        logger.info("  Identifying vacant dwellings");
        for (Dwelling dd : Dwelling.getDwellingArray()) {
            if (dd.getResidentId() == -1) {
                int dwellingId = dd.getId();
                int region = geoData.getRegionOfZone(dd.getZone());
                dwellingsByRegion[region]++;
                vacDwellingsByRegion[region][vacDwellingsByRegionPos[region]] = dwellingId;
                if (vacDwellingsByRegionPos[region] < numberOfStoredVacantDD) vacDwellingsByRegionPos[region]++;
                if (vacDwellingsByRegionPos[region] >= numberOfStoredVacantDD) IssueCounter.countExcessOfVacantDwellings(region);
                if (dwellingId == SiloUtil.trackDd)
                    SiloUtil.trackWriter.println("Added dwelling " + dwellingId + " to list of vacant dwelling.");
            }
        }
//        for (int region: SiloUtil.getRegionList()) System.out.println ("Region " + region + " has vacant dwellings: " +
//                (vacDwellingsByRegionPos[region]));
//        System.exit(1);
    }


    public static void writeBinaryDwellingDataObjects(ResourceBundle appRb) {
        // Store dwelling object data in binary file

        String fileName = SiloUtil.baseDirectory + ResourceUtil.getProperty(appRb, PROPERTIES_DD_FILE_BIN);
        logger.info("  Writing dwelling data to binary file.");
        Object[] data = Dwelling.getDwellings().toArray(new Dwelling[Dwelling.getDwellingCount()]);
        try {
            File fl = new File(fileName);
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fl));
            out.writeObject(data);
            out.close();
        } catch (Exception e) {
            logger.error ("Error saving to binary file " + fileName + ". Object not saved.\n" + e);
        }
    }


    private void readBinaryDwellingDataObjects() {
        // read dwellings from binary file

        String fileName = SiloUtil.baseDirectory + ResourceUtil.getProperty(rb, PROPERTIES_DD_FILE_BIN);
        logger.info("  Reading dwelling data from binary file.");
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(fileName)));
            Object data = in.readObject();
            Dwelling.saveDwellings((Dwelling[]) data);
        } catch (Exception e) {
            logger.error ("Error reading from binary file " + fileName + ". Object not read.\n" + e);
        }
        logger.info("  Finished reading " + Dwelling.getDwellingCount() + " dwellings.");
    }


    public void readLandUse() {
        // read land use data
        logger.info("Reading land use data");
        String fileName;
        if (SiloUtil.startYear == SiloUtil.getBaseYear()) {  // start in year 2000
            fileName = SiloUtil.baseDirectory + "input/" + ResourceUtil.getProperty(rb, PROPERTIES_LAND_USE_AREA) + ".csv";
        } else {                                             // start in different year (continue previous run)
            fileName = SiloUtil.baseDirectory + "scenOutput/" + SiloUtil.scenarioName + "/" +
                    ResourceUtil.getProperty(rb, PROPERTIES_LAND_USE_AREA) + "_" + SiloUtil.startYear + ".csv";
        }
        landUse = SiloUtil.readCSVfile(fileName);
        landUse.buildIndex(landUse.getColumnPosition("Zone"));

        // read developers data
        developableLUtypes = ResourceUtil.getIntegerArray(rb, PROPERTIES_DEVELOPABLE);
        String fileNameAcres = SiloUtil.baseDirectory + ResourceUtil.getProperty(rb, PROPERTIES_ACRES_BY_DD);
        TableDataSet tblAcresByDwellingType =  SiloUtil.readCSVfile(fileNameAcres);
        acresByDwellingType = new HashMap<>();
        for (int row = 1; row <= tblAcresByDwellingType.getRowCount(); row++) {
            String type = tblAcresByDwellingType.getStringValueAt(row, "DwellingType");
            float acres = tblAcresByDwellingType.getValueAt(row, "acres");
            boolean notFound = true;
            for (DwellingType dt: DwellingType.values()) {
                if (dt.toString().equals(type)) {
                    acresByDwellingType.put(dt, acres);
                    notFound = false;
                }
            }
            if (notFound) logger.error("Could not reference type " + type + " of " + fileNameAcres + " with DwellingType.");
        }

        String restrictionsFileName = SiloUtil.baseDirectory + ResourceUtil.getProperty(rb, PROPERTIES_DEVELOPM_RESTR);
        developmentRestrictions = SiloUtil.readCSVfile(restrictionsFileName);
        developmentRestrictions.buildIndex(developmentRestrictions.getColumnPosition("Zone"));

        useCapacityAsNumberOfDwellings = ResourceUtil.getBooleanProperty(rb, PROPERTIES_USE_CAPACITY, false);
        if (useCapacityAsNumberOfDwellings) {
            String capacityFileName;
            if (SiloUtil.startYear == SiloUtil.getBaseYear()) {  // start in year 2000
                capacityFileName = SiloUtil.baseDirectory + "input/" + ResourceUtil.getProperty(rb, PROPERTIES_CAPACITY_FILE) + ".csv";
            } else {                                             // start in different year (continue previous run)
                capacityFileName = SiloUtil.baseDirectory + "scenOutput/" + SiloUtil.scenarioName + "/" +
                        ResourceUtil.getProperty(rb, PROPERTIES_CAPACITY_FILE) + "_" + SiloUtil.startYear + ".csv";
            }
            developmentCapacity = SiloUtil.readCSVfile(capacityFileName);
            developmentCapacity.buildIndex(developmentCapacity.getColumnPosition("Zone"));

        }
    }


    public float getAcresNeededForOneDwelling(DwellingType dt) {
        return acresByDwellingType.get(dt);
    }


    public void fillQualityDistribution () {
        // count number of dwellings by quality and calculate average quality
        dwellingsByQuality = new int[SiloUtil.numberOfQualityLevels];
        initialQualityShares = new double[SiloUtil.numberOfQualityLevels];
        for (Dwelling dd: getDwellings()) dwellingsByQuality[dd.getQuality() - 1]++;
        for (int qual = 1; qual <= SiloUtil.numberOfQualityLevels; qual++)
            initialQualityShares[qual - 1] = (double) dwellingsByQuality[qual - 1] /
                    (double) SiloUtil.getSum(dwellingsByQuality);
    }


    public void setHighestVariables () {
        // identify highest dwelling ID in use and largest bedrooms, also calculate share of rent paid by each hh type
        // only done initially when model starts

        highestDwellingIdInUse = 0;
        largestNoBedrooms = 0;

        // identify how much rent (specified by 25 rent categories) is paid by households of each income category
        rentCategories = 25;
        float[][] priceByIncome = new float[SiloUtil.incBrackets.length + 1][rentCategories + 1];
        for (Dwelling dd: Dwelling.getDwellingArray()) {
            highestDwellingIdInUse = Math.max(highestDwellingIdInUse, dd.getId());
            largestNoBedrooms = Math.max(largestNoBedrooms, dd.getBedrooms());
            int hhId = dd.getResidentId();
            if (hhId > 0) {
                int hhinc = Household.getHouseholdFromId(hhId).getHhIncome();
                int incomeCategory = HouseholdDataManager.getIncomeCategoryForIncome(hhinc);
                int rentCategory = (int) ((dd.getPrice() * 1.) / 200.);  // rent category defined as <rent/200>
                rentCategory = Math.min(rentCategory, rentCategories);   // ensure that rent categories do not exceed max
                priceByIncome[incomeCategory - 1][rentCategory]++;
            }
        }
        priceByIncome[SiloUtil.incBrackets.length][rentCategories]++;  // make sure that most expensive category can be afforded by richest households
        ddPriceByHhType = new HashMap<>();
        for (int incomeCategory = 1; incomeCategory <= SiloUtil.incBrackets.length + 1; incomeCategory++) {
            float[] vector = new float[rentCategories + 1];
            System.arraycopy(priceByIncome[incomeCategory - 1], 0, vector, 0, vector.length);
            float sum = SiloUtil.getSum(vector);
            for (int i = 0; i < vector.length; i++) vector[i] = vector[i] / sum;
            ddPriceByHhType.put(incomeCategory, vector);
        }
    }


    public static float[] getRentPaymentsForIncomeGroup (int incomeCategory) {
        return ddPriceByHhType.get(incomeCategory);
    }


    public Collection<Dwelling> getDwellings() {
        // return collection of dwellings
        return Dwelling.getDwellings();
    }


    public static int getNextDwellingId() {
        // increase highestDwellingIdInUse by 1 and return value
        highestDwellingIdInUse++;
        return highestDwellingIdInUse;
    }


    public static double[] getInitialQualShares() {
        return initialQualityShares;
    }


    public static double[] getCurrentQualShares() {
        double[] currentQualityShares = new double[SiloUtil.numberOfQualityLevels];
        for (int qual = 1; qual <= SiloUtil.numberOfQualityLevels; qual++) currentQualityShares[qual - 1] =
                (double) dwellingsByQuality[qual - 1] / (double) SiloUtil.getSum(dwellingsByQuality);
        return currentQualityShares;
    }


    public static void calculateMedianRentByMSA() {
        // calculate median rent by MSA

        HashMap<Integer, ArrayList<Integer>> rentHashMap = new HashMap<>();
        for (Dwelling dd: Dwelling.getDwellingArray()) {
            int dwellingMSA = geoData.getMSAOfZone(dd.getZone());
            if (rentHashMap.containsKey(dwellingMSA)) {
                ArrayList<Integer> rents = rentHashMap.get(dwellingMSA);
                rents.add(dd.getPrice());
            } else {
                ArrayList<Integer> rents = new ArrayList<>();
                rents.add(dd.getPrice());
                rentHashMap.put(dwellingMSA, rents);
            }
        }
        medianRent = new float[99999];
        for (Integer thisMsa: rentHashMap.keySet()) {
            medianRent[thisMsa] = SiloUtil.getMedian(SiloUtil.convertIntegerArrayListToArray(rentHashMap.get(thisMsa)));
        }
    }


    public static float getMedianRent (int msa) {
        return medianRent[msa];
    }


    public static void summarizeDwellings (RealEstateDataManager realEstateData) {
        // aggregate dwellings

        summarizeData.resultFile("QualityLevel,Dwellings");
        for (int qual = 1; qual <= SiloUtil.numberOfQualityLevels; qual++) {
            String row = qual + "," + dwellingsByQuality[qual - 1];
            summarizeData.resultFile(row);
        }
        int[] ddByType = new int[DwellingType.values().length];
        for (Dwelling dd: Dwelling.getDwellingArray()) ddByType[dd.getType().ordinal()]++;
        for (DwellingType dt: DwellingType.values()) {
            summarizeData.resultFile("CountOfDD,"+dt.toString()+","+ddByType[dt.ordinal()]);
        }
        for (DwellingType dt: DwellingType.values()) {
            double avePrice = realEstateData.getAveragePriceByDwellingType()[dt.ordinal()];
            summarizeData.resultFile("AveMonthlyPrice,"+dt.toString()+","+avePrice);
        }
        for (DwellingType dt: DwellingType.values()) {
            double aveVac = realEstateData.getAverageVacancyByDwellingType()[dt.ordinal()];
            Formatter f = new Formatter();
            f.format("AveVacancy,%s,%f", dt.toString(), aveVac);
            summarizeData.resultFile(f.toString());
        }
        // aggregate developable land
        summarizeData.resultFile("Available land for construction by region");
        double[] availLand = new double[SiloUtil.getHighestVal(geoData.getRegionList()) + 1];
        for (int zone: geoData.getZones()) availLand[geoData.getRegionOfZone(zone)] +=
                realEstateData.getAvailableLandForConstruction(zone);
        for (int region: geoData.getRegionList()) {
            Formatter f = new Formatter();
            f.format("%d,%f", region, availLand[region]);
            summarizeData.resultFile(f.toString());
        }

        // summarize housing costs by income group
        summarizeData.resultFile("Housing costs by income group");
        String header = "Income";
        for (int i = 0; i < 10; i++) header = header.concat(",rent_" + ((i+1) * 250));
        header = header.concat(",averageRent");
        summarizeData.resultFile(header);
        int[][] rentByIncome = new int[10][10];
        int[] rents = new int[10];
        for (Household hh: Household.getHouseholdArray()) {
            int hhInc = hh.getHhIncome();
            int rent = Dwelling.getDwellingFromId(hh.getDwellingId()).getPrice();
            int incCat = Math.min((hhInc / 10000), 9);
            int rentCat = Math.min((rent / 250), 9);
            rentByIncome[incCat][rentCat]++;
            rents[incCat] += rent;
        }
        for (int i = 0; i < 10; i++) {
            String line = String.valueOf((i + 1) * 10000);
            int countThisIncome = 0;
            for (int r = 0; r < 10; r++) {
                line = line.concat("," + rentByIncome[i][r]);
                countThisIncome += rentByIncome[i][r];
            }
            line = line.concat("," + rents[i] / countThisIncome);
            summarizeData.resultFile(line);
        }
    }


    public static int findPositionInArray (DwellingType type){
        // return index position of element in array typeArray
        int ind = -1;
        DwellingType[] types = DwellingType.values();
        for (int a = 0; a < DwellingType.values().length; a++) if (types[a] == type) ind = a;
        if (ind == -1) logger.error ("Could not find dwelling type " + type.toString() +
                " in array (see method <findPositionInArray> in class <RealEstateDataManager>");
        return ind;
    }


    public static int getNumberOfDDinRegion (int region) {
        return dwellingsByRegion[region];
    }


    public static int[] getListOfVacantDwellingsInRegion (int region) {
        // return array with IDs of vacant dwellings in region

        int[] vacancies = new int[vacDwellingsByRegionPos[region]];
        System.arraycopy(vacDwellingsByRegion[region], 0, vacancies, 0, vacDwellingsByRegionPos[region]);
        return vacancies;
    }


    public static int getNumberOfVacantDDinRegion (int region) {
        return Math.max(vacDwellingsByRegionPos[region] - 1, 0);
    }


    public static void removeDwellingFromVacancyList (int ddId) {
        // remove dwelling with ID ddId from list of vacant dwellings

        boolean found = false;

        // todo: when selecting a vacant dwelling, I should be able to store the index of this dwelling in the vacDwellingByRegion array, which should make it faster to remove the vacant dwelling from this array.
        int region = geoData.getRegionOfZone(Dwelling.getDwellingFromId(ddId).getZone());
        for (int i = 0; i < vacDwellingsByRegionPos[region]; i++) {
            if (vacDwellingsByRegion[region][i] == ddId) {
                vacDwellingsByRegion[region][i] = vacDwellingsByRegion[region][vacDwellingsByRegionPos[region] - 1];
                vacDwellingsByRegion[region][vacDwellingsByRegionPos[region] - 1] = 0;
                vacDwellingsByRegionPos[region] -= 1;
                if (ddId == SiloUtil.trackDd) SiloUtil.trackWriter.println("Removed dwelling " + ddId +
                        " from list of vacant dwellings.");
                found = true;
                break;
            }
        }
        if (!found) logger.warn("Consistency error: Could not find vacant dwelling " + ddId + " in vacDwellingsByRegion.");
    }


    public static void addDwellingToVacancyList (Dwelling dd) {
        // add dwelling to vacancy list

        int region = geoData.getRegionOfZone(dd.getZone());
        vacDwellingsByRegion[region][vacDwellingsByRegionPos[region]] = dd.getId();
        if (vacDwellingsByRegionPos[region] < numberOfStoredVacantDD) vacDwellingsByRegionPos[region]++;
        if (vacDwellingsByRegionPos[region] >= numberOfStoredVacantDD) IssueCounter.countExcessOfVacantDwellings(region);
        if (dd.getId() == SiloUtil.trackDd) SiloUtil.trackWriter.println("Added dwelling " + dd.getId() +
                " to list of vacant dwellings.");
    }


    public void calculateRegionWidePriceAndVacancyByDwellingType() {
        // calculate region-wide average dwelling costs and vacancy by dwelling type

        int[][] vacOcc = SiloUtil.setArrayToValue(new int[2][DwellingType.values().length], 0);
        long[] price = SiloUtil.setArrayToValue(new long[DwellingType.values().length], 0);

        for (Dwelling dd: Dwelling.getDwellings()) {
            int dto = dd.getType().ordinal();
            price[dto] += dd.getPrice();

            if (dd.getResidentId() > 0) {
                vacOcc[1][dto] ++;
            } else {
                vacOcc[0][dto]++;
            }
        }
        aveVac = new double[DwellingType.values().length];
        avePrice = new double[DwellingType.values().length];

        for (DwellingType dt: DwellingType.values()) {
            int dto = dt.ordinal();

            if (vacOcc[0][dto] + vacOcc[1][dto] > 0) {
                aveVac[dto] = (double) vacOcc[0][dto] / (double) (vacOcc[0][dto] + vacOcc[1][dto]);
                avePrice[dto] = price[dto] / (double) (vacOcc[0][dto] + vacOcc[1][dto]);

            } else {
                aveVac[dto] = 0;
                avePrice[dto] = 0;
            }
        }
    }


    public double[][] getVacancyRateByTypeAndRegion() {
        // calculate vacancy rate by region and dwelling type

        int[] regionList = geoData.getRegionList();
        int[][][] vacOcc = SiloUtil.setArrayToValue(new int[2][DwellingType.values().length][SiloUtil.getHighestVal(regionList) + 1], 0);

        for (Dwelling dd: Dwelling.getDwellings()) {
            int dto = dd.getType().ordinal();
            if (dd.getResidentId() > 0) {
                vacOcc[1][dto][geoData.getRegionOfZone(dd.getZone())]++;
            } else {
                vacOcc[0][dto][geoData.getRegionOfZone(dd.getZone())]++;
            }
        }
        double[][] vacRate = new double[DwellingType.values().length][SiloUtil.getHighestVal(regionList) + 1];
        for (DwellingType dt: DwellingType.values()) {
            int dto = dt.ordinal();
            for (int region: geoData.getRegionList()) {
                if ((vacOcc[0][dto][region] + vacOcc[1][dto][region]) > 0) {
                    vacRate[dto][region] = (double) vacOcc[0][dto][region] / (double) (vacOcc[0][dto][region] + vacOcc[1][dto][region]);
                } else {
                    vacRate[dto][region] = 0.;
                }
            }
        }
        return vacRate;
    }


    public void setAvePriceByDwellingType(double[] newAvePrice) {
        avePrice = newAvePrice;
    }


    public double[] getAveragePriceByDwellingType() {
        return avePrice;
    }


    public double[] getAverageVacancyByDwellingType () {
        return aveVac;
    }


    public int[][] getDwellingCountByTypeAndRegion() {
        // return number of dwellings by type and region

        int[] regionList = geoData.getRegionList();
        int[][] dwellingCount =
                SiloUtil.setArrayToValue(new int[DwellingType.values().length][SiloUtil.getHighestVal(regionList) + 1], 1);

        for (Dwelling dd: Dwelling.getDwellings()) {
            dwellingCount[dd.getType().ordinal()][geoData.getRegionOfZone(dd.getZone())] ++;
        }
        return dwellingCount;
    }


    public double getAvailableLandForConstruction (int zone) {
        // return available land in developable land-use categories

        double sm;
        if (useDwellingCapacityForThisZone(zone)) {         // use absolute number of dwellings as capacity constraint
            sm = SiloUtil.rounder(developmentCapacity.getIndexedValueAt(zone, "DevCapacity"),0);  // some capacity values are not integer numbers, not sure why
        } else {
            sm = getDevelopableLand(zone);                            // use land use data
        }
        return sm;
    }


    public boolean useDwellingCapacityForThisZone (int zone) {
        // return true if capacity for number of dwellings is used in this zone, otherwise return false

        if (!useCapacityAsNumberOfDwellings) return false;
        try {
            developmentCapacity.getIndexedValueAt(zone, "DevCapacity");
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public double getDevelopableLand(int zone) {
        // return number of acres of available land for development

        double sm = 0;
        for (int type : developableLUtypes) {
            String column = "LU" + type;
            sm += landUse.getIndexedValueAt(zone, column);
        }
        return sm;
    }


    public boolean getWhetherConstructionIsPermitted(DwellingType dt, int zone) {
        // return true if DwellingType dt may be built in zone, otherwise return false

        int col = 0;
        try {
            col = developmentRestrictions.getColumnPosition(dt.toString());
        } catch (Exception e) {
            logger.error("Unknown DwellingType " + dt.toString() + ". Cannot find it in developmentRestrictions.");
            System.exit(1);
        }
        return (developmentRestrictions.getIndexedValueAt(zone, col) == 1);
    }


    public void convertLand(int zone, float acres) {
        // remove acres from developable land

        if (useDwellingCapacityForThisZone(zone)) {
            float capacity = developmentCapacity.getIndexedValueAt(zone, "DevCapacity") - 1f;
            if (SiloUtil.rounder(capacity,0) < 0) {        // some capacity values are not integer numbers, not sure why
                logger.error("In zone " + zone + " a dwelling was built even though the dwelling capacity was not available.");
                capacity = 0;
            }
            developmentCapacity.setIndexedValueAt(zone, "DevCapacity", capacity);
            return;
        } else {
            for (int type: developableLUtypes) {
                String column = "LU" + type;
                float existing = landUse.getIndexedValueAt(zone, column);
                if (existing < acres) {
                    landUse.setIndexedValueAt(zone, column, 0);
                    acres = acres - existing;
                } else {
                    float reduced = existing - acres;
                    landUse.setIndexedValueAt(zone, column, reduced);
                    acres = 0;
                }
                if (acres <= 0) return;
            }
        }
        logger.error("In zone " + zone + " new construction occurred even though not enough land was available.");
    }


    public float getDevelopmentCapacity (int zone) {
        return developmentCapacity.getIndexedValueAt(zone, "DevCapacity");
    }

}
