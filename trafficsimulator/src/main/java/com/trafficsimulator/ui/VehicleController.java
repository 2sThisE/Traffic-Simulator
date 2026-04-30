package com.trafficsimulator.ui;

import java.awt.geom.Area;
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
    public static final double VEHICLE_SPAWN_INTERVAL_TICKS = 5;

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

        checkCollisions(); // 충돌 검사 로직 추가 ❤️

        Iterator<Vehicle> iterator = vehicles.iterator();
        while (iterator.hasNext()) {
            Vehicle vehicle = iterator.next();
            
            // 충돌한 차량 처리 로직 ❤️
            if (vehicle.isCrashed()) {
                vehicle.incrementCrashTick();
                if (vehicle.getCrashTickCounter() >= 200) { // 200틱 후 소멸
                    iterator.remove();
                }
                continue; // 사고난 차량은 나머지 로직(이동, 감지) 건너뜀
            }

            vehicle.updateVisionArea(roadManager, junctionController, vehicles);
            vehicle.updateSpeedControl(roadManager);
            vehicle.updatePosition(junctionController);
            if (vehicle.isRouteFinished()) {
                iterator.remove();
                continue;
            }
            vehicle.updateDynamicPath(junctionController, vehicles);
        }
    }

    /**
     * 차량 간의 충돌을 검사하고 처리합니다. ❤️
     * O(N^2) 검사를 최소화하기 위해 거리 기반 1차 필터링 후 영역 겹침 검사를 수행합니다.
     */
    private void checkCollisions() {
        int n = vehicles.size();
        for (int i = 0; i < n; i++) {
            Vehicle v1 = vehicles.get(i);

            for (int j = i + 1; j < n; j++) {
                Vehicle v2 = vehicles.get(j);

                // 둘 다 이미 사고난 차량이면 서로 검사할 필요 없음 (최적화) ❤️
                if (v1.isCrashed() && v2.isCrashed()) continue;

                // 교차점 없이 겹치는 도로(오버패스 등)에서의 충돌 방지 필터링 ❤️
                Road r1 = v1.getCurrentRoad(roadManager);
                Road r2 = v2.getCurrentRoad(roadManager);
                if (r1 != null && r2 != null && !RoadManager.areRoadsConnected(r1, r2)) {
                    continue; 
                }

                // 1. 빠른 거리 기반 필터링 (Fast Distance Filter)
                double dx = v1.getX() - v2.getX();
                double dy = v1.getY() - v2.getY();
                double distSq = dx * dx + dy * dy;
                
                // 두 차량의 대각선 길이 합의 제곱을 임계값으로 사용 (안전마진 포함)
                // width/2와 height/2로 구성된 직각삼각형의 빗변 길이의 합
                double radius1 = Math.sqrt(v1.getWidth() * v1.getWidth() / 4.0 + v1.getHeight() * v1.getHeight() / 4.0);
                double radius2 = Math.sqrt(v2.getWidth() * v2.getWidth() / 4.0 + v2.getHeight() * v2.getHeight() / 4.0);
                double thresholdDistSq = Math.pow(radius1 + radius2, 2);

                if (distSq <= thresholdDistSq) {
                    // 2. 정밀 영역 교차 검사 (Precise Intersection Test)
                    Area a1 = new Area(v1.getShape());
                    Area a2 = new Area(v2.getShape());
                    a1.intersect(a2);
                    
                    if (!a1.isEmpty()) { // 영역이 겹쳤다면 충돌!
                        v1.setCrashed();
                        v2.setCrashed();
                        // v1이 새로 사고났고 j 루프 진행 중일 수 있으나, 
                        // 어차피 겹친 차량은 다 사고처리 해야 하므로 break 하지 않음 ❤️
                    }
                }
            }
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
