package com.traficsimulator.road;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JunctionController {
    private Map<Lane, Set<LaneConnection>> connection=new HashMap<>();

    /**
     * 도로의 연결 정보를 추가 합니다.
     * @param fromLane
     * @param toLane
     * @param laneType
     */
    public void addConnection(Lane fromLane, Lane toLane, LaneType laneType) {
        List<Point2D.Double> connectionPath = generateConnectionPath(fromLane, toLane);
        connection.computeIfAbsent(fromLane, k -> new HashSet<>())
                .add(new LaneConnection(toLane, laneType, connectionPath));
    }

    /**
     * 두 차선 사이를 잇는 부드러운 베지어 곡선 경로를 생성합니다.
     */
    private List<Point2D.Double> generateConnectionPath(Lane from, Lane to) {
        List<Point2D.Double> fromPath = from.getLanePath();
        List<Point2D.Double> toPath = to.getLanePath();

        if (fromPath.isEmpty() || toPath.isEmpty()) return new ArrayList<>();

        // 차선 방향에 따른 진출/진입점 선택
        Point2D.Double p0, p0_prev, p3, p3_next;

        if (from.isRoadDirection()) { // 상행: 리스트의 마지막이 진출
            p0 = fromPath.get(fromPath.size() - 1);
            p0_prev = fromPath.size() > 1 ? fromPath.get(fromPath.size() - 2) : p0;
        } else { // 하행: 리스트의 처음이 진출
            p0 = fromPath.get(0);
            p0_prev = fromPath.size() > 1 ? fromPath.get(1) : p0;
        }

        if (to.isRoadDirection()) { // 상행: 리스트의 처음이 진입
            p3 = toPath.get(0);
            p3_next = toPath.size() > 1 ? toPath.get(1) : p3;
        } else { // 하행: 리스트의 마지막이 진입
            p3 = toPath.get(toPath.size() - 1);
            p3_next = toPath.size() > 1 ? toPath.get(toPath.size() - 2) : p3;
        }

        // 방향 벡터 추출
        double dx1 = p0.x - p0_prev.x;
        double dy1 = p0.y - p0_prev.y;
        double len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        if (len1 < 1e-9) dy1 = 1; else { dx1 /= len1; dy1 /= len1; }

        double dx2 = p3_next.x - p3.x;
        double dy2 = p3_next.y - p3.y;
        double len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
        if (len2 < 1e-9) dy2 = 1; else { dx2 /= len2; dy2 /= len2; }

        double dist = p0.distance(p3);
        double weight = dist / 3.0; // 제어점 가중치 (1/3이 적당해)

        // 제어점 P1, P2 계산
        Point2D.Double p1 = new Point2D.Double(p0.x + dx1 * weight, p0.y + dy1 * weight);
        Point2D.Double p2 = new Point2D.Double(p3.x - dx2 * weight, p3.y - dy2 * weight);

        // 베지어 곡선 점 생성
        List<Point2D.Double> path = new ArrayList<>();
        int segments = 30; // 더 매끄럽게 30개로!
        for (int i = 0; i <= segments; i++) {
            path.add(calculateCubicBezier(i / (double) segments, p0, p1, p2, p3));
        }
        return path;
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
    /**
     * 해당 차선의 연결 정보를 반환합니다.
     * @param fromLane
     * @return
     */
    public Set<LaneConnection> getConnectionList(Lane fromLane){return connection.get(fromLane);}


    /**
     * 특정 차선의 연결 정보를 수정합니다
     * @param fromLane
     * @param target
     * @param newConnection
     * @return boolean
     */
    public boolean editConnection(Lane fromLane, LaneConnection target, LaneConnection newConnection) {
        Set<LaneConnection> laneConnections = connection.get(fromLane);
        if (laneConnections != null&&laneConnections.remove(target)) {
            laneConnections.add(newConnection);
            return true;
        }else return false;
    }

    /**
     * 특정 차선의 연결 정보를 삭제합니다
     * target이 null 일시 fromLane에 대한 모든 연결 정보를 삭제합니다
     * @param fromLane
     * @param target
     * @return
     */
     public boolean deleteConnection(Lane fromLane, LaneConnection target) {
        if (!connection.containsKey(fromLane)) return false;
        if (target == null) return connection.remove(fromLane) != null;
        else {
            Set<LaneConnection> set = connection.get(fromLane);
            return set.remove(target);
        }
    }
    /**
     * 특정 차선과 관련된 모든 연결의 경로를 다시 계산합니다.
     */
    public void refreshConnections(Lane lane) {
        if (connection.containsKey(lane)) {
            Set<LaneConnection> oldConns = connection.get(lane);
            Set<LaneConnection> newConns = new HashSet<>();
            for (LaneConnection conn : oldConns) {
                newConns.add(new LaneConnection(conn.targetLane(), conn.laneType(), generateConnectionPath(lane, conn.targetLane())));
            }
            connection.put(lane, newConns);
        }
        for (Map.Entry<Lane, Set<LaneConnection>> entry : connection.entrySet()) {
            Set<LaneConnection> conns = entry.getValue();
            Set<LaneConnection> updatedConns = new HashSet<>();
            for (LaneConnection conn : conns) {
                if (conn.targetLane() == lane) {
                    updatedConns.add(new LaneConnection(lane, conn.laneType(), generateConnectionPath(entry.getKey(), lane)));
                } else updatedConns.add(conn);
            }
            connection.put(entry.getKey(), updatedConns);
        }
    }

    /**
     * 차선이 삭제되었을 때 관련 연결 정보를 제거합니다.
     */
    public void removeLaneConnections(Lane lane) {
        connection.remove(lane);
        for (Set<LaneConnection> conns : connection.values()) {
            conns.removeIf(conn -> conn.targetLane() == lane);
        }
    }
}