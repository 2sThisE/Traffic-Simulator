package com.traficsimulator.road.camera;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.awt.geom.Point2D;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.Road;

/**
 * 도로 위 과속 등을 단속하는 카메라 클래스입니다. ❤️
 */
public class Camera {
    private Map<Lane, Point2D.Double> targetLaneMap = new HashMap<>(); 
    private int limitSpeed;
    private Point2D.Double loc; 

    /**
     * 지능형 생성자: 기준 차선을 기반으로 감시 대상을 자동 등록하고, 위치를 중앙으로 조정합니다. ❤️
     */
    public Camera(Road road, Point2D.Double loc, Lane selectLane) {
        this.limitSpeed = road.getLimitSpeed();
        this.loc = new Point2D.Double(loc.x, loc.y); // 원본 좌표 복사
        
        List<Lane> laneList = road.getCanChangeLaneList(selectLane);
        for (Lane lane : laneList) {
            addTargetLane(lane);
        }
        
        // 모든 차선이 등록된 후 카메라 위치를 중앙으로 이동! ❤️
        updateLocationToCenter();
    }

    /**
     * 감시할 차선을 추가합니다. 
     */
    public void addTargetLane(Lane lane) {
        if (lane == null || loc == null) return;
        
        Point2D.Double snapPt = findNearestPointOnLane(lane, this.loc);
        targetLaneMap.put(lane, snapPt);
    }

    /**
     * 감시할 차선을 목록에서 제거합니다.
     */
    public void removeTargetLane(Lane lane) {
        targetLaneMap.remove(lane);
        updateLocationToCenter(); // 차선이 빠지면 중앙점도 다시 잡아야지? ❤️
    }

    /**
     * 카메라 위치를 현재 감시 중인 모든 차선의 단속 지점 중앙으로 이동시킵니다. ❤️
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
     * 도로 모양이 변했을 때, 단속 지점을 재계산하고 카메라 위치도 중앙으로 다시 맞춥니다. ❤️
     */
    public void refreshEnforcementPoints() {
        if (targetLaneMap.isEmpty()) return;
        
        // 현재 카메라 위치를 기준으로 새로운 스냅 포인트들을 찾음
        for (Lane lane : new HashSet<>(targetLaneMap.keySet())) {
            Point2D.Double newSnapPt = findNearestPointOnLane(lane, this.loc);
            targetLaneMap.put(lane, newSnapPt);
        }
        
        // 다시 한번 중앙 정렬!
        updateLocationToCenter();
    }

    private Point2D.Double findNearestPointOnLane(Lane lane, Point2D.Double referencePt) {
        Point2D.Double nearest = null;
        double minDistance = Double.MAX_VALUE;

        if (lane.getLanePath() != null) {
            for (Point2D.Double pathPt : lane.getLanePath()) {
                double dist = pathPt.distance(referencePt);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = pathPt;
                }
            }
        }
        return nearest;
    }

    public Map<Lane, Point2D.Double> getTargetLaneMap() { return targetLaneMap; }
    public Point2D.Double getLoc() { return loc; }
    public void setLoc(Point2D.Double loc) { 
        this.loc.setLocation(loc); 
        refreshEnforcementPoints(); 
    }
    public int getLimitSpeed() { return limitSpeed; }
    public void setLimitSpeed(int limitSpeed) { this.limitSpeed = limitSpeed; }
}
