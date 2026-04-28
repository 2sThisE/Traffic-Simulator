package com.trafficsimulator.debug;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.VehicleType;

import javafx.scene.paint.Color;

/**
 * 실제 미터 단위를 기반으로 외형과 물리 상태를 관리하는 차량 객체입니다.
 */
public class Vehicle {
    private double x, y;          // 중심 좌표 (Pixel)
    private double angle;         // 회전 각도 (Degree)
    private double width;         // 차량 길이 (Pixel)
    private double height;        // 차량 너비 (Pixel)
    private double speedKmh;      // 현재 속도 (km/h)
    private Color color;          // 차량 색상
    private VehicleType type;     // 차량 종류
    private List<Point2D.Double> path = new ArrayList<>(); // 차량이 따라갈 시각적 경로
    private List<Set<Lane>> logicalRoute = new ArrayList<>(); // 차량이 주행 가능한 논리적 경로 세트 ❤️
    private int currentPhaseIndex = 0; // 현재 주행 중인 도로 구간 인덱스 ❤️
    private boolean selected = false; // 선택 여부
    private LaneConnection currentConnection = null; // 현재 교차로 통과 중인 경우 해당 연결 정보 ❤️

    public Vehicle(double x, double y, double angle, VehicleType type) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.type = type;
        this.speedKmh = 0; // 초기 속도 0
        
        // 실제 미터 단위 규격 설정 및 픽셀 변환
        double lengthInMeters;
        double widthInMeters;

        switch (type) {
            case BUS -> {
                lengthInMeters = 6.0;
                widthInMeters = 1.9;
                this.color = Color.web("#2ecc71");
            }
            case HEAVY_TRUCK -> {
                lengthInMeters = 6.0;
                widthInMeters = 1.9;
                this.color = Color.web("#6e6e6e");
            }
            case LIGHT_TRUCK -> {
                lengthInMeters = 5.2;
                widthInMeters = 1.9;
                this.color = Color.web("#95a5a6");
            }
            default -> { // NORMAL (승용차)
                lengthInMeters = 4.7;
                widthInMeters = 1.8;
                this.color = Color.web("#3498db");
            }
        }

