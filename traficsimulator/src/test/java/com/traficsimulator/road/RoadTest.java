package com.traficsimulator.road;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadTest {
    private Road road;
    private Point2D.Double start = new Point2D.Double(0, 0);
    private Point2D.Double end = new Point2D.Double(100, 0);

    @BeforeEach
    void setUp() {
        // 일방통행 도로로 초기화 (기본값)
        road = new Road(start, end, true);
    }

    @Test
    @DisplayName("일방통행 도로에서 모든 차선이 같은 방향일 때 변경 가능한 차선 리스트 테스트")
    void testGetCanChangeLaneListOneWay() {
        // 3개의 차선 추가 (모두 상행: true)
        road.addLane(true, 0);
        road.addLane(true, 1);
        road.addLane(true, 2);

        // 1번 차선(중간)에서 변경 가능한 차선 확인
        Lane middleLane = road.getLane(1);
        List<Lane> canChangeLanes = road.getCanChangeLaneList(middleLane);

        // 자기 자신을 포함하여 총 3개의 차선이 반환되어야 함
        assertEquals(3, canChangeLanes.size());
        assertTrue(canChangeLanes.contains(road.getLane(0)));
        assertTrue(canChangeLanes.contains(road.getLane(1)));
        assertTrue(canChangeLanes.contains(road.getLane(2)));
    }

    @Test
    @DisplayName("왕복 도로에서 방향이 다른 차선이 섞여 있을 때 테스트")
    void testGetCanChangeLaneListTwoWay() {
        // 차선 구성: [0: true, 1: true, 2: false, 3: false]
        road.addLane(true, 0);
        road.addLane(true, 1);
        road.addLane(false, 2);
        road.addLane(false, 3);

        // 1번 차선(true)에서 변경 가능한 차선 확인
        List<Lane> canChangeFrom1 = road.getCanChangeLaneList(1);
        assertEquals(2, canChangeFrom1.size(), "상행 차선은 2개여야 합니다.");
        assertTrue(canChangeFrom1.contains(road.getLane(0)));
        assertTrue(canChangeFrom1.contains(road.getLane(1)));
        assertFalse(canChangeFrom1.contains(road.getLane(2)));

        // 2번 차선(false)에서 변경 가능한 차선 확인
        List<Lane> canChangeFrom2 = road.getCanChangeLaneList(2);
        assertEquals(2, canChangeFrom2.size(), "하행 차선은 2개여야 합니다.");
        assertTrue(canChangeFrom2.contains(road.getLane(2)));
        assertTrue(canChangeFrom2.contains(road.getLane(3)));
        assertFalse(canChangeFrom2.contains(road.getLane(0)));
    }

    @Test
    @DisplayName("차선 번호로 변경 가능한 리스트를 가져오는 기능 테스트")
    void testGetCanChangeLaneListByIndex() {
        road.addLane(true, 0);
        road.addLane(true, 1);
        
        List<Lane> result = road.getCanChangeLaneList(0);
        assertEquals(2, result.size());
        assertEquals(road.getLane(0), result.get(0));
        assertEquals(road.getLane(1), result.get(1));
    }
}
