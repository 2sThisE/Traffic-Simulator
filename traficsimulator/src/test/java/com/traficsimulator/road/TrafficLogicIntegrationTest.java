package com.traficsimulator.road;

import com.traficsimulator.road.traficlight.*;
import com.traficsimulator.util.GlobalTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TrafficLogicIntegrationTest {
    private JunctionController junctionController;
    private TraficLightController traficLightController;
    private GlobalTimer globalTimer;
    
    private Road roadA, roadB, roadC;
    private Lane laneA, laneB, laneC;

    @BeforeEach
    void setUp() {
        junctionController = new JunctionController();
        traficLightController = new TraficLightController();
        globalTimer = new GlobalTimer(1.0);
        globalTimer.addTickListener(traficLightController);

        // 1. 도로 생성 (직선, 일방통행) ❤️
        roadA = new Road(new Point2D.Double(0,0), new Point2D.Double(100,0), true);
        roadB = new Road(new Point2D.Double(110,0), new Point2D.Double(210,0), true);
        roadC = new Road(new Point2D.Double(220,0), new Point2D.Double(320,0), true);

        // 2. 차선 직접 추가 ❤️
        roadA.addLane(true, 0);
        roadB.addLane(true, 0);
        roadC.addLane(true, 0);

        laneA = roadA.getLaneList().get(0);
        laneB = roadB.getLaneList().get(0);
        laneC = roadC.getLaneList().get(0);

        // 3. 교차로 연결 설정
        junctionController.addConnection(laneA, laneB, LaneType.STRAIGHT); // Junction 1
        junctionController.addConnection(laneB, laneC, LaneType.STRAIGHT); // Junction 2
    }

    @Test
    @DisplayName("복합 동시 신호 및 황색/적색 신호 통행 로직 테스트")
    void testComplexSignalLogic() {
        // 1. 새로운 도로 Road D (좌회전용) 추가
        Road roadD = new Road(new Point2D.Double(100, -110), new Point2D.Double(100, -210), true);
        roadD.addLane(true, 0);
        Lane laneD = roadD.getLaneList().get(0);

        // 2. Junction 1에 좌회전 연결 추가 (laneA -> laneD)
        junctionController.addConnection(laneA, laneD, LaneType.LEFT);

        // 3. 복합 신호 설정
        TraficLight tl = new TraficLight();
        tl.addControlLane(laneA);
        
        List<SignalSetting> loop = new ArrayList<>();
        // Phase 1: 직진+좌회전 동시 (3틱)
        loop.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT, TraficLightSignal.LEFT}, 3));
        // Phase 2: 황색 신호 (1틱)
        loop.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.YELLOW}, 1));
        // Phase 3: 적색 신호 (2틱)
        loop.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 2));
        
        tl.addSignalLoop(loop);
        traficLightController.addTraficLight(tl);

        System.out.println("\n=== Complex Traffic Logic Test Start ===");

        for (int tick = 0; tick < 7; tick++) {
            TraficLightSignal[] signals = tl.currentSignal();
            String sigStr = java.util.Arrays.toString(signals);
            System.out.println(String.format("\n[Tick %d] Current Signal: %s", tick, sigStr));
            
            // 직진 의사 결정
            checkJunctionPassage("Straight Car", laneA, LaneType.STRAIGHT);
            // 좌회전 의사 결정
            checkJunctionPassage("Left-Turn Car", laneA, LaneType.LEFT);

            globalTimer.manualTick();
        }
        
        System.out.println("\n=== Complex Traffic Logic Test End ===");
    }

    /**
     * 특정 차선에서 다음 차선으로 넘어갈 수 있는지 로직을 시뮬레이션하고 출력합니다. ❤️
     */
    private void checkJunctionPassage(String name, Lane currentLane, LaneType desiredMove) {
        Set<LaneConnection> connections = junctionController.getConnectionList(currentLane);
        if (connections == null || connections.isEmpty()) {
            System.out.println(name + ": No outgoing connections.");
            return;
        }

        // 해당 차선을 관리하는 신호등 찾기
        TraficLight activeTL = null;
        for (TraficLight tl : traficLightController.getTraficLights()) {
            if (tl.getControlLaneList().contains(currentLane)) {
                activeTL = tl;
                break;
            }
        }

        boolean canPass = false;
        String reason = "";

        if (activeTL == null) {
            canPass = true;
            reason = "No traffic light (Free pass)";
        } else {
            TraficLightSignal[] currentSignals = activeTL.currentSignal();
            
            // 신호등 로직: RED가 있으면 무조건 정지, 그 외엔 원하는 방향 신호가 있는지 확인 ❤️
            boolean isRed = false;
            boolean moveSignalFound = false;
            
            for (TraficLightSignal sig : currentSignals) {
                if (sig == TraficLightSignal.RED) {
                    isRed = true;
                    break;
                }
                // LaneType과 TraficLightSignal 매핑
                if (sig.name().equals(desiredMove.name())) {
                    moveSignalFound = true;
                }
            }

            if (isRed) {
                canPass = false;
                reason = "RED signal active";
            } else if (moveSignalFound) {
                canPass = true;
                reason = "Signal matches move type: " + desiredMove;
            } else {
                canPass = false;
                reason = "Signal does not allow move type: " + desiredMove;
            }
        }

        System.out.println(String.format("%s: [%s] -> %s", name, (canPass ? "PASS" : "WAIT"), reason));
    }
}
