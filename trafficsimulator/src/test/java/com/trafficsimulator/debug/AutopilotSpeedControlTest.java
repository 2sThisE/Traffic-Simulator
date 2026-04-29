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
        road.setLimitSpeed(150);
        road.addLane(true, 0);
        roadManager.addRoad(road);

        Lane lane = road.getLane(0);
        List<Set<Lane>> route = List.of(Set.of(lane));

        Vehicle rearVehicle = new Vehicle(0, 0, 0, VehicleType.NORMAL, DriverPersonality.AGGRESSIVE);
        rearVehicle.setLogicalRoute(route);
        rearVehicle.setSpeedKmh(150.0);

        Vehicle stoppedVehicle = new Vehicle(UnitConverter.toPixel(200.0), 0, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        stoppedVehicle.setLogicalRoute(route);
        stoppedVehicle.setSpeedKmh(0.0);

        List<Vehicle> vehicles = List.of(rearVehicle, stoppedVehicle);

        double minSpeed = rearVehicle.getSpeedKmh();
        double stoppedVehicleRearX = stoppedVehicle.getX() - stoppedVehicle.getWidth() / 2.0;

        System.out.println("Tick | Speed (km/h) | Brake (delta) | Distance (m)");
        for (int i = 0; i < 500; i++) {
            double prevSpeed = rearVehicle.getSpeedKmh();
            rearVehicle.updateVisionArea(roadManager, junctionController, vehicles);
            rearVehicle.updateSpeedControl(roadManager);
            rearVehicle.updatePosition(junctionController);
            double currentSpeed = rearVehicle.getSpeedKmh();
            minSpeed = Math.min(minSpeed, currentSpeed);
            
            double delta = prevSpeed - currentSpeed;
            double rearVehicleFrontX = rearVehicle.getX() + rearVehicle.getWidth() / 2.0;
            double distancePx = stoppedVehicleRearX - rearVehicleFrontX;
            double distanceM = UnitConverter.toMeter(distancePx);

            System.out.printf("%4d | %12.2f | %13.2f | %12.2f%n", i, currentSpeed, delta > 0 ? delta : 0, distanceM);

            assertTrue(rearVehicleFrontX <= stoppedVehicleRearX + 0.1, 
                "Collision or overtake detected at tick " + i + "! Front: " + rearVehicleFrontX + ", Target Rear: " + stoppedVehicleRearX);
        }

        assertTrue(minSpeed < 150.0, "Rear vehicle should have braked.");
    }

    @Test
    void rearVehicleResumesAfterStoppedVehicleStartsMoving() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(2000.0), 0),
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

        // Front vehicle starts at 200m
        Vehicle frontVehicle = new Vehicle(UnitConverter.toPixel(200.0), 0, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        frontVehicle.setLogicalRoute(route);
        frontVehicle.setSpeedKmh(0.0);

        List<Vehicle> vehicles = List.of(rearVehicle, frontVehicle);

        System.out.println("\n--- Stop-and-Go Scenario ---");
        System.out.println("Tick | Rear Speed | Front Speed | Distance (m)");

        // Phase 1: Rear vehicle stops behind front vehicle (front vehicle stays at 0)
        boolean reachedStop = false;
        for (int i = 0; i < 1000; i++) {
            rearVehicle.updateVisionArea(roadManager, junctionController, vehicles);
            rearVehicle.updateSpeedControl(roadManager);
            rearVehicle.updatePosition(junctionController);
            
            // Front vehicle does NOT update speed/position, stays at 200m, 0km/h
            
            double distanceM = UnitConverter.toMeter(frontVehicle.getX() - rearVehicle.getX() - frontVehicle.getWidth());
            if (i % 50 == 0) {
                System.out.printf("%4d | %10.2f | %11.2f | %12.2f%n", i, rearVehicle.getSpeedKmh(), frontVehicle.getSpeedKmh(), distanceM);
            }

            if (rearVehicle.getSpeedKmh() < 0.1) {
                reachedStop = true;
                System.out.println("Rear vehicle stopped at tick " + i);
                break;
            }
        }

        assertTrue(reachedStop, "Rear vehicle should have stopped.");

        // Phase 2: Front vehicle starts moving
        System.out.println("--- Front vehicle starts moving ---");
        frontVehicle.setX(frontVehicle.getX() + 1.0); // Slightly move to break "stationary" detection
        frontVehicle.setSpeedKmh(50.0);
        
        boolean resumed = false;
        for (int i = 0; i < 1000; i++) {
            rearVehicle.updateVisionArea(roadManager, junctionController, vehicles);
            rearVehicle.updateSpeedControl(roadManager);
            rearVehicle.updatePosition(junctionController);
            
            // Front vehicle now moves at constant 50km/h (we update its position)
            double ms = UnitConverter.kmhToMs(frontVehicle.getSpeedKmh());
            double pixelPerTick = UnitConverter.toPixelPerTick(ms);
            frontVehicle.setX(frontVehicle.getX() + pixelPerTick);

            double distanceM = UnitConverter.toMeter(frontVehicle.getX() - rearVehicle.getX() - frontVehicle.getWidth());
            if (i % 50 == 0) {
                System.out.printf("%4d | %10.2f | %11.2f | %12.2f%n", i, rearVehicle.getSpeedKmh(), frontVehicle.getSpeedKmh(), distanceM);
            }

            if (rearVehicle.getSpeedKmh() > 5.0) {
                resumed = true;
                System.out.println("Rear vehicle resumed at tick " + i);
                break;
            }
        }

        assertTrue(resumed, "Rear vehicle should have resumed moving after front vehicle started.");
    }
}
