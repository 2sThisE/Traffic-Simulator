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
    private Area lastSideVisionArea = new Area(); // 측방 감지 영역(Area 객체) 저장용 ❤️

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
        // 차선 경계에서 살짝 안쪽으로 들어오도록 0.4m 정도 여유를 줌 ❤️
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
        fillVisionList(forwardVisionArea, forwardVision);

        // 2. 측방 감지 영역 (타 차량용) ❤️
        Area sideVision = new Area();
        double sideMaxDist = UnitConverter.toPixel(40.0); // 앞뒤 40m로 분리 적용 ❤️
        double vehicleHalfLength = vehicle.getWidth() / 2.0;

        for (Lane lane : sideTargetLanes) {
            double myProjDist = getProjectionDistance(lane, p0);
            if (myProjDist < 0) continue;

            // 구간 3개로 분리: 바로 옆(Center), 뒤쪽(Back), 앞쪽(Forward) ❤️
            boolean centerBlocked = false;
            double forwardLimit = sideMaxDist;
            double backwardLimit = sideMaxDist;

            for (Vehicle other : allVehicles) {
                if (other == vehicle) continue;

                // 다른 차량이 해당 차선이 속한 도로를 실제로 주행 중인지(논리적 검사) ❤️
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
                        // 차선 너비 내에 있으면 (측방 차량이 실제로 해당 차선 위에 있으면)
                        if (distToLaneCenterSq < (laneWidthPx * laneWidthPx / 4.0)) {
                            double longDist = otherProjDist - myProjDist;
                            double otherHalfLength = other.getWidth() / 2.0;

                            double otherFront = longDist + otherHalfLength;
                            double otherRear = longDist - otherHalfLength;

                            // 1. 바로 옆(Center) 범위 검사: [-vehicleHalfLength, vehicleHalfLength]
                            if (otherFront > -vehicleHalfLength && otherRear < vehicleHalfLength) {
                                centerBlocked = true;
                                //break; // 내 차 바로 옆에 차가 있으면 아예 변경 불가능 판단, 범위 그리지 않음
                            } 
                            // 2. 앞쪽 40m 범위 검사: 다른 차가 내 차보다 앞에 있을 때
                            else if (otherRear >= vehicleHalfLength) {
                                double clearDist = otherRear - vehicleHalfLength;
                                if (clearDist < forwardLimit) {
                                    forwardLimit = Math.max(0, clearDist);
                                }
                            } 
                            // 3. 뒤쪽 40m 범위 검사: 다른 차가 내 차보다 뒤에 있을 때
                            else if (otherFront <= -vehicleHalfLength) {
                                double clearDist = -vehicleHalfLength - otherFront;
                                if (clearDist < backwardLimit) {
                                    backwardLimit = Math.max(0, clearDist);
                                }
                            }
                        }
                    }
                }
            }

            // 바로 옆 범위(Center)에 차량이 없어서 안전한 경우에만 각각 축소된 앞/뒤 범위를 합쳐서 그립니다. ❤️
            if (!centerBlocked) {
                double startOffset = -vehicleHalfLength - backwardLimit;
                double endOffset = vehicleHalfLength + forwardLimit;
                
                List<Point2D.Double> subPath = getLaneSubPath(lane, p0, startOffset, endOffset, junctionController);
                if (subPath != null && subPath.size() >= 2) {
                    addPathToArea(sideVision, subPath, halfWidth);
                }
            }
        }
        fillVisionList(sideVisionArea, sideVision);
        this.lastSideVisionArea = sideVision; // 측방 감지 영역 저장 ❤️

        // 3. 전방 차량 감지 영역 (도로 굴곡 추적 방식) ❤️
        double safetyDistM = TrafficLaw.getRecommendedSafetyDistance(vehicle.getSpeedKmh());
        safetyDistM = Math.min(safetyDistM, 100.0); 
        double safetyDistPx = UnitConverter.toPixel(safetyDistM);
        
        // 전방 차량 감지 및 범위 축소 로직 ❤️
        double adjustedSafetyDistPx = safetyDistPx;
        for (Vehicle other : allVehicles) {
            if (other == vehicle) continue;

            // 전방 안전거리 감지 예외 처리 (다른 도로면 무시) ❤️
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
            
            // 임시로 전체 경로를 추적해서 해당 차량이 내 경로 위에 있는지 확인 (앞부분 frontPos 기준 시작! ❤️)
            List<Point2D.Double> fullTraced = traceRoutePath(currentLane, frontPos, safetyDistPx, currentPhaseIndex, junctionController);
            if (fullTraced != null && fullTraced.size() >= 2) {
                double accumulatedDist = 0;
                for (int i = 0; i < fullTraced.size() - 1; i++) {
                    Point2D.Double p1 = fullTraced.get(i);
                    Point2D.Double p2 = fullTraced.get(i + 1);
                    double dSq = getDistanceSqToSegment(p1, p2, otherPos);
                    
                    // 차선 너비 내에 있으면 같은 차선으로 간주
                    if (dSq < (laneWidthPx * laneWidthPx / 4.0)) {
                        // 경로를 따라 계산한 거리를 사용하여 다른 차량 뒷부분까지의 거리 계산 ❤️
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

        // 최종 감지 영역 생성도 frontPos 기준! ❤️
        List<Point2D.Double> tracedPath = traceRoutePath(currentLane, frontPos, adjustedSafetyDistPx, currentPhaseIndex, junctionController);
        
        Area vehicleVision = new Area();
        if (tracedPath != null && tracedPath.size() >= 2) {
            addPathToArea(vehicleVision, tracedPath, halfWidth);
        }
        
        fillVisionList(vehicleVisionArea, vehicleVision);
    }

    /**
     * 차선의 시작점으로부터 특정 좌표까지 경로를 따라 이동한 투영 거리(Projection Distance)를 반환합니다.
     */
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

    /**
     * 특정 좌표가 경로와 얼마나 떨어져 있는지 최단 거리의 제곱을 반환합니다.
     */
    private double getDistanceSqToPath(Lane lane, Point2D.Double pos) {
        List<Point2D.Double> fullPath = lane.getLanePath();
        if (fullPath == null || fullPath.size() < 2) return Double.MAX_VALUE;

        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < fullPath.size() - 1; i++) {
            double dSq = getDistanceSqToSegment(fullPath.get(i), fullPath.get(i + 1), pos);
            if (dSq < minDistSq) {
                minDistSq = dSq;
            }
        }
        return minDistSq;
    }

    /**
     * 현재 위치에서부터 경로를 따라 지정된 거리만큼의 포인트 리스트를 추출합니다. ❤️
     */
    private List<Point2D.Double> traceRoutePath(Lane startLane, Point2D.Double startPos, double totalDist, int startPhaseIdx, JunctionController junctionController) {
        List<Point2D.Double> traced = new ArrayList<>();
        double remainingDist = totalDist;
        
        Lane currentLane = startLane;
        Point2D.Double currentPos = startPos;

        for (int p = startPhaseIdx; p < logicalRoute.size(); p++) {
            // 1. 현재 차선에서의 subPath 추출
            List<Point2D.Double> sub = getLaneSubPath(currentLane, currentPos, 0, remainingDist, junctionController);
            if (sub != null && !sub.isEmpty()) {
                // 중복 점 제거하며 추가
                for (Point2D.Double pt : sub) {
                    if (traced.isEmpty() || traced.get(traced.size() - 1).distanceSq(pt) > 0.1) {
                        traced.add(pt);
                    }
                }
                
                // 이동한 거리만큼 차감
                double segmentDist = 0;
                for (int i = 0; i < sub.size() - 1; i++) {
                    segmentDist += sub.get(i).distance(sub.get(i + 1));
                }
                remainingDist -= segmentDist;
            }

            if (remainingDist <= 1.0) break; // 거리 다 채움

            // 2. 다음 단계로 넘어가기 위한 연결 탐색
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
                    List<Point2D.Double> connPath = nextConn.connectionPath();
                    // 연결로(Connection) 추적
                    List<Point2D.Double> connSub = getPathSubList(connPath, remainingDist);
                    if (connSub != null && !connSub.isEmpty()) {
                        for (Point2D.Double pt : connSub) {
                            if (traced.isEmpty() || traced.get(traced.size() - 1).distanceSq(pt) > 0.1) {
                                traced.add(pt);
                            }
                        }
                        double cDist = 0;
                        for (int i = 0; i < connSub.size() - 1; i++) {
                            cDist += connSub.get(i).distance(connSub.get(i + 1));
                        }
                        remainingDist -= cDist;
                        
                        // 다음 차선 준비
                        currentLane = nextConn.targetLane();
                        currentPos = connSub.get(connSub.size() - 1);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
            
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
        double lookaheadDist = Math.max(30.0, vehicle.getSpeedKmh() * 3.0);

        // --- 측방 충돌 방지 로직 (감지된 영역 자체를 가져다가 직접 사용) ❤️ ---
        if (stepTargetLane != currentLane && lastSideVisionArea != null) {
            double minLookahead = UnitConverter.toPixel(15.0); // 안전한 차선 변경을 위한 최소 거리
            double safeLimit = 0;
            double checkStep = UnitConverter.toPixel(1.0); // 2m 간격으로 점검
            
            // 목표 차선을 따라 점들을 추출하여 안전 영역(lastSideVisionArea) 내부에 있는지 직접 검사
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
                        double cx = p1.x + (p2.x - p1.x) * ratio;
                        double cy = p1.y + (p2.y - p1.y) * ratio;
                        
                        // 현재 점이 측방 감지 영역(안전 지대) 바깥이라면 막힌 것으로 간주!
                        if (!lastSideVisionArea.contains(cx, cy)) {
                            blocked = true;
                            break;
                        }
                        safeLimit = accumulated + dist * ratio;
                    }
                    if (blocked) break;
                    accumulated += dist;
                }
                
                if (blocked && safeLimit < minLookahead) {
                    stepTargetLane = currentLane; // 변경 공간이 부족하면 변경 취소 (직진)
                } else if (blocked) {
                    lookaheadDist = Math.min(lookaheadDist, safeLimit); // 안전한 곳까지만 경로 생성
                }
            } else {
                stepTargetLane = currentLane;
            }
        }
        // ------------------------------------------------

        List<Point2D.Double> lanePoints = stepTargetLane.getLanePath();
        if (lanePoints == null || lanePoints.size() < 2) return;

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
