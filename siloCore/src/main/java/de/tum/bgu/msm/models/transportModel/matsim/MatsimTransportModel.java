/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package de.tum.bgu.msm.models.transportModel.matsim;

//import Implementation;

import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.models.transportModel.TransportModelI;
import de.tum.bgu.msm.properties.Properties;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.MatsimWriter;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripRouterFactoryBuilderWithDefaults;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;

import java.io.File;
import java.util.Objects;

//import GeoDataMuc;
//import de.tum.bgu.msm.data.travelTimes.TravelTimes;
//import org.matsim.core.router.util.TravelDisutility;
//import org.matsim.core.router.util.TravelTime;
//import org.matsim.core.utils.gis.ShapeFileReader;
//import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
//import org.opengis.feature.simple.SimpleFeature;
//import java.util.HashMap;
//import java.util.Map;

/**
 * @author dziemke
 */
public final class MatsimTransportModel implements TransportModelI  {
	private static final Logger LOG = Logger.getLogger( MatsimTransportModel.class );
	
	private final Config initialMatsimConfig;
	private final MatsimTravelTimes travelTimes;
//	private TripRouter tripRouter = null;
	private final SiloDataContainer dataContainer;
	
	
	public MatsimTransportModel(SiloDataContainer dataContainer, Config matsimConfig) {
		this.dataContainer = Objects.requireNonNull(dataContainer);
		this.initialMatsimConfig = Objects.requireNonNull(matsimConfig );
		this.travelTimes = (MatsimTravelTimes) Objects.requireNonNull(dataContainer.getTravelTimes());
	}

	@Override
	public void runTransportModel(int year) {
		LOG.warn("Running MATSim transport model for year " + year + ".");

		String scenarioName = Properties.get().main.scenarioName;

		initialMatsimConfig.global().setCoordinateSystem(Properties.get().transportModel.matsimZoneCRS);
//		String zoneShapeFile = Properties.get().main.baseDirectory + "/" + Properties.get().transportModel.matsimZoneShapeFile;
		
		// In the current implementation, MATSim is used to reflect the functionality that was previously
		// covered by MSTM. As such, based on the MATSim transport simulation, a travel time matrix (skim)
		// is computed. To do so, random coordinates in each zone are taken to measure the zone-to-zone
		// travel times. <code>numberOfCalcPoints</code> states how many such points in each zone are used;
		// in case multiple points are used; the average of all travel times of a given relation is used.
//		int numberOfCalcPoints = 1;
		boolean writePopulation = false;

//		double populationScalingFactor = 0.01;
		double populationScalingFactor = 1.; // For test
		
		// people working at non-peak times (only peak traffic is simulated), and people going by a mode other
		// than car in case a car is still available to them
		double workerScalingFactor = 0.66;
		
//		Map<Integer,SimpleFeature> zoneFeatureMap = new HashMap<>();
//		for (SimpleFeature feature: ShapeFileReader.getAllFeatures(zoneShapeFile)) {
//			Integer zoneId = (Integer) feature.getAttribute("SMZRMZ");
//			// (may fail, then go back to first converting to string and then Integer.valueOf(...)) ;
//			zoneFeatureMap.put(zoneId,feature);
//		}
		
		String matsimRunId = scenarioName + "_" + year;
		
		Config config = SiloMatsimUtils.createMatsimConfig(initialMatsimConfig, matsimRunId, populationScalingFactor, workerScalingFactor);
		
//		Population population = SiloMatsimUtils.createMatsimPopulation(config, dataContainer, zoneFeatureMap, populationScalingFactor * workerScalingFactor);
		Population population = SiloMatsimUtils.createMatsimPopulation(config, dataContainer, populationScalingFactor * workerScalingFactor);
		
		if (writePopulation) {
    		new File("./test/scenarios/annapolis_reduced/matsim_output/").mkdirs();
    		MatsimWriter populationWriter = new PopulationWriter(population);
    		populationWriter.write("./test/scenarios/annapolis_reduced/matsim_output/population_" + year + ".xml");
    	}

		// Get travel Times from MATSim
		LOG.warn("Using MATSim to compute travel times from zone to zone.");
		
		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);
		scenario.setPopulation(population);
		
		final Controler controler = new Controler(scenario);
		
		controler.run();
		LOG.warn("Running MATSim transport model for year " + year + " finished.");
		
		TravelTime travelTime = controler.getLinkTravelTimes();
        TravelDisutility travelDisutility = controler.getTravelDisutilityFactory().createTravelDisutility(travelTime);
        updateTravelTimes(scenario, controler.getTripRouterProvider().get(), travelTime, travelDisutility);
	}

    /**
     *
     * @param eventsFile
     */
	public void replayFromEvents(String eventsFile) {
        MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(initialMatsimConfig);
	    TravelTimeCalculator ttCalculator = TravelTimeCalculator.create(scenario.getNetwork(), scenario.getConfig().travelTimeCalculator());
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(ttCalculator);
        (new MatsimEventsReader(events)).readFile(eventsFile);
        TripRouter tripRouter = TripRouterFactoryBuilderWithDefaults.createDefaultTripRouterFactoryImpl(scenario).get();
        TravelTime travelTime = ttCalculator.getLinkTravelTimes();
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutilityFactory().createTravelDisutility(travelTime);
        updateTravelTimes(scenario, tripRouter, travelTime, travelDisutility);
	}

	private void updateTravelTimes(MutableScenario scenario, TripRouter tripRouter, TravelTime travelTime, TravelDisutility disutility) {
		LeastCostPathTree leastCoastPathTree = new LeastCostPathTree(travelTime, disutility);
//
////		travelTimes.update(leastCoastPathTree, zoneFeatureMap, scenario.getNetwork(), controler.getTripRouterProvider().get() );
//		// for now, pt inforamtion from MATSim not required as there are no changes in PT supply (schedule) expected currently;
//		// potentially revise this later; nk/dz, nov'17
//		//TODO: Optimize pt travel time query
////		MatsimPtTravelTimes matsimPtTravelTimes = new MatsimPtTravelTimes(controler.getTripRouterProvider().get(), zoneFeatureMap, scenario.getNetwork());
////		acc.addTravelTimeForMode(TransportMode.pt, matsimTravelTimes); // use car times for now also, as pt travel times are too slow to compute, Nico Oct 17
//
//		if (config.transit().isUseTransit() && Properties.get().main.implementation == Implementation.MUNICH) {
//			MatsimPTDistances matsimPTDistances = new MatsimPTDistances(config, scenario, (GeoDataMuc) dataContainer.getGeoData());
//		}
		travelTimes.update(tripRouter, dataContainer.getGeoData().getZones().values(),
				scenario.getNetwork(), leastCoastPathTree);

//		tripRouter = controler.getTripRouterProvider().get();
	}



	// Other idea; provide TripRouter more directly; requires more fundamental change, however
//	public final TripRouter getTripRouter() {
//		if(tripRouter == null) {
//			throw new RuntimeException("MATSim Transport Model needs to run at least once before trips can be queried!");
//		}
//		return tripRouter;
//	}
}