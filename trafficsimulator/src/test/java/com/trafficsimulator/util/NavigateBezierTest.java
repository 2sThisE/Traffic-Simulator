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

class NavigateBezierTest {

    private RoadManager roadManager;
    private JunctionController junctionController;

    @BeforeEach
    void setUp() {
        roadManager = new RoadManager();
        junctionController = new JunctionController();
    }

    @Test
    void testLaneChangeAndJunctionConnection() {
        // 1. 도로 1 생성: (0,0) -> (500,0), 상행 5개 차선
        Road road1 = new Road(new Point2D.Double(0, 0), new Point2D.Double(500, 0), false);
        for (int i = 0; i < 5; i++) {
            road1.addLane(true, i); 
        }
        roadManager.addRoad(road1);

        // 2. 도로 2 생성: (500, 0)에서 좌회전해서 위로 (500, -200)
        Road road2 = new Road(new Point2D.Double(500, 0), new Point2D.Double(500, -200), false);
        road2.addLane(true, 0); 
        roadManager.addRoad(road2);

        // 3. 연결 설정: 도로 1의 1차선(index 0)에서 도로 2의 1차선으로 좌회전 연결
        Lane road1Lane1 = road1.getLane(0); 
        Lane road1Lane5 = road1.getLane(4); 
        Lane road2Lane1 = road2.getLane(0);
        junctionController.addConnection(road1Lane1, road2Lane1, LaneType.LEFT);

        System.out.println("========== CASE: Start from Lane 5 (Lane Changes + Left Turn) ==========");
        
        // 1. 경로 계산
        List<Set<Lane>> route = Navigate.calculateRoute(road1Lane5, roadManager, junctionController);

        assertNotNull(route, "경로가 반드시 찾아져야 합니다!");
        System.out.println("Route Phase Count: " + route.size());

        // 2. 전체 포인트 생성
        List<Point2D.Double> totalPoints = Navigate.generateTotalPathPoints(route, junctionController);
        System.out.println("Total Path Points generated: " + totalPoints.size());

        // 3. 검증
        assertTrue(route.size() >= 2, "도로 1과 도로 2를 모두 거쳐야 합니다.");
        
        // 마지막 포인트가 도로 2의 끝점인지 확인
        Point2D.Double lastPoint = totalPoints.get(totalPoints.size() - 1);
        assertEquals(500.0, lastPoint.x, 1.0);
        assertEquals(-200.0, lastPoint.y, 1.0);
        
        System.out.println("Result: SUCCESS! Path from Lane 5 to Road 2 through Lane Changes found.");
        System.out.printf("  Destination reached: (%.1f, %.1f)%n", lastPoint.x, lastPoint.y);
    }
}
