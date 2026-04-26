package com.traficsimulator.road.camera;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.Road;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CameraTest {
    private Road road;
    private Lane lane0, lane1, lane2;

    @BeforeEach
    void setUp() {
        // 1. 3차선 일방통행 직선 도로 생성 (X: 0 -> 100)
        road = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), true);
        road.setLaneWidth(40.0);
        
        road.addLane(true, 0); 
        road.addLane(true, 1); 
        road.addLane(true, 2); 
        
        road.refresh(); 

        lane0 = road.getLaneList().get(0);
        lane1 = road.getLaneList().get(1);
        lane2 = road.getLaneList().get(2);
    }

    @Test
    @DisplayName("카메라 생성 시 관련 차선 자동 등록 및 중앙 정렬 테스트")
    void testCameraInitializationAndCentering() {
        // 카메라를 정지선(X=100) 근처에 설치 ❤️
        Point2D.Double initialPos = new Point2D.Double(100, 50);
        Camera camera = new Camera(road, initialPos, lane1);

        // 1. 감시 차선 개수 확인
        assertEquals(3, camera.getTargetLaneMap().size());

        // 2. 단속 지점이 X=100으로 잘 잡혔는지 확인 (정지선 근처에 설치했으므로!) ❤️
        for (Point2D.Double snapPt : camera.getTargetLaneMap().values()) {
            assertEquals(100.0, snapPt.x, 0.001, "단속 지점은 X=100(정지선)에 위치해야 합니다.");
        }

        // 3. 중앙 위치 계산 확인
        // lane0 정지선: (100, -40), lane1: (100, 0), lane2: (100, 40)
        // 평균 Y: (-40+0+40)/3 = 0
        Point2D.Double center = camera.getLoc();
        assertEquals(100.0, center.x, 0.001);
        assertEquals(0.0, center.y, 0.001, "카메라는 정지선들의 정중앙(100, 0)으로 이동해야 합니다.");
    }

    @Test
    @DisplayName("차선 제거 시 카메라 위치 자동 재조정 테스트")
    void testCameraReCenteringOnLaneRemoval() {
        // 정지선 근처 설치
        Camera camera = new Camera(road, new Point2D.Double(100, 50), lane1);
        
        // 초기 중앙 위치는 Y=0
        assertEquals(0.0, camera.getLoc().y, 0.001);

        // lane0(Y=-40)을 제거 ❤️
        camera.removeTargetLane(lane0);

        // 남은 lane1(0), lane2(40)의 중앙 Y는 (0+40)/2 = 20
        assertEquals(20.0, camera.getLoc().y, 0.001, "차선 제거 후 중앙 위치가 20.0으로 업데이트되어야 합니다.");
    }

    @Test
    @DisplayName("도로 이동 시 단속 지점 갱신 및 재배치 테스트")
    void testRoadMovementResponse() {
        Camera camera = new Camera(road, new Point2D.Double(100, 50), lane1);
        
        // 도로를 Y축으로 100 이동
        road.move(0, 100);
        
        // 단속 지점 갱신 호출
        camera.refreshEnforcementPoints();

        // 새 정지선들: (100, 60), (100, 100), (100, 140)
        // 평균 Y: 100
        assertEquals(100.0, camera.getLoc().y, 0.001);
    }

    @Test
    @DisplayName("카메라 수동 위치 변경 시 단속 지점 스냅 테스트")
    void testManualLocationUpdateAndSnapping() {
        Camera camera = new Camera(road, new Point2D.Double(100, 0), lane1);
        
        // 카메라를 도로 시작점(X=0) 근처로 수동 이동 ❤️
        camera.setLoc(new Point2D.Double(0, 0));

        // 이제 단속 지점들이 X=0 지점으로 다시 스냅되어야 함
        for (Point2D.Double snapPt : camera.getTargetLaneMap().values()) {
            assertEquals(0.0, snapPt.x, 0.001, "카메라 이동 후 단속 지점은 시작점(X=0)으로 스냅되어야 합니다.");
        }
    }
}
