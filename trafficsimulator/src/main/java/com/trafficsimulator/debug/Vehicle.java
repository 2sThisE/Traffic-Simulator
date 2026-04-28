package com.trafficsimulator.debug;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
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
    private List<Point2D.Double> visionArea = new ArrayList<>(); // 차량의 감지 영역 (시각화용) ❤️

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
     * 차량의 감지 영역(Vision Area)을 도형 교집합(Intersection) 및 합집합(Union)으로 계산합니다. ❤️
     * 전방: 차량이 진입 가능한 모든 차선의 150m (120도 부채꼴) 영역
     * 측방: 차선 변경이 가능한 인접 차선만, 차량 앞코(Front) 기준 뒤로 50m 영역 (도로 굴곡 반영)
     */
    public void updateVisionArea(RoadManager roadManager, JunctionController junctionController) {
        visionArea.clear();
        
        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) {
            return; 
        }

        Point2D.Double p0 = new Point2D.Double(this.x, this.y);
        Set<Lane> currentPhase = logicalRoute.get(currentPhaseIndex);
        
        // 1. 현재 주행 중인 가장 가까운 차선 찾기
        Lane currentLane = null;
        double minDistance = Double.MAX_VALUE;
        for (Lane lane : currentPhase) {
            List<Point2D.Double> pts = lane.getLanePath();
            if (pts == null || pts.size() < 2) continue;
            for (int i = 0; i < pts.size() - 1; i++) {
                double d = getDistanceSqToSegment(pts.get(i), pts.get(i + 1), p0);
                if (d < minDistance) {
                    minDistance = d;
                    currentLane = lane;
                }
            }
        }
        if (currentLane == null) return;

        Road currentRoad = roadManager.findRoadByLane(currentLane);
        if (currentRoad == null) return;

        // 2. 가용 차선 분류 ❤️
        List<Lane> canChangeLanes = currentRoad.getCanChangeLaneList(currentLane);
        
        // 전방용: 현재 차선 포함, 변경 가능한 모든 차선
        List<Lane> forwardTargetLanes = new ArrayList<>(canChangeLanes);
        if (!forwardTargetLanes.contains(currentLane)) forwardTargetLanes.add(currentLane);
        
        // 측방용: 바로 옆 차선(거리 1)이면서 '변경 가능'한 차선만 추출 ❤️
        List<Lane> sideTargetLanes = new ArrayList<>();
        int currentIdx = currentRoad.getLaneList().indexOf(currentLane);
        for (Lane lane : canChangeLanes) {
            int idx = currentRoad.getLaneList().indexOf(lane);
            if (Math.abs(idx - currentIdx) == 1) {
                sideTargetLanes.add(lane);
            }
        }

        double laneWidthPx = UnitConverter.toPixel(3.5); 
        double halfWidth = (laneWidthPx + UnitConverter.toPixel(0.5)) / 2.0;

        // 3. 전방 영역 계산 (150m 부채꼴 ∩ 전체 가용 차선 궤적)
        Area forwardLanesArea = new Area();
        buildLanesArea(forwardLanesArea, forwardTargetLanes, currentPhaseIndex, junctionController, halfWidth);

        double frontMaxDist = UnitConverter.toPixel(150.0);
        Path2D.Double coneMask = createConeMask(p0, this.angle, frontMaxDist, 120.0);
        Area forwardVision = new Area(coneMask);
        forwardVision.intersect(forwardLanesArea);

        // 4. 측방 영역 계산 (차량 앞코 기준 뒤로 50m 궤적) ❤️
        Area sideVision = new Area();
        double sideMaxDist = UnitConverter.toPixel(50.0);
        double vehicleHalfLength = this.width / 2.0; // width가 차량의 길이(Length)임

        for (Lane lane : sideTargetLanes) {
            // 앞코(Front) 위치 = ProjDist + vehicleHalfLength
            // 뒤로 50m 구간 = [ProjDist + vehicleHalfLength - 50, ProjDist + vehicleHalfLength]
            double startOffset = vehicleHalfLength - sideMaxDist;
            double endOffset = vehicleHalfLength;
            
            List<Point2D.Double> subPath = getLaneSubPath(lane, p0, startOffset, endOffset, junctionController);
            if (subPath != null && subPath.size() >= 2) {
                addPathToArea(sideVision, subPath, halfWidth);
            }
        }

        // 5. 최종 시야 영역 합치기
        Area totalVisionArea = new Area();
        totalVisionArea.add(forwardVision);
        totalVisionArea.add(sideVision);

        // 6. 결과 변환
        PathIterator pi = totalVisionArea.getPathIterator(null);
        double[] coords = new double[6];
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                if (!visionArea.isEmpty()) visionArea.add(null);
                visionArea.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_LINETO) {
                visionArea.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_CLOSE) {
                visionArea.add(null);
            }
            pi.next();
        }
    }

    /**
     * 특정 차선들의 전방 궤적을 기반으로 Area를 생성합니다. (교차로 포함)
     */
    private void buildLanesArea(Area targetArea, List<Lane> startLanes, int startPhaseIdx, JunctionController junctionController, double halfWidth) {
        List<Lane> currentTrack = new ArrayList<>(startLanes);
        for (int p = startPhaseIdx; p < logicalRoute.size(); p++) {
            Set<Lane> phaseLanes = logicalRoute.get(p);
            List<Lane> nextTrack = new ArrayList<>();
            for (Lane lane : currentTrack) {
                if (phaseLanes.contains(lane)) {
                    addPathToArea(targetArea, lane.getLanePath(), halfWidth);
                    if (junctionController != null && p < logicalRoute.size() - 1) {
                        Set<Lane> nextPhaseLanes = logicalRoute.get(p + 1);
                        Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                        if (conns != null) {
                            for (LaneConnection conn : conns) {
                                if (nextPhaseLanes.contains(conn.targetLane())) {
                                    addPathToArea(targetArea, conn.connectionPath(), halfWidth);
                                    nextTrack.add(conn.targetLane());
                                }
                            }
                        }
                    }
                }
            }
            currentTrack = nextTrack;
            if (currentTrack.isEmpty()) break;
        }
    }

    /**
     * 부채꼴 모양의 마스크를 생성합니다.
     */
    private Path2D.Double createConeMask(Point2D.Double origin, double centerAngleDeg, double distance, double fovDeg) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(origin.x, origin.y);
        
        int segments = 20;
        double startAngle = Math.toRadians(centerAngleDeg - fovDeg / 2.0);
        double angleStep = Math.toRadians(fovDeg) / segments;
        
        for (int i = 0; i <= segments; i++) {
            double ang = startAngle + (angleStep * i);
            path.lineTo(origin.x + Math.cos(ang) * distance, origin.y + Math.sin(ang) * distance);
        }
        path.closePath();
        return path;
    }

    /**
     * 차선에서 차량의 현재 위치(투영점) 기준, 상대적인 offset 구간의 경로를 추출합니다. ❤️
     */
    private List<Point2D.Double> getLaneSubPath(Lane lane, Point2D.Double vehiclePos, double startOffset, double endOffset, JunctionController junctionController) {
        List<Point2D.Double> fullPath = new ArrayList<>();
        
        // 1. 현재 차선 경로 수집
        List<Point2D.Double> lp = lane.getLanePath();
        if (lp == null || lp.size() < 2) return null;
        List<Point2D.Double> ordered = new ArrayList<>(lp);
        if (!lane.isRoadDirection()) java.util.Collections.reverse(ordered);
        fullPath.addAll(ordered);

        // 2. 차량 투영 지점(ProjDist) 찾기
        double currentProjDist = 0;
        double accumulatedDist = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < fullPath.size() - 1; i++) {
            Point2D.Double p1 = fullPath.get(i);
            Point2D.Double p2 = fullPath.get(i + 1);
            Point2D.Double closest = getClosestPointOnSegment(p1, p2, vehiclePos);
            double d = closest.distance(vehiclePos);
            if (d < minDist) {
                minDist = d;
                currentProjDist = accumulatedDist + p1.distance(closest);
            }
            accumulatedDist += p1.distance(p2);
        }

        // 3. 목표 구간 계산
        double targetStartDist = currentProjDist + startOffset;
        double targetEndDist = currentProjDist + endOffset;
        
        List<Point2D.Double> subPath = new ArrayList<>();
        accumulatedDist = 0;
        boolean started = false;

        for (int i = 0; i < fullPath.size() - 1; i++) {
            Point2D.Double p1 = fullPath.get(i);
            Point2D.Double p2 = fullPath.get(i + 1);
            double segLen = p1.distance(p2);
            
            // 구간 시작점 탐색
            if (!started && accumulatedDist + segLen >= targetStartDist) {
                double ratio = Math.max(0, (targetStartDist - accumulatedDist) / segLen);
                if (ratio <= 1.0) {
                    subPath.add(new Point2D.Double(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio));
                    started = true;
                }
            }
            
            if (started) {
                // 구간 종료점 탐색
                if (accumulatedDist + segLen >= targetEndDist) {
                    double ratio = Math.max(0, (targetEndDist - accumulatedDist) / segLen);
                    if (ratio <= 1.0) {
                        subPath.add(new Point2D.Double(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio));
                    } else {
                        subPath.add(p2);
                    }
                    break;
                } else {
                    subPath.add(p2);
                }
            }
            accumulatedDist += segLen;
        }
        
        return subPath;
    }

    /**
     * 중심선(path)을 양쪽으로 넓혀 다각형 Area에 더합니다.
     */
    private void addPathToArea(Area targetArea, List<Point2D.Double> pts, double halfWidth) {
        if (pts == null || pts.size() < 2) return;

        Path2D.Double poly = new Path2D.Double();
        // 오른쪽 테두리
        for (int i = 0; i < pts.size(); i++) {
            Point2D.Double pt = getOffsetPoint(pts, i, halfWidth);
            if (i == 0) poly.moveTo(pt.x, pt.y);
            else poly.lineTo(pt.x, pt.y);
        }
        // 왼쪽 테두리 (역순으로 돌아오며 닫음)
        for (int i = pts.size() - 1; i >= 0; i--) {
            Point2D.Double pt = getOffsetPoint(pts, i, -halfWidth);
            poly.lineTo(pt.x, pt.y);
        }
        poly.closePath();
        targetArea.add(new Area(poly));
    }

    private Point2D.Double getOffsetPoint(List<Point2D.Double> pts, int i, double offset) {
        Point2D.Double p1, p2;
        if (i < pts.size() - 1) {
            p1 = pts.get(i);
            p2 = pts.get(i + 1);

        } else {
            p1 = pts.get(i - 1);
            p2 = pts.get(i);
        }
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len == 0) return pts.get(i);
        double nx = -dy / len;
        double ny = dx / len;
        return new Point2D.Double(pts.get(i).x + nx * offset, pts.get(i).y + ny * offset);
    }

    /**
     * 최적화를 위해 객체 생성 없이 점과 선분 사이의 거리 제곱을 반환합니다.
     */
    private double getDistanceSqToSegment(Point2D.Double p1, Point2D.Double p2, Point2D.Double t) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return t.distanceSq(p1);
        double t_proj = ((t.x - p1.x) * dx + (t.y - p1.y) * dy) / l2;
        t_proj = Math.max(0, Math.min(1, t_proj));
        double projX = p1.x + t_proj * dx;
        double projY = p1.y + t_proj * dy;
        double rx = t.x - projX;
        double ry = t.y - projY;
        return rx * rx + ry * ry;
    }

    public List<Point2D.Double> getVisionArea() { return visionArea; }

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

        // 현재 차량 각도
        double rad = Math.toRadians(this.angle);
        // 차량의 앞부분(Front)을 시작점 p0로 설정 ❤️
        Point2D.Double p0 = new Point2D.Double(
            this.x + Math.cos(rad) * (this.width / 2.0),
            this.y + Math.sin(rad) * (this.width / 2.0)
        );

        // 현재 차량에서 가장 가까운 차선 찾기 (currentLane)
        Lane currentLane = null;
        double minDistance = Double.MAX_VALUE;
        for (Lane lane : currentPhase) {
            List<Point2D.Double> pts = lane.getLanePath();
            if (pts == null || pts.size() < 2) continue;
            for (int i = 0; i < pts.size() - 1; i++) {
                Point2D.Double closest = getClosestPointOnSegment(pts.get(i), pts.get(i + 1), p0);
                double d = closest.distance(p0);
                if (d < minDistance) {
                    minDistance = d;
                    currentLane = lane;
                }
            }
        }
        if (currentLane == null) currentLane = targetLane;

        // 내 차선이 목표 차선과 일치하면 동적 베지어 계산 스킵 ❤️
        if (currentLane == targetLane) {
            this.path = new ArrayList<>();
            return;
        }

        // 1차선씩 변경: targetLane 방향으로 한 칸 인접한 차선(stepTargetLane) 찾기
        Lane stepTargetLane = targetLane;
        if (currentLane != targetLane) {
            Point2D.Double targetStart = targetLane.getLanePath().get(0);
            Point2D.Double currentStart = currentLane.getLanePath().get(0);
            double distToTargetFromCurrent = currentStart.distance(targetStart);
            
            Lane bestAdjacent = null;
            double minAdjacentDiff = Double.MAX_VALUE;
            
            for (Lane lane : currentPhase) {
                if (lane == currentLane) continue;
                Point2D.Double laneStart = lane.getLanePath().get(0);
                double distToTarget = laneStart.distance(targetStart);
                double distToCurrent = laneStart.distance(currentStart);
                
                // 타겟 차선 방향으로 더 가깝고, 현재 차선과 가장 인접한 차선
                if (distToTarget < distToTargetFromCurrent) {
                    if (distToCurrent < minAdjacentDiff) {
                        minAdjacentDiff = distToCurrent;
                        bestAdjacent = lane;
                    }
                }
            }
            if (bestAdjacent != null) {
                stepTargetLane = bestAdjacent;
            }
        }

        List<Point2D.Double> lanePoints = stepTargetLane.getLanePath();
        if (lanePoints == null || lanePoints.size() < 2) return;

        // 속도에 비례한 베지어 끝점(p3) 찾기
        // 속도가 빠를수록 멀리, 느릴수록 가까이 (lookahead)
        double lookaheadDist = Math.max(30.0, speedKmh * 3.0);
        
        Point2D.Double p3 = null;
        Point2D.Double p2_dir = null;
        
        // stepTargetLane의 경로를 순방향으로 정렬
        List<Point2D.Double> orderedPoints = new ArrayList<>(lanePoints);
        if (!stepTargetLane.isRoadDirection()) {
            java.util.Collections.reverse(orderedPoints);
        }

        // 차량 위치에서 stepTargetLane 상의 투영점(현재 진행 거리) 찾기
        double accumulatedDist = 0;
        double currentProjDist = 0;
        double minDistToPath = Double.MAX_VALUE;
        
        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            Point2D.Double pt1 = orderedPoints.get(i);
            Point2D.Double pt2 = orderedPoints.get(i + 1);
            Point2D.Double closest = getClosestPointOnSegment(pt1, pt2, p0);
            double d = closest.distance(p0);
            if (d < minDistToPath) {
                minDistToPath = d;
                currentProjDist = accumulatedDist + pt1.distance(closest);
            }
            accumulatedDist += pt1.distance(pt2);
        }

        // 목표 진행 거리 (현재 위치 + 룩어헤드)
        double targetDist = currentProjDist + lookaheadDist;
        
        // 목표 거리 위치에 해당하는 p3와 방향 벡터(p2_dir) 계산
        accumulatedDist = 0;
        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            Point2D.Double pt1 = orderedPoints.get(i);
            Point2D.Double pt2 = orderedPoints.get(i + 1);
            double segmentLen = pt1.distance(pt2);
            
            if (accumulatedDist + segmentLen >= targetDist) {
                double ratio = (targetDist - accumulatedDist) / segmentLen;
                p3 = new Point2D.Double(
                    pt1.x + (pt2.x - pt1.x) * ratio,
                    pt1.y + (pt2.y - pt1.y) * ratio
                );
                p2_dir = new Point2D.Double(pt2.x - pt1.x, pt2.y - pt1.y);
                break;
            }
            accumulatedDist += segmentLen;
        }

        // 남은 거리가 짧아 끝을 넘어간다면, 차선 끝을 p3로 설정
        if (p3 == null) {
            p3 = orderedPoints.get(orderedPoints.size() - 1);
            Point2D.Double p3_prev = orderedPoints.get(orderedPoints.size() - 2);
            p2_dir = new Point2D.Double(p3.x - p3_prev.x, p3.y - p3_prev.y);
        }

        
        double dist = p0.distance(p3);
        double weight = dist / 2.5; 

        Point2D.Double p1 = new Point2D.Double(p0.x + Math.cos(rad) * weight, p0.y + Math.sin(rad) * weight);
        double len2 = Math.sqrt(p2_dir.x * p2_dir.x + p2_dir.y * p2_dir.y);
        // p2_dir 방향의 단위벡터를 빼서 p2 생성 (끝점에서 들어오는 방향 고려)
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
