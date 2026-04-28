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
        
        boolean currentDirection = lane.isRoadDirection();
        for (Lane l : road.getLaneList()) {
            // 같은 방향인 차선들 중에서만 나가는 연결이 있는지 확인합니다.
            if (l.isRoadDirection() == currentDirection) {
                Set<LaneConnection> conns = junctionController.getConnectionList(l);
                if (conns != null && !conns.isEmpty()) return false; 
            }
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

    public static List<List<Set<Lane>>> calculateAllRoutes(Lane startLane, RoadManager roadManager, JunctionController junctionController) {
        GlobalEndpoints endpoints = findGlobalEndpoints(roadManager, junctionController);
        List<List<Lane>> allLanePaths = findAllLanePathsWithLaneChanges(startLane, endpoints.endLanes, junctionController, roadManager);
        
        List<List<Set<Lane>>> allFullRoutes = new ArrayList<>();
        for (List<Lane> lanePath : allLanePaths) {
            List<Set<Lane>> fullRoute = new ArrayList<>();
            
            // 도로별로 마지막 차선을 추적하여 해당 도로에서의 최종 가용 차선 그룹을 계산
            Map<Road, Lane> lastLaneOnRoad = new LinkedHashMap<>();
            for (Lane lane : lanePath) {
                Road road = roadManager.findRoadByLane(lane);
                if (road != null) {
                    lastLaneOnRoad.put(road, lane);
                }
            }

            for (Map.Entry<Road, Lane> entry : lastLaneOnRoad.entrySet()) {
                Road road = entry.getKey();
                Lane lastLane = entry.getValue();
                
                Set<Lane> laneGroup = new HashSet<>(road.getCanChangeLaneList(lastLane));
                laneGroup.add(lastLane);
                fullRoute.add(laneGroup);
            }
            allFullRoutes.add(fullRoute);
        }
        return allFullRoutes;
    }

    private static List<List<Lane>> findAllLanePathsWithLaneChanges(Lane start, Set<Lane> targets, JunctionController junctionController, RoadManager roadManager) {
        List<List<Lane>> results = new ArrayList<>();
        Queue<List<Lane>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));
        
        while (!queue.isEmpty()) {
            List<Lane> path = queue.poll();
            Lane last = path.get(path.size() - 1);

            if (targets.contains(last)) {
                results.add(path);
                if (results.size() >= 10) break; 
                continue;
            }

            Set<LaneConnection> conns = junctionController.getConnectionList(last);
            if (conns != null) {
                for (LaneConnection conn : conns) {
                    if (!path.contains(conn.targetLane())) {
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
                    if (!path.contains(adj)) {
                        List<Lane> newPath = new ArrayList<>(path);
                        newPath.add(adj);
                        queue.add(newPath);
                    }
                }
            }
        }
        return results;
    }

    public static List<Set<Lane>> calculateRoute(Lane startLane, RoadManager roadManager, JunctionController junctionController) {
        GlobalEndpoints endpoints = findGlobalEndpoints(roadManager, junctionController);
        List<Lane> lanePath = findSingleLanePathWithLaneChanges(startLane, endpoints.endLanes, junctionController, roadManager);
        if (lanePath == null) return null;

        // 1. 경로에 포함된 도로 순서 추출
        List<Road> roadSequence = new ArrayList<>();
        for (Lane lane : lanePath) {
            Road road = roadManager.findRoadByLane(lane);
            if (road != null && (roadSequence.isEmpty() || roadSequence.get(roadSequence.size() - 1) != road)) {
                roadSequence.add(road);
            }
        }

        List<Set<Lane>> fullRoute = new ArrayList<>();
        
        // 2. 각 도로 구간(Phase)별로 '다음 도로로 갈 수 있는 유효한 차선'만 필터링
        for (int i = 0; i < roadSequence.size(); i++) {
            Road currentRoad = roadSequence.get(i);
            Set<Lane> validLanes = new HashSet<>();
            
            if (i < roadSequence.size() - 1) {
                // 다음 도로가 있는 경우: 다음 도로의 차선 중 하나로 연결되는 현재 도로의 차선들을 찾음
                Road nextRoad = roadSequence.get(i + 1);
                Set<Lane> nextRoadLanes = new HashSet<>(nextRoad.getLaneList());
                
                // 현재 도로의 모든 차선 중 다음 도로로 이어지는 '출구 차선'들 찾기
                Set<Lane> exitLanes = new HashSet<>();
                for (Lane lane : currentRoad.getLaneList()) {
                    Set<LaneConnection> conns = junctionController.getConnectionList(lane);
                    if (conns != null) {
                        for (LaneConnection conn : conns) {
                            if (nextRoadLanes.contains(conn.targetLane())) {
                                exitLanes.add(lane);
                                break;
                            }
                        }
                    }
                }
                
                // '출구 차선'으로 이동 가능한 모든 차선들(차선 변경 포함)을 유효 차선으로 지정
                for (Lane exitLane : exitLanes) {
                    validLanes.add(exitLane);
                    validLanes.addAll(currentRoad.getCanChangeLaneList(exitLane));
                }
            } else {
                // 마지막 도로인 경우: BFS가 찾았던 마지막 차선을 기준으로 변경 가능한 모든 차선 허용
                Lane lastLane = lanePath.get(lanePath.size() - 1);
                validLanes.add(lastLane);
                validLanes.addAll(currentRoad.getCanChangeLaneList(lastLane));
            }
            
            fullRoute.add(validLanes);
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
