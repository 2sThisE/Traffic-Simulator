package com.trafficsimulator.util;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class NavigateRefreshTest {

    private RoadManager roadManager;
    private JunctionController junctionController;

    @BeforeEach
    void setUp() {
        roadManager = new RoadManager();
        junctionController = new JunctionController();
    }

    @Test
    void testNavigateAfterRefresh() {
        // 1. 도로 A 생성 (0,0 -> 100,0)
        Road roadA = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        roadA.addLane(true, 0); // 우측 방향 차선
        
        // 2. 도로 B 생성 (100,0 -> 200,0)
        Road roadB = new Road(new Point2D.Double(100, 0), new Point2D.Double(200, 0), false);
        roadB.addLane(true, 0);
        
        roadManager.addRoad(roadA);
        roadManager.addRoad(roadB);
        
        Lane laneA = roadA.getLane(0);
        Lane laneB = roadB.getLane(0);
        
        // 3. 연결 추가 (A -> B)
        junctionController.addConnection(laneA, laneB, LaneType.STRAIGHT);
        
        // 4. 리프레시 전 테스트 (경로가 안 잡힐 수 있음)
        List<Set<Lane>> routeBefore = Navigate.calculateRoute(laneA, roadManager, junctionController);
        System.out.println("Route before refresh found: " + (routeBefore != null));
        
        // 5. 전체 리프레시 실행 (SimulatorController의 로직 재현)
        for (Road road : roadManager.getRoadList()) {
            for (Lane lane : road.getLaneList()) {
                junctionController.refreshConnections(lane);
            }
        }
        
        // 6. 리프레시 후 테스트
        List<Set<Lane>> routeAfter = Navigate.calculateRoute(laneA, roadManager, junctionController);
        
        assertNotNull(routeAfter, "Route should be found after refresh");
        assertTrue(routeAfter.size() >= 2, "Route should contain at least 2 road phases");
        System.out.println("Route after refresh found! Size: " + routeAfter.size());
    }
}
