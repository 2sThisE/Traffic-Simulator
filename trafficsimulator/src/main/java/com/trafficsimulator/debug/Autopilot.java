package com.trafficsimulator.debug;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.TrafficLaw;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.PhysicEngine;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.VehicleLight;

/**
 * 차량의 주행 지능 및 경로 관리를 담당하는 오토파일럿 클래스입니다. ❤️
 */
public class Autopilot {
    private final Vehicle vehicle;
    private List<Point2D.Double> path = new ArrayList<>(); // 차량이 따라갈 시각적 경로
    private Area pathArea = new Area(); // 차량의 폭을 고려한 주행 궤적 영역 ❤️
    private List<Set<Lane>> logicalRoute = new ArrayList<>(); // 차량이 주행 가능한 논리적 경로 세트
    private int currentPhaseIndex = 0; // 현재 주행 중인 도로 구간 인덱스
    private LaneConnection currentConnection = null; // 현재 교차로 통과 중인 경우 해당 연결 정보
    private Lane laneChangeTargetLane = null;
    private double laneChangePathDistance = 0.0;
    private double connectionPathDistance = 0.0;
    private boolean routeFinished = false;
    private Vehicle overtakeTargetVehicle = null;
    private Lane overtakeReferenceLane = null;
    private Lane overtakeLane = null;
    private static final double OVERTAKE_CLEARANCE_M = 8.0;
    private static final double OVERTAKE_SPEED_BOOST_KMH = 10.0;
    private static final double BRAKE_LIGHT_EPSILON_KMH = 0.05;
    
    // 시각적 렌더링을 위한 포인트 리스트 ❤️
    private List<Point2D.Double> forwardVisionArea = new ArrayList<>(); 
    private List<Point2D.Double> sideVisionArea = new ArrayList<>();    
    private List<Point2D.Double> vehicleVisionArea = new ArrayList<>(); 

    // 논리적 판단을 위한 Area 객체 저장용 ❤️
    private Area lastForwardVisionArea = new Area(); 
    private Area lastSideVisionArea = new Area(); 
    private Area lastVehicleVisionArea = new Area(); 
    private double previousVehicleVisionDistancePx = Double.NaN;
    private double currentVehicleVisionDistancePx = Double.POSITIVE_INFINITY;
    private double desiredSafetyDistancePx = 0.0;
    private final List<RememberedTrafficLight> rememberedTrafficLights = new ArrayList<>();
    private static final double TRAFFIC_LIGHT_PASS_COMMIT_DISTANCE_M = 5.0;

