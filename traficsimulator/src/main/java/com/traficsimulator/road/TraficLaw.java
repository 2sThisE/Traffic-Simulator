package com.traficsimulator.road;

import com.traficsimulator.road.traficlight.TraficLightSignal;
import com.traficsimulator.vehicle.VehicleType;
import com.traficsimulator.road.camera.Camera;
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
        if (tls == null || tls.length == 0) return true; 

        Set<TraficLightSignal> currentSignals = new HashSet<>(Arrays.asList(tls));

        if (myPass == LaneType.RIGHT) {
            if (availableTls != null && availableTls.contains(TraficLightSignal.RIGHT)) {
                return currentSignals.contains(TraficLightSignal.RIGHT);
            }
            return true; 
        }

        if (currentSignals.contains(TraficLightSignal.RED)) {
            if (currentSignals.contains(TraficLightSignal.YELLOW)) {
                if (myPass == LaneType.LEFT) return true;
            }
            if (myPass == LaneType.LEFT && currentSignals.contains(TraficLightSignal.LEFT)) return true;
            if (myPass == LaneType.U_TRUN && currentSignals.contains(TraficLightSignal.U_TURN)) return true;
            return false;
        }

        if (myPass == LaneType.STRAIGHT && currentSignals.contains(TraficLightSignal.STRAIGHT)) return true;
        if (myPass == LaneType.LEFT && currentSignals.contains(TraficLightSignal.LEFT)) return true;
        if (myPass == LaneType.U_TRUN && currentSignals.contains(TraficLightSignal.U_TURN)) return true;

        if (currentSignals.contains(TraficLightSignal.YELLOW)) {
            if (myPass == LaneType.STRAIGHT) return true;
        }

        return false;
    }

    /**
     * 해당 차선에 특정 차종이 진입 가능한지 판단합니다. ❤️
     * @param lane 대상 차선
     * @param vehicleType 차량 종류
     * @return 통행 가능하면 true
     */
    public static boolean checkVehicleAccess(Lane lane, VehicleType vehicleType) {
        if (lane == null || vehicleType == null) return true;
        
        Set<VehicleType> allowed = lane.getAllowVehicle();
        // 허용 리스트가 비어있으면 모든 차종 통과 가능 (기본값)
        if (allowed == null || allowed.isEmpty()) return true;
        
        return allowed.contains(vehicleType);
    }

    /**
     * 과속 여부를 판단합니다. ❤️
     * @param camera 단속 카메라
     * @param currentSpeed 차량의 현재 속도 (km/h)
     * @return 과속이면 true, 아니면 false
     */
    public static boolean checkSpeedViolation(Camera camera, double currentSpeed) {
        if (camera == null) return false;
        
        int limit = camera.getLimitSpeed();
        
        // 자기가 말한 조건: 제한 속도가 0이면 단속 안 함! ❤️
        if (limit <= 0) return false;
        
        // 정지 상태(속도 0)는 무조건 세이프!
        if (currentSpeed <= 0) return false;
        
        return currentSpeed > limit;
    }
}
