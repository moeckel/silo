package de.tum.bgu.msm.properties.modules;

import de.tum.bgu.msm.properties.PropertiesUtil;

import java.util.ResourceBundle;

public class RealEstateProperties {

    public final boolean readBinaryDwellingFile;
    public final boolean writeBinDwellingsFile;
    public final String dwellingsFileName;
    public final String dwellingsFinalFileName;
    public final String dwellingTypeAcresFile;
    public final int maxStorageOfVacantDwellings;
    public final String binaryDwellingsFile;
    public final double[] structuralVacancy;
    public final boolean constructionOverwriteDwelling;
    public final boolean traceOverwriteDwellings;
    public final String overWriteDwellingsTraceFile;
    public final String constructionOverwriteDwellingFile;
    public final boolean makeSomeNewDdAffordable;
    public final float affordableDwellingsShare;
    public final float levelOfAffordability;
    public final float constructionLogModelBeta;
    public final float constructionLogModelInflator;

    public RealEstateProperties(ResourceBundle bundle) {
        PropertiesUtil.newPropertySubmodule("Real state properties");
        PropertiesUtil.newPropertySubmodule("Real state - dwelling input data");
        dwellingsFileName = PropertiesUtil.getStringProperty(bundle, "dwelling.file.ascii", "microData/dd");
        dwellingsFinalFileName = PropertiesUtil.getStringProperty(bundle, "dwelling.final.file.ascii", "microData/futureYears/dd");
        dwellingTypeAcresFile = PropertiesUtil.getStringProperty(bundle, "developer.acres.per.dwelling.by.type", "input/acresPerDwellingByType.csv");
        maxStorageOfVacantDwellings = PropertiesUtil.getIntProperty(bundle, "vacant.dd.by.reg.array", 100000);
        readBinaryDwellingFile = PropertiesUtil.getBooleanProperty(bundle, "read.binary.dd.file", false);
        writeBinDwellingsFile = PropertiesUtil.getBooleanProperty(bundle, "write.binary.dd.file", false);
        binaryDwellingsFile = PropertiesUtil.getStringProperty(bundle, "dwellings.file.bin", "microData/ddData.bin");
        structuralVacancy = PropertiesUtil.getDoublePropertyArray(bundle, "vacancy.rate.by.type", new double[]{0.01,0.03,0.05,0.04,0.03});

        PropertiesUtil.newPropertySubmodule("Real state - model parameters and input");
        constructionOverwriteDwelling = PropertiesUtil.getBooleanProperty(bundle, "construct.dwelling.use.overwrite", false);
        traceOverwriteDwellings = PropertiesUtil.getBooleanProperty(bundle, "trace.use.of.overwrite.dwellings", false);
        overWriteDwellingsTraceFile = PropertiesUtil.getStringProperty(bundle,"trace.file.for.overwrite.dwellings", "useOfOverwriteDwellings");
        constructionOverwriteDwellingFile = PropertiesUtil.getStringProperty(bundle, "construct.dwelling.overwrite", "input/assumptions/dwellingOverwrite_empty.csv");
        makeSomeNewDdAffordable = PropertiesUtil.getBooleanProperty(bundle, "make.new.dwellings.partly.affordable", false);
        affordableDwellingsShare = (float) PropertiesUtil.getDoubleProperty(bundle, "share.of.affordable.dwellings", 1.0);
        levelOfAffordability = (float) PropertiesUtil.getDoubleProperty(bundle, "level.of.affordability.setting", 0.3);
        constructionLogModelBeta = (float) PropertiesUtil.getDoubleProperty(bundle, "construct.dwelling.mn.log.model.beta", 0.5);
        constructionLogModelInflator = (float) PropertiesUtil.getDoubleProperty(bundle, "construct.dwelling.mn.log.model.inflator", 1.5);
    }
}
