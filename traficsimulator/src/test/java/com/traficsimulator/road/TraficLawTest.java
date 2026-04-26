package com.traficsimulator.road;

import com.traficsimulator.road.traficlight.TraficLightSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class TraficLawTest {

    @Test
    @DisplayName("1. 우회전 전용 신호가 있는 경우 테스트")
    void testRightTurnWithSpecificSignal() {
        // 신호등 설정: [RED, YELLOW, STRAIGHT, RIGHT]
        Set<TraficLightSignal> available = new HashSet<>(Arrays.asList(
            TraficLightSignal.RED, TraficLightSignal.YELLOW, TraficLightSignal.STRAIGHT, TraficLightSignal.RIGHT
        ));

        // 빨간불일 때: 우회전 신호가 없으면 정지 ❤️
        assertFalse(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.RED}, available, LaneType.RIGHT));
        
        // 빨간불 + 우회전 신호일 때: 통과! ❤️
        assertTrue(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.RED, TraficLightSignal.RIGHT}, available, LaneType.RIGHT));
        
        // 직진 신호만 있을 때: 우회전 전용 신호가 설정되어 있으므로 정지 (철저한 통제!) ❤️
        assertFalse(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, available, LaneType.RIGHT));
    }

    @Test
    @DisplayName("2. 우회전 전용 신호가 없는 경우 테스트")
    void testRightTurnWithoutSpecificSignal() {
        // 신호등 설정: [RED, YELLOW, STRAIGHT] (RIGHT 없음) ❤️
        Set<TraficLightSignal> available = new HashSet<>(Arrays.asList(
            TraficLightSignal.RED, TraficLightSignal.YELLOW, TraficLightSignal.STRAIGHT
        ));

        // 빨간불이어도 우회전 신호 기능이 없으면 언제든 통과 (자기의 철칙!) ❤️
        assertTrue(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.RED}, available, LaneType.RIGHT));
        
        // 직진 신호일 때도 당연히 통과
        assertTrue(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.STRAIGHT}, available, LaneType.RIGHT));
    }

    @Test
    @DisplayName("3. 기존 좌회전 및 유턴 로직 유지 확인")
    void testOtherRules() {
        Set<TraficLightSignal> available = new HashSet<>(Arrays.asList(
            TraficLightSignal.RED, TraficLightSignal.LEFT, TraficLightSignal.U_TURN
        ));

        // 빨간불 + 좌회전 신호 -> 좌회전 가능
        assertTrue(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.RED, TraficLightSignal.LEFT}, available, LaneType.LEFT));
        
        // 빨간불 + 유턴 신호 -> 유턴 가능
        assertTrue(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.RED, TraficLightSignal.U_TURN}, available, LaneType.U_TRUN));
        
        // 좌회전 신호만으로 유턴 시도 -> 이제는 불가능! ❤️
        assertFalse(TraficLaw.checkTraficLight(new TraficLightSignal[]{TraficLightSignal.LEFT}, available, LaneType.U_TRUN));
    }
}
