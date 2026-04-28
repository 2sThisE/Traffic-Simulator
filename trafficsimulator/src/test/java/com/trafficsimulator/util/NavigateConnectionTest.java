package com.trafficsimulator.util;

import com.trafficsimulator.road.*;
import com.trafficsimulator.ui.RoadManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NavigateConnectionTest {

    private RoadManager roadManager;
    private JunctionController junctionController;

    @BeforeEach
    void setUp() {
        roadManager = new RoadManager();
        junctionController = new JunctionController();
    }

    @Test
    void testBidirectionalRoadConnection() {
        // Given: 두 개의 왕복 4차선 도로 생성
        Road road1 = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        road1.addLane(false, 0); road1.addLane(false, 1); // 하행
        road1.addLane(true, 2);  road1.addLane(true, 3);  // 상행
        
        Road road2 = new Road(new Point2D.Double(100, 0), new Point2D.Double(200, 0), false);
        road2.addLane(false, 0); road2.addLane(false, 1); // 하행
        road2.addLane(true, 2);  road2.addLane(true, 3);  // 상행

        roadManager.addRoad(road1);
        roadManager.addRoad(road2);

        // 상행 연결: Road 1 -> Road 2
        junctionController.addConnection(road1.getLane(2), road2.getLane(2), LaneType.STRAIGHT);
        junctionController.addConnection(road1.getLane(3), road2.getLane(3), LaneType.STRAIGHT);

        // 하행 연결: Road 2 -> Road 1
        junctionController.addConnection(road2.getLane(0), road1.getLane(0), LaneType.STRAIGHT);
        junctionController.addConnection(road2.getLane(1), road1.getLane(1), LaneType.STRAIGHT);

        // When
        Navigate.GlobalEndpoints endpoints = Navigate.findGlobalEndpoints(roadManager, junctionController);

        // Then: 수정된 로직에서는 방향별로 DeadEnd를 판단함
        // Road 2의 상행 차선(2,3)은 해당 방향으로 나가는 연결이 없으므로 목적지여야 함
        assertTrue(endpoints.endLanes.contains(road2.getLane(2)), "Road 2 Lane 2 should now be an end lane");
        assertTrue(endpoints.endLanes.contains(road2.getLane(3)), "Road 2 Lane 3 should now be an end lane");
        
        // Road 1의 하행 차선(0,1)도 목적지여야 함
        assertTrue(endpoints.endLanes.contains(road1.getLane(0)), "Road 1 Lane 0 should now be an end lane");

        // 경로 탐색 확인
        List<Set<Lane>> upwardRoute = Navigate.calculateRoute(road1.getLane(2), roadManager, junctionController);
        assertNotNull(upwardRoute, "Route should now be found for bidirectional roads");
        assertEquals(2, upwardRoute.size());
        
        List<Set<Lane>> downwardRoute = Navigate.calculateRoute(road2.getLane(0), roadManager, junctionController);
        assertNotNull(downwardRoute, "Downward route should also be found");
        assertEquals(2, downwardRoute.size());
    }
}
