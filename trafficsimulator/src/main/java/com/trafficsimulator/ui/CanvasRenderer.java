package com.trafficsimulator.ui;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trafficsimulator.debug.Vehicle;
import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.util.UnitConverter;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

public class CanvasRenderer {
    private final Canvas canvas;

    public CanvasRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    public void render(List<Road> roads, List<TrafficLight> trafficLights, List<Camera> cameras,
                       List<Vehicle> vehicles,
                       Point2D.Double dragStart, Point2D.Double currentMouse, boolean isDrawing,
                       double cameraX, double cameraY, double zoom, SelectionManager selectionManager,
                       RoadManager.PointHit hoveredPoint, JunctionController junctionController,
                       double interpolationAlpha) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.save();
        gc.translate(cameraX, cameraY);
        gc.scale(zoom, zoom);

        // 1. 도로 본체 그리기
        for (Road road : roads) {
            drawRoad(gc, road, selectionManager, zoom);
        }

        // 2. 교차로 연결부 그리기
        drawJunctionConnections(gc, roads, junctionController, selectionManager, zoom);

        // 3. 차량 그리기
        if (vehicles != null) {
            for (Vehicle v : vehicles) {
                drawVehicle(gc, v, selectionManager, interpolationAlpha);
                if (selectionManager.getSelectedVehicle() == v) {
                    drawVehiclePath(gc, v, zoom);
                    drawVehicleVision(gc, v, zoom); // 감지 영역 그리기 추가 ❤️
                }
            }
        }

        // 4. 정적 객체들 그리기 ❤️
        drawRegisteredLanesHighlight(gc, roads, selectionManager, zoom);
        drawTrafficLights(gc, trafficLights, selectionManager, zoom);
        drawCameras(gc, cameras, selectionManager, zoom); 

        // 5. 포인트 조절 핸들 그리기
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

    private void drawVehicle(GraphicsContext gc, Vehicle v, SelectionManager sm, double interpolationAlpha) {
        gc.save();
        gc.translate(v.getInterpolatedX(interpolationAlpha), v.getInterpolatedY(interpolationAlpha));
        gc.rotate(v.getInterpolatedAngle(interpolationAlpha));

        double w = v.getWidth();
        double h = v.getHeight();

        // 선택 하이라이트
        if (sm.getSelectedVehicle() == v) {
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2.0);
            gc.strokeRect(-w / 2 - 2, -h / 2 - 2, w + 4, h + 4);
        }

        // Body (Rounded Rectangle)
        gc.setFill(v.getColor());
        gc.fillRoundRect(-w / 2, -h / 2, w, h, 8, 8);

        // Headlights
        gc.setFill(Color.YELLOW);
        gc.fillOval(w / 2 - 5, -h / 2 + 2, 4, 4);
        gc.fillOval(w / 2 - 5, h / 2 - 6, 4, 4);

        // Taillights
        gc.setFill(Color.RED);
        gc.fillRect(-w / 2, -h / 2 + 3, 2, 3);
        gc.fillRect(-w / 2, h / 2 - 6, 2, 3);

