package com.trafficsimulator.ui;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.trafficsimulator.debug.Vehicle;
import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.util.Navigate;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleType;

public class VehicleController {
    public static final int MAX_VEHICLE_COUNT = 100;
    public static final int VEHICLE_SPAWN_INTERVAL_TICKS = 10;

    private static final double NORMAL_VEHICLE_RATIO = 0.75;
    private static final double LIGHT_TRUCK_RATIO = 0.12;
    private static final double HEAVY_TRUCK_RATIO = 0.08;
    private static final double BUS_RATIO = 0.05;

    private static final double MODEL_DRIVER_RATIO = 0.15;
    private static final double AVERAGE_DRIVER_RATIO = 0.70;
    private static final double AGGRESSIVE_DRIVER_RATIO = 0.15;

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final RoadManager roadManager;
    private final JunctionController junctionController;
    private final Random random = new Random();
    private final List<Lane> spawnLanes = new ArrayList<>();
    private int spawnTickCounter = 0;
    private boolean spawnLanesInitialized = false;

    public VehicleController(RoadManager roadManager, JunctionController junctionController) {
        this.roadManager = roadManager;
        this.junctionController = junctionController;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public void prepareSimulation() {
        refreshSpawnLanes();
        spawnTickCounter = 0;
    }

    public Vehicle findVehicleHit(Point2D.Double worldPt) {
        for (Vehicle vehicle : vehicles) {
            double dx = vehicle.getX() - worldPt.x;
            double dy = vehicle.getY() - worldPt.y;
            if (Math.sqrt(dx * dx + dy * dy) < 20.0) {
                return vehicle;
            }
        }
        return null;
    }

    public Vehicle createVehicleOnLane(Point2D.Double worldPt, Lane lane, VehicleType type) {
        return createVehicleOnLane(worldPt, lane, type, DriverPersonality.AVERAGE);
    }

    public Vehicle createVehicleOnLane(Point2D.Double worldPt, Lane lane, VehicleType type, DriverPersonality personality) {
        Vehicle vehicle = new Vehicle(worldPt.x, worldPt.y, 0, type, personality);
        assignRandomRoute(vehicle, lane);
        vehicle.snapToNearestPoint(worldPt, junctionController);
        vehicle.updateDynamicPath(junctionController, vehicles);
        vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        vehicles.add(vehicle);
        return vehicle;
    }

    public boolean updateRouteFromLane(Vehicle vehicle, Point2D.Double worldPt, Lane lane) {
        vehicle.snapToNearestPoint(worldPt, junctionController);
        return assignRoute(vehicle, lane);
    }

    public void dragVehicle(Vehicle vehicle, Point2D.Double worldPt) {
        vehicle.snapToNearestPoint(worldPt, junctionController);
        vehicle.updateDynamicPath(junctionController, vehicles);
        updateAllVisionAreas();
    }

    public void updateAllVisionAreas() {
        for (Vehicle vehicle : vehicles) {
            vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        }
    }

    public void onTick() {
        ensureSpawnLanesInitialized();
        spawnTickCounter++;
        if (spawnTickCounter >= VEHICLE_SPAWN_INTERVAL_TICKS) {
            spawnTickCounter = 0;
            spawnVehicleFromRandomStartLane();
        }

        Iterator<Vehicle> iterator = vehicles.iterator();
        while (iterator.hasNext()) {
            Vehicle vehicle = iterator.next();
            vehicle.updatePosition(junctionController);
            if (vehicle.isRouteFinished()) {
                iterator.remove();
                continue;
            }
            vehicle.updateVisionArea(roadManager, junctionController, vehicles);
            vehicle.updateDynamicPath(junctionController, vehicles);
        }
    }

    private void ensureSpawnLanesInitialized() {
        if (!spawnLanesInitialized) {
            refreshSpawnLanes();
        }
    }

    private void refreshSpawnLanes() {
        spawnLanes.clear();
        spawnLanes.addAll(Navigate.findGlobalEndpoints(roadManager, junctionController).startLanes);
        spawnLanesInitialized = true;
    }

    private void spawnVehicleFromRandomStartLane() {
        if (vehicles.size() >= MAX_VEHICLE_COUNT || spawnLanes.isEmpty()) {
            return;
        }

        Lane spawnLane = spawnLanes.get(random.nextInt(spawnLanes.size()));
        Point2D.Double spawnPoint = getSpawnPoint(spawnLane);
        if (spawnPoint == null) {
            return;
        }

        Vehicle vehicle = new Vehicle(spawnPoint.x, spawnPoint.y, 0, randomVehicleType(), randomDriverPersonality());
        if (!assignRandomRoute(vehicle, spawnLane)) {
            return;
        }
        vehicle.snapToNearestPoint(spawnPoint, junctionController);
        vehicle.updateDynamicPath(junctionController, vehicles);
        vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        vehicles.add(vehicle);

        Road spawnRoad = roadManager.findRoadByLane(spawnLane);
        if (spawnRoad != null) {
            vehicle.setSpeedKmh(spawnRoad.getLimitSpeed());
        }
    }

    private Point2D.Double getSpawnPoint(Lane lane) {
        List<Point2D.Double> lanePath = lane.getLanePath();
        if (lanePath == null || lanePath.isEmpty()) {
            return null;
        }

        Point2D.Double point = lane.isRoadDirection()
                ? lanePath.get(0)
                : lanePath.get(lanePath.size() - 1);
        return new Point2D.Double(point.x, point.y);
    }

    private VehicleType randomVehicleType() {
        double roll = random.nextDouble();
        if (roll < NORMAL_VEHICLE_RATIO) return VehicleType.NORMAL;
        roll -= NORMAL_VEHICLE_RATIO;
        if (roll < LIGHT_TRUCK_RATIO) return VehicleType.LIGHT_TRUCK;
        roll -= LIGHT_TRUCK_RATIO;
        if (roll < HEAVY_TRUCK_RATIO) return VehicleType.HEAVY_TRUCK;
        return VehicleType.BUS;
    }

    private DriverPersonality randomDriverPersonality() {
        double roll = random.nextDouble();
        if (roll < MODEL_DRIVER_RATIO) return DriverPersonality.MODEL;
        roll -= MODEL_DRIVER_RATIO;
        if (roll < AVERAGE_DRIVER_RATIO) return DriverPersonality.AVERAGE;
        return DriverPersonality.AGGRESSIVE;
    }

    private boolean assignRoute(Vehicle vehicle, Lane lane) {
        List<Set<Lane>> route = Navigate.calculateRoute(lane, roadManager, junctionController);
        if (route == null) {
            return false;
        }

        vehicle.setLogicalRoute(route);
        vehicle.setCurrentPhaseIndex(0);
        vehicle.updateDynamicPath(junctionController, vehicles);
        vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        return true;
    }

    private boolean assignRandomRoute(Vehicle vehicle, Lane lane) {
        List<List<Set<Lane>>> routes = Navigate.calculateAllRoutes(lane, roadManager, junctionController);
        if (routes.isEmpty()) {
            return false;
        }

        List<Set<Lane>> route = routes.get(random.nextInt(routes.size()));
        vehicle.setLogicalRoute(route);
        vehicle.setCurrentPhaseIndex(0);
        vehicle.updateDynamicPath(junctionController, vehicles);
        vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        return true;
    }
}
