package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import com.traficsimulator.road.traficlight.TraficLight;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CanvasRenderer {
    private final Canvas canvas;

    public CanvasRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    public void render(List<Road> roads, List<TraficLight> traficLights,
                       Point2D.Double dragStart, Point2D.Double currentMouse, boolean isDrawing,
                       double cameraX, double cameraY, double zoom, SelectionManager selectionManager,
                       RoadManager.PointHit hoveredPoint, JunctionController junctionController) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.save();
        gc.translate(cameraX, cameraY);
        gc.scale(zoom, zoom);

        // 1. 도로 본체 그리기
        for (Road road : roads) {
            drawRoad(gc, road, selectionManager, zoom);
        }

        // 2. 교차로 연결부 그리기 (아스팔트는 항상, 유도선은 선택 시)
        drawJunctionConnections(gc, roads, junctionController, selectionManager, zoom);

        // 3. 신호등 및 연결된 차선 그리기 ❤️
        drawTraficLights(gc, traficLights, selectionManager, zoom);
        drawRegisteredLanesHighlight(gc, roads, selectionManager, zoom);

        // 4. 포인트 조절 핸들 그리기
        drawHandles(gc, roads, selectionManager, hoveredPoint, zoom);

        if (isDrawing && dragStart != null && currentMouse != null) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(1.0 / zoom);
            gc.setLineDashes(5.0 / zoom);
            gc.strokeLine(dragStart.x, dragStart.y, currentMouse.x, currentMouse.y);
            gc.setLineDashes(0);
        }

        gc.restore();
    }

    private void drawRegisteredLanesHighlight(GraphicsContext gc, List<Road> roads,
                                             SelectionManager sm, double zoom) {
        TraficLight selectedTL = sm.getSelectedTraficLight();
        if (selectedTL == null) return;

        Lane highlighted = sm.getHighlightedLane();

        for (Lane lane : selectedTL.getControlLaneList()) {
            double laneWidth = 20.0; // 기본값
            
            // 해당 차선이 속한 도로의 너비를 찾음 ❤️
            for (Road r : roads) {
                if (r.getLaneList().contains(lane)) {
                    laneWidth = r.getLaneWidth();
                    break;
                }
            }

            if (lane == highlighted) {
                // 리스트에서 선택된 차선: 선명한 주황색 반투명 ❤️
                gc.setStroke(Color.ORANGERED.deriveColor(0, 1, 1, 0.5));
            } else {
                // 그냥 등록된 차선들: 은은한 노란색 반투명 ❤️
                gc.setStroke(Color.YELLOW.deriveColor(0, 1, 1, 0.3));
            }
            
            gc.setLineWidth(laneWidth);
            gc.setLineCap(StrokeLineCap.BUTT);
            strokePath(gc, lane.getLanePath());
        }
    }

    private void drawTraficLights(GraphicsContext gc, List<TraficLight> lights, 
                                 SelectionManager sm, double zoom) {
        if (lights == null) return;
        
        double width = 12.0 / zoom;
        double height = 30.0 / zoom;
        double radius = 3.0 / zoom;

        for (TraficLight tl : lights) {
            Point2D.Double pos = tl.getCoordinates();
            if (pos == null) continue;

            // 선택 하이라이트
            if (sm.getSelectedTraficLight() == tl) {
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(2.0 / zoom);
                gc.strokeRect(pos.x - width/2 - 2/zoom, pos.y - height/2 - 2/zoom, width + 4/zoom, height + 4/zoom);
            }

            // 신호등 본체
            gc.setFill(Color.rgb(40, 40, 40));
            gc.fillRect(pos.x - width/2, pos.y - height/2, width, height);
            
            // 신호등 램프 (빨, 노, 초) - 지금은 틱이 없으니 기본 상태로 표시 ❤️
            double lampX = pos.x - radius;
            gc.setFill(Color.RED.darker());
            gc.fillOval(lampX, pos.y - height/2 + 2/zoom, radius*2, radius*2);
            
            gc.setFill(Color.YELLOW.darker());
            gc.fillOval(lampX, pos.y - height/2 + 11/zoom, radius*2, radius*2);
            
            gc.setFill(Color.GREEN.darker());
            gc.fillOval(lampX, pos.y - height/2 + 20/zoom, radius*2, radius*2);
        }
    }

    private void drawJunctionConnections(GraphicsContext gc, List<Road> roads, JunctionController jc, 
                                         SelectionManager sm, double zoom) {
        if (jc == null) return;

        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineDashes(0);
        gc.setStroke(Color.rgb(50, 50, 50));

        for (Road road : roads) {
            double laneWidth = road.getLaneWidth();
            for (Lane lane : road.getLaneList()) {
                Set<LaneConnection> connections = jc.getConnectionList(lane);
                if (connections == null) continue;
                for (LaneConnection conn : connections) {
                    drawJunctionAsphalt(gc, conn, laneWidth);
                }
            }
        }

        List<Lane> selectedLanes = sm.getSelectedLanes();
        LaneConnection selectedConn = sm.getSelectedConnection();

        if (!selectedLanes.isEmpty() || selectedConn != null) {
            for (Lane roadLane : selectedLanes) {
                Set<LaneConnection> conns = jc.getConnectionList(roadLane);
                if (conns == null) continue;
                for (LaneConnection conn : conns) {
                    drawSingleGuideline(gc, conn, conn == selectedConn, true, zoom);
                }
            }
            if (selectedConn != null) {
                drawSingleGuideline(gc, selectedConn, true, false, zoom);
            }
        }
    }

    private void drawJunctionAsphalt(GraphicsContext gc, LaneConnection conn, double width) {
        List<Point2D.Double> path = conn.connectionPath();
        if (path.size() < 2) return;
        gc.setLineWidth(width);
        strokePath(gc, path);
    }

    private void drawSingleGuideline(GraphicsContext gc, LaneConnection conn, boolean isSelected, boolean isFromSelectedLane, double zoom) {
        List<Point2D.Double> path = conn.connectionPath();
        if (path.size() < 2) return;

        if (isSelected) {
            gc.setStroke(Color.MAGENTA);
            gc.setLineWidth(3.0 / zoom);
            gc.setLineDashes(0);
        } else if (isFromSelectedLane) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(2.0 / zoom);
            gc.setLineDashes(5.0 / zoom, 5.0 / zoom);
        } else {
            gc.setStroke(Color.CYAN.deriveColor(0, 1, 1, 0.6));
            gc.setLineWidth(1.5 / zoom);
            gc.setLineDashes(10.0 / zoom, 10.0 / zoom);
        }

        strokePath(gc, path);
        drawArrowHead(gc, path.get(path.size()/2), path.get(path.size()/2 + 1));
        gc.setLineDashes(0);
    }

    private void drawHandles(GraphicsContext gc, List<Road> roads, SelectionManager selectionManager, 
                             RoadManager.PointHit hovered, double zoom) {
        double r = 5.0 / zoom;
        for (Road road : roads) {
            if (selectionManager.getSelectedRoad() == road) {
                drawHandle(gc, road.getStartPoint(), Color.WHITE, Color.BLUE, r);
                drawHandle(gc, road.getEndPoint(), Color.WHITE, Color.BLUE, r);
                if (road.getPathPoints().size() > 2) {
                    gc.setStroke(Color.ORANGE); gc.setLineWidth(1.0/zoom); gc.setLineDashes(3.0/zoom);
                    gc.strokeLine(road.getStartPoint().x, road.getStartPoint().y, road.getControl1().x, road.getControl1().y);
                    gc.strokeLine(road.getEndPoint().x, road.getEndPoint().y, road.getControl2().x, road.getControl2().y);
                    gc.setLineDashes(0);
                    drawHandle(gc, road.getControl1(), Color.WHITE, Color.ORANGE, r);
                    drawHandle(gc, road.getControl2(), Color.WHITE, Color.ORANGE, r);
                }
            }
        }
        if (hovered != null) {
            Point2D.Double pt = switch (hovered.type) {
                case START -> hovered.road.getStartPoint();
                case END -> hovered.road.getEndPoint();
                case CONTROL1 -> hovered.road.getControl1();
                case CONTROL2 -> hovered.road.getControl2();
            };
            drawHandle(gc, pt, Color.BLUE, Color.CYAN, r * 1.5);
        }
    }

    private void drawHandle(GraphicsContext gc, Point2D.Double pt, Color stroke, Color fill, double r) {
        if (pt == null) return;
        gc.setStroke(stroke); gc.setFill(fill); gc.setLineWidth(1.0);
        gc.fillOval(pt.x - r, pt.y - r, r * 2, r * 2);
        gc.strokeOval(pt.x - r, pt.y - r, r * 2, r * 2);
    }

    private void drawRoad(GraphicsContext gc, Road road, SelectionManager sm, double zoom) {
        List<Point2D.Double> centerPoints = road.getPathPoints();
        if (centerPoints.size() < 2) return;
        List<Lane> lanes = road.getLaneList();
        double laneWidth = road.getLaneWidth();
        double totalWidth = lanes.size() * laneWidth;

        if (sm.isSelected(road)) {
            gc.setStroke(Color.web("00FFFF", 0.3)); gc.setLineWidth(totalWidth + 10.0); strokePath(gc, centerPoints);
        }

        gc.setStroke(Color.rgb(50, 50, 50)); gc.setLineWidth(totalWidth); gc.setLineCap(StrokeLineCap.BUTT);
        strokePath(gc, centerPoints);

        for (Lane lane : lanes) {
            boolean isTarget = (sm.getSelectedConnection() != null && sm.getSelectedConnection().targetLane() == lane);
            if (sm.isSelected(lane) || isTarget) {
                gc.setStroke(isTarget ? Color.web("FF00FF", 0.4) : Color.web("00FFFF", 0.4));
                gc.setLineWidth(laneWidth);
                strokePath(gc, lane.getLanePath());
                
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.0 / zoom);
                gc.setLineDashes(5.0 / zoom, 5.0 / zoom);
                strokePath(gc, lane.getLanePath());
                gc.setLineDashes(0);
            }
        }

        for (int i = 0; i <= lanes.size(); i++) {
            double offset = (i - lanes.size() / 2.0) * laneWidth;
            List<Point2D.Double> boundaryPath = generateOffsetPath(centerPoints, offset);
            if (i == 0 || i == lanes.size()) {
                gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.setLineDashes(0);
            } else {
                Lane leftLane = lanes.get(i - 1); Lane rightLane = lanes.get(i);
                if (leftLane.isRoadDirection() != rightLane.isRoadDirection()) {
                    gc.setStroke(Color.YELLOW); gc.setLineWidth(3); gc.setLineDashes(0);
                } else {
                    gc.setStroke(Color.WHITE); gc.setLineWidth(1); gc.setLineDashes(10, 10);
                }
            }
            strokePath(gc, boundaryPath);
        }
        drawLaneArrows(gc, lanes);
        gc.setLineDashes(0);
    }

    private void drawLaneArrows(GraphicsContext gc, List<Lane> lanes) {
        gc.setStroke(Color.rgb(200, 200, 200, 0.5)); gc.setLineWidth(2);
        for (Lane lane : lanes) {
            List<Point2D.Double> path = lane.getLanePath();
            if (path.size() < 2) continue;
            int mid = path.size() / 2;
            Point2D.Double p1 = path.get(mid);
            Point2D.Double p2 = (mid + 1 < path.size()) ? path.get(mid + 1) : p1;
            if (p1.distance(p2) < 0.1 && mid > 0) p1 = path.get(mid - 1);
            if (!lane.isRoadDirection()) { Point2D.Double t = p1; p1 = p2; p2 = t; }
            drawArrowHead(gc, p1, p2);
        }
    }

    private void drawArrowHead(GraphicsContext gc, Point2D.Double from, Point2D.Double to) {
        double dx = to.x - from.x; double dy = to.y - from.y;
        double angle = Math.atan2(dy, dx); double len = 12;
        gc.strokeLine(to.x, to.y, to.x - len * Math.cos(angle - Math.PI / 6), to.y - len * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(to.x, to.y, to.x - len * Math.cos(angle + Math.PI / 6), to.y - len * Math.sin(angle + Math.PI / 6));
    }

    private void strokePath(GraphicsContext gc, List<Point2D.Double> points) {
        if (points.size() < 2) return;
        gc.beginPath(); gc.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) gc.lineTo(points.get(i).x, points.get(i).y);
        gc.stroke();
    }

    private List<Point2D.Double> generateOffsetPath(List<Point2D.Double> path, double offset) {
        List<Point2D.Double> offsetPath = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i); Point2D.Double p2 = path.get(i + 1);
            double dx = p2.x - p1.x; double dy = p2.y - p1.y; double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-6) continue;
            double nx = -dy / len; double ny = dx / len;
            offsetPath.add(new Point2D.Double(p1.x + nx * offset, p1.y + ny * offset));
            if (i == path.size() - 2) offsetPath.add(new Point2D.Double(p2.x + nx * offset, p2.y + ny * offset));
        }
        return offsetPath;
    }
}