        this.width = UnitConverter.toPixel(lengthInMeters);
        this.height = UnitConverter.toPixel(widthInMeters);
    }

    /**
     * 현재 속도(km/h)에 따른 1틱당 이동 거리(Pixel)를 계산하여 위치를 업데이트합니다.
     */
    public void updatePosition() {
        double ms = UnitConverter.kmhToMs(speedKmh);
        double pixelPerTick = UnitConverter.toPixelPerTick(ms);
        
        double rad = Math.toRadians(angle);
        this.x += Math.cos(rad) * pixelPerTick;
        this.y += Math.sin(rad) * pixelPerTick;
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     */
    public void snapToNearestPoint(Point2D.Double target) {
        snapToNearestPoint(target, null);
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     * 논리적 경로가 있다면 차선 변경이 가능하도록 모든 차선을 검사하고, 
     * 교차로 연결로도 검사하여 부드러운 이동을 지원합니다. ❤️
     */
    public void snapToNearestPoint(Point2D.Double target, JunctionController junctionController) {
        double minDist = Double.MAX_VALUE;
        Point2D.Double bestPt = null;
        double bestAngle = this.angle;
        int bestPhase = this.currentPhaseIndex;
        LaneConnection bestConn = null;

        // 1. 논리적 경로(Lane Groups)가 있다면 모든 가능성을 탐색
        if (logicalRoute != null && !logicalRoute.isEmpty()) {
            for (int p = 0; p < logicalRoute.size(); p++) {
                Set<Lane> phase = logicalRoute.get(p);
                
                // 1-1. 현재 도로 구간의 모든 차선 체크 (차선 변경 지원)
                for (Lane lane : phase) {
                    List<Point2D.Double> lanePoints = lane.getLanePath();
                    if (lanePoints == null || lanePoints.size() < 2) continue;

                    List<Point2D.Double> points = new ArrayList<>(lanePoints);
                    if (!lane.isRoadDirection()) java.util.Collections.reverse(points);

                    for (int i = 0; i < points.size() - 1; i++) {
                        Point2D.Double p1 = points.get(i);
                        Point2D.Double p2 = points.get(i + 1);
                        Point2D.Double closest = getClosestPointOnSegment(p1, p2, target);
                        double d = closest.distance(target);
                        if (d < minDist) {
                            minDist = d;
                            bestPt = closest;
                            bestAngle = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
                            bestPhase = p;
                            bestConn = null; // 차선 위에 있음
                        }
                    }
                }

                // 1-2. 다음 도로 구간으로 이어지는 교차로 연결로 체크 (순간이동 방지)
                if (junctionController != null && p < logicalRoute.size() - 1) {
                    Set<Lane> nextPhase = logicalRoute.get(p + 1);
                    for (Lane lane : phase) {
                        Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                        if (conns != null) {
                            for (LaneConnection conn : conns) {
                                if (nextPhase.contains(conn.targetLane())) {
                                    List<Point2D.Double> connPath = conn.connectionPath();
                                    for (int i = 0; i < connPath.size() - 1; i++) {
                                        Point2D.Double p1 = connPath.get(i);
                                        Point2D.Double p2 = connPath.get(i + 1);
                                        Point2D.Double closest = getClosestPointOnSegment(p1, p2, target);
                                        double d = closest.distance(target);
                                        if (d < minDist) {
                                            minDist = d;
                                            bestPt = closest;
                                            bestAngle = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
                                            bestPhase = p; 
                                            bestConn = conn; // 교차로 연결로 위에 있음
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 결과 적용
        if (bestPt != null) {
            this.x = bestPt.x;
            this.y = bestPt.y;
            this.angle = bestAngle;
            this.currentPhaseIndex = bestPhase;
            this.currentConnection = bestConn; // 교차로 연결 정보 업데이트 ❤️
        } else if (path != null && !path.isEmpty()) {
            // Legacy path fallback
            for (int i = 0; i < path.size() - 1; i++) {
                Point2D.Double p1 = path.get(i);
                Point2D.Double p2 = path.get(i + 1);
                Point2D.Double closest = getClosestPointOnSegment(p1, p2, target);
                double d = closest.distance(target);
                if (d < minDist) {
                    minDist = d;
                    bestPt = closest;
                    bestAngle = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
                }
            }
            if (bestPt != null) {
                this.x = bestPt.x;
                this.y = bestPt.y;
                this.angle = bestAngle;
                this.currentConnection = null;
            }
        } else {
            this.x = target.x;
            this.y = target.y;
            this.currentConnection = null;
        }
    }

    private Point2D.Double getClosestPointOnSegment(Point2D.Double p1, Point2D.Double p2, Point2D.Double t) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return p1;
        double t_proj = ((t.x - p1.x) * dx + (t.y - p1.y) * dy) / l2;
        t_proj = Math.max(0, Math.min(1, t_proj));
        return new Point2D.Double(p1.x + t_proj * dx, p1.y + t_proj * dy);
    }

    // Getter 및 Setter
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(double speedKmh) { this.speedKmh = speedKmh; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public VehicleType getType() { return type; }
    public List<Point2D.Double> getPath() { return path; }
    public void setPath(List<Point2D.Double> path) { this.path = path; }
    public List<Set<Lane>> getLogicalRoute() { return logicalRoute; }
    public void setLogicalRoute(List<Set<Lane>> route) { this.logicalRoute = route; }

    /**
     * 현재 위치에서 목표 차선의 끝점까지 동적인 베지어 경로를 생성하여 path를 업데이트합니다.
     * 단, 교차로 통과 중(currentConnection != null)일 때는 교차로의 고유 경로를 보여줍니다. ❤️
     */
    public void updateDynamicPath(JunctionController junctionController) {
        // 1. 교차로 통과 중인 경우: 교차로의 고유 연결 경로를 그대로 사용 ❤️
        if (currentConnection != null) {
            this.path = new ArrayList<>(currentConnection.connectionPath());
            return;
        }

        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) return;

        // 2. 일반 도로 주행 중인 경우: 동적 베지어 경로 생성
        Set<Lane> currentPhase = logicalRoute.get(currentPhaseIndex);
        Lane targetLane = null;
        if (currentPhaseIndex < logicalRoute.size() - 1) {
            Set<Lane> nextPhase = logicalRoute.get(currentPhaseIndex + 1);
            for (Lane lane : currentPhase) {
                Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                if (conns != null) {
                    for (LaneConnection conn : conns) {
                        if (nextPhase.contains(conn.targetLane())) {
                            targetLane = lane;
                            break;
                        }
                    }
                }
                if (targetLane != null) break;
            }
        }
        
        if (targetLane == null) {
            targetLane = currentPhase.iterator().next();
        }

        List<Point2D.Double> lanePoints = targetLane.getLanePath();
        if (lanePoints == null || lanePoints.size() < 2) return;

        Point2D.Double p3, p2_dir;
        if (targetLane.isRoadDirection()) {
            p3 = lanePoints.get(lanePoints.size() - 1);
            Point2D.Double p3_prev = lanePoints.get(lanePoints.size() - 2);
            p2_dir = new Point2D.Double(p3.x - p3_prev.x, p3.y - p3_prev.y);
        } else {
            p3 = lanePoints.get(0);
            Point2D.Double p3_next = lanePoints.get(1);
            p2_dir = new Point2D.Double(p3.x - p3_next.x, p3.y - p3_next.y);
        }

        Point2D.Double p0 = new Point2D.Double(this.x, this.y);
        double rad = Math.toRadians(this.angle);
        double dist = p0.distance(p3);
        double weight = dist / 2.5; 

        Point2D.Double p1 = new Point2D.Double(p0.x + Math.cos(rad) * weight, p0.y + Math.sin(rad) * weight);
        double len2 = Math.sqrt(p2_dir.x * p2_dir.x + p2_dir.y * p2_dir.y);
        Point2D.Double p2 = new Point2D.Double(p3.x - (p2_dir.x / len2) * weight, p3.y - (p2_dir.y / len2) * weight);

        this.path = new ArrayList<>();
        int segments = 30;
        for (int i = 0; i <= segments; i++) {
            this.path.add(calculateCubicBezier(i / (double) segments, p0, p1, p2, p3));
        }
    }

    private Point2D.Double calculateCubicBezier(double t, Point2D.Double p0, Point2D.Double p1, Point2D.Double p2, Point2D.Double p3) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;
        double x = uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x;
        double y = uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y;
        return new Point2D.Double(x, y);
    }

    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(int index) { this.currentPhaseIndex = index; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
