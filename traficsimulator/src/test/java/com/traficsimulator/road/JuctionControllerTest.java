package com.traficsimulator.road;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JuctionControllerTest {
    private JuctionController controller;
    private Road road1;
    private Road road2;
    private Road road3;

    @BeforeEach
    void setUp() {
        controller = new JuctionController();
        Point2D.Double p1 = new Point2D.Double(0, 0);
        Point2D.Double p2 = new Point2D.Double(100, 0);
        road1 = new Road(p1, p2, true); // 도로 1
        road2 = new Road(p1, p2, true); // 도로 2
        road3 = new Road(p1, p2, true); // 도로 3
        
        // 차선들을 미리 좀 만들어둘까요? (로직상 필수는 아니지만 실제 상황 모사)
        road1.addLane(true, 0);
        road1.addLane(true, 1);
        road2.addLane(true, 0);
        road3.addLane(true, 0);
    }

    @Test
    @DisplayName("빡센 조건 1: 한 차선에서 다중 목적지 연결 및 중복 방지")
    void testMultipleConnectionsFromOneLane() {
        // Road1의 0번 차선에서 Road2의 0번(직진)과 Road3의 0번(우회전)으로 연결
        controller.addConnection(road1, 0, road2, 0, LaneType.STRAIGHT);
        controller.addConnection(road1, 0, road3, 0, LaneType.RIGHT);
        
        // 중복 추가 시도 (Set이므로 추가되지 않아야 함)
        boolean isAddedAgain = controller.addConnection(road1, 0, road2, 0, LaneType.STRAIGHT);
        assertFalse(isAddedAgain, "이미 존재하는 연결은 추가되지 않아야 합니다.");

        Set<LaneConnection> connections = controller.getConnectionList(road1, 0);
        assertEquals(2, connections.size(), "연결은 총 2개여야 합니다.");
    }

    @Test
    @DisplayName("빡센 조건 2: 복합 수정(Edit) 시나리오 - 타겟이 정확히 일치해야 함")
    void testComplexEditScenario() {
        controller.addConnection(road1, 0, road2, 0, LaneType.STRAIGHT);
        
        LaneConnection wrongTarget = new LaneConnection(road2, 0, LaneType.RIGHT); // 타입이 다름
        LaneConnection newConn = new LaneConnection(road3, 0, LaneType.LEFT);
        
        // 잘못된 타겟으로 수정 시도
        boolean result = controller.editConnection(road1, 0, wrongTarget, newConn);
        assertFalse(result, "정확히 일치하는 연결 정보가 없으면 수정에 실패해야 합니다.");

        // 정확한 타겟으로 수정
        LaneConnection rightTarget = new LaneConnection(road2, 0, LaneType.STRAIGHT);
        boolean success = controller.editConnection(road1, 0, rightTarget, newConn);
        assertTrue(success);
        
        Set<LaneConnection> list = controller.getConnectionList(road1, 0);
        assertTrue(list.contains(newConn));
        assertFalse(list.contains(rightTarget));
    }

    @Test
    @DisplayName("빡센 조건 3: 삭제(Delete) 후 재생성 및 전체 삭제")
    void testDeleteAndConsistency() {
        controller.addConnection(road1, 0, road2, 0, LaneType.STRAIGHT);
        controller.addConnection(road1, 1, road2, 1, LaneType.STRAIGHT);

        // 특정 차선의 특정 연결 삭제
        assertTrue(controller.deleteConnection(road1, 0, new LaneConnection(road2, 0, LaneType.STRAIGHT)));
        assertEquals(0, controller.getConnectionList(road1, 0).size());
        
        // 다른 차선의 연결은 살아있어야 함
        assertNotNull(controller.getConnectionList(road1, 1));
        assertEquals(1, controller.getConnectionList(road1, 1).size());

        // 전체 삭제 (target = null)
        assertTrue(controller.deleteConnection(road1, 1, null));
        assertNull(controller.getConnectionList(road1, 1), "전체 삭제 시 Map에서 Key 자체가 사라져야 합니다.");
    }

    @Test
    @DisplayName("빡센 조건 4: 존재하지 않는 도로/차선에 대한 방어 로직")
    void testEmptyScenarios() {
        // 아무것도 없는 상태에서 조회
        assertNull(controller.getConnectionList(road1, 99));
        
        // 아무것도 없는 상태에서 삭제 시도
        assertFalse(controller.deleteConnection(road1, 99, null));
        
        // 아무것도 없는 상태에서 수정 시도
        assertFalse(controller.editConnection(road1, 0, 
                new LaneConnection(road2, 0, LaneType.STRAIGHT), 
                new LaneConnection(road3, 0, LaneType.STRAIGHT)));
    }
}
