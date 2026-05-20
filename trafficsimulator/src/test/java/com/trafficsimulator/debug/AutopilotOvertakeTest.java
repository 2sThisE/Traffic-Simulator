package com.trafficsimulator.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleType;

class AutopilotOvertakeTest {

    @Test
    void testStoppedTrafficChangesToFreeAdjacentLane() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();
        Road road = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(300.0), 0),
                true
        );
        road.setLimitSpeed(60);
        road.addLane(true, 0);
        road.addLane(true, 1);
        roadManager.addRoad(road);

        Lane lane0 = road.getLane(0);
        Lane lane1 = road.getLane(1);
        List<Set<Lane>> route = List.of(Set.of(lane0, lane1));

        Vehicle ego = new Vehicle(0, lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.AVERAGE);
        ego.setLogicalRoute(route);
        ego.setSpeedKmh(0.0);

        Vehicle stoppedFront = new Vehicle(UnitConverter.toPixel(15.0), lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        stoppedFront.setLogicalRoute(route);
        stoppedFront.setSpeedKmh(0.0);

        List<Vehicle> vehicles = List.of(ego, stoppedFront);
        ego.updateVisionArea(roadManager, junctionController, vehicles);
        ego.updateDynamicPath(junctionController, vehicles);

        assertFalse(ego.getPath().isEmpty(), "정체 상황에서 옆차선이 비어 있으면 차선 변경 경로를 만들어야 합니다.");
    }

    @Test
    void testDoesNotChangeIntoLaneWithCrashedVehicleAhead() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();
        Road road = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(300.0), 0),
                true
        );
        road.setLimitSpeed(60);
        road.addLane(true, 0);
        road.addLane(true, 1);
        roadManager.addRoad(road);

        Lane lane0 = road.getLane(0);
        Lane lane1 = road.getLane(1);
        List<Set<Lane>> route = List.of(Set.of(lane0, lane1));

        Vehicle ego = new Vehicle(0, lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.AVERAGE);
        ego.setLogicalRoute(route);
        ego.setSpeedKmh(60.0);

        Vehicle stoppedFront = new Vehicle(UnitConverter.toPixel(20.0), lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        stoppedFront.setLogicalRoute(route);
        stoppedFront.setSpeedKmh(0.0);

        Vehicle crashedAhead = new Vehicle(UnitConverter.toPixel(70.0), lane1.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        crashedAhead.setLogicalRoute(route);
        crashedAhead.setCrashed();

        List<Vehicle> vehicles = List.of(ego, stoppedFront, crashedAhead);
        ego.updateVisionArea(roadManager, junctionController, vehicles);
        ego.updateDynamicPath(junctionController, vehicles);

        assertTrue(ego.getPath().isEmpty(), "목표 차선 전방 80m 안에 사고 차량이 있으면 차선 변경하지 않아야 합니다.");
    }

    @Test
    void testIntelligentLaneChangeAndReturn() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        // 1. 도로 생성 (1km)
        Road road1 = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                true
        );
        road1.setLimitSpeed(100); // 100km/h
        road1.addLane(true, 0); // 1차선 (lane0)
        road1.addLane(true, 1); // 2차선 (lane1)
        roadManager.addRoad(road1);

        // 2. 목적지 도로 생성 (1차선만 존재, 500m)
        Road road2 = new Road(
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                new Point2D.Double(UnitConverter.toPixel(1500.0), 0),
                true
        );
        road2.setLimitSpeed(100);
        road2.addLane(true, 0); // 1차선 (lane0)
        roadManager.addRoad(road2);

        Lane r1_lane0 = road1.getLane(0);
        Lane r1_lane1 = road1.getLane(1);
        Lane r2_lane0 = road2.getLane(0);

        // 3. 교차로 연결 (1차선 -> 1차선)
        junctionController.addConnection(r1_lane0, r2_lane0, com.trafficsimulator.road.LaneType.STRAIGHT);

        // 경로 설정: [road1의 1, 2차선] -> [road2의 1차선]
        List<Set<Lane>> route = List.of(Set.of(r1_lane0, r1_lane1), Set.of(r2_lane0));

        // 4. 차량 배치
        // 뒤차 (빠른 차) - 1차선(Y=0 부근), 0m, 100km/h
        Vehicle fastCar = new Vehicle(0, r1_lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.AGGRESSIVE);
        fastCar.setLogicalRoute(route);
        fastCar.setSpeedKmh(100.0);

        // 앞차 (느린 차) - 1차선, 200m, 40km/h
        Vehicle slowCar = new Vehicle(UnitConverter.toPixel(200.0), r1_lane0.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        slowCar.setLogicalRoute(route);
        slowCar.setSpeedKmh(40.0);

        List<Vehicle> vehicles = List.of(fastCar, slowCar);

        System.out.println("Tick | Fast X(m) | Fast Y(px) | Fast Spd | Slow X(m) | Slow Spd");

        boolean movedToLane1 = false;
        boolean returnedToLane0 = false;
        
        double lane0Y = r1_lane0.getLanePath().get(0).y;
        double lane1Y = r1_lane1.getLanePath().get(0).y;
        
        System.out.println("Lane0 Y: " + lane0Y + ", Lane1 Y: " + lane1Y);

        for (int i = 0; i < 2500; i++) {
            // Update Fast Car
            fastCar.updateVisionArea(roadManager, junctionController, vehicles);
            fastCar.updateSpeedControl(roadManager);
            fastCar.updateDynamicPath(junctionController, vehicles);
            fastCar.updatePosition(junctionController);

            // Update Slow Car
            slowCar.updateVisionArea(roadManager, junctionController, vehicles);
            // slowCar.updateSpeedControl(roadManager); // 속도 40km/h 유지
            slowCar.updateDynamicPath(junctionController, vehicles);
            slowCar.updatePosition(junctionController);

            double fastX = UnitConverter.toMeter(fastCar.getX());
            double slowX = UnitConverter.toMeter(slowCar.getX());

            if (i % 50 == 0) {
                System.out.printf("%4d | %9.2f | %10.2f | %8.2f | %9.2f | %8.2f%n",
                        i, fastX, fastCar.getY(), fastCar.getSpeedKmh(), slowX, slowCar.getSpeedKmh());
            }

            // 차선 변경 확인
            // fastCar의 Y 좌표가 lane1Y에 가까워지면 차선 변경 성공으로 판단
            if (Math.abs(fastCar.getY() - lane1Y) < UnitConverter.toPixel(1.0)) {
                movedToLane1 = true;
            }
            
            // lane1로 갔다가 다시 lane0로 돌아왔는지 확인 (추월 후 복귀)
            if (movedToLane1 && fastX > slowX + 5.0 && Math.abs(fastCar.getY() - lane0Y) < UnitConverter.toPixel(1.0)) {
                returnedToLane0 = true;
            }
            
            if (returnedToLane0 && fastX > 800.0) {
                break; // 목적 달성 시 조기 종료
            }
        }

        assertTrue(movedToLane1, "Fast car should have moved to lane 1 to overtake.");
        assertTrue(returnedToLane0, "Fast car should have returned to lane 0 after overtaking.");
    }
}
