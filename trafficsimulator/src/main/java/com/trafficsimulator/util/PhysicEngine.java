package com.trafficsimulator.util;

/**
 * 차량의 물리 법칙(가감속 등)을 계산하는 엔진 클래스입니다. ❤️
 */
public class PhysicEngine {

    /**
     * 현재 속도와 가속력을 바탕으로 다음 틱의 속도를 계산합니다.
     * @param currentSpeedKmh 현재 속도 (km/h)
     * @param accelerationMs2 가속도 (m/s^2, 감속일 경우 음수)
     * @return 계산된 속도 (km/h)
     */
    public static double calculateSpeed(double currentSpeedKmh, double accelerationMs2) {
        double currentSpeedMs = UnitConverter.kmhToMs(currentSpeedKmh);
        
        // v = v0 + a * t (t = GlobalTimer.TICKLATE)
        double newSpeedMs = currentSpeedMs + (accelerationMs2 * GlobalTimer.TICKLATE);
        
        // 정지 임계값 처리: 약 0.1 km/h 미만은 정지로 간주 ❤️
        if (newSpeedMs < 0.280) { 
            return 0.0;
        }
        
        return UnitConverter.msToKmh(newSpeedMs);
    }

    /**
     * 가속도가 있는 상황에서 한 틱 동안 이동한 거리(meter)를 계산합니다.
     * @param currentSpeedKmh 현재 속도 (km/h)
     * @param accelerationMs2 가속도 (m/s^2)
     * @return 이동 거리 (m)
     */
    public static double calculateDistance(double currentSpeedKmh, double accelerationMs2) {
        double v0 = UnitConverter.kmhToMs(currentSpeedKmh);
        double t = GlobalTimer.TICKLATE;
        
        // s = v0*t + 0.5 * a * t^2
        return (v0 * t) + (0.5 * accelerationMs2 * t * t);
    }

    /**
     * 특정 속도에서 감속하여 정지할 때까지 필요한 거리(meter)를 계산합니다.
     * @param currentSpeedKmh 현재 속도 (km/h)
     * @param decelerationMs2 감속도 (m/s^2, 양수 값 입력)
     * @return 정지 거리 (m)
     */
    public static double calculateStoppingDistance(double currentSpeedKmh, double decelerationMs2) {
        if (decelerationMs2 <= 0) return Double.MAX_VALUE;
        
        double v = UnitConverter.kmhToMs(currentSpeedKmh);
        // s = v^2 / (2 * a)
        return (v * v) / (2 * decelerationMs2);
    }

    /**
     * 특정 거리 내에서 목표 속도에 도달하기 위해 필요한 가속도를 계산합니다.
     * @param currentSpeedKmh 현재 속도 (km/h)
     * @param targetSpeedKmh 목표 속도 (km/h)
     * @param distanceM 남은 거리 (m)
     * @return 필요한 가속도 (m/s^2)
     */
    public static double calculateRequiredAcceleration(double currentSpeedKmh, double targetSpeedKmh, double distanceM) {
        if (distanceM <= 0) return -100.0; // 이미 지났거나 너무 가까우면 급감속
        
        double v0 = UnitConverter.kmhToMs(currentSpeedKmh);
        double vt = UnitConverter.kmhToMs(targetSpeedKmh);
        
        // a = (vt^2 - v0^2) / (2 * s)
        return (vt * vt - v0 * v0) / (2 * distanceM);
    }

    /**
     * 타겟(앞차 등)과의 거리와 상대 속도를 고려하여, 충돌을 피하기 위해 필요한 감속도를 계산합니다.
     * @param mySpeedKmh 내 차량 속도 (km/h)
     * @param targetSpeedKmh 타겟 차량 속도 (km/h)
     * @param distanceM 타겟과의 거리 (m)
     * @return 필요한 감속도 (m/s^2, 브레이크를 밟아야 할 양으로 양수 값 반환)
     */
    public static double calculateRequiredBraking(double mySpeedKmh, double targetSpeedKmh, double distanceM) {
        if (distanceM <= 0.5) return 100.0; // 0.5m 이내면 즉시 급제동

        double v0 = UnitConverter.kmhToMs(mySpeedKmh);
        double vt = UnitConverter.kmhToMs(targetSpeedKmh);

        // 내 속도가 타겟보다 느리거나 같으면 브레이크를 밟을 필요 없음
        if (v0 <= vt) return 0.0;

        // vt^2 = v0^2 - 2ad 공식을 변형
        // a = (v0^2 - vt^2) / (2 * distance)
        double requiredDecel = (v0 * v0 - vt * vt) / (2 * distanceM);

        return Math.max(0, requiredDecel);
    }
}
