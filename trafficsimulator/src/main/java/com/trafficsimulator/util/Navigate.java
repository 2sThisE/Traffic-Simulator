package com.trafficsimulator.util;

import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.ui.RoadManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 시뮬레이션 내 경로 탐색 및 도로 네트워크 분석을 담당하는 클래스입니다.
 */
public class Navigate {

    /**
     * 시작 차선과 끝 차선을 담기 위한 내부 클래스입니다.
     */
    public static class GlobalEndpoints {
        public final Set<Lane> startLanes = new HashSet<>();
        public final Set<Lane> endLanes = new HashSet<>();
    }

    /**
     * 도로 전체에서 더 이상 연결된 도착점이 없는 차선을 찾아 시작 차선과 끝 차선을 반환합니다.
     * @param roadManager 도로 목록 관리자
     * @param junctionController 차선 연결 정보 컨트롤러
     * @return 시작 차선과 끝 차선이 담긴 GlobalEndpoints 객체
     */
    public static GlobalEndpoints findGlobalEndpoints(RoadManager roadManager, JunctionController junctionController) {
        GlobalEndpoints endpoints = new GlobalEndpoints();
        List<Road> roadList = roadManager.getRoadList();

        for (Road road : roadList) {
            for (Lane lane : road.getLaneList()) {
                // 해당 차선에서 나가는 연결 정보 확인
                Set<LaneConnection> connections = junctionController.getConnectionList(lane);

                // 더 이상 연결된 도착점이 없는 경우 (연결 정보가 null이거나 비어있음)
                if (connections == null || connections.isEmpty()) {
                    // 하행(false)이면 시작 차선, 상행(true)이면 끝 차선으로 판단
                    if (!lane.isRoadDirection()) {
                        endpoints.startLanes.add(lane);
                    } else {
                        endpoints.endLanes.add(lane);
                    }
                }
            }
        }
        return endpoints;
    }
}
