package com.trafficsimulator.road.camera;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.awt.geom.Point2D;

import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;

/**
 * 도로 위 과속 등을 단속하는 카메라 클래스입니다. ❤️
 */
public class Camera {
    private Map<Lane, Point2D.Double> targetLaneMap = new HashMap<>(); 
    private int limitSpeed;
    private Point2D.Double loc; 
    private Road road;

    /**
     * 지능형 생성자: 기준 차선을 기반으로 감시 대상을 자동 등록하고, 위치를 중앙으로 조정합니다.
     */
    public Camera(Road road, Point2D.Double loc, Lane selectLane) {
        this.road=road;
        this.limitSpeed = road.getLimitSpeed();
        this.loc = new Point2D.Double(loc.x, loc.y); 
        
        List<Lane> laneList = road.getCanChangeLaneList(selectLane);
        for (Lane lane : laneList) {
            addTargetLane(lane);
        }
        
        updateLocationToCenter();
    }

    /**
     * 감시할 차선을 추가합니다. 
     */
    public void addTargetLane(Lane lane) {
        if (lane == null || loc == null) return;
        if (!road.getLaneList().contains(lane)) return;
        // 차선 경로(선분) 위에서 가장 가까운 점을 찾도록 수정! ❤️
        Point2D.Double snapPt = findNearestPointOnLane(lane, this.loc);
        targetLaneMap.put(lane, snapPt);
    }

    public void removeTargetLane(Lane lane) {
        targetLaneMap.remove(lane);
        updateLocationToCenter();
    }

    /**
     * 카메라 위치를 현재 감시 중인 모든 차선의 단속 지점 중앙으로 이동시킵니다.
     */
    public void updateLocationToCenter() {
        if (targetLaneMap.isEmpty()) return;
        
        double sumX = 0;
        double sumY = 0;
        for (Point2D.Double p : targetLaneMap.values()) {
            sumX += p.x;
            sumY += p.y;
        }
        
        this.loc.setLocation(sumX / targetLaneMap.size(), sumY / targetLaneMap.size());
    }

    /**
     * 도로 모양이 변하거나 카메라가 이동했을 때 단속 지점을 재계산합니다. ❤️
     */
    public void refreshEnforcementPoints() {
        if (targetLaneMap.isEmpty()) return;
        
        for (Lane lane : new HashSet<>(targetLaneMap.keySet())) {
            Point2D.Double newSnapPt = findNearestPointOnLane(lane, this.loc);
            targetLaneMap.put(lane, newSnapPt);
        }
        
        // 단속 지점들이 갱신되면 카메라 위치도 미세하게 중앙으로 보정! ❤️
        updateLocationToCenter();
    }

    /**
     * 차선 경로(선분들) 중에서 특정 좌표와 가장 가까운 지점을 계산합니다. (선분 투영 적용) ❤️
     */
    private Point2D.Double findNearestPointOnLane(Lane lane, Point2D.Double referencePt) {
        List<Point2D.Double> path = lane.getLanePath();
        if (path == null || path.size() < 2) return null;

        Point2D.Double bestPt = null;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < path.size() - 1; i++) {
            Point2D.Double v = path.get(i);
            Point2D.Double w = path.get(i + 1);
            
            // 선분 v-w 위에서 referencePt와 가장 가까운 점 계산 (Projection) ❤️
            Point2D.Double nearestOnSegment = getNearestPointOnSegment(referencePt, v, w);
            double d = nearestOnSegment.distance(referencePt);
            
            if (d < minDistance) {
                minDistance = d;
                bestPt = nearestOnSegment;
            }
        }
        return bestPt;
    }

    /**
     * 선분(v-w) 위에서 점 p와 가장 가까운 좌표를 계산하여 반환합니다.
     */
    private Point2D.Double getNearestPointOnSegment(Point2D.Double p, Point2D.Double v, Point2D.Double w) {
        double l2 = v.distanceSq(w);
        if (l2 == 0.0) return v;
        double t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2;
        t = Math.max(0, Math.min(1, t));
        return new Point2D.Double(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y));
    }

    public Map<Lane, Point2D.Double> getTargetLaneMap() { return targetLaneMap; }
    public Point2D.Double getLoc() { return loc; }
    public Road getRoad() { return road; }
    public void setLoc(Point2D.Double loc) { 
        // 외부(드래그 등)에서 위치를 강제로 설정할 때 사용 ❤️
        this.loc.setLocation(loc); 
        refreshEnforcementPoints(); 
    }
    public int getLimitSpeed() { return limitSpeed; }
    public void setLimitSpeed(int limitSpeed) { this.limitSpeed = limitSpeed; }
}