        gc.restore();
    }

    /**
     * 차량이 따라갈 경로와 궤적 영역을 그립니다. ❤️
     */
    private void drawVehiclePath(GraphicsContext gc, Vehicle v, double zoom) {
        // 1. 궤적 영역 (pathArea) 그리기 - 반투명 빨간색 ❤️
        java.awt.geom.Area area = v.getAutopilot().getPathArea();
        if (area != null && !area.isEmpty()) {
            gc.save();
            gc.setFill(Color.web("#e74c3c", 0.3)); // 반투명 빨간색
            gc.setStroke(Color.web("#e74c3c", 0.6));
            gc.setLineWidth(1.0 / zoom);

            java.awt.geom.PathIterator pi = area.getPathIterator(null);
            double[] coords = new double[6];
            gc.beginPath();
            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                if (type == java.awt.geom.PathIterator.SEG_MOVETO) gc.moveTo(coords[0], coords[1]);
                else if (type == java.awt.geom.PathIterator.SEG_LINETO) gc.lineTo(coords[0], coords[1]);
                else if (type == java.awt.geom.PathIterator.SEG_CLOSE) gc.closePath();
                pi.next();
            }
            gc.fill();
            gc.stroke();
            gc.restore();
        }
    }

    /**
     * 차량의 감지 영역을 시각화합니다. ❤️
     */
    private void drawVehicleVision(GraphicsContext gc, Vehicle v, double zoom) {
        // 1. 전방 감지 영역 (신호등, 카메라 등) - 노란색 ❤️
        drawVisionPolygon(gc, v.getForwardVisionArea(), Color.web("#f1c40f", 0.2), Color.web("#f1c40f", 0.5), zoom);
        
        // 2. 측방 감지 영역 (타 차량 등) - 하늘색 ❤️
        drawVisionPolygon(gc, v.getSideVisionArea(), Color.web("#3498db", 0.15), Color.web("#3498db", 0.4), zoom);

        // 3. 전방 차량 감지 영역 (안전거리) - 하늘색 (측방과 동일) ❤️
        drawVisionPolygon(gc, v.getVehicleVisionArea(), Color.web("#3498db", 0.15), Color.web("#3498db", 0.4), zoom);
    }

    private void drawVisionPolygon(GraphicsContext gc, List<Point2D.Double> vision, Color fill, Color stroke, double zoom) {
        if (vision == null || vision.isEmpty()) return;

        gc.save();
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(1.0 / zoom);

        boolean isNewPolygon = true;
        for (Point2D.Double pt : vision) {
            if (pt == null) {
                if (!isNewPolygon) {
                    gc.closePath();
                    gc.fill();
                    gc.stroke();
                }
                isNewPolygon = true;
            } else {
                if (isNewPolygon) {
                    gc.beginPath();
                    gc.moveTo(pt.x, pt.y);
                    isNewPolygon = false;
                } else {
                    gc.lineTo(pt.x, pt.y);
                }
            }
        }
        if (!isNewPolygon) {
            gc.closePath();
            gc.fill();
            gc.stroke();
        }
        gc.restore();
    }

    /**
     * 단속 카메라와 단속 지점을 그립니다. ❤️
     */
    private void drawCameras(GraphicsContext gc, List<Camera> cameras, 
                             SelectionManager sm, double zoom) {
        if (cameras == null) return;

        for (Camera cam : cameras) {
            Point2D.Double pos = cam.getLoc();
            if (pos == null) continue;

            // 1. 감시 차선별 단속 지점(선) 그리기 ❤️
            gc.setLineWidth(4.0);
            gc.setStroke(Color.rgb(255, 100, 100, 0.6)); // 약간 투명한 빨간색
            for (Map.Entry<Lane, Point2D.Double> entry : cam.getTargetLaneMap().entrySet()) {
                Lane lane = entry.getKey();
                Point2D.Double snapPt = entry.getValue();
                
                // 해당 차선 위의 단속선 긋기 (법선 벡터 활용)
                drawEnforcementLine(gc, lane, snapPt);
                
                // 카메라 본체와 단속 지점 연결 가이드선 (선택 시에만)
                if (sm.isSelected(cam)) {
                    gc.save();
                    gc.setStroke(Color.rgb(255, 255, 255, 0.3));
                    gc.setLineWidth(1.0 / zoom);
                    gc.setLineDashes(3.0 / zoom);
                    gc.strokeLine(pos.x, pos.y, snapPt.x, snapPt.y);
                    gc.restore();
                }
            }

            // 2. 카메라 본체 그리기 ❤️
            double size = 18.0;
            if (sm.isSelected(cam)) {
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(2.0 / zoom);
                gc.strokeRect(pos.x - size/2 - 2/zoom, pos.y - size/2 - 2/zoom, size + 4/zoom, size + 4/zoom);
            }

            // 본체 아이콘 (진한 회색 상자 + 렌즈)
            gc.setFill(Color.rgb(45, 52, 54));
            gc.fillRoundRect(pos.x - size/2, pos.y - size/2, size, size, 4, 4);
            
            gc.setFill(Color.rgb(223, 230, 233));
            gc.fillOval(pos.x - size/4, pos.y - size/4, size/2, size/2); // 렌즈 외곽
            
            gc.setFill(Color.rgb(45, 52, 54));
            gc.fillOval(pos.x - size/8, pos.y - size/8, size/4, size/4); // 렌즈 중심
            
            // 빨간색 작동 램프
            gc.setFill(Color.RED);
            gc.fillOval(pos.x + size/4, pos.y - size/3, 3, 3);
        }
    }

    /**
     * 특정 지점에 차선 너비만큼의 단속선을 그립니다. ❤️
     */
    private void drawEnforcementLine(GraphicsContext gc, Lane lane, Point2D.Double centerPt) {
        List<Point2D.Double> path = lane.getLanePath();
        if (path.size() < 2) return;

        // 가장 가까운 세그먼트 찾기
        int idx = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double d = path.get(i).distance(centerPt);
            if (d < minDist) {
                minDist = d;
                idx = i;
            }
        }

        // 방향 벡터 계산
        Point2D.Double p1 = (idx < path.size() - 1) ? path.get(idx) : path.get(idx - 1);
        Point2D.Double p2 = (idx < path.size() - 1) ? path.get(idx + 1) : path.get(idx);
        
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return;

        double nx = -dy / len;
        double ny = dx / len;

        double halfWidth = UnitConverter.toPixel(3.2) / 2.0; // 기본값 (3.2m / 2)
        // 실제 차선 너비를 찾기 위한 도로 검색 (최적화 가능)
        // 여기서는 시각적인 효과를 위해 고정 너비를 쓰거나 파라미터로 받을 수 있음

        gc.strokeLine(
            centerPt.x - nx * halfWidth, centerPt.y - ny * halfWidth,
            centerPt.x + nx * halfWidth, centerPt.y + ny * halfWidth
        );
    }

    private void drawRegisteredLanesHighlight(GraphicsContext gc, List<Road> roads,
                                             SelectionManager sm, double zoom) {
        TrafficLight selectedTL = sm.getSelectedTrafficLight();
        if (selectedTL == null) return;

        Lane highlighted = sm.getHighlightedLane();

        for (Lane lane : selectedTL.getControlLaneList()) {
            double laneWidth = 20.0;
            for (Road r : roads) {
                if (r.getLaneList().contains(lane)) {
                    laneWidth = r.getLaneWidth();
                    break;
                }
            }

            if (lane == highlighted) {
                gc.setStroke(Color.ORANGERED.deriveColor(0, 1, 1, 0.5));
            } else {
                gc.setStroke(Color.YELLOW.deriveColor(0, 1, 1, 0.3));
            }
            
            gc.setLineWidth(laneWidth);
            gc.setLineCap(StrokeLineCap.BUTT);
            strokePath(gc, lane.getLanePath());
        }
    }

    private void drawTrafficLights(GraphicsContext gc, List<TrafficLight> lights, 
                                 SelectionManager sm, double zoom) {
        if (lights == null) return;
        
        for (TrafficLight tl : lights) {
            Point2D.Double pos = tl.getCoordinates();
            if (pos == null) continue;

            Set<TrafficLightSignal> availableSignals = tl.getLightList();
            if (availableSignals.isEmpty()) continue;

            int signalCount = availableSignals.size();
            double lampSize = 15.0;   
            double padding = 4.0;    
            double width = lampSize + padding * 2;
            double height = (lampSize + padding) * signalCount + padding;

            if (sm.isSelected(tl)) {
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(2.0 / zoom);
                gc.strokeRect(pos.x - width/2 - 2/zoom, pos.y - height/2 - 2/zoom, width + 4/zoom, height + 4/zoom);
            }

            gc.setFill(Color.rgb(30, 30, 30));
            gc.fillRoundRect(pos.x - width/2, pos.y - height/2, width, height, 3, 3);

            TrafficLightSignal[] currentActive = tl.currentSignal();
            List<TrafficLightSignal> activeList = java.util.Arrays.asList(currentActive);

            List<TrafficLightSignal> orderedSignals = java.util.Arrays.stream(TrafficLightSignal.values())
                    .filter(availableSignals::contains)
                    .toList();

            for (int i = 0; i < orderedSignals.size(); i++) {
                TrafficLightSignal sig = orderedSignals.get(i);
                double lampY = (pos.y - height/2 + padding) + i * (lampSize + padding);
                boolean isOn = activeList.contains(sig);

                Color baseColor = switch (sig) {
                    case RED -> Color.RED;
                    case YELLOW -> Color.YELLOW;
                    case STRAIGHT -> Color.LIME;
                    case LEFT -> Color.AQUA;
                    case RIGHT -> Color.GREENYELLOW;
                    case U_TURN -> Color.MAGENTA;
                };

                if (isOn) {
                    gc.setFill(baseColor);
                    gc.setGlobalAlpha(0.3);
                    gc.fillOval(pos.x - lampSize/2 - 2, lampY - 2, lampSize + 4, lampSize + 4);
                    gc.setGlobalAlpha(1.0);
                } else {
                    gc.setFill(baseColor.deriveColor(0, 1, 0.2, 1.0));
                }

                if (sig == TrafficLightSignal.LEFT || sig == TrafficLightSignal.RIGHT || sig == TrafficLightSignal.U_TURN) {
                    if (sig == TrafficLightSignal.U_TURN) {
                        drawUTurnSignal(gc, pos.x, lampY + lampSize/2, lampSize);
                    } else {
                        drawArrowSignal(gc, pos.x, lampY + lampSize/2, lampSize, sig == TrafficLightSignal.LEFT);
                    }
                } else {
                    gc.fillOval(pos.x - lampSize/2, lampY, lampSize, lampSize);
                }
            }
        }
    }

    private void drawUTurnSignal(GraphicsContext gc, double cx, double cy, double size) {
        double r = size * 0.4;
        gc.save();
        gc.setLineWidth(1.5);
        gc.setStroke(gc.getFill());
        gc.strokeArc(cx - r, cy - r/2, r * 1.5, r, 0, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - r, cy, cx - r, cy + r/2);
        gc.strokeLine(cx + r * 0.5, cy, cx + r * 0.5, cy + r/2);
        double headX = cx - r;
        double headY = cy + r/2;
        gc.strokeLine(headX, headY, headX - 2, headY - 3);
        gc.strokeLine(headX, headY, headX + 2, headY - 3);
        gc.restore();
    }

    private void drawArrowSignal(GraphicsContext gc, double cx, double cy, double size, boolean isLeft) {
        double r = size / 2;
        double direction = isLeft ? -1 : 1;
        gc.save();
        gc.setLineWidth(1.5);
        gc.setStroke(gc.getFill()); 
        gc.strokeLine(cx - r * 0.5 * direction, cy, cx + r * 0.5 * direction, cy);
        gc.strokeLine(cx + r * 0.5 * direction, cy, cx + r * 0.1 * direction, cy - r * 0.4);
        gc.strokeLine(cx + r * 0.5 * direction, cy, cx + r * 0.1 * direction, cy + r * 0.4);
        gc.restore();
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
        
        drawStopLines(gc, lanes, laneWidth, zoom); // 정지선 그리기 ❤️
        drawLaneArrows(gc, lanes);
    }

    private void drawStopLines(GraphicsContext gc, List<Lane> lanes, double laneWidth, double zoom) {
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3.0); // 도로 선들과 어울리도록 3.0으로 조정 ❤️
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineDashes(0);

        for (Lane lane : lanes) {
            // 자기가 만든 속성 드디어 연결! ❤️
            if (!lane.isDrawStopLine()) continue;

            List<Point2D.Double> path = lane.getLanePath();
            if (path.size() < 2) continue;

            Point2D.Double p1, p2;
            if (lane.isRoadDirection()) {
                p1 = path.get(path.size() - 2);
                p2 = path.get(path.size() - 1);
            } else {
                p1 = path.get(1);
                p2 = path.get(0);
            }

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-6) continue;

            double nx = -dy / len;
            double ny = dx / len;

            double halfWidth = laneWidth / 2.0;
            gc.strokeLine(
                p2.x - nx * halfWidth, p2.y - ny * halfWidth,
                p2.x + nx * halfWidth, p2.y + ny * halfWidth
            );
        }
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
