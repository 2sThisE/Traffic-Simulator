package com.trafficsimulator.ui;

import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.camera.Camera; // 추가 ❤️

public class RoadManager {
    private final List<Road> roadList = new ArrayList<>();
    private final List<TrafficLight> trafficLightList = new ArrayList<>();
    private final List<Camera> cameraList = new ArrayList<>(); // 추가 ❤️

    public void addRoad(Road road) {
        roadList.add(road);
    }

    public void removeRoad(Road road) {
        roadList.remove(road);
    }

    public List<Road> getRoadList() {
        return roadList;
    }

    public void addTrafficLight(TrafficLight tl) {
        trafficLightList.add(tl);
    }

    public void removeTrafficLight(TrafficLight tl) {
        trafficLightList.remove(tl);
    }

    public List<TrafficLight> getTrafficLightList() {
        return trafficLightList;
    }

    // --- 카메라 관리 기능 추가 ❤️ ---
    public void addCamera(Camera camera) {
        cameraList.add(camera);
    }

    public void removeCamera(Camera camera) {
        cameraList.remove(camera);
    }

    public List<Camera> getCameraList() {
        return cameraList;
    }

    /**
     * 특정 차선이 속해 있는 도로 객체를 반환합니다. ❤️
     */
    public Road findRoadByLane(Lane lane) {
        for (Road road : roadList) {
            if (road.getLaneList().contains(lane)) {
                return road;
            }
        }
        return null;
    }

    /**
     * 특정 좌표 근처에 카메라가 있는지 확인합니다. ❤️
     */
    public Camera findCameraHit(Point2D.Double worldPt, double threshold) {
        for (Camera c : cameraList) {
            if (c.getLoc() != null && c.getLoc().distance(worldPt) < threshold) {
                return c;
            }
        }
        return null;
    }

    /**
     * 특정 좌표에서 가장 가까운 도로 위의 지점을 찾습니다. (카메라 드래그 스냅용) ❤️
     * 선분(Segment) 위로의 투영을 계산하여 점이 적은 도로에서도 부드럽게 작동합니다.
     */
    public Point2D.Double findNearestPointOnAnyRoad(Point2D.Double pt) {
        Point2D.Double bestPt = null;
        double minDist = Double.MAX_VALUE;

        for (Road road : roadList) {
            for (Lane lane : road.getLaneList()) {
                List<Point2D.Double> path = lane.getLanePath();
                if (path == null || path.size() < 2) continue;

                for (int i = 0; i < path.size() - 1; i++) {
                    Point2D.Double v = path.get(i);
                    Point2D.Double w = path.get(i + 1);
                    
                    // 선분 v-w 위에서 pt와 가장 가까운 지점 계산 ❤️
                    Point2D.Double nearestOnSegment = getNearestPointOnSegment(pt, v, w);
                    double d = nearestOnSegment.distance(pt);
                    
                    if (d < minDist) {
                        minDist = d;
                        bestPt = nearestOnSegment;
                    }
                }
            }
        }
        return (bestPt != null) ? bestPt : pt;
    }

    private Point2D.Double getNearestPointOnSegment(Point2D.Double p, Point2D.Double v, Point2D.Double w) {
        double l2 = v.distanceSq(w);
        if (l2 == 0.0) return v;
        double t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2;
        t = Math.max(0, Math.min(1, t));
        return new Point2D.Double(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y));
    }

    /**
     * 모든 신호등/카메라의 위치를 차선 상태에 맞춰 업데이트합니다.
     */
    public void refreshStaticObjectPositions() {
        for (TrafficLight tl : trafficLightList) {
            tl.updatePositionToLanesCenter();
        }
        for (Camera c : cameraList) {
            c.refreshEnforcementPoints();
        }
    }

    /**
     * 특정 좌표 근처에 신호등이 있는지 확인합니다.
     */
    public TrafficLight findTrafficLightHit(Point2D.Double worldPt, double threshold) {
        for (TrafficLight tl : trafficLightList) {
            if (tl.getCoordinates() != null && tl.getCoordinates().distance(worldPt) < threshold) {
                return tl;
            }
        }
        return null;
    }

    public HitResult findHit(Point2D.Double worldPt) {
        Road bestRoad = null;
        for (int i = roadList.size() - 1; i >= 0; i--) {
            Road road = roadList.get(i);
            for (Lane lane : road.getLaneList()) {
                double dist = getDistanceToPath(worldPt, lane.getLanePath());
                if (dist < road.getLaneWidth() / 2.0) {
                    return new HitResult(road, lane);
                }
            }
            double centerDist = getDistanceToPath(worldPt, road.getPathPoints());
            if (centerDist < 20.0) {
                bestRoad = road;
                return new HitResult(bestRoad, null);
            }
        }
        return new HitResult(bestRoad, null);
    }

    public PointHit findNearestPoint(Point2D.Double worldPt, double threshold) {
        for (int i = roadList.size() - 1; i >= 0; i--) {
            Road road = roadList.get(i);
            if (worldPt.distance(road.getStartPoint()) < threshold) return new PointHit(road, PointType.START);
            if (worldPt.distance(road.getEndPoint()) < threshold) return new PointHit(road, PointType.END);
            if (road.getPathPoints().size() > 2) {
                if (road.getControl1() != null && worldPt.distance(road.getControl1()) < threshold) return new PointHit(road, PointType.CONTROL1);
                if (road.getControl2() != null && worldPt.distance(road.getControl2()) < threshold) return new PointHit(road, PointType.CONTROL2);
            }
        }
        return null;
    }

    private double getDistanceToPath(Point2D.Double pt, List<Point2D.Double> path) {
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size() - 1; i++) {
            minDist = Math.min(minDist, distToSegment(pt, path.get(i), path.get(i + 1)));
        }
        return minDist;
    }

    private double distToSegment(Point2D.Double p, Point2D.Double v, Point2D.Double w) {
        double l2 = v.distanceSq(w);
        if (l2 == 0.0) return p.distance(v);
        double t = Math.max(0, Math.min(1, ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2));
        return p.distance(new Point2D.Double(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y)));
    }

    public enum PointType { START, END, CONTROL1, CONTROL2 }
    public static class PointHit {
        public final Road road;
        public final PointType type;
        public PointHit(Road road, PointType type) { this.road = road; this.type = type; }
    }
    public static class HitResult {
        public final Road road;
        public final Lane lane;
        public HitResult(Road road, Lane lane) { this.road = road; this.lane = lane; }
    }
}
