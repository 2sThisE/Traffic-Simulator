package com.traficsimulator.ui;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.Road;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class RoadManager {
    private final List<Road> roadList = new ArrayList<>();

    public void addRoad(Road road) {
        roadList.add(road);
    }

    public void removeRoad(Road road) {
        roadList.remove(road);
    }

    public List<Road> getRoadList() {
        return roadList;
    }

    public HitResult findHit(Point2D.Double worldPt) {
        Road bestRoad = null;
        Lane bestLane = null;
        double minRoadDist = 20.0;

        // 리스트를 뒤에서부터 훑어서 가장 위에 있는 도로를 먼저 찾음 ❤️
        for (int i = roadList.size() - 1; i >= 0; i--) {
            Road road = roadList.get(i);
            for (Lane lane : road.getLaneList()) {
                double dist = getDistanceToPath(worldPt, lane.getLanePath());
                if (dist < road.getLaneWidth() / 2.0) {
                    return new HitResult(road, lane);
                }
            }
            double centerDist = getDistanceToPath(worldPt, road.getPathPoints());
            if (centerDist < minRoadDist) {
                // 더 나은 후보(위에 있는 도로)를 위해 루프를 계속 돌되, 가장 마지막에 찾은 게 가장 위임
                bestRoad = road;
                return new HitResult(bestRoad, null); // 도로 중심선도 위에 있는 거 우선!
            }
        }
        return new HitResult(bestRoad, null);
    }

    /**
     * 시작점, 끝점 또는 베지어 제어점 근처인지 확인합니다. (위에서부터 탐색) ❤️
     */
    public PointHit findNearestPoint(Point2D.Double worldPt, double threshold) {
        for (int i = roadList.size() - 1; i >= 0; i--) {
            Road road = roadList.get(i);
            // 1. 시작/끝점 체크
            if (worldPt.distance(road.getStartPoint()) < threshold) {
                return new PointHit(road, PointType.START);
            }
            if (worldPt.distance(road.getEndPoint()) < threshold) {
                return new PointHit(road, PointType.END);
            }
            
            // 2. 제어점 체크 (곡선일 때만)
            if (road.getPathPoints().size() > 2) {
                if (road.getControl1() != null && worldPt.distance(road.getControl1()) < threshold) {
                    return new PointHit(road, PointType.CONTROL1);
                }
                if (road.getControl2() != null && worldPt.distance(road.getControl2()) < threshold) {
                    return new PointHit(road, PointType.CONTROL2);
                }
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
        double t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2;
        t = Math.max(0, Math.min(1, t));
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
