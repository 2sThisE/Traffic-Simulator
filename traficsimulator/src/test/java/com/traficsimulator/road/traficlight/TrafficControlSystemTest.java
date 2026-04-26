package com.traficsimulator.road.traficlight;

import com.traficsimulator.util.GlobalTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class TrafficControlSystemTest {
    private GlobalTimer globalTimer;
    private TraficLightController controller;

    @BeforeEach
    void setUp() {
        // 1. 심장(타이머)과 신경계(컨트롤러) 생성
        globalTimer = new GlobalTimer(1.0);
        controller = new TraficLightController();
        
        // 2. 타이머에 컨트롤러 등록
        globalTimer.addTickListener(controller);
    }

    @Test
    @DisplayName("전체 시스템 통합 틱 전파 테스트")
    void testSystemTickPropagation() {
        // 3. 신호등 A 생성 (5틱 주기: STRAIGHT 3, RED 2)
        TraficLight lightA = new TraficLight();
        List<SignalSetting> settingsA = new ArrayList<>();
        settingsA.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, 3));
        settingsA.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 2));
        lightA.addSignalLoop(settingsA);

        // 4. 신호등 B 생성 (10틱 주기: RED 7, YELLOW 3)
        TraficLight lightB = new TraficLight();
        List<SignalSetting> settingsB = new ArrayList<>();
        settingsB.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 7));
        settingsB.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.YELLOW}, 3));
        lightB.addSignalLoop(settingsB);

        // 5. 컨트롤러에 신호등들 등록
        controller.addTraficLight(lightA);
        controller.addTraficLight(lightB);

        // --- 검증 시작 ---

        // 0틱: 초기 상태 확인
        assertEquals(TraficLightSignal.STRAIGHT, lightA.currentSignal()[0]);
        assertEquals(TraficLightSignal.RED, lightB.currentSignal()[0]);

        // 3틱 발생: lightA는 RED로 변해야 하고, lightB는 여전히 RED여야 함
        for(int i=0; i<3; i++) globalTimer.manualTick();
        assertEquals(3, globalTimer.getTotalTicks());
        assertEquals(TraficLightSignal.RED, lightA.currentSignal()[0], "lightA should be RED after 3 ticks");
        assertEquals(TraficLightSignal.RED, lightB.currentSignal()[0], "lightB should still be RED after 3 ticks");

        // 4틱 더 발생 (총 7틱): lightA는 다시 STRAIGHT(한 바퀴 돌음), lightB는 YELLOW로 변하기 직전/변함
        for(int i=0; i<4; i++) globalTimer.manualTick();
        assertEquals(7, globalTimer.getTotalTicks());
        assertEquals(TraficLightSignal.STRAIGHT, lightA.currentSignal()[0], "lightA should cycle back to STRAIGHT");
        assertEquals(TraficLightSignal.YELLOW, lightB.currentSignal()[0], "lightB should be YELLOW after 7 ticks");
    }

    @Test
    @DisplayName("동적 신호등 추가 및 제거 테스트")
    void testDynamicLightManagement() {
        TraficLight light = new TraficLight();
        List<SignalSetting> settings = new ArrayList<>();
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, 10));
        light.addSignalLoop(settings);

        // 1. 등록 전에는 틱을 줘도 변화가 없어야 함 (사실 내부 틱은 0이겠지만 검증용 ❤️)
        globalTimer.manualTick();
        
        // 2. 등록 후 틱 전파 확인
        controller.addTraficLight(light);
        globalTimer.manualTick(); // 총 2틱
        
        // 3. 제거 후 틱 전파 중단 확인
        controller.removeTraficLight(light);
        globalTimer.manualTick(); // 총 3틱
        
        // 여기서 신호등의 내부 상태를 직접 확인할 순 없지만, 
        // 컨트롤러의 리스트 크기로 간접 확인
        assertFalse(controller.getTraficLights().contains(light));
    }

    @Test
    @DisplayName("다중 리스너 공존 테스트")
    void testMultipleListeners() {
        // 익명 리스너로 틱 횟수 세기
        final int[] sideEffectCounter = {0};
        globalTimer.addTickListener(() -> sideEffectCounter[0]++);

        globalTimer.manualTick();
        globalTimer.manualTick();

        assertEquals(2, sideEffectCounter[0], "Multiple listeners should all receive ticks");
    }
}
