package com.traficsimulator.road.traficlight;

import com.traficsimulator.road.Lane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class TraficLightTest {
    private TraficLight traficLight;

    @BeforeEach
    void setUp() {
        traficLight = new TraficLight();
    }

    @Test
    @DisplayName("기본 신호 루프 및 순환 테스트")
    void testSignalLoop() {
        List<SignalSetting> settings = new ArrayList<>();
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, 10));
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.YELLOW}, 3));
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 7));
        
        traficLight.addSignalLoop(settings); // 총 20틱

        // 1. 첫 페이즈 (0~9)
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);
        for(int i=0; i<9; i++) traficLight.nextTick();
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);

        // 2. 두 번째 페이즈 (10~12)
        traficLight.nextTick();
        assertEquals(TraficLightSignal.YELLOW, traficLight.currentSignal()[0]);
        traficLight.nextTick();
        traficLight.nextTick();
        assertEquals(TraficLightSignal.YELLOW, traficLight.currentSignal()[0]);

        // 3. 세 번째 페이즈 (13~19)
        traficLight.nextTick();
        assertEquals(TraficLightSignal.RED, traficLight.currentSignal()[0]);
        for(int i=0; i<6; i++) traficLight.nextTick();
        assertEquals(TraficLightSignal.RED, traficLight.currentSignal()[0]);

        // 4. 다시 첫 페이즈로 순환 (20 -> 0)
        traficLight.nextTick();
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);
    }

    @Test
    @DisplayName("점프 틱 스트레스 테스트")
    void testJumpTick() {
        List<SignalSetting> settings = new ArrayList<>();
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, 10));
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 10));
        traficLight.addSignalLoop(settings); // 총 20틱

        // 15틱 점프 (RED 페이즈 중간이어야 함)
        traficLight.jmpTick(15);
        assertEquals(TraficLightSignal.RED, traficLight.currentSignal()[0]);

        // 다시 10틱 점프 (총 25틱 -> 5틱, STRAIGHT 페이즈 중간이어야 함)
        traficLight.jmpTick(10);
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);

        // 아주 큰 값 점프 (1000틱 -> 20의 배수이므로 제자리여야 함)
        traficLight.resetCurrentTick();
        traficLight.jmpTick(1000);
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);
    }

    @Test
    @DisplayName("동적 루프 수정 및 안정성 테스트")
    void testDynamicModification() {
        List<SignalSetting> settings = new ArrayList<>();
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, 10));
        settings.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 10));
        traficLight.addSignalLoop(settings);

        // 15틱 진행 (현재 RED)
        traficLight.jmpTick(15);
        assertEquals(TraficLightSignal.RED, traficLight.currentSignal()[0]);

        // 갑자기 루프에서 RED를 삭제 (STRAIGHT만 남음)
        traficLight.deleteSignalLoop(1);
        
        // 0틱(STRAIGHT)으로 싱크되어야 함
        assertEquals(TraficLightSignal.STRAIGHT, traficLight.currentSignal()[0]);
    }

    @Test
    @DisplayName("빈 신호 루프 방어 로직 테스트")
    void testEmptyLoop() {
        // 루프가 없을 때 RED가 기본값인지 확인 (자기 코드 로직 확인용 ❤️)
        assertArrayEquals(new TraficLightSignal[]{TraficLightSignal.RED}, traficLight.currentSignal());
        
        // 0틱 상황에서 nextTick 호출 시 에러가 안 나는지
        assertDoesNotThrow(() -> traficLight.nextTick());
        assertDoesNotThrow(() -> traficLight.jmpTick(100));
    }
}
