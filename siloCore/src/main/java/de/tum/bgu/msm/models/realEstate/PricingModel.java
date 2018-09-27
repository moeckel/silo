package de.tum.bgu.msm.models.realEstate;

import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.DwellingType;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.properties.Properties;
import org.apache.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;

/**
 * Updates prices of dwellings based on current demand
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 24 Febuary 2010 in Santa Fe
 **/

public final class PricingModel extends AbstractModel {

    static Logger logger = Logger.getLogger(PricingModel.class);

    private PricingJSCalculator pricingCalculator;

    private double inflectionLow;
    private double inflectionHigh;
    private double slopeLow;
    private double slopeMain;
    private double slopeHigh;
    private double maxDelta;
    private double[] structuralVacancy;


    public PricingModel (SiloDataContainer dataContainer) {
        super(dataContainer);
        setupPricingModel();
    }


    private void setupPricingModel() {

        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("PricingCalc"));
        pricingCalculator = new PricingJSCalculator(reader);

        inflectionLow = pricingCalculator.getLowInflectionPoint();
        inflectionHigh = pricingCalculator.getHighInflectionPoint();
        slopeLow = pricingCalculator.getLowerSlope();
        slopeMain = pricingCalculator.getMainSlope();
        slopeHigh = pricingCalculator.getHighSlope();
        maxDelta = pricingCalculator.getMaximumChange();
        structuralVacancy = Properties.get().realEstate.structuralVacancy;
    }


    public void updatedRealEstatePrices () {
        // updated prices based on current demand
        logger.info("  Updating real-estate prices");

        // get vacancy rate
        double[][] vacRate = dataContainer.getRealEstateData().getVacancyRateByTypeAndRegion();

        HashMap<String, Integer> priceChange = new HashMap<>();

        int[] cnt = new int[DwellingType.values().length];
        double[] sumOfPrices = new double[DwellingType.values().length];
        for (Dwelling dd: dataContainer.getRealEstateData().getDwellings()) {
            if (dd.getRestriction() != 0) continue;  // dwelling is under affordable-housing constraints, rent cannot be raised
            int dto = dd.getType().ordinal();
            float structuralVacLow = (float) (structuralVacancy[dto] * inflectionLow);
            float structuralVacHigh = (float) (structuralVacancy[dto] * inflectionHigh);
            int currentPrice = dd.getPrice();

            int region = dataContainer.getGeoData().getZones().get(dd.getZoneId()).getRegion().getId();
            double changeRate;
            if (vacRate[dto][region] < structuralVacLow) {
                // vacancy is particularly low, prices need to rise steeply
                changeRate = 1 - structuralVacLow * slopeLow +
                        (-structuralVacancy[dto] * slopeMain + structuralVacLow * slopeMain) +
                        slopeLow * vacRate[dto][region];
            } else if (vacRate[dto][region] < structuralVacHigh) {
                // vacancy is within a normal range, prices change gradually
                changeRate = 1 - structuralVacancy[dto] * slopeMain + slopeMain * vacRate[dto][region];
            } else {
                // vacancy is very low, prices do not change much anymore
                changeRate = 1 - structuralVacHigh * slopeHigh +
                        (-structuralVacancy[dto]*slopeMain + structuralVacHigh * slopeMain) +
                        slopeHigh * vacRate[dto][region];
            }
            changeRate = Math.min(changeRate, 1f + maxDelta);
            changeRate = Math.max(changeRate, 1f - maxDelta);
            double newPrice = currentPrice * changeRate;

            if (dd.getId() == SiloUtil.trackDd) {
                SiloUtil.trackWriter.println("The monthly costs of dwelling " +
                        dd.getId() + " was changed from " + currentPrice + " to " + newPrice + " (in 2000$).");
            }
            dd.setPrice((int) (newPrice + 0.5));
            cnt[dto]++;
            sumOfPrices[dto] += newPrice;

            String token = dto+"_"+vacRate[dto][region]+"_"+currentPrice+"_"+newPrice;
            if (priceChange.containsKey(token)) {
                priceChange.put(token, (priceChange.get(token) + 1));
            } else {
                priceChange.put(token, 1);
            }

        }
        double[] averagePrice = new double[DwellingType.values().length];
        for (DwellingType dt: DwellingType.values()) {
            int dto = dt.ordinal();
            averagePrice[dto] = sumOfPrices[dto] / cnt[dto];
        }
        dataContainer.getRealEstateData().setAvePriceByDwellingType(averagePrice);
    }
}
