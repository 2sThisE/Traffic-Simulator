package com.trafficsimulator.debug;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.TrafficLaw;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;

/**
 * 차량의 주행 지능 및 경로 관리를 담당하는 오토파일럿 클래스입니다. ❤️
 */
public class Autopilot {
    private final Vehicle vehicle;
    private List<Point2D.Double> path = new ArrayList<>(); // 차량이 따라갈 시각적 경로
    private List<Set<Lane>> logicalRoute = new ArrayList<>(); // 차량이 주행 가능한 논리적 경로 세트
    private int currentPhaseIndex = 0; // 현재 주행 중인 도로 구간 인덱스
    private LaneConnection currentConnection = null; // 현재 교차로 통과 중인 경우 해당 연결 정보
    private List<Point2D.Double> forwardVisionArea = new ArrayList<>(); // 전방 감지 영역 (신호등, 카메라 등) ❤️
    private List<Point2D.Double> sideVisionArea = new ArrayList<>();    // 측방 감지 영역 (타 차량 등) ❤️
    private List<Point2D.Double> vehicleVisionArea = new ArrayList<>(); // 전방 차량 감지 영역 (안전거리 기반) ❤️

    public Autopilot(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    /**
     * 현재 속도(km/h)에 따른 1틱당 이동 거리(Pixel)를 계산하여 차량 위치를 업데이트합니다.
     */
    public void updatePosition() {
        double ms = UnitConverter.kmhToMs(vehicle.getSpeedKmh());
        double pixelPerTick = UnitConverter.toPixelPerTick(ms);
        
        double rad = Math.toRadians(vehicle.getAngle());
        vehicle.setX(vehicle.getX() + Math.cos(rad) * pixelPerTick);
        vehicle.setY(vehicle.getY() + Math.sin(rad) * pixelPerTick);
    }

    /**
     * 차량의 감지 영역(Vision Area)을 계산합니다.
     */
    public void updateVisionArea(RoadManager roadManager, JunctionController junctionController) {
        forwardVisionArea.clear();
        sideVisionArea.clear();
        vehicleVisionArea.clear();
        
        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) {
            return; 
        }

        Point2D.Double p0 = new Point2D.Double(vehicle.getX(), vehicle.getY());
        Set<Lane> currentPhase = logicalRoute.get(currentPhaseIndex);
        
        Lane currentLane = findNearestLane(currentPhase, p0);
        if (currentLane == null) return;

        Road currentRoad = roadManager.findRoadByLane(currentLane);
        if (currentRoad == null) return;

        List<Lane> canChangeLanes = currentRoad.getCanChangeLaneList(currentLane);
        List<Lane> forwardTargetLanes = new ArrayList<>(canChangeLanes);
        if (!forwardTargetLanes.contains(currentLane)) forwardTargetLanes.add(currentLane);
        
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

        // 1. 전방 감지 영역 (신호등, 카메라용) ❤️
        Area forwardLanesArea = new Area();
        buildLanesArea(forwardLanesArea, forwardTargetLanes, currentPhaseIndex, junctionController, halfWidth);

        double frontMaxDist = UnitConverter.toPixel(150.0);
        Path2D.Double coneMask = createConeMask(p0, vehicle.getAngle(), frontMaxDist, 120.0);
        Area forwardVision = new Area(coneMask);
        forwardVision.intersect(forwardLanesArea);
        fillVisionList(forwardVisionArea, forwardVision);

        // 2. 측방 감지 영역 (타 차량용) ❤️
        Area sideVision = new Area();
        double sideMaxDist = UnitConverter.toPixel(50.0);
        double vehicleHalfLength = vehicle.getWidth() / 2.0;

        for (Lane lane : sideTargetLanes) {
            double startOffset = vehicleHalfLength - sideMaxDist;
            double endOffset = vehicleHalfLength;
            
            List<Point2D.Double> subPath = getLaneSubPath(lane, p0, startOffset, endOffset, junctionController);
            if (subPath != null && subPath.size() >= 2) {
                addPathToArea(sideVision, subPath, halfWidth);
            }
        }
        fillVisionList(sideVisionArea, sideVision);

        // 3. 전방 차량 감지 영역 (신호등 감지처럼 직선으로 뻗되, 차선 경계 중 가장 먼 접점 기준) ❤️
        double safetyDistM = TrafficLaw.getRecommendedSafetyDistance(vehicle.getSpeedKmh());
        double safetyDistPx = UnitConverter.toPixel(safetyDistM);
        
        // 현재 차선 및 향후 경로를 포함하는 전체 영역 생성
        Area trackArea = new Area();
        buildLanesArea(trackArea, Collections.singletonList(currentLane), currentPhaseIndex, junctionController, halfWidth);

        // [STEP A] 우선 최대 안전거리까지 직선으로 뻗는 임시 마스크 생성
        Path2D.Double tempMask = createRectMask(p0, vehicle.getAngle(), vehicleHalfLength, safetyDistPx, halfWidth * 2.0);
        Area tempIntersection = new Area(tempMask);
        tempIntersection.intersect(trackArea);

        // [STEP B] 임시 영역의 점들 중 차량 중심(p0)에서 가장 먼 거리 찾기 ❤️
        double maxContactDistSq = 0;
        PathIterator pi = tempIntersection.getPathIterator(null);
        double[] coords = new double[6];
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            if (type != PathIterator.SEG_CLOSE) {
                double d2 = p0.distanceSq(coords[0], coords[1]);
                if (d2 > maxContactDistSq) {
                    maxContactDistSq = d2;
                }
            }
            pi.next();
        }
        
