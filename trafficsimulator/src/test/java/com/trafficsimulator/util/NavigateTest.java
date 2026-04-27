package com.trafficsimulator.util;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

class NavigateTest {

    private RoadManager roadManager;
    private JunctionController junctionController;

    @BeforeEach
    void setUp() {
        roadManager = new RoadManager();
        junctionController = new JunctionController();
    }

    @Test
    void testFindGlobalEndpoints_IsolatedRoad() {
        // Given: 고립된 직선 도로 하나 (0,0) -> (100,0)
        Road road = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        road.addLane(false, 0); // 하행 (시작 차선 후보)
        road.addLane(true, 1);  // 상행 (끝 차선 후보)
        roadManager.addRoad(road);

        // When
        Navigate.GlobalEndpoints endpoints = Navigate.findGlobalEndpoints(roadManager, junctionController);

        // Then
        assertFalse(endpoints.startLanes.isEmpty(), "시작 차선이 존재해야 합니다.");
        assertFalse(endpoints.endLanes.isEmpty(), "끝 차선이 존재해야 합니다.");
        
        // Lane 객체가 정확히 들어갔는지 확인
        assertTrue(endpoints.startLanes.contains(road.getLane(0)));
        assertTrue(endpoints.endLanes.contains(road.getLane(1)));
    }

    @Test
    void testFindGlobalEndpoints_ConnectedRoads() {
        // Given: 도로 1 (0,0 -> 100,0) 과 도로 2 (100,0 -> 200,0)
        Road road1 = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        road1.addLane(true, 0); // 상행
        
        Road road2 = new Road(new Point2D.Double(100, 0), new Point2D.Double(200, 0), false);
        road2.addLane(true, 0); // 상행
        
        roadManager.addRoad(road1);
        roadManager.addRoad(road2);

        // 도로 1의 차선을 도로 2의 차선으로 연결
        junctionController.addConnection(road1.getLane(0), road2.getLane(0), LaneType.STRAIGHT);

        // When
        Navigate.GlobalEndpoints endpoints = Navigate.findGlobalEndpoints(roadManager, junctionController);

        // Then
        // 도로 1의 차선은 연결이 있으므로 끝 차선이 되면 안 됨
        assertFalse(endpoints.endLanes.contains(road1.getLane(0)), "연결된 도로 1의 차선은 목록에 없어야 합니다.");
        // 도로 2의 차선은 연결이 없으므로 끝 차선이어야 함
        assertTrue(endpoints.endLanes.contains(road2.getLane(0)), "연결이 없는 도로 2의 차선은 목록에 있어야 합니다.");
    }
}
