package com.traficsimulator.road;

import com.traficsimulator.road.traficlight.TraficLightSignal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 도로교통법 및 통행 규칙을 관리하는 클래스입니다. ❤️
 */
public class TraficLaw {

    private TraficLaw() {}

    /**
     * 현재 신호 상태와 차량의 통행 의사에 따라 통행 가능 여부를 판단합니다.
     * @param tls 현재 신호등에 켜진 신호 리스트
     * @param availableTls 신호등이 낼 수 있는 모든 신호 종류 (설정값) ❤️
     * @param myPass 차량이 가고자 하는 방향 (LaneType)
     * @return 통행 가능하면 true, 불가능하면 false
     */
    public static boolean checkTraficLight(TraficLightSignal[] tls, Set<TraficLightSignal> availableTls, LaneType myPass) {
        if (tls == null || tls.length == 0) return true; // 신호등 없으면 무정차 통과

        Set<TraficLightSignal> currentSignals = new HashSet<>(Arrays.asList(tls));

        // --- 우회전 특별법 처리 ❤️ ---
        if (myPass == LaneType.RIGHT) {
            // 1. 신호등에 우회전 신호 기능이 있는 경우
            if (availableTls != null && availableTls.contains(TraficLightSignal.RIGHT)) {
                // 반드시 우회전 신호가 켜져 있어야만 통과 가능!
                return currentSignals.contains(TraficLightSignal.RIGHT);
            }
            // 2. 우회전 신호 기능이 없는 경우
            else {
                // 자기가 말한 "모든 통행 가능" 로직 적용 ❤️
                return true; 
            }
        }

        // --- 나머지 일반 법규 ---
        
        // 1. 빨간불(RED)이 켜져 있는 경우
        if (currentSignals.contains(TraficLightSignal.RED)) {
            if (currentSignals.contains(TraficLightSignal.YELLOW)) {
                if (myPass == LaneType.LEFT) return true;
            }
            if (myPass == LaneType.LEFT && currentSignals.contains(TraficLightSignal.LEFT)) return true;
            if (myPass == LaneType.U_TRUN && currentSignals.contains(TraficLightSignal.U_TURN)) return true;
            
            return false;
        }

        // 2. 초록불/화살표 신호가 있는 경우 (RED가 없는 상태)
        if (myPass == LaneType.STRAIGHT && currentSignals.contains(TraficLightSignal.STRAIGHT)) return true;
        if (myPass == LaneType.LEFT && currentSignals.contains(TraficLightSignal.LEFT)) return true;
        if (myPass == LaneType.U_TRUN && currentSignals.contains(TraficLightSignal.U_TURN)) return true;

        // 3. 노란불(YELLOW)만 켜져 있는 경우 (빨간불 없이)
        if (currentSignals.contains(TraficLightSignal.YELLOW)) {
            if (myPass == LaneType.STRAIGHT) return true;
        }

        return false;
    }
}
