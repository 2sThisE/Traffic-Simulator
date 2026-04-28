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
    
    // 시각적 렌더링을 위한 포인트 리스트 ❤️
    private List<Point2D.Double> forwardVisionArea = new ArrayList<>(); 
    private List<Point2D.Double> sideVisionArea = new ArrayList<>();    
    private List<Point2D.Double> vehicleVisionArea = new ArrayList<>(); 

    // 논리적 판단을 위한 Area 객체 저장용 ❤️
    private Area lastForwardVisionArea = new Area(); 
    private Area lastSideVisionArea = new Area(); 
    private Area lastVehicleVisionArea = new Area(); 

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
     * 차량의 모든 감지 영역(Vision Area)을 계산합니다.
     */
    public void updateVisionArea(RoadManager roadManager, JunctionController junctionController, List<Vehicle> allVehicles) {
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
        double halfWidth = (laneWidthPx - UnitConverter.toPixel(0.4)) / 2.0;

        // 1. 전방 감지 영역 (신호등, 카메라용) ❤️
        Area forwardLanesArea = new Area();
        buildLanesArea(forwardLanesArea, forwardTargetLanes, currentPhaseIndex, junctionController, halfWidth);
        
        Area raycastArea = new Area();
        buildLanesArea(raycastArea, forwardTargetLanes, currentPhaseIndex, junctionController, laneWidthPx / 2.0 + UnitConverter.toPixel(0.5));

        double maxDistPx = UnitConverter.toPixel(300.0);
        double rad = Math.toRadians(vehicle.getAngle());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        
        Point2D.Double frontPos = new Point2D.Double(
            vehicle.getX() + cos * (vehicle.getWidth() / 2.0),
            vehicle.getY() + sin * (vehicle.getWidth() / 2.0)
        );

        double actualRadiusPx = maxDistPx;
        double stepPx = UnitConverter.toPixel(1.0); 
        boolean inside = false;
        
        for (double d = 0; d <= maxDistPx; d += stepPx) {
            double tx = frontPos.x + cos * d;
            double ty = frontPos.y + sin * d;
            
            if (raycastArea.contains(tx, ty)) {
                inside = true;
            } else if (inside) {
                actualRadiusPx = d;
                break;
            }
        }

        Path2D.Double arcMask = createConeMask(frontPos, vehicle.getAngle(), actualRadiusPx, 120.0);
        Area forwardVision = new Area(arcMask);
        forwardVision.intersect(forwardLanesArea);
        
        this.lastForwardVisionArea = forwardVision; // Area 저장
        fillVisionList(forwardVisionArea, forwardVision); // 포인트 리스트 변환

        // 2. 측방 감지 영역 (타 차량용) ❤️
        Area sideVision = new Area();
        double sideMaxDist = UnitConverter.toPixel(40.0); 
        double vehicleHalfLength = vehicle.getWidth() / 2.0;

        for (Lane lane : sideTargetLanes) {
            double myProjDist = getProjectionDistance(lane, p0);
            if (myProjDist < 0) continue;

            List<double[]> occupiedRanges = new ArrayList<>();
            for (Vehicle other : allVehicles) {
                if (other == vehicle) continue;

                boolean isOnSameRoad = false;
                List<Set<Lane>> otherRoute = other.getLogicalRoute();
                int otherPhaseIdx = other.getCurrentPhaseIndex();
                if (otherRoute != null && !otherRoute.isEmpty() && otherPhaseIdx < otherRoute.size()) {
                    Set<Lane> otherPhaseLanes = otherRoute.get(otherPhaseIdx);
                    if (otherPhaseLanes.contains(lane)) {
                        isOnSameRoad = true;
                    }
                }

                if (isOnSameRoad) {
                    Point2D.Double otherPos = new Point2D.Double(other.getX(), other.getY());
                    double otherProjDist = getProjectionDistance(lane, otherPos);
                    if (otherProjDist >= 0) {
                        double distToLaneCenterSq = getDistanceSqToPath(lane, otherPos);
                        if (distToLaneCenterSq < (laneWidthPx * laneWidthPx / 4.0)) {
                            double longDist = otherProjDist - myProjDist;
                            double otherHalfLength = other.getWidth() / 2.0;
                            occupiedRanges.add(new double[]{longDist - otherHalfLength, longDist + otherHalfLength});
                        }
                    }
                }
            }

            occupiedRanges.sort((a, b) -> Double.compare(a[0], b[0]));

            // 후방 경계: 가장 가까운 차까지만 (최대 40m) ❤️
            double nearestRearBoundary = -sideMaxDist;
            for (double[] range : occupiedRanges) {
                if (range[0] < -vehicleHalfLength) {
                    nearestRearBoundary = Math.max(nearestRearBoundary, range[1]);
                }
            }

            double viewStart = Math.min(sideMaxDist, nearestRearBoundary);
            double viewEnd = sideMaxDist;
            double currentPos = viewStart;

            // 전방: 모든 빈 공간 표시 ❤️
            for (double[] range : occupiedRanges) {
                if (range[1] <= currentPos) continue;
                if (range[0] > currentPos) {
                    double gapStart = currentPos;
                    double gapEnd = Math.min(range[0], viewEnd);
                    if (gapEnd > gapStart) {
                        List<Point2D.Double> subPath = getLaneSubPath(lane, p0, gapStart, gapEnd, junctionController);
                        if (subPath != null && subPath.size() >= 2) {
                            addPathToArea(sideVision, subPath, halfWidth);
                        }
                    }
                }
                currentPos = Math.max(currentPos, range[1]);
                if (currentPos >= viewEnd) break;
            }

            if (currentPos < viewEnd) {
                List<Point2D.Double> subPath = getLaneSubPath(lane, p0, currentPos, viewEnd, junctionController);
                if (subPath != null && subPath.size() >= 2) {
                    addPathToArea(sideVision, subPath, halfWidth);
                }
            }
        }
        
        this.lastSideVisionArea = sideVision; // Area 저장
        fillVisionList(sideVisionArea, sideVision); // 포인트 리스트 변환

        // 3. 전방 차량 감지 영역 (안전거리용) ❤️
        double safetyDistM = TrafficLaw.getRecommendedSafetyDistance(vehicle.getSpeedKmh());
        safetyDistM = Math.min(safetyDistM, 100.0); 
        double safetyDistPx = UnitConverter.toPixel(safetyDistM);
        
        double adjustedSafetyDistPx = safetyDistPx;
        for (Vehicle other : allVehicles) {
            if (other == vehicle) continue;

            boolean isOnSameRoad = false;
            List<Set<Lane>> otherRoute = other.getLogicalRoute();
            int otherPhaseIdx = other.getCurrentPhaseIndex();
            if (otherRoute != null && !otherRoute.isEmpty() && otherPhaseIdx < otherRoute.size()) {
                Set<Lane> otherPhaseLanes = otherRoute.get(otherPhaseIdx);
                for (int p = currentPhaseIndex; p < logicalRoute.size(); p++) {
                    Set<Lane> myPhaseLanes = logicalRoute.get(p);
                    if (!Collections.disjoint(myPhaseLanes, otherPhaseLanes)) {
                        isOnSameRoad = true;
                        break;
                    }
                }
            }
            if (!isOnSameRoad) continue;

            Point2D.Double otherPos = new Point2D.Double(other.getX(), other.getY());
            List<Point2D.Double> fullTraced = traceRoutePath(currentLane, frontPos, safetyDistPx, currentPhaseIndex, junctionController);
            if (fullTraced != null && fullTraced.size() >= 2) {
                double accumulatedDist = 0;
                for (int i = 0; i < fullTraced.size() - 1; i++) {
                    Point2D.Double p1 = fullTraced.get(i);
                    Point2D.Double p2 = fullTraced.get(i + 1);
                    double dSq = getDistanceSqToSegment(p1, p2, otherPos);
                    
                    if (dSq < (laneWidthPx * laneWidthPx / 4.0)) {
                        Point2D.Double closest = getClosestPointOnSegment(p1, p2, otherPos);
                        double pathDistToOtherCenter = accumulatedDist + p1.distance(closest);
                        double pathDistToOtherRear = pathDistToOtherCenter - other.getWidth() / 2.0;
                        if (pathDistToOtherRear < adjustedSafetyDistPx) {
                            adjustedSafetyDistPx = Math.max(0, pathDistToOtherRear);
                        }
                    }
                    accumulatedDist += p1.distance(p2);
                }
            }
        }

        List<Point2D.Double> tracedPath = traceRoutePath(currentLane, frontPos, adjustedSafetyDistPx, currentPhaseIndex, junctionController);
        Area vehicleVision = new Area();
        if (tracedPath != null && tracedPath.size() >= 2) {
            addPathToArea(vehicleVision, tracedPath, halfWidth);
        }
        
        this.lastVehicleVisionArea = vehicleVision; // Area 저장
        fillVisionList(vehicleVisionArea, vehicleVision); // 포인트 리스트 변환
    }

    private double getProjectionDistance(Lane lane, Point2D.Double pos) {
        List<Point2D.Double> fullPath = lane.getLanePath();
        if (fullPath == null || fullPath.size() < 2) return -1;
        List<Point2D.Double> path = new ArrayList<>(fullPath);
        if (!lane.isRoadDirection()) Collections.reverse(path);
        double accumulatedDist = 0;
        double minDist = Double.MAX_VALUE;
        double currentProjDist = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            Point2D.Double closest = getClosestPointOnSegment(p1, p2, pos);
            double d = closest.distance(pos);
            if (d < minDist) {
                minDist = d;
                currentProjDist = accumulatedDist + p1.distance(closest);
            }
            accumulatedDist += p1.distance(p2);
        }
        return currentProjDist;
    }

    private double getDistanceSqToPath(Lane lane, Point2D.Double pos) {
        List<Point2D.Double> fullPath = lane.getLanePath();
        if (fullPath == null || fullPath.size() < 2) return Double.MAX_VALUE;
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < fullPath.size() - 1; i++) {
            double dSq = getDistanceSqToSegment(fullPath.get(i), fullPath.get(i + 1), pos);
            if (dSq < minDistSq) minDistSq = dSq;
        }
        return minDistSq;
    }

    private List<Point2D.Double> traceRoutePath(Lane startLane, Point2D.Double startPos, double totalDist, int startPhaseIdx, JunctionController junctionController) {
        List<Point2D.Double> traced = new ArrayList<>();
        double remainingDist = totalDist;
        Lane currentLane = startLane;
        Point2D.Double currentPos = startPos;

        for (int p = startPhaseIdx; p < logicalRoute.size(); p++) {
            List<Point2D.Double> sub = getLaneSubPath(currentLane, currentPos, 0, remainingDist, junctionController);
            if (sub != null && !sub.isEmpty()) {
                for (Point2D.Double pt : sub) {
                    if (traced.isEmpty() || traced.get(traced.size() - 1).distanceSq(pt) > 0.1) traced.add(pt);
                }
                double segmentDist = 0;
                for (int i = 0; i < sub.size() - 1; i++) segmentDist += sub.get(i).distance(sub.get(i + 1));
                remainingDist -= segmentDist;
            }
            if (remainingDist <= 1.0) break;

            if (p < logicalRoute.size() - 1 && junctionController != null) {
                Set<Lane> nextPhaseLanes = logicalRoute.get(p + 1);
                Set<LaneConnection> conns = junctionController.getConnectionList(currentLane);
                LaneConnection nextConn = null;
                if (conns != null) {
                    for (LaneConnection conn : conns) {
                        if (nextPhaseLanes.contains(conn.targetLane())) {
                            nextConn = conn;
                            break;
                        }
                    }
                }
                if (nextConn != null) {
                    List<Point2D.Double> connSub = getPathSubList(nextConn.connectionPath(), remainingDist);
                    if (connSub != null && !connSub.isEmpty()) {
                        for (Point2D.Double pt : connSub) {
                            if (traced.isEmpty() || traced.get(traced.size() - 1).distanceSq(pt) > 0.1) traced.add(pt);
                        }
                        double cDist = 0;
                        for (int i = 0; i < connSub.size() - 1; i++) cDist += connSub.get(i).distance(connSub.get(i + 1));
                        remainingDist -= cDist;
                        currentLane = nextConn.targetLane();
                        currentPos = connSub.get(connSub.size() - 1);
                    } else break;
                } else break;
            } else break;
            if (remainingDist <= 1.0) break;
        }
        return traced;
    }

    private List<Point2D.Double> getPathSubList(List<Point2D.Double> fullPath, double maxDist) {
        if (fullPath == null || fullPath.isEmpty()) return null;
        List<Point2D.Double> sub = new ArrayList<>();
        double acc = 0;
        sub.add(fullPath.get(0));
        for (int i = 0; i < fullPath.size() - 1; i++) {
            double d = fullPath.get(i).distance(fullPath.get(i + 1));
            if (acc + d > maxDist) {
                double ratio = (maxDist - acc) / d;
                Point2D.Double p1 = fullPath.get(i);
                Point2D.Double p2 = fullPath.get(i + 1);
                sub.add(new Point2D.Double(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio));
                break;
            }
            sub.add(fullPath.get(i + 1));
            acc += d;
            if (acc >= maxDist) break;
        }
        return sub;
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
                        Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                        if (conns != null) {
                            for (LaneConnection conn : conns) {
                                if (logicalRoute.get(p + 1).contains(conn.targetLane())) {
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
                    } else subPath.add(p2);
                    break;
                } else subPath.add(p2);
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
        double lookaheadDist = Math.max(30.0, vehicle.getSpeedKmh() * 3.0);

        if (stepTargetLane != currentLane && lastSideVisionArea != null) {
            double minLookahead = UnitConverter.toPixel(15.0);
            double safeLimit = 0;
            double checkStep = UnitConverter.toPixel(1.0);
            List<Point2D.Double> checkPath = getLaneSubPath(stepTargetLane, p0, 0, lookaheadDist, junctionController);
            if (checkPath != null && checkPath.size() >= 2) {
                double accumulated = 0;
                boolean blocked = false;
                for (int i = 0; i < checkPath.size() - 1; i++) {
                    Point2D.Double p1 = checkPath.get(i);
                    Point2D.Double p2 = checkPath.get(i + 1);
                    double dist = p1.distance(p2);
                    int steps = (int) Math.max(1, Math.ceil(dist / checkStep));
                    for(int s = 0; s <= steps; s++) {
                        double ratio = s / (double)steps;
                        if (!lastSideVisionArea.contains(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio)) {
                            blocked = true; break;
                        }
                        safeLimit = accumulated + dist * ratio;
                    }
                    if (blocked) break;
                    accumulated += dist;
                }
                if (blocked && safeLimit < minLookahead) stepTargetLane = currentLane;
                else if (blocked) lookaheadDist = Math.min(lookaheadDist, safeLimit);
            } else stepTargetLane = currentLane;
        }

        List<Point2D.Double> orderedPoints = new ArrayList<>(stepTargetLane.getLanePath());
        if (!stepTargetLane.isRoadDirection()) Collections.reverse(orderedPoints);

        double accDist = 0, currentProjDist = 0, minDistToPath = Double.MAX_VALUE;
        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            Point2D.Double closest = getClosestPointOnSegment(orderedPoints.get(i), orderedPoints.get(i + 1), p0);
            double d = closest.distance(p0);
            if (d < minDistToPath) { minDistToPath = d; currentProjDist = accDist + orderedPoints.get(i).distance(closest); }
            accDist += orderedPoints.get(i).distance(orderedPoints.get(i+1));
        }

        double targetDist = currentProjDist + lookaheadDist;
        Point2D.Double p3 = null, p2_dir = null;
        accDist = 0;
        for (int i = 0; i < orderedPoints.size() - 1; i++) {
            double segLen = orderedPoints.get(i).distance(orderedPoints.get(i+1));
            if (accDist + segLen >= targetDist) {
                double ratio = (targetDist - accDist) / segLen;
                p3 = new Point2D.Double(orderedPoints.get(i).x + (orderedPoints.get(i+1).x - orderedPoints.get(i).x) * ratio, orderedPoints.get(i).y + (orderedPoints.get(i+1).y - orderedPoints.get(i).y) * ratio);
                p2_dir = new Point2D.Double(orderedPoints.get(i+1).x - orderedPoints.get(i).x, orderedPoints.get(i+1).y - orderedPoints.get(i).y);
                break;
            }
            accDist += segLen;
        }
        if (p3 == null) {
            p3 = orderedPoints.get(orderedPoints.size() - 1);
            p2_dir = new Point2D.Double(p3.x - orderedPoints.get(orderedPoints.size() - 2).x, p3.y - orderedPoints.get(orderedPoints.size() - 2).y);
        }

        double dist = p0.distance(p3), weight = dist / 2.5;
        Point2D.Double p1 = new Point2D.Double(p0.x + Math.cos(rad) * weight, p0.y + Math.sin(rad) * weight);
        double len2 = Math.sqrt(p2_dir.x * p2_dir.x + p2_dir.y * p2_dir.y);
        Point2D.Double p2 = new Point2D.Double(p3.x - (p2_dir.x / len2) * weight, p3.y - (p2_dir.y / len2) * weight);

        this.path = new ArrayList<>();
        for (int i = 0; i <= 30; i++) this.path.add(calculateCubicBezier(i / 30.0, p0, p1, p2, p3));
    }

    private Lane findStepTargetLane(Set<Lane> currentPhase, Lane currentLane, Lane targetLane) {
        Point2D.Double targetStart = targetLane.getLanePath().get(0), currentStart = currentLane.getLanePath().get(0);
        double distToTargetFromCurrent = currentStart.distance(targetStart), minAdjacentDiff = Double.MAX_VALUE;
        Lane bestAdjacent = targetLane;
        for (Lane lane : currentPhase) {
            if (lane == currentLane) continue;
            double distToTarget = lane.getLanePath().get(0).distance(targetStart), distToCurrent = lane.getLanePath().get(0).distance(currentStart);
            if (distToTarget < distToTargetFromCurrent && distToCurrent < minAdjacentDiff) { minAdjacentDiff = distToCurrent; bestAdjacent = lane; }
        }
        return bestAdjacent;
    }

    private Point2D.Double calculateCubicBezier(double t, Point2D.Double p0, Point2D.Double p1, Point2D.Double p2, Point2D.Double p3) {
        double u = 1 - t, tt = t * t, uu = u * u, uuu = uu * u, ttt = tt * t;
        return new Point2D.Double(uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x, uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y);
    }

    private Point2D.Double getClosestPointOnSegment(Point2D.Double p1, Point2D.Double p2, Point2D.Double t) {
        double dx = p2.x - p1.x, dy = p2.y - p1.y, l2 = dx * dx + dy * dy;
        if (l2 == 0) return p1;
        double t_proj = Math.max(0, Math.min(1, ((t.x - p1.x) * dx + (t.y - p1.y) * dy) / l2));
        return new Point2D.Double(p1.x + t_proj * dx, p1.y + t_proj * dy);
    }

    private double getDistanceSqToSegment(Point2D.Double p1, Point2D.Double p2, Point2D.Double t) {
        Point2D.Double closest = getClosestPointOnSegment(p1, p2, t);
        return t.distanceSq(closest);
    }

    // Getters and Setters ❤️
    public List<Point2D.Double> getPath() { return path; }
    public void setPath(List<Point2D.Double> path) { this.path = path; }
    public List<Set<Lane>> getLogicalRoute() { return logicalRoute; }
    public void setLogicalRoute(List<Set<Lane>> route) { this.logicalRoute = route; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(int index) { this.currentPhaseIndex = index; }
    public List<Point2D.Double> getForwardVisionArea() { return forwardVisionArea; }
    public List<Point2D.Double> getSideVisionArea() { return sideVisionArea; }
    public List<Point2D.Double> getVehicleVisionArea() { return vehicleVisionArea; }
    public Area getLastForwardVisionArea() { return lastForwardVisionArea; }
    public Area getLastSideVisionArea() { return lastSideVisionArea; }
    public Area getLastVehicleVisionArea() { return lastVehicleVisionArea; }
    public LaneConnection getCurrentConnection() { return currentConnection; }
}
