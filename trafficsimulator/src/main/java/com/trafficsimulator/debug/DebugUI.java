package com.trafficsimulator.debug;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.ui.SimulatorController;
import com.trafficsimulator.util.Navigate;
import com.trafficsimulator.vehicle.VehicleType;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Debug UI with Correct Right-Hand Traffic (RHT) 4-way Intersection.
 */
public class DebugUI extends Application {

    private RoadManager roadManager;
    private JunctionController junctionController;
    private SimulatorController controller;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/trafficsimulator/ui/simulator_main.fxml"));
        Parent root = loader.load();
        
        controller = loader.getController();
        controller.setDebugMode(true); // 디버그 모드 활성화 ❤️
        roadManager = controller.getRoadManager();
        junctionController = controller.getJunctionController();

        setupIntersection();

        // 차량 추가 (West 도로의 진입 차선 Lane 3에 배치)
        // West 도로 좌표: (-530, 0) ~ (-130, 0), Lane 3 Y-offset: 96
        Vehicle car = new Vehicle(-300, 150, 0, VehicleType.HEAVY_TRUCK);
        
        // 차량의 이동 경로(베지어 커브 포함) 계산 및 설정
        Road westRoad = roadManager.getRoadList().stream()
                .filter(r -> r.getStartPoint().x < -200)
                .findFirst().orElse(null);
        if (westRoad != null) {
            Lane startLane = westRoad.getLane(3);
            List<Set<Lane>> route = Navigate.calculateRoute(startLane, roadManager, junctionController);
            if (route != null) {
                List<Point2D.Double> pathPoints = Navigate.generateTotalPathPoints(route, junctionController);
                car.setPath(pathPoints);
            }
        }
        
        controller.getVehicles().add(car);

        primaryStage.setTitle("Traffic Simulator - Debug Mode (RHT Intersection)");
        primaryStage.setScene(new Scene(root, 1200, 800));
        primaryStage.show();

        controller.requestRender();
    }

    private void setupIntersection() {
        double intersectionSize = 130; // Sufficient gap for 4-lane roads (width ~256px)
        double roadLength = 400;
        
        // Roads defined starting from outside, ending at junction (0,0)
        Road north = new Road(new Point2D.Double(0, -intersectionSize - roadLength), new Point2D.Double(0, -intersectionSize), false);
        Road south = new Road(new Point2D.Double(0, intersectionSize + roadLength), new Point2D.Double(0, intersectionSize), false);
        Road west = new Road(new Point2D.Double(-intersectionSize - roadLength, 0), new Point2D.Double(-intersectionSize, 0), false);
        Road east = new Road(new Point2D.Double(intersectionSize + roadLength, 0), new Point2D.Double(intersectionSize, 0), false);

        // --- Lane Setup (Right-Hand Traffic) ---
        // For North/South roads (Vertical): Lanes 0,1 are Right side (Incoming), Lanes 2,3 are Left side (Outgoing)
        setupLanes(north, false, false, true, true);
        setupLanes(south, false, false, true, true);
        
        // For West/East roads (Horizontal): Lanes 2,3 are Right side (Incoming), Lanes 0,1 are Left side (Outgoing)
        setupLanes(west, false, false, true, true);
        setupLanes(east, false, false, true, true);

        roadManager.addRoad(north);
        roadManager.addRoad(south);
        roadManager.addRoad(west);
        roadManager.addRoad(east);

        // --- Intersection Connections ---
        // North (In: 0-Outer, 1-Inner) -> South(Out: 2-Inner, 3-Outer), East(Out: 0-Outer, 1-Inner), West(Out: 0-Outer, 1-Inner)
        connect(north, 2, 3, south, 1, 0, east, 1, west, 0);
        // South (In: 0-Outer, 1-Inner) -> North(Out: 2-Inner, 3-Outer), West(Out: 0-Outer, 1-Inner), East(Out: 0-Outer, 1-Inner)
        connect(south, 2, 3, north, 1, 0, west, 1, east, 0);
        // West (In: 3-Outer, 2-Inner) -> East(Out: 0-Outer, 1-Inner), North(Out: 2-Inner, 3-Outer), South(Out: 2-Inner, 3-Outer)
        connect(west, 2, 3, east, 1, 0, north, 1, south, 0);
        // East (In: 3-Outer, 2-Inner) -> West(Out: 0-Outer, 1-Inner), South(Out: 2-Inner, 3-Outer), North(Out: 2-Inner, 3-Outer)
        connect(east, 2, 3, west, 1, 0, south, 1, north, 0);

        // 네비게이터가 인지할 수 있도록 전체 연결 상태 리프레시 ❤️
        controller.refreshAllConnections();
    }

    private void setupLanes(Road r, boolean l0, boolean l1, boolean l2, boolean l3) {
        r.addLane(l0, 0);
        r.addLane(l1, 1);
        r.addLane(l2, 2);
        r.addLane(l3, 3);
    }

    /**
     * @param in Road entering the junction
     * @param inInner Inner incoming lane index
     * @param inOuter Outer incoming lane index
     * @param straight Target road for straight
     * @param outSInner Target inner outgoing lane index
     * @param outSOuter Target outer outgoing lane index
     * @param left Target road for left turn
     * @param outLInner Target inner outgoing lane index
     * @param right Target road for right turn
     * @param outROuter Target outer outgoing lane index
     */
    private void connect(Road in, int inInner, int inOuter, 
                         Road straight, int outSInner, int outSOuter,
                         Road left, int outLInner,
                         Road right, int outROuter) {
        
        Lane inILane = in.getLane(inInner);
        Lane inOLane = in.getLane(inOuter);

        // Straight connections
        junctionController.addConnection(inILane, straight.getLane(outSInner), LaneType.STRAIGHT);
        junctionController.addConnection(inOLane, straight.getLane(outSOuter), LaneType.STRAIGHT);

        // Left turn (from Inner lane)
        junctionController.addConnection(inILane, left.getLane(outLInner), LaneType.LEFT);

        // Right turn (from Outer lane)
        junctionController.addConnection(inOLane, right.getLane(outROuter), LaneType.RIGHT);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
