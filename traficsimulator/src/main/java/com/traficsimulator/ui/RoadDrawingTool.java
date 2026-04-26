package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneType;
import com.traficsimulator.road.Road;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class RoadDrawingTool {
    private final RoadManager roadManager;
    private final JunctionController junctionController;
    private Point2D.Double dragStart; 
    private Point2D.Double currentMouse; 
    private boolean isDrawing = false;
    private final Runnable onUpdate;

    private static final double SNAP_THRESHOLD = 15.0;

    public RoadDrawingTool(RoadManager roadManager, JunctionController jc, Runnable onUpdate) {
        this.roadManager = roadManager;
        this.junctionController = jc;
        this.onUpdate = onUpdate;
    }

    public void handleMousePressed(MouseEvent e, String selectedComponent, double camX, double camY, double zoom) {
        if ("Road".equals(selectedComponent)) {
            Point2D.Double worldPt = screenToWorld(e.getX(), e.getY(), camX, camY, zoom);
            RoadManager.PointHit snap = roadManager.findNearestPoint(worldPt, SNAP_THRESHOLD / zoom);
            
            if (snap != null) {
                // 좌표값만 복사해서 새로운 객체 생성 (연동 방지 ❤️)
                Point2D.Double snapPt = (snap.type == RoadManager.PointType.START) ? snap.road.getStartPoint() : snap.road.getEndPoint();
                dragStart = new Point2D.Double(snapPt.x, snapPt.y);
            } else {
                dragStart = worldPt;
            }
            isDrawing = true;
        }
    }

    public void handleMouseDragged(MouseEvent e, double camX, double camY, double zoom) {
        if (isDrawing) {
            Point2D.Double worldPt = screenToWorld(e.getX(), e.getY(), camX, camY, zoom);
            RoadManager.PointHit snap = roadManager.findNearestPoint(worldPt, SNAP_THRESHOLD / zoom);
            
            if (snap != null) {
                Point2D.Double snapPt = (snap.type == RoadManager.PointType.START) ? snap.road.getStartPoint() : snap.road.getEndPoint();
                currentMouse = new Point2D.Double(snapPt.x, snapPt.y);
            } else {
                currentMouse = worldPt;
            }
            onUpdate.run();
        }
    }

    public void handleMouseReleased(MouseEvent e, double camX, double camY, double zoom) {
        if (isDrawing && dragStart != null) {
            Point2D.Double worldPt = screenToWorld(e.getX(), e.getY(), camX, camY, zoom);
            
            // 시작점과 끝점의 스냅 정보 다시 확인
            RoadManager.PointHit startSnap = roadManager.findNearestPoint(dragStart, SNAP_THRESHOLD / zoom);
            RoadManager.PointHit endSnap = roadManager.findNearestPoint(worldPt, SNAP_THRESHOLD / zoom);
            
            Point2D.Double finalEnd;
            if (endSnap != null) {
                Point2D.Double snapPt = (endSnap.type == RoadManager.PointType.START) ? endSnap.road.getStartPoint() : endSnap.road.getEndPoint();
                finalEnd = new Point2D.Double(snapPt.x, snapPt.y); // 객체 분리 ❤️
            } else {
                finalEnd = worldPt;
            }

            if (dragStart.distance(finalEnd) > 5.0) {
                Road newRoad = new Road(dragStart, finalEnd, false);
                
                // 차선 구성 복제 (스냅된 도로 기준)
                Road template = (startSnap != null) ? startSnap.road : (endSnap != null ? endSnap.road : null);
                if (template != null) {
                    List<Lane> templateLanes = template.getLaneList();
                    for (int i = 0; i < templateLanes.size(); i++) {
                        newRoad.addLane(templateLanes.get(i).isRoadDirection(), i);
                    }
                } else {
                    newRoad.addLane(false, 0); 
                    newRoad.addLane(true, 1);  
                }

                roadManager.addRoad(newRoad);

                // 지능형 양방향 순차 연결 ❤️
                if (startSnap != null) connectRoadsAtPoint(startSnap.road, startSnap.type, newRoad, RoadManager.PointType.START);
                if (endSnap != null) connectRoadsAtPoint(newRoad, RoadManager.PointType.END, endSnap.road, endSnap.type);
            }
            
            resetDrawing();
            onUpdate.run();
        }
    }

    /**
     * 두 도로가 맞닿은 점의 타입을 분석하여 모든 가능한 차선을 1:1로 자동 연결합니다.
     */
    private void connectRoadsAtPoint(Road r1, RoadManager.PointType t1, Road r2, RoadManager.PointType t2) {
        List<Lane> lanes1 = r1.getLaneList();
        List<Lane> lanes2 = r2.getLaneList();
        int count = Math.min(lanes1.size(), lanes2.size());

        for (int i = 0; i < count; i++) {
            Lane l1 = lanes1.get(i);
            Lane l2 = lanes2.get(i);

            // 1. R1에서 R2로 가는 흐름 체크
            if (isExiting(l1, t1) && isEntering(l2, t2)) {
                junctionController.addConnection(l1, l2, LaneType.STRAIGHT);
            }
            // 2. R2에서 R1으로 가는 흐름 체크
            if (isExiting(l2, t2) && isEntering(l1, t1)) {
                junctionController.addConnection(l2, l1, LaneType.STRAIGHT);
            }
        }
    }

    // 차선 방향(Up/Down)과 끝점 타입(START/END)을 조합해 진출 여부 판별 ❤️
    private boolean isExiting(Lane lane, RoadManager.PointType type) {
        if (lane.isRoadDirection()) return type == RoadManager.PointType.END; // 상행은 END에서 나감
        else return type == RoadManager.PointType.START; // 하행은 START에서 나감
    }

    // 차선 방향과 끝점 타입을 조합해 진입 여부 판별 ❤️
    private boolean isEntering(Lane lane, RoadManager.PointType type) {
        if (lane.isRoadDirection()) return type == RoadManager.PointType.START; // 상행은 START로 들어옴
        else return type == RoadManager.PointType.END; // 하행은 END로 들어옴
    }

    public Point2D.Double screenToWorld(double sx, double sy, double camX, double camY, double zoom) {
        return new Point2D.Double((sx - camX) / zoom, (sy - camY) / zoom);
    }

    public void handleKeyEvent(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE && isDrawing) {
            resetDrawing();
            onUpdate.run();
        }
    }

    public void resetDrawing() {
        isDrawing = false;
        dragStart = null;
        currentMouse = null;
    }

    public List<Road> getRoadList() { return roadManager.getRoadList(); }
    public Point2D.Double getDragStart() { return dragStart; }
    public Point2D.Double getCurrentMouse() { return currentMouse; }
    public boolean isDrawing() { return isDrawing; }
}
