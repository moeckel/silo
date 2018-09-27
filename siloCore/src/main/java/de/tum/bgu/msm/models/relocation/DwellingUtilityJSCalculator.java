package de.tum.bgu.msm.models.relocation;

import de.tum.bgu.msm.data.HouseholdType;
import de.tum.bgu.msm.util.js.JavaScriptCalculator;

import java.io.Reader;

public class DwellingUtilityJSCalculator extends JavaScriptCalculator<Double> {
    public DwellingUtilityJSCalculator(Reader reader) {
        super(reader);
    }

    public double calculateSelectDwellingUtility(HouseholdType ht, double ddSizeUtility, double ddPriceUtility,
                                                 double ddQualityUtility, double ddAutoAccessibilityUtility,
                                                 double transitAccessibilityUtility) {
       return super.calculate("calculateSelectDwellingUtility", ht, ddSizeUtility, ddPriceUtility,
                ddQualityUtility, ddAutoAccessibilityUtility,
                transitAccessibilityUtility);
    }

    public double personalizeUtility(HouseholdType ht, double genericUtility, double workDistanceUtility, double travelCostUtility) {
        return super.calculate("personalizeDwellingUtility", ht, genericUtility, workDistanceUtility, travelCostUtility);
    }
}
