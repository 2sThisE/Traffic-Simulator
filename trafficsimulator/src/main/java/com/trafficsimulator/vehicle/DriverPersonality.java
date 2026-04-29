package com.trafficsimulator.vehicle;

/**
 * 운전자의 주행 성향을 정의하는 클래스입니다. ❤️
 */
public enum DriverPersonality {
    /**
     * 모범 운전자: 도로 규정 속도 엄격 준수, 안전거리 100% 유지, 법규 위반 없음.
     */
    MODEL(1.0, 0.0, true),

    /**
     * 평균 운전자: 도로 규정보다 최대 20km/h 과속(카메라만 조심), 안전거리 30% 유지.
     */
    AVERAGE(0.3, 20.0, false),

    /**
     * 난폭 운전자: 초과속 주행, 안전거리 10%만 유지, 공격적 주행.
     */
    AGGRESSIVE(0.1, 100.0, false);

    private final double safetyDistanceRatio; // 권장 안전거리 대비 실제 유지 비율
    private final double maxSpeedOffset;      // 규정 속도 대비 추가 허용 속도 (km/h)
    private final boolean strictLawAdherence; // 도로 규정 속도 엄격 준수 여부 (false면 카메라만 신경 씀)

    DriverPersonality(double safetyDistanceRatio, double maxSpeedOffset, boolean strictLawAdherence) {
        this.safetyDistanceRatio = safetyDistanceRatio;
        this.maxSpeedOffset = maxSpeedOffset;
        this.strictLawAdherence = strictLawAdherence;
    }

    public double getSafetyDistanceRatio() {
        return safetyDistanceRatio;
    }

    public double getMaxSpeedOffset() {
        return maxSpeedOffset;
    }

    public boolean isStrictLawAdherence() {
        return strictLawAdherence;
    }
}
