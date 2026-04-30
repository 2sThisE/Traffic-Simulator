package com.trafficsimulator.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.trafficlight.SignalSetting;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleType;

class AutopilotTrafficLightTest {

    @Test
    void vehicleStopsAtRedThenStartsAtGreenAcrossStraightConnectedRoads() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road1 = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                true
        );
        road1.setLimitSpeed(60);
        road1.addLane(true, 0);
        roadManager.addRoad(road1);

        Road road2 = new Road(
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                true
        );
        road2.setLimitSpeed(60);
        road2.addLane(true, 0);
        roadManager.addRoad(road2);

        Lane road1Lane = road1.getLane(0);
        Lane road2Lane = road2.getLane(0);
        junctionController.addConnection(road1Lane, road2Lane, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(road1Lane);
        trafficLight.updatePositionToLanesCenter();
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 300.0)));
        trafficLight.resetCurrentTick();
        roadManager.addTrafficLight(trafficLight);

        Vehicle vehicle = new Vehicle(0, road1Lane.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        vehicle.setLogicalRoute(List.of(Set.of(road1Lane), Set.of(road2Lane)));
        vehicle.setSpeedKmh(60.0);
        List<Vehicle> vehicles = List.of(vehicle);

        double stopLineX = road1Lane.getLanePath().get(road1Lane.getLanePath().size() - 1).x;
        System.out.printf(
                "SETUP | road1=500.00m | road2=500.00m | limit1=%dkm/h | limit2=%dkm/h | stopLine=%.2fm | initialSignal=%s%n",
                road1.getLimitSpeed(),
                road2.getLimitSpeed(),
                UnitConverter.toMeter(stopLineX),
                List.of(trafficLight.currentSignal())
        );

        boolean stoppedAtRed = false;
        int stoppedTick = -1;
        for (int tick = 0; tick < 1_000; tick++) {
            runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
            logTrafficTick("RED", tick, vehicle, trafficLight, stopLineX);

            double frontX = vehicle.getX() + vehicle.getWidth() / 2.0;
            if (vehicle.getSpeedKmh() < 0.1 && frontX <= stopLineX + UnitConverter.toPixel(0.5)) {
                stoppedAtRed = true;
                stoppedTick = tick;
                System.out.printf(
                        "EVENT | stoppedAtRedTick=%d | center=%.2fm | front=%.2fm | stopLine=%.2fm | gap=%.2fm | speed=%.2fkm/h | signal=%s%n",
                        tick,
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(frontX),
                        UnitConverter.toMeter(stopLineX),
                        UnitConverter.toMeter(stopLineX - frontX),
                        vehicle.getSpeedKmh(),
                        List.of(trafficLight.currentSignal())
                );
                break;
            }
        }

        assertTrue(stoppedAtRed, "차량이 도로 시작점에서 빨간불을 발견하고 정지해야 합니다.");
        assertFalse(vehicle.isRouteFinished(), "빨간불 대기 중에는 경로가 끝나면 안 됩니다.");

        double stoppedFrontX = vehicle.getX() + vehicle.getWidth() / 2.0;
        assertTrue(stoppedFrontX <= stopLineX + UnitConverter.toPixel(0.5), "빨간불에서 정지선을 넘으면 안 됩니다.");
        assertTrue(stoppedFrontX >= stopLineX - UnitConverter.toPixel(12.0), "빨간불에서 정지선보다 지나치게 멀리 멈추면 안 됩니다.");

        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.STRAIGHT}, 300.0)));
        trafficLight.resetCurrentTick();
        System.out.printf("EVENT | signalChangedToGreenAfterStop | stoppedTick=%d | signal=%s%n", stoppedTick, List.of(trafficLight.currentSignal()));

        boolean startedAtGreen = false;
        boolean enteredSecondRoad = false;
        for (int tick = 0; tick < 300; tick++) {
            runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
            logTrafficTick("GREEN", tick, vehicle, trafficLight, stopLineX);

            if (!startedAtGreen && vehicle.getSpeedKmh() > 1.0) {
                startedAtGreen = true;
                System.out.printf(
                        "EVENT | startedAtGreenTick=%d | center=%.2fm | front=%.2fm | speed=%.2fkm/h | signal=%s%n",
                        tick,
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(vehicle.getX() + vehicle.getWidth() / 2.0),
                        vehicle.getSpeedKmh(),
                        List.of(trafficLight.currentSignal())
                );
            }

            if (vehicle.getCurrentPhaseIndex() >= 1 || vehicle.getX() > road2Lane.getLanePath().get(0).x) {
                enteredSecondRoad = true;
                System.out.printf(
                        "EVENT | enteredSecondRoadTick=%d | phase=%d | center=%.2fm | speed=%.2fkm/h%n",
                        tick,
                        vehicle.getCurrentPhaseIndex(),
                        UnitConverter.toMeter(vehicle.getX()),
                        vehicle.getSpeedKmh()
                );
                break;
            }
        }

        assertTrue(startedAtGreen, "초록불로 바뀌면 정차 상태에서 다시 출발해야 합니다.");
        assertTrue(enteredSecondRoad, "초록불 이후 직진 연결을 따라 두 번째 도로로 진입해야 합니다.");
    }

    @Test
    void vehicleDoesNotStopInIntersectionWhenPassedGreenLightTurnsRed() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road1 = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                true
        );
        road1.setLimitSpeed(60);
        road1.addLane(true, 0);
        roadManager.addRoad(road1);

        Road road2 = new Road(
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                true
        );
        road2.setLimitSpeed(60);
        road2.addLane(true, 0);
        roadManager.addRoad(road2);

        Lane road1Lane = road1.getLane(0);
        Lane road2Lane = road2.getLane(0);
        junctionController.addConnection(road1Lane, road2Lane, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(road1Lane);
        trafficLight.updatePositionToLanesCenter();
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.STRAIGHT}, 300.0)));
        trafficLight.resetCurrentTick();
        roadManager.addTrafficLight(trafficLight);

        Vehicle vehicle = new Vehicle(0, road1Lane.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        vehicle.setLogicalRoute(List.of(Set.of(road1Lane), Set.of(road2Lane)));
        vehicle.setSpeedKmh(60.0);
        List<Vehicle> vehicles = List.of(vehicle);

        double stopLineX = road1Lane.getLanePath().get(road1Lane.getLanePath().size() - 1).x;
        boolean changedToRedAfterPassing = false;
        boolean enteredSecondRoad = false;
        double minimumSpeedAfterRed = Double.POSITIVE_INFINITY;

        System.out.printf(
                "SETUP_GREEN_TO_RED | stopLine=%.2fm | initialSignal=%s%n",
                UnitConverter.toMeter(stopLineX),
                List.of(trafficLight.currentSignal())
        );

        for (int tick = 0; tick < 500; tick++) {
            runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
            logTrafficTick("GREEN_TO_RED", tick, vehicle, trafficLight, stopLineX);

            double frontX = vehicle.getX() + vehicle.getWidth() / 2.0;
            if (!changedToRedAfterPassing && frontX > stopLineX + UnitConverter.toPixel(1.0)) {
                trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 300.0)));
                trafficLight.resetCurrentTick();
                changedToRedAfterPassing = true;
                System.out.printf(
                        "EVENT | passedGreenThenSignalChangedRed | tick=%d | phase=%d | center=%.2fm | front=%.2fm | speed=%.2fkm/h | signal=%s%n",
                        tick,
                        vehicle.getCurrentPhaseIndex(),
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(frontX),
                        vehicle.getSpeedKmh(),
                        List.of(trafficLight.currentSignal())
                );
            }

            if (changedToRedAfterPassing) {
                minimumSpeedAfterRed = Math.min(minimumSpeedAfterRed, vehicle.getSpeedKmh());
            }

            if (vehicle.getCurrentPhaseIndex() >= 1 || vehicle.getX() > road2Lane.getLanePath().get(0).x) {
                enteredSecondRoad = true;
                System.out.printf(
                        "EVENT | enteredSecondRoadAfterRedTick=%d | phase=%d | center=%.2fm | speed=%.2fkm/h%n",
                        tick,
                        vehicle.getCurrentPhaseIndex(),
                        UnitConverter.toMeter(vehicle.getX()),
                        vehicle.getSpeedKmh()
                );
                break;
            }
        }

        assertTrue(changedToRedAfterPassing, "차량이 초록불로 정지선을 지난 뒤 테스트 신호를 빨간불로 바꿔야 합니다.");
        assertTrue(enteredSecondRoad, "이미 통과한 신호등이 빨간불로 바뀌어도 교차로 중간에서 멈추면 안 됩니다.");
        assertTrue(minimumSpeedAfterRed > 0.1, "통과한 신호등 때문에 차량 속도가 0으로 떨어지면 안 됩니다.");
    }

    @Test
    void vehicleBrakesFrom100KmhWhenVisibleGreenLightTurnsRedBeforeStopLine() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                true
        );
        road.setLimitSpeed(100);
        road.addLane(true, 0);
        roadManager.addRoad(road);

        Lane lane = road.getLane(0);
        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(lane);
        trafficLight.updatePositionToLanesCenter();
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.STRAIGHT}, 300.0)));
        trafficLight.resetCurrentTick();
        roadManager.addTrafficLight(trafficLight);

        double startX = UnitConverter.toPixel(700.0);
        Vehicle vehicle = new Vehicle(startX, lane.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        vehicle.setLogicalRoute(List.of(Set.of(lane)));
        vehicle.setSpeedKmh(100.0);
        List<Vehicle> vehicles = List.of(vehicle);

        double stopLineX = lane.getLanePath().get(lane.getLanePath().size() - 1).x;
        System.out.printf(
                "SETUP_100KMH_GREEN_TO_RED | road=1000.00m | limit=%dkm/h | start=%.2fm | stopLine=%.2fm | initialSignal=%s%n",
                road.getLimitSpeed(),
                UnitConverter.toMeter(vehicle.getX()),
                UnitConverter.toMeter(stopLineX),
                List.of(trafficLight.currentSignal())
        );

        runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
        double redChangeCenterM = UnitConverter.toMeter(vehicle.getX());
        double redChangeFrontM = UnitConverter.toMeter(vehicle.getX() + vehicle.getWidth() / 2.0);
        double redChangeGapM = UnitConverter.toMeter(stopLineX - (vehicle.getX() + vehicle.getWidth() / 2.0));
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 300.0)));
        trafficLight.resetCurrentTick();
        System.out.printf(
                "EVENT | greenSeenThenChangedToRedAt100Kmh | center=%.2fm | front=%.2fm | gap=%.2fm | speed=%.2fkm/h | signal=%s%n",
                redChangeCenterM,
                redChangeFrontM,
                redChangeGapM,
                vehicle.getSpeedKmh(),
                List.of(trafficLight.currentSignal())
        );
        System.out.println("BRAKE_100KMH | tick | centerM | frontM | gapM | speedBefore | speedAfter | brakeKmhTick | decelMs2 | movedM");

        boolean stoppedAtRed = false;
        double maxBrakeKmhPerTick = 0.0;
        double totalBrakeKmh = 0.0;
        int brakingTicks = 0;
        int stoppedTick = -1;

        for (int tick = 0; tick < 300; tick++) {
            double beforeSpeed = vehicle.getSpeedKmh();
            double beforeX = vehicle.getX();
            runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
            double afterSpeed = vehicle.getSpeedKmh();
            double brakeKmhPerTick = Math.max(0.0, beforeSpeed - afterSpeed);
            double decelMs2 = UnitConverter.kmhToMs(brakeKmhPerTick) / UnitConverter.TICK_INTERVAL_SECONDS;
            double movedM = UnitConverter.toMeter(vehicle.getX() - beforeX);
            double frontX = vehicle.getX() + vehicle.getWidth() / 2.0;
            double gapM = UnitConverter.toMeter(stopLineX - frontX);

            if (brakeKmhPerTick > 0.0) {
                maxBrakeKmhPerTick = Math.max(maxBrakeKmhPerTick, brakeKmhPerTick);
                totalBrakeKmh += brakeKmhPerTick;
                brakingTicks++;
            }

            if (tick < 20 || tick % 5 == 0 || afterSpeed < 5.0) {
                System.out.printf(
                        "BRAKE_100KMH | %04d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f%n",
                        tick,
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(frontX),
                        gapM,
                        beforeSpeed,
                        afterSpeed,
                        brakeKmhPerTick,
                        decelMs2,
                        movedM
                );
            }

            if (afterSpeed < 0.1) {
                stoppedAtRed = true;
                stoppedTick = tick;
                System.out.printf(
                        "EVENT | stoppedAfter100KmhRedChange | tick=%d | center=%.2fm | front=%.2fm | gap=%.2fm | maxBrake=%.2fkm/h/tick | avgBrake=%.2fkm/h/tick | brakingTicks=%d%n",
                        tick,
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(frontX),
                        gapM,
                        maxBrakeKmhPerTick,
                        brakingTicks == 0 ? 0.0 : totalBrakeKmh / brakingTicks,
                        brakingTicks
                );
                break;
            }
        }

        assertTrue(stoppedAtRed, "100km/h 주행 중 보이던 초록불이 빨간불로 바뀌면 정지해야 합니다.");
        assertTrue(vehicle.getX() + vehicle.getWidth() / 2.0 <= stopLineX + UnitConverter.toPixel(0.5), "100km/h 감속 후 정지선을 넘으면 안 됩니다.");
        assertTrue(maxBrakeKmhPerTick <= VehicleType.NORMAL.getHardBrakeKmhPerTick() + 1e-9, "브레이킹은 차량 타입의 하드 브레이크 한계를 넘으면 안 됩니다.");
        assertTrue(stoppedTick >= 0, "정지 tick 로그가 기록되어야 합니다.");
    }

    @Test
    void vehicleCommitsToPassWhenGreenNearStopLineThenSignalTurnsRed() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road road1 = new Road(
                new Point2D.Double(0, 0),
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                true
        );
        road1.setLimitSpeed(60);
        road1.addLane(true, 0);
        roadManager.addRoad(road1);

        Road road2 = new Road(
                new Point2D.Double(UnitConverter.toPixel(500.0), 0),
                new Point2D.Double(UnitConverter.toPixel(1000.0), 0),
                true
        );
        road2.setLimitSpeed(60);
        road2.addLane(true, 0);
        roadManager.addRoad(road2);

        Lane road1Lane = road1.getLane(0);
        Lane road2Lane = road2.getLane(0);
        junctionController.addConnection(road1Lane, road2Lane, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(road1Lane);
        trafficLight.updatePositionToLanesCenter();
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.STRAIGHT}, 300.0)));
        trafficLight.resetCurrentTick();
        roadManager.addTrafficLight(trafficLight);

        double stopLineX = road1Lane.getLanePath().get(road1Lane.getLanePath().size() - 1).x;
        double initialFrontGapM = 1.5;
        double vehicleX = stopLineX - UnitConverter.toPixel(initialFrontGapM) - new Vehicle(0, 0, 0, VehicleType.NORMAL).getWidth() / 2.0;
        Vehicle vehicle = new Vehicle(vehicleX, road1Lane.getLanePath().get(0).y, 0, VehicleType.NORMAL, DriverPersonality.MODEL);
        vehicle.setLogicalRoute(List.of(Set.of(road1Lane), Set.of(road2Lane)));
        vehicle.setSpeedKmh(0.0);
        List<Vehicle> vehicles = List.of(vehicle);

        System.out.printf(
                "SETUP_COMMIT_GREEN_THEN_RED | center=%.2fm | front=%.2fm | gap=%.2fm | signal=%s%n",
                UnitConverter.toMeter(vehicle.getX()),
                UnitConverter.toMeter(vehicle.getX() + vehicle.getWidth() / 2.0),
                UnitConverter.toMeter(stopLineX - (vehicle.getX() + vehicle.getWidth() / 2.0)),
                List.of(trafficLight.currentSignal())
        );

        runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
        System.out.printf(
                "EVENT | committedOnGreenNearStopLine | center=%.2fm | front=%.2fm | gap=%.2fm | speed=%.2fkm/h | signal=%s%n",
                UnitConverter.toMeter(vehicle.getX()),
                UnitConverter.toMeter(vehicle.getX() + vehicle.getWidth() / 2.0),
                UnitConverter.toMeter(stopLineX - (vehicle.getX() + vehicle.getWidth() / 2.0)),
                vehicle.getSpeedKmh(),
                List.of(trafficLight.currentSignal())
        );

        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 300.0)));
        trafficLight.resetCurrentTick();
        System.out.printf("EVENT | changedToRedAfterCommit | signal=%s%n", List.of(trafficLight.currentSignal()));

        boolean crossedStopLine = false;
        boolean enteredSecondRoad = false;
        double minSpeedAfterRed = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < 80; tick++) {
            runOneVehicleTick(vehicle, roadManager, junctionController, vehicles);
            double frontX = vehicle.getX() + vehicle.getWidth() / 2.0;
            double gapM = UnitConverter.toMeter(stopLineX - frontX);
            minSpeedAfterRed = Math.min(minSpeedAfterRed, vehicle.getSpeedKmh());

            if (tick < 20 || tick % 10 == 0) {
                System.out.printf(
                        "COMMIT_GREEN_THEN_RED | tick=%04d | phase=%d | center=%.2fm | front=%.2fm | gap=%.2fm | speed=%.2fkm/h | signal=%s%n",
                        tick,
                        vehicle.getCurrentPhaseIndex(),
                        UnitConverter.toMeter(vehicle.getX()),
                        UnitConverter.toMeter(frontX),
                        gapM,
                        vehicle.getSpeedKmh(),
                        List.of(trafficLight.currentSignal())
                );
            }

            if (frontX > stopLineX) {
                crossedStopLine = true;
            }
            if (vehicle.getCurrentPhaseIndex() >= 1 || vehicle.getX() > road2Lane.getLanePath().get(0).x) {
                enteredSecondRoad = true;
                break;
            }
        }

        assertTrue(crossedStopLine, "초록불에서 통과 확정한 뒤 빨간불로 바뀌어도 정지선을 넘어야 합니다.");
        assertTrue(enteredSecondRoad, "통과 확정 후에는 교차로 연결을 따라 다음 도로에 진입해야 합니다.");
        assertTrue(minSpeedAfterRed > 0.0, "통과 확정 후 빨간불 때문에 다시 완전 정지하면 안 됩니다.");
    }

    private void runOneVehicleTick(Vehicle vehicle, RoadManager roadManager, JunctionController junctionController, List<Vehicle> vehicles) {
        vehicle.updateVisionArea(roadManager, junctionController, vehicles);
        vehicle.updateSpeedControl(roadManager);
        vehicle.updatePosition(junctionController);
        vehicle.updateDynamicPath(junctionController, vehicles);
    }

    private void logTrafficTick(String phase, int tick, Vehicle vehicle, TrafficLight trafficLight, double stopLineX) {
        if (tick % 10 != 0 && vehicle.getSpeedKmh() >= 0.1) {
            return;
        }

        double centerM = UnitConverter.toMeter(vehicle.getX());
        double frontM = UnitConverter.toMeter(vehicle.getX() + vehicle.getWidth() / 2.0);
        double stopLineM = UnitConverter.toMeter(stopLineX);
        double gapM = stopLineM - frontM;
        System.out.printf(
                "%s | tick=%04d | phaseIndex=%d | center=%.2fm | front=%.2fm | stopLine=%.2fm | gap=%.2fm | speed=%.2fkm/h | routeFinished=%s | signal=%s%n",
                phase,
                tick,
                vehicle.getCurrentPhaseIndex(),
                centerM,
                frontM,
                stopLineM,
                gapM,
                vehicle.getSpeedKmh(),
                vehicle.isRouteFinished(),
                List.of(trafficLight.currentSignal())
        );
    }
}