    public Autopilot(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    /**
     * 현재 속도(km/h)에 따른 1틱당 이동 거리(Pixel)를 계산하여 차량 위치를 업데이트합니다.
     */
    public void updatePosition(JunctionController junctionController) {
        if (routeFinished) {
            return;
        }

        double ms = UnitConverter.kmhToMs(vehicle.getSpeedKmh());
        double pixelPerTick = UnitConverter.toPixelPerTick(ms);

        if (isLaneChanging()) {
            boolean reachedEnd = advanceAlongLaneChangePath(pixelPerTick);
            if (reachedEnd) {
                clearLaneChangePath();
            }
            return;
        }

        if (currentConnection != null && path != null && path.size() >= 2) {
            boolean reachedEnd = advanceAlongConnectionPath(pixelPerTick);
            if (reachedEnd) {
                if (logicalRoute != null && currentPhaseIndex < logicalRoute.size() - 1) {
                    currentPhaseIndex++;
                }
                clearConnectionPath();
            }
            return;
        }

        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) {
            moveStraight(pixelPerTick);
            return;
        }

        Point2D.Double currentPos = new Point2D.Double(vehicle.getX(), vehicle.getY());
        Lane currentLane = findNearestLane(logicalRoute.get(currentPhaseIndex), currentPos);
        if (currentLane == null) {
            moveStraight(pixelPerTick);
            return;
        }

        boolean reachedEnd = advanceAlongLanePath(currentLane, currentPos, pixelPerTick);
        if (reachedEnd) {
            beginConnectionToNextPhase(currentLane, junctionController);
        }
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
        
        this.lastForwardVisionArea = forwardVision; 
        fillVisionList(forwardVisionArea, forwardVision); 
        updateRememberedTrafficLight(roadManager, junctionController, currentLane, frontPos);

        // 2. 측방 감지 영역 (타 차량용) ❤️
        Area sideVision = new Area();
        double sideMaxDist = UnitConverter.toPixel(40.0); 
        double vehicleHalfLength = vehicle.getWidth() / 2.0;
        double sideMaxCheckDistSq = (sideMaxDist + UnitConverter.toPixel(20.0)) * (sideMaxDist + UnitConverter.toPixel(20.0));

        for (Lane lane : sideTargetLanes) {
            double myProjDist = getProjectionDistance(lane, p0);
            if (myProjDist < 0) continue;

            List<double[]> occupiedRanges = new ArrayList<>();
            for (Vehicle other : allVehicles) {
                if (other == vehicle) continue;
                
                Point2D.Double otherPos = new Point2D.Double(other.getX(), other.getY());
                if (p0.distanceSq(otherPos) > sideMaxCheckDistSq) continue; // 최적화: 일정 거리 이상 차량 패스

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
                    double[] projAndDistSq = getProjectionAndDistanceSq(lane, otherPos);
                    double otherProjDist = projAndDistSq[0];
                    if (otherProjDist >= 0) {
                        double distToLaneCenterSq = projAndDistSq[1];
                        if (distToLaneCenterSq < (laneWidthPx * laneWidthPx / 4.0)) {
                            double longDist = otherProjDist - myProjDist;
                            double otherHalfLength = other.getWidth() / 2.0;
                            occupiedRanges.add(new double[]{longDist - otherHalfLength, longDist + otherHalfLength});
                        }
                    }
                }
            }

            occupiedRanges.sort((a, b) -> Double.compare(a[0], b[0]));

            double nearestRearBoundary = -sideMaxDist;
            for (double[] range : occupiedRanges) {
                if (range[0] < -vehicleHalfLength) {
                    nearestRearBoundary = Math.max(nearestRearBoundary, range[1]);
                }
            }

            double viewStart = Math.min(sideMaxDist, nearestRearBoundary);
            double viewEnd = sideMaxDist;
            double currentPos = viewStart;

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
        
        this.lastSideVisionArea = sideVision; 
        fillVisionList(sideVisionArea, sideVision); 

        // 3. 전방 차량 감지 영역 (안전거리용) ❤️
        double safetyDistM = TrafficLaw.getRecommendedSafetyDistance(vehicle.getSpeedKmh())
                * vehicle.getDriverPersonality().getSafetyDistanceRatio();
        safetyDistM = Math.min(safetyDistM, 100.0); 
        double safetyDistPx = UnitConverter.toPixel(safetyDistM);
        double detectionDistPx = UnitConverter.toPixel(300.0);
        desiredSafetyDistancePx = safetyDistPx;
        
        double adjustedSafetyDistPx = detectionDistPx;
        double maxSafetyDistSqForCheck = (detectionDistPx + UnitConverter.toPixel(40.0)) * (detectionDistPx + UnitConverter.toPixel(40.0));
        
        // 최적화: 루프 밖에서 공통 전방 경로 한 번만 추적
        List<Point2D.Double> fullTraced = traceRoutePath(currentLane, frontPos, detectionDistPx, currentPhaseIndex, junctionController);

        if (fullTraced != null && fullTraced.size() >= 2) {
            for (Vehicle other : allVehicles) {
                if (other == vehicle) continue;
                if (p0.distanceSq(other.getX(), other.getY()) > maxSafetyDistSqForCheck) continue; // 최적화: 너무 멀리 있는 차량 스킵

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

        previousVehicleVisionDistancePx = currentVehicleVisionDistancePx;
        currentVehicleVisionDistancePx = adjustedSafetyDistPx;

        List<Point2D.Double> tracedPath = traceRoutePath(currentLane, frontPos, Math.min(adjustedSafetyDistPx, safetyDistPx), currentPhaseIndex, junctionController);
        Area vehicleVision = new Area();
        if (tracedPath != null && tracedPath.size() >= 2) {
            addPathToArea(vehicleVision, tracedPath, halfWidth);
        }
        
        this.lastVehicleVisionArea = vehicleVision; 
        fillVisionList(vehicleVisionArea, vehicleVision); 
    }

    public void updateSpeedControl(RoadManager roadManager) {
        if (routeFinished || logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) {
            return;
        }

        Point2D.Double currentPos = new Point2D.Double(vehicle.getX(), vehicle.getY());
        Lane currentLane = findNearestLane(logicalRoute.get(currentPhaseIndex), currentPos);
        if (currentLane == null) {
            return;
        }

        Road currentRoad = roadManager.findRoadByLane(currentLane);
        if (currentRoad == null) {
            return;
        }

        double targetSpeed = currentRoad.getLimitSpeed();
        if (!vehicle.getDriverPersonality().isStrictLawAdherence()) {
            targetSpeed += vehicle.getDriverPersonality().getMaxSpeedOffset();
        }
        if (isOvertaking()) {
            targetSpeed = Math.max(targetSpeed, currentRoad.getLimitSpeed() + OVERTAKE_SPEED_BOOST_KMH);
        }

        double currentSpeed = vehicle.getSpeedKmh();
        double desiredSafetyPx = desiredSafetyDistancePx;
        double availableForwardPx = currentVehicleVisionDistancePx;
        double trafficLightStopDistancePx = getActiveTrafficLightStopDistancePx();
        boolean mustStopForTrafficLight = Double.isFinite(trafficLightStopDistancePx) && trafficLightStopDistancePx <= availableForwardPx;
        if (mustStopForTrafficLight) {
            availableForwardPx = trafficLightStopDistancePx;
            desiredSafetyPx = UnitConverter.toPixel(1.0);
        }
        double closingPxPerTick = 0.0;
        if (Double.isFinite(previousVehicleVisionDistancePx) && Double.isFinite(currentVehicleVisionDistancePx)) {
            closingPxPerTick = Math.max(0.0, previousVehicleVisionDistancePx - currentVehicleVisionDistancePx);
        }

        double nextSpeed = currentSpeed;
        boolean isLeadStationary = false;
        
        // 앞차가 정지해 있는지 판단 (상대 속도가 내 속도와 거의 같으면 앞차 속도는 0) ❤️
        if (Double.isFinite(closingPxPerTick) && closingPxPerTick > 0.0) {
            double relativeSpeedKmh = UnitConverter.msToKmh(
                    UnitConverter.toMeter(closingPxPerTick) / UnitConverter.TICK_INTERVAL_SECONDS
            );
            // 내 속도와 상대 속도의 차이가 아주 작으면 앞차는 멈춘 것으로 간주
            if (Math.abs(currentSpeed - relativeSpeedKmh) < 0.1) {
                isLeadStationary = true;
            }
        }

        // 정지 상황 특수 처리: 앞차가 정지해 있거나 아주 가까울 때 ❤️
        if (mustStopForTrafficLight) {
            if (availableForwardPx <= desiredSafetyPx) {
                double maxBrakeKmhPerTick = vehicle.getType().getHardBrakeKmhPerTick();
                nextSpeed = Math.max(0.0, currentSpeed - maxBrakeKmhPerTick);
            } else {
                double brakingDistanceM = Math.max(0.01, UnitConverter.toMeter(availableForwardPx - desiredSafetyPx));
                double requiredBrakeMs2 = PhysicEngine.calculateRequiredBraking(currentSpeed, 0.0, brakingDistanceM);
                double maxBrakeMs2 = UnitConverter.kmhToMs(vehicle.getType().getHardBrakeKmhPerTick()) / UnitConverter.TICK_INTERVAL_SECONDS;

                double brakeMs2 = Math.min(maxBrakeMs2, requiredBrakeMs2);
                nextSpeed = Math.min(currentSpeed, PhysicEngine.calculateSpeed(currentSpeed, -brakeMs2));
            }
        } else if (isLeadStationary || (currentSpeed < 0.1 && Double.isFinite(availableForwardPx) && availableForwardPx < desiredSafetyPx)) {
            double stationarySafetyPx = desiredSafetyPx; // 하드코딩(2.0m) 대신 운전자 성향 반영 ❤️
            
            if (availableForwardPx <= stationarySafetyPx) {
                // 이미 정차 간격 이내라면 감속 성능 내에서 멈춤 (갑자기 0으로 변하는 현상 방지) ❤️
                double maxBrakeKmhPerTick = vehicle.getType().getHardBrakeKmhPerTick();
                nextSpeed = Math.max(0.0, currentSpeed - maxBrakeKmhPerTick);
            } else if (closingPxPerTick > 0.0) {
                // 다가가고 있다면 정지 간격을 목표로 브레이크 계산
                double brakingDistanceM = Math.max(0.01, UnitConverter.toMeter(availableForwardPx - stationarySafetyPx));
                double requiredBrakeMs2 = PhysicEngine.calculateRequiredBraking(currentSpeed, 0.0, brakingDistanceM);
                double maxBrakeMs2 = UnitConverter.kmhToMs(vehicle.getType().getHardBrakeKmhPerTick()) / UnitConverter.TICK_INTERVAL_SECONDS;
                
                double brakeMs2 = Math.min(maxBrakeMs2, requiredBrakeMs2);
                nextSpeed = Math.min(currentSpeed, PhysicEngine.calculateSpeed(currentSpeed, -brakeMs2));
            } else {
                // 정지 목표 거리보다 멀지만 멈춰있는 경우, 가속하지 않고 현행 유지 (움찔거림 방지)
                nextSpeed = currentSpeed;
            }
        } else if (Double.isFinite(availableForwardPx) && closingPxPerTick > 0.0) {
            // 주행 중 브레이크 로직 (기존)
            double closingSpeedKmh = UnitConverter.msToKmh(
                    UnitConverter.toMeter(closingPxPerTick) / UnitConverter.TICK_INTERVAL_SECONDS
            );
            double targetLeadSpeed = Math.max(0.0, currentSpeed - closingSpeedKmh);
            double brakingDistanceM = Math.max(0.5, UnitConverter.toMeter(availableForwardPx - desiredSafetyPx));
            double requiredBrakeMs2 = PhysicEngine.calculateRequiredBraking(currentSpeed, targetLeadSpeed, brakingDistanceM);
            double maxBrakeMs2 = UnitConverter.kmhToMs(vehicle.getType().getHardBrakeKmhPerTick()) / UnitConverter.TICK_INTERVAL_SECONDS;
            
            double brakeMs2 = Math.min(maxBrakeMs2, requiredBrakeMs2);
            nextSpeed = Math.min(currentSpeed, PhysicEngine.calculateSpeed(currentSpeed, -brakeMs2));
        } else if (currentSpeed < targetSpeed) {
            // 가속 조건: 정지 목표 거리 근처가 아닐 때만 가속 (성향에 따른 떨림 방지 보정) ❤️
            double accelerationBufferPx = UnitConverter.toPixel(1.0); // 1미터 여유
            if (currentSpeed < 1.0 && Double.isFinite(availableForwardPx) && availableForwardPx < (desiredSafetyPx + accelerationBufferPx)) {
                nextSpeed = currentSpeed; 
            } else {
                nextSpeed = Math.min(targetSpeed, currentSpeed + vehicle.getType().getAccelerationKmhPerTick());
            }
        } else if (currentSpeed > targetSpeed) {
            nextSpeed = Math.max(targetSpeed, currentSpeed - vehicle.getType().getBrakeKmhPerTick());
        }

        updateBrakeLights(currentSpeed, nextSpeed);
        vehicle.setSpeedKmh(nextSpeed);
    }

    private void updateBrakeLights(double currentSpeed, double nextSpeed) {
        double brakeDelta = Math.max(0.0, currentSpeed - nextSpeed);
        boolean stopped = nextSpeed < 0.1;
        boolean braking = brakeDelta > BRAKE_LIGHT_EPSILON_KMH;
        boolean hardBraking = brakeDelta >= vehicle.getType().getHardBrakeKmhPerTick() * 0.8;

        vehicle.setLight(VehicleLight.BREAK, braking || stopped);
        vehicle.setLight(VehicleLight.SLAMBRAKE, hardBraking);
    }

    private void updateRememberedTrafficLight(RoadManager roadManager, JunctionController junctionController, Lane currentLane, Point2D.Double frontPos) {
        refreshRememberedTrafficLights(currentLane, frontPos);

        if (roadManager != null && lastForwardVisionArea != null && !lastForwardVisionArea.isEmpty()) {
            for (TrafficLight trafficLight : roadManager.getTrafficLightList()) {
                Point2D.Double coordinates = trafficLight.getCoordinates();
                if (coordinates == null || !isPointInForwardVision(coordinates)) continue;

                TrafficLightCandidate candidate = findTrafficLightCandidate(trafficLight, currentLane, frontPos, junctionController);
                if (candidate != null) {
                    rememberVisibleTrafficLight(trafficLight, candidate);
                }
            }
        }

        sortRememberedTrafficLights();
    }

    private void refreshRememberedTrafficLights(Lane currentLane, Point2D.Double frontPos) {
        rememberedTrafficLights.removeIf(remembered -> {
            int phaseIndex = findLaneRoutePhaseIndex(remembered.lane);
            if (phaseIndex < 0 || phaseIndex < currentPhaseIndex) {
                return true;
            }

            remembered.routePhaseIndex = phaseIndex;
            if (phaseIndex == currentPhaseIndex || remembered.lane == currentLane) {
                remembered.stopDistancePx = getStopDistanceToLaneEnd(remembered.lane, frontPos);
                return remembered.stopDistancePx < 0.0;
            }

            remembered.stopDistancePx = Double.POSITIVE_INFINITY;
            return false;
        });
    }

    private void rememberVisibleTrafficLight(TrafficLight trafficLight, TrafficLightCandidate candidate) {
        RememberedTrafficLight remembered = findRememberedTrafficLight(trafficLight);
        if (remembered == null) {
            remembered = new RememberedTrafficLight(trafficLight);
            rememberedTrafficLights.add(remembered);
        }

        remembered.signals = Arrays.copyOf(trafficLight.currentSignal(), trafficLight.currentSignal().length);
        remembered.lane = candidate.lane;
        remembered.pass = candidate.pass;
        remembered.stopDistancePx = candidate.stopDistancePx;
        remembered.routePhaseIndex = candidate.routePhaseIndex;
    }

    private RememberedTrafficLight findRememberedTrafficLight(TrafficLight trafficLight) {
        for (RememberedTrafficLight remembered : rememberedTrafficLights) {
            if (remembered.trafficLight == trafficLight) {
                return remembered;
            }
        }
        return null;
    }

    private void sortRememberedTrafficLights() {
        rememberedTrafficLights.sort((a, b) -> {
            int phaseCompare = Integer.compare(a.routePhaseIndex, b.routePhaseIndex);
            if (phaseCompare != 0) return phaseCompare;
            return Double.compare(a.stopDistancePx, b.stopDistancePx);
        });
    }

    private boolean isPointInForwardVision(Point2D.Double point) {
        if (lastForwardVisionArea.contains(point)) return true;

        double tolerancePx = UnitConverter.toPixel(2.0);
        return lastForwardVisionArea.intersects(
                point.x - tolerancePx,
                point.y - tolerancePx,
                tolerancePx * 2.0,
                tolerancePx * 2.0
        );
    }

    private TrafficLightCandidate findTrafficLightCandidate(TrafficLight trafficLight, Lane currentLane, Point2D.Double frontPos, JunctionController junctionController) {
        TrafficLightCandidate bestCandidate = null;

        for (Lane lane : trafficLight.getControlLaneList()) {
            int routePhaseIndex = findLaneRoutePhaseIndex(lane);
            if (routePhaseIndex < 0) continue;

            double stopDistancePx = getStopDistanceToLaneEnd(lane, frontPos);
            if (routePhaseIndex == currentPhaseIndex || lane == currentLane) {
                if (stopDistancePx < 0.0) continue;
            } else {
                stopDistancePx = Double.POSITIVE_INFINITY;
            }

            LaneType pass = getPlannedPassType(lane, junctionController);
            TrafficLightCandidate candidate = new TrafficLightCandidate(lane, pass, Math.max(0.0, stopDistancePx), routePhaseIndex);
            if (bestCandidate == null || compareTrafficLightCandidates(candidate, bestCandidate) < 0) {
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    private int compareTrafficLightCandidates(TrafficLightCandidate a, TrafficLightCandidate b) {
        int phaseCompare = Integer.compare(a.routePhaseIndex, b.routePhaseIndex);
        if (phaseCompare != 0) return phaseCompare;
        return Double.compare(a.stopDistancePx, b.stopDistancePx);
    }

    private int findLaneRoutePhaseIndex(Lane lane) {
        if (lane == null || logicalRoute == null) return -1;
        for (int p = currentPhaseIndex; p < logicalRoute.size(); p++) {
            if (logicalRoute.get(p).contains(lane)) return p;
        }
        return -1;
    }

    private LaneType getPlannedPassType(Lane lane, JunctionController junctionController) {
        if (junctionController == null || logicalRoute == null || currentPhaseIndex >= logicalRoute.size() - 1) {
            return LaneType.STRAIGHT;
        }

        Set<Lane> nextPhase = logicalRoute.get(currentPhaseIndex + 1);
        Set<LaneConnection> connections = junctionController.getConnectionList(lane);
        if (connections != null) {
            for (LaneConnection connection : connections) {
                if (nextPhase.contains(connection.targetLane())) {
                    return connection.laneType();
                }
            }
        }

        return LaneType.STRAIGHT;
    }

    private double getStopDistanceToLaneEnd(Lane lane, Point2D.Double frontPos) {
        List<Point2D.Double> orderedPath = getOrderedLanePath(lane);
        if (orderedPath.size() < 2) return Double.POSITIVE_INFINITY;
        double currentDistance = getProjectionDistanceOnPath(orderedPath, frontPos);
        double pathLength = getPathLength(orderedPath);
        return pathLength - currentDistance;
    }

    private double getActiveTrafficLightStopDistancePx() {
        if (rememberedTrafficLights.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        RememberedTrafficLight activeTrafficLight = rememberedTrafficLights.get(0);
        if (activeTrafficLight.signals == null || activeTrafficLight.lane == null || activeTrafficLight.routePhaseIndex > currentPhaseIndex) {
            return Double.POSITIVE_INFINITY;
        }

        boolean canPass = TrafficLaw.checkTrafficLight(
                activeTrafficLight.signals,
                activeTrafficLight.trafficLight.getLightList(),
                activeTrafficLight.pass
        );
        if (canPass) {
            if (activeTrafficLight.routePhaseIndex == currentPhaseIndex
                    && activeTrafficLight.stopDistancePx <= UnitConverter.toPixel(TRAFFIC_LIGHT_PASS_COMMIT_DISTANCE_M)) {
                activeTrafficLight.committedToPass = true;
            }
            return Double.POSITIVE_INFINITY;
        }
        if (activeTrafficLight.committedToPass) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.max(0.0, activeTrafficLight.stopDistancePx);
    }

    private void clearRememberedTrafficLight() {
        rememberedTrafficLights.clear();
    }

    private static class RememberedTrafficLight {
        private final TrafficLight trafficLight;
        private TrafficLightSignal[] signals;
        private Lane lane;
        private LaneType pass = LaneType.STRAIGHT;
        private double stopDistancePx = Double.POSITIVE_INFINITY;
        private int routePhaseIndex = Integer.MAX_VALUE;
        private boolean committedToPass = false;

        private RememberedTrafficLight(TrafficLight trafficLight) {
            this.trafficLight = trafficLight;
        }
    }

    private record TrafficLightCandidate(Lane lane, LaneType pass, double stopDistancePx, int routePhaseIndex) {}

    private double[] getProjectionAndDistanceSq(Lane lane, Point2D.Double pos) {
        List<Point2D.Double> fullPath = lane.getLanePath();
        if (fullPath == null || fullPath.size() < 2) return new double[]{-1, Double.MAX_VALUE};
        boolean reverse = !lane.isRoadDirection();
        int size = fullPath.size();
        
        double accumulatedDist = 0;
        double minDistSq = Double.MAX_VALUE;
        double currentProjDist = 0;
        
        for (int i = 0; i < size - 1; i++) {
            int idx1 = reverse ? size - 1 - i : i;
            int idx2 = reverse ? size - 2 - i : i + 1;
            Point2D.Double p1 = fullPath.get(idx1);
            Point2D.Double p2 = fullPath.get(idx2);
            
            double dx = p2.x - p1.x, dy = p2.y - p1.y, l2 = dx * dx + dy * dy;
            double t_proj = (l2 == 0) ? 0 : Math.max(0, Math.min(1, ((pos.x - p1.x) * dx + (pos.y - p1.y) * dy) / l2));
            double cx = p1.x + t_proj * dx, cy = p1.y + t_proj * dy;
            double dSq = (pos.x - cx) * (pos.x - cx) + (pos.y - cy) * (pos.y - cy);
            
            if (dSq < minDistSq) {
                minDistSq = dSq;
                currentProjDist = accumulatedDist + Math.sqrt((cx - p1.x) * (cx - p1.x) + (cy - p1.y) * (cy - p1.y));
            }
            accumulatedDist += Math.sqrt(l2);
        }
        return new double[]{currentProjDist, minDistSq};
    }

    private double getProjectionDistance(Lane lane, Point2D.Double pos) {
        return getProjectionAndDistanceSq(lane, pos)[0];
    }

    private double getDistanceSqToPath(Lane lane, Point2D.Double pos) {
        return getProjectionAndDistanceSq(lane, pos)[1];
    }

    private void moveStraight(double pixelPerTick) {
        double rad = Math.toRadians(vehicle.getAngle());
        vehicle.setX(vehicle.getX() + Math.cos(rad) * pixelPerTick);
        vehicle.setY(vehicle.getY() + Math.sin(rad) * pixelPerTick);
    }

    private boolean advanceAlongLanePath(Lane lane, Point2D.Double currentPos, double pixelPerTick) {
        List<Point2D.Double> orderedPath = getOrderedLanePath(lane);
        if (orderedPath.size() < 2) {
            moveStraight(pixelPerTick);
            return false;
        }

        double currentDistance = getProjectionDistanceOnPath(orderedPath, currentPos);
        double pathLength = getPathLength(orderedPath);
        double nextDistance = Math.min(pathLength, currentDistance + pixelPerTick);
        PointOnPath nextPoint = getPointAtDistance(orderedPath, nextDistance);
        if (nextPoint == null) {
            moveStraight(pixelPerTick);
            return false;
        }

        vehicle.setX(nextPoint.point.x);
        vehicle.setY(nextPoint.point.y);
        vehicle.setAngle(nextPoint.angleDeg);
        return nextDistance >= pathLength - 0.1;
    }

    private boolean advanceAlongLaneChangePath(double pixelPerTick) {
        double pathLength = getPathLength(path);
        if (pathLength <= 0) {
            return true;
        }

        laneChangePathDistance = Math.min(pathLength, laneChangePathDistance + pixelPerTick);
        PointOnPath nextPoint = getPointAtDistance(path, laneChangePathDistance);
        if (nextPoint == null) {
            moveStraight(pixelPerTick);
            return false;
        }

        vehicle.setX(nextPoint.point.x);
        vehicle.setY(nextPoint.point.y);
        vehicle.setAngle(nextPoint.angleDeg);
        return laneChangePathDistance >= pathLength - 0.1;
    }

    private boolean advanceAlongConnectionPath(double pixelPerTick) {
        double pathLength = getPathLength(path);
        if (pathLength <= 0) {
            return true;
        }

        connectionPathDistance = Math.min(pathLength, connectionPathDistance + pixelPerTick);
        PointOnPath nextPoint = getPointAtDistance(path, connectionPathDistance);
        if (nextPoint == null) {
            moveStraight(pixelPerTick);
            return false;
        }

        vehicle.setX(nextPoint.point.x);
        vehicle.setY(nextPoint.point.y);
        vehicle.setAngle(nextPoint.angleDeg);
        return connectionPathDistance >= pathLength - 0.1;
    }

    private void beginConnectionToNextPhase(Lane currentLane, JunctionController junctionController) {
        if (junctionController == null || logicalRoute == null || currentPhaseIndex >= logicalRoute.size() - 1) {
            routeFinished = true;
            return;
        }

        Set<Lane> nextPhase = logicalRoute.get(currentPhaseIndex + 1);
        Set<LaneConnection> connections = junctionController.getConnectionList(currentLane);
        if (connections == null) {
            currentPhaseIndex++;
            return;
        }

        for (LaneConnection connection : connections) {
            if (nextPhase.contains(connection.targetLane())) {
                currentConnection = connection;
                path = new ArrayList<>(connection.connectionPath());
                connectionPathDistance = 0.0;
                updatePathArea();
                return;
            }
        }

        currentPhaseIndex++;
    }

    private void clearConnectionPath() {
        this.path = new ArrayList<>();
        this.currentConnection = null;
        this.connectionPathDistance = 0.0;
        updatePathArea();
    }

    private boolean isLaneChanging() {
        return laneChangeTargetLane != null && path != null && path.size() >= 2;
    }

    private boolean isOvertaking() {
        return overtakeTargetVehicle != null && overtakeReferenceLane != null && overtakeLane != null;
    }

    private void beginLaneChangePath(List<Point2D.Double> newPath, Lane targetLane) {
        updateLaneChangeLights(newPath);
        this.path = newPath;
        this.laneChangeTargetLane = targetLane;
        this.laneChangePathDistance = 0.0;
        updatePathArea();
    }

    private void clearLaneChangePath() {
        this.path = new ArrayList<>();
        this.laneChangeTargetLane = null;
        this.laneChangePathDistance = 0.0;
        vehicle.clearLights(VehicleLight.LEFT, VehicleLight.RIGHT);
        updatePathArea();
    }

    private void updateLaneChangeLights(List<Point2D.Double> newPath) {
        vehicle.clearLights(VehicleLight.LEFT, VehicleLight.RIGHT);
        if (newPath == null || newPath.isEmpty()) return;

        Point2D.Double targetPoint = newPath.get(newPath.size() - 1);
        double rad = Math.toRadians(vehicle.getAngle());
        double forwardX = Math.cos(rad);
        double forwardY = Math.sin(rad);
        double targetX = targetPoint.x - vehicle.getX();
        double targetY = targetPoint.y - vehicle.getY();
        double cross = forwardX * targetY - forwardY * targetX;

        if (cross > 0.0) {
            vehicle.setLight(VehicleLight.RIGHT, true);
        } else if (cross < 0.0) {
            vehicle.setLight(VehicleLight.LEFT, true);
        }
    }

    private void beginOvertake(Vehicle targetVehicle, Lane referenceLane, Lane targetLane) {
        this.overtakeTargetVehicle = targetVehicle;
        this.overtakeReferenceLane = referenceLane;
        this.overtakeLane = targetLane;
    }

    private void clearOvertake() {
        this.overtakeTargetVehicle = null;
        this.overtakeReferenceLane = null;
        this.overtakeLane = null;
    }

    private List<Point2D.Double> getOrderedLanePath(Lane lane) {
        List<Point2D.Double> orderedPath = new ArrayList<>(lane.getLanePath());
        if (!lane.isRoadDirection()) {
            Collections.reverse(orderedPath);
        }
        return orderedPath;
    }

    private double getProjectionDistanceOnPath(List<Point2D.Double> path, Point2D.Double pos) {
        double accumulatedDist = 0;
        double bestDistance = 0;
        double minDistSq = Double.MAX_VALUE;

        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double segmentLengthSq = dx * dx + dy * dy;
            if (segmentLengthSq == 0) continue;

            double t = ((pos.x - p1.x) * dx + (pos.y - p1.y) * dy) / segmentLengthSq;
            t = Math.max(0, Math.min(1, t));
            double projectedX = p1.x + t * dx;
            double projectedY = p1.y + t * dy;
            double distSq = (pos.x - projectedX) * (pos.x - projectedX) + (pos.y - projectedY) * (pos.y - projectedY);

            if (distSq < minDistSq) {
                minDistSq = distSq;
                bestDistance = accumulatedDist + Math.sqrt(segmentLengthSq) * t;
            }
            accumulatedDist += Math.sqrt(segmentLengthSq);
        }

        return bestDistance;
    }

    private double getPathLength(List<Point2D.Double> path) {
        double length = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            length += path.get(i).distance(path.get(i + 1));
        }
        return length;
    }

    private PointOnPath getPointAtDistance(List<Point2D.Double> path, double targetDistance) {
        double accumulatedDist = 0;

        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double p1 = path.get(i);
            Point2D.Double p2 = path.get(i + 1);
            double segmentLength = p1.distance(p2);
            if (segmentLength == 0) continue;

            if (accumulatedDist + segmentLength >= targetDistance) {
                double ratio = Math.max(0, Math.min(1, (targetDistance - accumulatedDist) / segmentLength));
                Point2D.Double point = new Point2D.Double(
                        p1.x + (p2.x - p1.x) * ratio,
                        p1.y + (p2.y - p1.y) * ratio
                );
                double angleDeg = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
                return new PointOnPath(point, angleDeg);
            }

            accumulatedDist += segmentLength;
        }

        Point2D.Double last = path.get(path.size() - 1);
        Point2D.Double prev = path.get(path.size() - 2);
        double angleDeg = Math.toDegrees(Math.atan2(last.y - prev.y, last.x - prev.x));
        return new PointOnPath(new Point2D.Double(last.x, last.y), angleDeg);
    }

    private record PointOnPath(Point2D.Double point, double angleDeg) {}

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
        double minDistanceSq = Double.MAX_VALUE;
        for (Lane lane : lanes) {
            List<Point2D.Double> pts = lane.getLanePath();
            if (pts == null || pts.size() < 2) continue;
            for (int i = 0; i < pts.size() - 1; i++) {
                double dSq = getDistanceSqToSegment(pts.get(i), pts.get(i + 1), pos);
                if (dSq < minDistanceSq) {
                    minDistanceSq = dSq;
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
        
        boolean reverse = !lane.isRoadDirection();
        int size = lp.size();

        double currentProjDist = 0;
        double accumulatedDist = 0;
        double minDistSq = Double.MAX_VALUE;
        
        for (int i = 0; i < size - 1; i++) {
            int idx1 = reverse ? size - 1 - i : i;
            int idx2 = reverse ? size - 2 - i : i + 1;
            Point2D.Double p1 = lp.get(idx1);
            Point2D.Double p2 = lp.get(idx2);
            
            double dx = p2.x - p1.x, dy = p2.y - p1.y, l2 = dx * dx + dy * dy;
            double t_proj = (l2 == 0) ? 0 : Math.max(0, Math.min(1, ((vehiclePos.x - p1.x) * dx + (vehiclePos.y - p1.y) * dy) / l2));
            double cx = p1.x + t_proj * dx, cy = p1.y + t_proj * dy;
            double dSq = (vehiclePos.x - cx) * (vehiclePos.x - cx) + (vehiclePos.y - cy) * (vehiclePos.y - cy);
            
            if (dSq < minDistSq) {
                minDistSq = dSq;
                currentProjDist = accumulatedDist + Math.sqrt((cx - p1.x) * (cx - p1.x) + (cy - p1.y) * (cy - p1.y));
            }
            accumulatedDist += Math.sqrt(l2);
        }

        double targetStartDist = currentProjDist + startOffset;
        double targetEndDist = currentProjDist + endOffset;
        List<Point2D.Double> subPath = new ArrayList<>();
        accumulatedDist = 0;
        boolean started = false;
        
        for (int i = 0; i < size - 1; i++) {
            int idx1 = reverse ? size - 1 - i : i;
            int idx2 = reverse ? size - 2 - i : i + 1;
            Point2D.Double p1 = lp.get(idx1);
            Point2D.Double p2 = lp.get(idx2);
            
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
        double minDistSq = Double.MAX_VALUE;
        Point2D.Double bestPt = null;
        double bestAngle = vehicle.getAngle();
        int bestPhase = this.currentPhaseIndex;
        LaneConnection bestConn = null;

        if (logicalRoute != null && !logicalRoute.isEmpty()) {
            for (int p = 0; p < logicalRoute.size(); p++) {
                Set<Lane> phase = logicalRoute.get(p);
                for (Lane lane : phase) {
                    List<Point2D.Double> points = lane.getLanePath();
                    boolean reverse = !lane.isRoadDirection();
                    int size = points.size();
                    for (int i = 0; i < size - 1; i++) {
                        int idx1 = reverse ? size - 1 - i : i;
                        int idx2 = reverse ? size - 2 - i : i + 1;
                        Point2D.Double p1 = points.get(idx1);
                        Point2D.Double p2 = points.get(idx2);
                        Point2D.Double closest = getClosestPointOnSegment(p1, p2, target);
                        double dSq = closest.distanceSq(target);
                        if (dSq < minDistSq) {
                            minDistSq = dSq;
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
                                        double dSq = closest.distanceSq(target);
                                        if (dSq < minDistSq) {
                                            minDistSq = dSq;
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
            this.connectionPathDistance = 0.0;
        } else if (path != null && !path.isEmpty()) {
            for (int i = 0; i < path.size() - 1; i++) {
                Point2D.Double p1 = path.get(i);
                Point2D.Double p2 = path.get(i + 1);
                Point2D.Double closest = getClosestPointOnSegment(p1, p2, target);
                double dSq = closest.distanceSq(target);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
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

    public void updateDynamicPath(JunctionController junctionController, List<Vehicle> allVehicles) {
        if (isLaneChanging()) {
            updatePathArea();
            return;
        }

        if (currentConnection != null) {
            this.path = new ArrayList<>(currentConnection.connectionPath());
            updatePathArea();
            return;
        }

        if (logicalRoute == null || logicalRoute.isEmpty() || currentPhaseIndex >= logicalRoute.size()) return;

        Set<Lane> currentPhase = logicalRoute.get(currentPhaseIndex);
        Point2D.Double p0 = new Point2D.Double(vehicle.getX(), vehicle.getY());
        Lane nearestLane = findNearestLane(currentPhase, p0);
        Lane routeTargetLane = null;

        if (currentPhaseIndex < logicalRoute.size() - 1) {
            Set<Lane> nextPhase = logicalRoute.get(currentPhaseIndex + 1);
            if (nearestLane != null) {
                Set<LaneConnection> conns = junctionController.getConnectionList(nearestLane);
                if (conns != null) {
                    for (LaneConnection conn : conns) {
                        if (nextPhase.contains(conn.targetLane())) {
                            routeTargetLane = nearestLane;
                            break;
                        }
                    }
                }
            }
            if (routeTargetLane == null) {
                for (Lane lane : currentPhase) {
                    Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                    if (conns != null) {
                        for (LaneConnection conn : conns) {
                            if (nextPhase.contains(conn.targetLane())) {
                                routeTargetLane = lane;
                                break;
                            }
                        }
                    }
                    if (routeTargetLane != null) break;
                }
            }
        }

        if (routeTargetLane == null) {
            routeTargetLane = (nearestLane != null) ? nearestLane : currentPhase.iterator().next();
        }

        double rad = Math.toRadians(vehicle.getAngle());
        Lane currentLane = (nearestLane != null) ? nearestLane : routeTargetLane;

        if (isOvertaking()) {
            if (allVehicles != null && !allVehicles.contains(overtakeTargetVehicle)) {
                clearOvertake();
            } else if (!hasPassedOvertakeTarget()) {
                clearLaneChangePath();
                return;
            } else {
                clearOvertake();
            }
        }
        
        Lane stepTargetLane = currentLane;
        double lookaheadDist = Math.max(30.0, vehicle.getSpeedKmh() * 3.0);
        
        // [지능형 차선 변경 (추월) 판단] ❤️
        boolean needRouteChange = (currentLane != routeTargetLane);
        
        // 현실적인 추월 판단 로직: 고정 수치 대신 TTC(Time-To-Collision) 개념 도입 ❤️
        double currentSpeedMs = UnitConverter.kmhToMs(vehicle.getSpeedKmh());
        double timeToReachFrontSec = UnitConverter.toMeter(currentVehicleVisionDistancePx) / Math.max(1.0, currentSpeedMs);
        double decisionTimeSec = 2.0 + (1.0 - vehicle.getDriverPersonality().getSafetyDistanceRatio()) * 5.0;
        
        boolean isBlockedByFront = Double.isFinite(currentVehicleVisionDistancePx) && 
                                   (timeToReachFrontSec <= decisionTimeSec || currentVehicleVisionDistancePx <= (desiredSafetyDistancePx * 2.0));
        Vehicle frontVehicle = (allVehicles == null) ? null : findFrontVehicleOnLane(currentLane, p0, allVehicles);

        List<Lane> adjacentLanes = getAdjacentLanes(currentPhase, currentLane, p0);
        Lane primaryAdjacent = null;
        Lane secondaryAdjacent = null;

        if (needRouteChange) {
            primaryAdjacent = findStepTargetLane(currentPhase, currentLane, routeTargetLane);
            for (Lane adj : adjacentLanes) {
                if (adj != primaryAdjacent) {
                    secondaryAdjacent = adj;
                    break;
                }
            }
        } else if (isBlockedByFront && frontVehicle != null && !adjacentLanes.isEmpty()) {
            primaryAdjacent = adjacentLanes.get(0);
            if (adjacentLanes.size() > 1) secondaryAdjacent = adjacentLanes.get(1);
        }

        boolean foundGap = false;
        
        if (primaryAdjacent != null && primaryAdjacent != currentLane) {
            if (checkLaneGapSafe(primaryAdjacent, p0, lookaheadDist, junctionController)) {
                stepTargetLane = primaryAdjacent;
                foundGap = true;
            }
        }
        
        if (!foundGap && isBlockedByFront && frontVehicle != null && secondaryAdjacent != null && secondaryAdjacent != currentLane) {
            if (checkLaneGapSafe(secondaryAdjacent, p0, lookaheadDist, junctionController)) {
                stepTargetLane = secondaryAdjacent;
            }
        }

        if (stepTargetLane == currentLane) {
            clearLaneChangePath();
            return;
        }

        List<Point2D.Double> orderedPoints = stepTargetLane.getLanePath();
        if (orderedPoints == null || orderedPoints.size() < 2) {
            clearLaneChangePath();
            return;
        }
        boolean reverse = !stepTargetLane.isRoadDirection();
        int size = orderedPoints.size();

        double accDist = 0, currentProjDist = 0, minDistToPath = Double.MAX_VALUE;
        for (int i = 0; i < size - 1; i++) {
            int idx1 = reverse ? size - 1 - i : i;
            int idx2 = reverse ? size - 2 - i : i + 1;
            Point2D.Double p1 = orderedPoints.get(idx1);
            Point2D.Double p2 = orderedPoints.get(idx2);
            Point2D.Double closest = getClosestPointOnSegment(p1, p2, p0);
            double dSq = closest.distanceSq(p0);
            if (dSq < minDistToPath) { 
                minDistToPath = dSq; 
                currentProjDist = accDist + p1.distance(closest); 
            }
            accDist += p1.distance(p2);
        }

        double targetDist = currentProjDist + lookaheadDist;
        Point2D.Double p3 = null, p2_dir = null;
        accDist = 0;
        for (int i = 0; i < size - 1; i++) {
            int idx1 = reverse ? size - 1 - i : i;
            int idx2 = reverse ? size - 2 - i : i + 1;
            Point2D.Double p1 = orderedPoints.get(idx1);
            Point2D.Double p2 = orderedPoints.get(idx2);
            double segLen = p1.distance(p2);
            if (accDist + segLen >= targetDist) {
                double ratio = (targetDist - accDist) / segLen;
                p3 = new Point2D.Double(p1.x + (p2.x - p1.x) * ratio, p1.y + (p2.y - p1.y) * ratio);
                p2_dir = new Point2D.Double(p2.x - p1.x, p2.y - p1.y);
                break;
            }
            accDist += segLen;
        }
        if (p3 == null) {
            int lastIdx = reverse ? 0 : size - 1;
            int prevIdx = reverse ? 1 : size - 2;
            p3 = orderedPoints.get(lastIdx);
            p2_dir = new Point2D.Double(p3.x - orderedPoints.get(prevIdx).x, p3.y - orderedPoints.get(prevIdx).y);
        }

        double dist = p0.distance(p3), weight = dist / 2.5;
        Point2D.Double p1 = new Point2D.Double(p0.x + Math.cos(rad) * weight, p0.y + Math.sin(rad) * weight);
        double len2 = Math.sqrt(p2_dir.x * p2_dir.x + p2_dir.y * p2_dir.y);
        if (len2 == 0) {
            clearLaneChangePath();
            return;
        }
        Point2D.Double p2 = new Point2D.Double(p3.x - (p2_dir.x / len2) * weight, p3.y - (p2_dir.y / len2) * weight);

        List<Point2D.Double> newPath = new ArrayList<>();
        for (int i = 0; i <= 30; i++) newPath.add(calculateCubicBezier(i / 30.0, p0, p1, p2, p3));
        beginLaneChangePath(newPath, stepTargetLane);
        if (!needRouteChange && isBlockedByFront && frontVehicle != null) {
            beginOvertake(frontVehicle, currentLane, stepTargetLane);
        }
        
        updatePathArea(); // 궤적 영역 업데이트 ❤️
    }

    private boolean checkLaneGapSafe(Lane targetLane, Point2D.Double p0, double lookaheadDist, JunctionController junctionController) {
        if (lastSideVisionArea == null || lastSideVisionArea.isEmpty()) return false;
        double minLookahead = UnitConverter.toPixel(15.0);
        List<Point2D.Double> testPoints = getLaneSubPath(targetLane, p0, 0, Math.max(minLookahead, lookaheadDist), junctionController);
        if (testPoints != null && testPoints.size() >= 2) {
            Area testArea = new Area();
            addPathToArea(testArea, testPoints, vehicle.getHeight() / 2.0 + UnitConverter.toPixel(0.5));
            Area collisionCheck = new Area(testArea);
            collisionCheck.subtract(lastSideVisionArea);
            return collisionCheck.isEmpty();
        }
        return false;
    }

    private Vehicle findFrontVehicleOnLane(Lane lane, Point2D.Double p0, List<Vehicle> allVehicles) {
        if (lane == null || allVehicles == null) return null;

        double myProjDist = getProjectionDistance(lane, p0);
        if (myProjDist < 0) return null;

        double laneWidthPx = UnitConverter.toPixel(3.5);
        double maxLaneCenterDistSq = laneWidthPx * laneWidthPx / 4.0;
        double maxFrontDistancePx = Math.max(UnitConverter.toPixel(10.0), currentVehicleVisionDistancePx + UnitConverter.toPixel(20.0));
        Vehicle nearestFrontVehicle = null;
        double nearestFrontDistance = Double.POSITIVE_INFINITY;

        for (Vehicle other : allVehicles) {
            if (other == vehicle) continue;

            Point2D.Double otherPos = new Point2D.Double(other.getX(), other.getY());
            double[] projAndDistSq = getProjectionAndDistanceSq(lane, otherPos);
            if (projAndDistSq[1] > maxLaneCenterDistSq) continue;

            double frontDistance = projAndDistSq[0] - myProjDist - (vehicle.getWidth() + other.getWidth()) / 2.0;
            if (frontDistance >= 0.0 && frontDistance < nearestFrontDistance && frontDistance <= maxFrontDistancePx) {
                nearestFrontDistance = frontDistance;
                nearestFrontVehicle = other;
            }
        }

        return nearestFrontVehicle;
    }

    private boolean hasPassedOvertakeTarget() {
        if (!isOvertaking()) return true;

        Point2D.Double myPos = new Point2D.Double(vehicle.getX(), vehicle.getY());
        Point2D.Double targetPos = new Point2D.Double(overtakeTargetVehicle.getX(), overtakeTargetVehicle.getY());
        double myProjDist = getProjectionDistance(overtakeReferenceLane, myPos);
        double targetProjDist = getProjectionDistance(overtakeReferenceLane, targetPos);
        if (myProjDist < 0 || targetProjDist < 0) {
            return true;
        }

        double clearancePx = UnitConverter.toPixel(OVERTAKE_CLEARANCE_M);
        double requiredGap = (vehicle.getWidth() + overtakeTargetVehicle.getWidth()) / 2.0 + clearancePx;
        return myProjDist - targetProjDist > requiredGap;
    }

    private List<Lane> getAdjacentLanes(Set<Lane> currentPhase, Lane currentLane, Point2D.Double p0) {
        List<Lane> adj = new ArrayList<>();
        double laneWidthPx = UnitConverter.toPixel(3.5);
        double maxDistSq = (laneWidthPx * 1.8) * (laneWidthPx * 1.8);
        double minDistSq = (laneWidthPx * 0.5) * (laneWidthPx * 0.5);
        
        for (Lane lane : currentPhase) {
            if (lane == currentLane) continue;
            double distSq = getDistanceSqToPath(lane, p0);
            if (distSq > minDistSq && distSq < maxDistSq) {
                adj.add(lane);
            }
        }
        return adj;
    }

    private void updatePathArea() {
        if (path == null || path.size() < 2) {
            pathArea = new Area();
            return;
        }
        Area newArea = new Area();
        addPathToArea(newArea, path, vehicle.getHeight() / 2.0);
        this.pathArea = newArea;
    }

    public Area getPathArea() { return pathArea; }

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
        double dx = p2.x - p1.x, dy = p2.y - p1.y, l2 = dx * dx + dy * dy;
        if (l2 == 0) return t.distanceSq(p1);
        double t_proj = Math.max(0, Math.min(1, ((t.x - p1.x) * dx + (t.y - p1.y) * dy) / l2));
        double cx = p1.x + t_proj * dx, cy = p1.y + t_proj * dy;
        return (t.x - cx) * (t.x - cx) + (t.y - cy) * (t.y - cy);
    }

    // Getters and Setters ❤️
    public List<Point2D.Double> getPath() { return path; }
    public void setPath(List<Point2D.Double> path) { this.path = path; }
    public List<Set<Lane>> getLogicalRoute() { return logicalRoute; }
    public void setLogicalRoute(List<Set<Lane>> route) {
        this.logicalRoute = route;
        this.routeFinished = false;
        clearOvertake();
        clearRememberedTrafficLight();
    }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(int index) { this.currentPhaseIndex = index; }
    public List<Point2D.Double> getForwardVisionArea() { return forwardVisionArea; }
    public List<Point2D.Double> getSideVisionArea() { return sideVisionArea; }
    public List<Point2D.Double> getVehicleVisionArea() { return vehicleVisionArea; }
    public Area getLastForwardVisionArea() { return lastForwardVisionArea; }
    public Area getLastSideVisionArea() { return lastSideVisionArea; }
    public Area getLastVehicleVisionArea() { return lastVehicleVisionArea; }
    public LaneConnection getCurrentConnection() { return currentConnection; }
    public boolean isRouteFinished() { return routeFinished; }
}
