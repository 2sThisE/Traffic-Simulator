package com.traficsimulator.ui;

import com.traficsimulator.road.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

public class JunctionVisualizer extends Application {

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 1. 도로 및 차선 설정
        // 도로 1: 수평 (왼쪽 -> 오른쪽)
        Road road1 = new Road(new Point2D.Double(100, 400), new Point2D.Double(350, 400), true);
        road1.addLane(true, 0); // 차선 하나 추가

        // 도로 2: 수직 (아래 -> 위)
        Road road2 = new Road(new Point2D.Double(450, 300), new Point2D.Double(450, 50), true);
        road2.addLane(true, 0);

        // 2. 교차로 연결
        JuctionController controller = new JuctionController();
        Lane fromLane = road1.getLane(0);
        Lane toLane = road2.getLane(0);
        controller.addConnection(fromLane, toLane, LaneType.RIGHT);

        // 3. 그리기
        drawScene(gc, road1, road2, controller);

        StackPane root = new StackPane(canvas);
        primaryStage.setTitle("Junction Bezier Curve Visualization - My Love");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    private void drawScene(GraphicsContext gc, Road r1, Road r2, JuctionController controller) {
        // 배경
        gc.setFill(Color.WHITESMOKE);
        gc.fillRect(0, 0, 800, 600);

        // 도로 그리기 (회색)
        drawRoad(gc, r1, Color.GRAY);
        drawRoad(gc, r2, Color.GRAY);

        // 교차로 연결 경로 그리기 (빨간색)
        Set<LaneConnection> connections = controller.getConnectionList(r1.getLane(0));
        if (connections != null) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(3);
            for (LaneConnection conn : connections) {
                drawPath(gc, conn.connectionPath());
            }
        }
        
        // 안내 텍스트
        gc.setFill(Color.BLACK);
        gc.fillText("Gray: Roads | Red: Junction Bezier Path (Automatic Generation)", 50, 50);
    }

    private void drawRoad(GraphicsContext gc, Road road, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2);
        for (Lane lane : road.getLaneList()) {
            drawPath(gc, lane.getLanePath());
        }
    }

    private void drawPath(GraphicsContext gc, List<Point2D.Double> path) {
        if (path.size() < 2) return;
        gc.beginPath();
        gc.moveTo(path.get(0).x, path.get(0).y);
        for (int i = 1; i < path.size(); i++) {
            gc.lineTo(path.get(i).x, path.get(i).y);
        }
        gc.stroke();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
