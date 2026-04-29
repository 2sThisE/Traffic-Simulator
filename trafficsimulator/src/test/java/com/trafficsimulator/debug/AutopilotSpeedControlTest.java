package com.trafficsimulator.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleType;

class AutopilotSpeedControlTest {

    @Test
    void rearVehicleSlowsDownForStoppedVehicleAheadOnStraightRoad() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                true
        );
        road.setLimitSpeed(100);
        road.addLane(true, 0);
        roadManager.addRoad(road);

        Lane lane = road.getLane(0);
        List<Set<Lane>> route = List.of(Set.of(lane));

        Vehicle rearVehicle = new Vehicle(0, 0, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        rearVehicle.setLogicalRoute(route);
        rearVehicle.setSpeedKmh(100.0);

        Vehicle stoppedVehicle = new Vehicle(UnitConverter.toPixel(200.0), 0, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        stoppedVehicle.setLogicalRoute(route);
        stoppedVehicle.setSpeedKmh(0.0);

        List<Vehicle> vehicles = List.of(rearVehicle, stoppedVehicle);

        double minSpeed = rearVehicle.getSpeedKmh();
        for (int i = 0; i < 20; i++) {
            rearVehicle.updateVisionArea(roadManager, junctionController, vehicles);
            rearVehicle.updateSpeedControl(roadManager);
            rearVehicle.updatePosition(junctionController);
            minSpeed = Math.min(minSpeed, rearVehicle.getSpeedKmh());
        }

        assertTrue(minSpeed < 100.0, "Rear vehicle should brake when the forward vehicle vision distance shrinks.");
    }
}
