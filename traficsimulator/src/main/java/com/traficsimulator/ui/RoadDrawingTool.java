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
    private RoadManager.PointHit startSnap; // 시작 시 스냅 정보 저장 ❤️
    private boolean isDrawing = false;
    private final Runnable onUpdate;

    private static final double SNAP_THRESHOLD = 20.0;

    public RoadDrawingTool(RoadManager roadManager, JunctionController jc, Runnable onUpdate) {
        this.roadManager = roadManager;
        this.junctionController = jc;
        this.onUpdate = onUpdate;
    }

    public void handleMousePressed(MouseEvent e, String selectedComponent, double camX, double camY, double zoom) {
        if ("Road".equals(selectedComponent)) {
            Point2D.Double worldPt = screenToWorld(e.getX(), e.getY(), camX, camY, zoom);
            startSnap = roadManager.findNearestPoint(worldPt, SNAP_THRESHOLD / zoom);
            
            if (startSnap != null) {
                // 좌표값만 복사 (객체 분리 ❤️)
                Point2D.Double snapPt = (startSnap.type == RoadManager.PointType.START) ? startSnap.road.getStartPoint() : startSnap.road.getEndPoint();
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
            RoadManager.PointHit endSnap = roadManager.findNearestPoint(worldPt, SNAP_THRESHOLD / zoom);
            
            Point2D.Double finalEnd;
            if (endSnap != null) {
                Point2D.Double snapPt = (endSnap.type == RoadManager.PointType.START) ? endSnap.road.getStartPoint() : endSnap.road.getEndPoint();
                finalEnd = new Point2D.Double(snapPt.x, snapPt.y);
            } else {
                finalEnd = worldPt;
            }

            if (dragStart.distance(finalEnd) > 5.0) {
                Road newRoad = new Road(dragStart, finalEnd, false);
                
                // 스냅된 도로가 있으면 차선 구성을 복제 ❤️
                Road template = (startSnap != null) ? startSnap.road : (endSnap != null ? endSnap.road : null);
                if (template != null) {
                    List<Lane> templateLanes = template.getLaneList();
                    for (int i = 0; i < templateLanes.size(); i++) {
                        newRoad.addLane(templateLanes.get(i).isRoadDirection(), i);
                    }
                } else {
                    newRoad.addLane(false, 0); // 기본 2차선
                    newRoad.addLane(true, 1);  
                }

                roadManager.addRoad(newRoad);

                // 순차적 자동 연결 (1:1 매칭) ❤️
                if (startSnap != null) {
                    connectSequentially(startSnap.road, startSnap.type, newRoad, RoadManager.PointType.START);
                }
                if (endSnap != null) {
                    connectSequentially(newRoad, RoadManager.PointType.END, endSnap.road, endSnap.type);
                }
            }
            
            resetDrawing();
            onUpdate.run();
        }
    }

    private void connectSequentially(Road r1, RoadManager.PointType t1, Road r2, RoadManager.PointType t2) {
        List<Lane> lanes1 = r1.getLaneList();
        List<Lane> lanes2 = r2.getLaneList();
        int count = Math.min(lanes1.size(), lanes2.size());

        for (int i = 0; i < count; i++) {
            Lane l1 = lanes1.get(i);
            Lane l2 = lanes2.get(i);

            // r1의 i번 차선이 나가고 r2의 i번 차선이 들어오는 경우 ❤️
            if (isExiting(l1, t1) && isEntering(l2, t2)) {
                junctionController.addConnection(l1, l2, LaneType.STRAIGHT);
            }
            // r2의 i번 차선이 나가고 r1의 i번 차선이 들어오는 경우 ❤️
            if (isExiting(l2, t2) && isEntering(l1, t1)) {
                junctionController.addConnection(l2, l1, LaneType.STRAIGHT);
            }
        }
    }

    private boolean isExiting(Lane lane, RoadManager.PointType type) {
        if (lane.isRoadDirection()) return type == RoadManager.PointType.END;
        else return type == RoadManager.PointType.START;
    }

    private boolean isEntering(Lane lane, RoadManager.PointType type) {
        if (lane.isRoadDirection()) return type == RoadManager.PointType.START;
        else return type == RoadManager.PointType.END;
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
        startSnap = null;
    }

    public List<Road> getRoadList() { return roadManager.getRoadList(); }
    public Point2D.Double getDragStart() { return dragStart; }
    public Point2D.Double getCurrentMouse() { return currentMouse; }
    public boolean isDrawing() { return isDrawing; }
}