        // [STEP C] 가장 먼 접점의 거리를 감지 영역의 최종 길이로 설정
        double finalLength = Math.sqrt(maxContactDistSq) - vehicleHalfLength;
        finalLength = Math.max(0, Math.min(finalLength, safetyDistPx)); // 안전거리 내로 보정

        // [STEP D] 결정된 길이를 바탕으로 최종 영역 생성
        Path2D.Double finalRect = createRectMask(p0, vehicle.getAngle(), vehicleHalfLength, finalLength, halfWidth * 2.0);
        Area vehicleVision = new Area(finalRect);
        vehicleVision.intersect(trackArea); 
        
        fillVisionList(vehicleVisionArea, vehicleVision);
    }

    private void fillVisionList(List<Point2D.Double> targetList, Area area) {
        PathIterator pi = area.getPathIterator(null);
        double[] coords = new double[6];
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                if (!targetList.isEmpty()) targetList.add(null);
                targetList.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_LINETO) {
                targetList.add(new Point2D.Double(coords[0], coords[1]));
            } else if (type == PathIterator.SEG_CLOSE) {
                targetList.add(null);
            }
            pi.next();
        }
    }

    private Lane findNearestLane(Set<Lane> lanes, Point2D.Double pos) {
        Lane currentLane = null;
        double minDistance = Double.MAX_VALUE;
        for (Lane lane : lanes) {
            List<Point2D.Double> pts = lane.getLanePath();
            if (pts == null || pts.size() < 2) continue;
            for (int i = 0; i < pts.size() - 1; i++) {
                double d = getDistanceSqToSegment(pts.get(i), pts.get(i + 1), pos);
                if (d < minDistance) {
                    minDistance = d;
                    currentLane = lane;
                }
            }
        }
        return currentLane;
    }

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

    private Path2D.Double createRectMask(Point2D.Double origin, double angleDeg, double startOffset, double length, double width) {
        Path2D.Double path = new Path2D.Double();
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        
        // 너비 방향 법선 벡터 (nx, ny)
        double nx = -sin * (width / 2.0);
        double ny = cos * (width / 2.0);
        
        // 네 모서리 계산
        double x1 = origin.x + cos * startOffset - nx;
        double y1 = origin.y + sin * startOffset - ny;
        double x2 = origin.x + cos * (startOffset + length) - nx;
        double y2 = origin.y + sin * (startOffset + length) - ny;
        double x3 = origin.x + cos * (startOffset + length) + nx;
        double y3 = origin.y + sin * (startOffset + length) + ny;
        double x4 = origin.x + cos * startOffset + nx;
        double y4 = origin.y + sin * startOffset + ny;

        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.closePath();
        return path;
    }

    private List<Point2D.Double> getLaneSubPath(Lane lane, Point2D.Double vehiclePos, double startOffset, double endOffset, JunctionController junctionController) {
        List<Point2D.Double> lp = lane.getLanePath();
        if (lp == null || lp.size() < 2) return null;
        List<Point2D.Double> fullPath = new ArrayList<>(lp);
        if (!lane.isRoadDirection()) Collections.reverse(fullPath);

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

        double targetStartDist = currentProjDist + startOffset;
        double targetEndDist = currentProjDist + endOffset;
        List<Point2D.Double> subPath = new ArrayList<>();
        accumulatedDist = 0;
        boolean started = false;
        for (int i = 0; i < fullPath.size() - 1; i++) {
            Point2D.Double p1 = fullPath.get(i);
            Point2D.Double p2 = fullPath.get(i + 1);
            double segLen = p1.distance(p2);
            if (!started && accumulatedDist + segLen >= targetStartDist) {
                double ratio = Math.max(0, (targetStartDist - accumulatedDist) / segLen);
                if (ratio <= 1.0) {
                    subPath.add(new Point2D.Double(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio));
                    started = true;
                }
            }
            if (started) {
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

    private void addPathToArea(Area targetArea, List<Point2D.Double> pts, double halfWidth) {
        if (pts == null || pts.size() < 2) return;
        Path2D.Double poly = new Path2D.Double();
        for (int i = 0; i < pts.size(); i++) {
            Point2D.Double pt = getOffsetPoint(pts, i, halfWidth);
            if (i == 0) poly.moveTo(pt.x, pt.y);
            else poly.lineTo(pt.x, pt.y);
        }
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

    public void snapToNearestPoint(Point2D.Double target, JunctionController junctionController) {
        double minDist = Double.MAX_VALUE;
        Point2D.Double bestPt = null;
        double bestAngle = vehicle.getAngle();
        int bestPhase = this.currentPhaseIndex;
        LaneConnection bestConn = null;

        if (logicalRoute != null && !logicalRoute.isEmpty()) {
            for (int p = 0; p < logicalRoute.size(); p++) {
                Set<Lane> phase = logicalRoute.get(p);
                for (Lane lane : phase) {
                    List<Point2D.Double> points = new ArrayList<>(lane.getLanePath());
                    if (!lane.isRoadDirection()) Collections.reverse(points);
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
                            bestConn = null;
                        }
                    }
                }
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
                                            bestConn = conn;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bestPt != null) {
            vehicle.setX(bestPt.x);
            vehicle.setY(bestPt.y);
            vehicle.setAngle(bestAngle);
            this.currentPhaseIndex = bestPhase;
            this.currentConnection = bestConn;
        } else if (path != null && !path.isEmpty()) {
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
                vehicle.setX(bestPt.x);
                vehicle.setY(bestPt.y);
                vehicle.setAngle(bestAngle);
                this.currentConnection = null;
            }
        } else {
            vehicle.setX(target.x);
            vehicle.setY(target.y);
            this.currentConnection = null;
        }
    }

    public void updateDynamicPath(JunctionController junctionController) {
        if (currentConnection != null) {
            this.path = new ArrayList<>(currentConnection.connectionPath());
            return;
        }

        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) return;

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
        
        if (targetLane == null) targetLane = currentPhase.iterator().next();

        double rad = Math.toRadians(vehicle.getAngle());
        Point2D.Double p0 = new Point2D.Double(
            vehicle.getX() + Math.cos(rad) * (vehicle.getWidth() / 2.0),
            vehicle.getY() + Math.sin(rad) * (vehicle.getWidth() / 2.0)
        );

        Lane currentLane = findNearestLane(currentPhase, p0);
        if (currentLane == null) currentLane = targetLane;

        if (currentLane == targetLane) {
            this.path = new ArrayList<>();
            return;
        }

        Lane stepTargetLane = findStepTargetLane(currentPhase, currentLane, targetLane);
        List<Point2D.Double> lanePoints = stepTargetLane.getLanePath();
        if (lanePoints == null || lanePoints.size() < 2) return;

        double lookaheadDist = Math.max(30.0, vehicle.getSpeedKmh() * 3.0);
        List<Point2D.Double> orderedPoints = new ArrayList<>(lanePoints);
        if (!stepTargetLane.isRoadDirection()) Collections.reverse(orderedPoints);

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

        double targetDist = currentProjDist + lookaheadDist;
        Point2D.Double p3 = null;
        Point2D.Double p2_dir = null;
        accumulatedDist = 0;
        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            Point2D.Double pt1 = orderedPoints.get(i);
            Point2D.Double pt2 = orderedPoints.get(i + 1);
            double segmentLen = pt1.distance(pt2);
            if (accumulatedDist + segmentLen >= targetDist) {
                double ratio = (targetDist - accumulatedDist) / segmentLen;
                p3 = new Point2D.Double(pt1.x + (pt2.x - pt1.x) * ratio, pt1.y + (pt2.y - pt1.y) * ratio);
                p2_dir = new Point2D.Double(pt2.x - pt1.x, pt2.y - pt1.y);
                break;
            }
            accumulatedDist += segmentLen;
        }

        if (p3 == null) {
            p3 = orderedPoints.get(orderedPoints.size() - 1);
            Point2D.Double p3_prev = orderedPoints.get(orderedPoints.size() - 2);
            p2_dir = new Point2D.Double(p3.x - p3_prev.x, p3.y - p3_prev.y);
        }

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

    private Lane findStepTargetLane(Set<Lane> currentPhase, Lane currentLane, Lane targetLane) {
        Point2D.Double targetStart = targetLane.getLanePath().get(0);
        Point2D.Double currentStart = currentLane.getLanePath().get(0);
        double distToTargetFromCurrent = currentStart.distance(targetStart);
        Lane bestAdjacent = targetLane;
        double minAdjacentDiff = Double.MAX_VALUE;
        for (Lane lane : currentPhase) {
            if (lane == currentLane) continue;
            Point2D.Double laneStart = lane.getLanePath().get(0);
            double distToTarget = laneStart.distance(targetStart);
            double distToCurrent = laneStart.distance(currentStart);
            if (distToTarget < distToTargetFromCurrent) {
                if (distToCurrent < minAdjacentDiff) {
                    minAdjacentDiff = distToCurrent;
                    bestAdjacent = lane;
                }
            }
        }
        return bestAdjacent;
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

    private Point2D.Double getClosestPointOnSegment(Point2D.Double p1, Point2D.Double p2, Point2D.Double t) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return p1;
        double t_proj = ((t.x - p1.x) * dx + (t.y - p1.y) * dy) / l2;
        t_proj = Math.max(0, Math.min(1, t_proj));
        return new Point2D.Double(p1.x + t_proj * dx, p1.y + t_proj * dy);
    }

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

    // Getters and Setters
    public List<Point2D.Double> getPath() { return path; }
    public void setPath(List<Point2D.Double> path) { this.path = path; }
    public List<Set<Lane>> getLogicalRoute() { return logicalRoute; }
    public void setLogicalRoute(List<Set<Lane>> route) { this.logicalRoute = route; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(int index) { this.currentPhaseIndex = index; }
    public List<Point2D.Double> getForwardVisionArea() { return forwardVisionArea; }
    public List<Point2D.Double> getSideVisionArea() { return sideVisionArea; }
    public List<Point2D.Double> getVehicleVisionArea() { return vehicleVisionArea; }
    public LaneConnection getCurrentConnection() { return currentConnection; }
}
