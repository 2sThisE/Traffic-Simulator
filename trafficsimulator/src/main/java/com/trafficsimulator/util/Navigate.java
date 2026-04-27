package com.trafficsimulator.util;

import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.ui.RoadManager;

import java.awt.geom.Point2D;
import java.util.*;

/**
 * 시뮬레이션 내 경로 탐색 및 도로 네트워크 분석을 담당하는 클래스입니다.
 */
public class Navigate {

    public static class GlobalEndpoints {
        public final Set<Lane> startLanes = new HashSet<>();
        public final Set<Lane> endLanes = new HashSet<>();
    }

    public static GlobalEndpoints findGlobalEndpoints(RoadManager roadManager, JunctionController junctionController) {
        GlobalEndpoints endpoints = new GlobalEndpoints();
        List<Road> roadList = roadManager.getRoadList();
        Set<Lane> allLanes = new HashSet<>();
        Set<Lane> hasIncoming = new HashSet<>();

        for (Road road : roadList) {
            for (Lane lane : road.getLaneList()) {
                allLanes.add(lane);
                Set<LaneConnection> connections = junctionController.getConnectionList(lane);
                if (connections != null) {
                    for (LaneConnection conn : connections) {
                        hasIncoming.add(conn.targetLane());
                    }
                }
            }
        }

        for (Lane lane : allLanes) {
            if (!hasIncoming.contains(lane)) endpoints.startLanes.add(lane);
            
            // 끝점 판단: 해당 차선에서 나가는 연결도 없고, 
            // 같은 도로 내의 다른 차선들 중에서도 나가는 연결이 '전혀' 없을 때 진짜 끝점이라고 판단함 ❤️
            if (isTrueDeadEnd(lane, roadManager, junctionController)) {
                endpoints.endLanes.add(lane);
            }
        }
        return endpoints;
    }

    private static boolean isTrueDeadEnd(Lane lane, RoadManager roadManager, JunctionController junctionController) {
        Road road = roadManager.findRoadByLane(lane);
        if (road == null) return true;
        
        for (Lane l : road.getLaneList()) {
            Set<LaneConnection> conns = junctionController.getConnectionList(l);
            if (conns != null && !conns.isEmpty()) return false; // 옆 차선이라도 어디론가 갈 수 있다면 죽은 길이 아님!
        }
        return true;
    }

    public static List<Point2D.Double> generateTotalPathPoints(List<Set<Lane>> routePhases, JunctionController junctionController) {
        List<Point2D.Double> totalPoints = new ArrayList<>();
        if (routePhases == null || routePhases.isEmpty()) return totalPoints;

        for (int i = 0; i < routePhases.size(); i++) {
            Set<Lane> currentPhase = routePhases.get(i);
            Lane bestLane = null;
            LaneConnection bestConn = null;

            if (i < routePhases.size() - 1) {
                Set<Lane> nextPhase = routePhases.get(i + 1);
                for (Lane lane : currentPhase) {
                    Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                    if (conns != null) {
                        for (LaneConnection conn : conns) {
                            if (nextPhase.contains(conn.targetLane())) {
                                bestLane = lane;
                                bestConn = conn;
                                break;
                            }
                        }
                    }
                    if (bestLane != null) break;
                }
            }

            if (bestLane == null) {
                bestLane = currentPhase.iterator().next();
            }

            List<Point2D.Double> lanePoints = bestLane.getLanePath();
            if (bestLane.isRoadDirection()) {
                totalPoints.addAll(lanePoints);
            } else {
                List<Point2D.Double> reversed = new ArrayList<>(lanePoints);
                Collections.reverse(reversed);
                totalPoints.addAll(reversed);
            }

            if (bestConn != null) {
                totalPoints.addAll(bestConn.connectionPath());
            }
        }
        return totalPoints;
    }

    public static List<Set<Lane>> calculateRoute(Lane startLane, RoadManager roadManager, JunctionController junctionController) {
        GlobalEndpoints endpoints = findGlobalEndpoints(roadManager, junctionController);
        List<Lane> lanePath = findSingleLanePathWithLaneChanges(startLane, endpoints.endLanes, junctionController, roadManager);
        if (lanePath == null) return null;

        List<Set<Lane>> fullRoute = new ArrayList<>();
        Road lastRoad = null;
        for (Lane lane : lanePath) {
            Road road = roadManager.findRoadByLane(lane);
            if (road != null && road != lastRoad) {
                Set<Lane> laneGroup = new HashSet<>(road.getCanChangeLaneList(lane));
                laneGroup.add(lane);
                fullRoute.add(laneGroup);
                lastRoad = road;
            }
        }
        return fullRoute;
    }

    private static List<Lane> findSingleLanePathWithLaneChanges(Lane start, Set<Lane> targets, JunctionController junctionController, RoadManager roadManager) {
        Queue<List<Lane>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));
        Set<Lane> visited = new HashSet<>();
        visited.add(start);

        while (!queue.isEmpty()) {
            List<Lane> path = queue.poll();
            Lane last = path.get(path.size() - 1);

            if (targets.contains(last)) return path;

            Set<LaneConnection> conns = junctionController.getConnectionList(last);
            if (conns != null) {
                for (LaneConnection conn : conns) {
                    if (!visited.contains(conn.targetLane())) {
                        visited.add(conn.targetLane());
                        List<Lane> newPath = new ArrayList<>(path);
                        newPath.add(conn.targetLane());
                        queue.add(newPath);
                    }
                }
            }

            Road road = roadManager.findRoadByLane(last);
            if (road != null) {
                List<Lane> adjacentLanes = road.getCanChangeLaneList(last);
                for (Lane adj : adjacentLanes) {
                    if (!visited.contains(adj)) {
                        visited.add(adj);
                        List<Lane> newPath = new ArrayList<>(path);
                        newPath.add(adj);
                        queue.add(newPath);
                    }
                }
            }
        }
        return null;
    }
}
