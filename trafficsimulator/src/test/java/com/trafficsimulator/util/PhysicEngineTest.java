package com.trafficsimulator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicEngineTest {

    @Test
    @DisplayName("가속도에 따른 속도 변화 테스트")
    void testCalculateSpeed() {
        // Given: 현재 60km/h, 가속도 2.5m/s^2, 틱레이트 0.1s
        double currentSpeed = 60.0;
        double acceleration = 2.5;
        
        // When
        double nextSpeed = PhysicEngine.calculateSpeed(currentSpeed, acceleration);
        
        // Then: 
        // 60km/h = 16.666...m/s
        // v = 16.666 + 2.5 * 0.1 = 16.9166...m/s
        // 16.9166 * 3.6 = 60.9km/h
        assertEquals(60.9, nextSpeed, 0.001);
    }

    @Test
    @DisplayName("정지 임계값 테스트 (저속에서 완전 정지 여부)")
    void testStopThreshold() {
        // Given: 아주 낮은 속도 (약 0.05 km/h), 감속 중
        double lowSpeed = 0.05;
        double deceleration = -1.0;
        
        // When
        double nextSpeed = PhysicEngine.calculateSpeed(lowSpeed, deceleration);
        
        // Then: 임계값(0.028 m/s) 이하이므로 0.0이 되어야 함
        assertEquals(0.0, nextSpeed);
    }

    @Test
    @DisplayName("가속 상황에서의 이동 거리 계산 테스트")
    void testCalculateDistance() {
        // Given: 0km/h에서 2.5m/s^2으로 0.1초간 가속 이동
        double currentSpeed = 0.0;
        double acceleration = 2.5;
        
        // When
        double distance = PhysicEngine.calculateDistance(currentSpeed, acceleration);
        
        // Then: s = 0*0.1 + 0.5 * 2.5 * 0.1^2 = 0.0125m
        assertEquals(0.0125, distance, 0.0001);
    }

    @Test
    @DisplayName("정지 거리 계산 테스트")
    void testCalculateStoppingDistance() {
        // Given: 36km/h(10m/s)에서 5m/s^2으로 감속 시
        double speed = 36.0;
        double deceleration = 5.0;
        
        // When
        double stopDist = PhysicEngine.calculateStoppingDistance(speed, deceleration);
        
        // Then: s = 10^2 / (2 * 5) = 10m
        assertEquals(10.0, stopDist, 0.0001);
    }

    @Test
    @DisplayName("목표 도달을 위한 필요 가속도 계산 테스트")
    void testCalculateRequiredAcceleration() {
        // Given: 0km/h에서 20m 앞에 정지하기 위해 필요한 가속도
        double currentSpeed = 0.0;
        double targetSpeed = 0.0; // 정지 목표
        double distance = 20.0;
        
        // When
        double reqAcc = PhysicEngine.calculateRequiredAcceleration(currentSpeed, targetSpeed, distance);
        
        // Then: a = (0^2 - 0^2) / (2 * 20) = 0
        assertEquals(0.0, reqAcc);

        // Given: 36km/h(10m/s)에서 10m 내에 정지하기 위한 가속도
        reqAcc = PhysicEngine.calculateRequiredAcceleration(36.0, 0.0, 10.0);
        // a = (0 - 10^2) / (2 * 10) = -100 / 20 = -5.0m/s^2
        assertEquals(-5.0, reqAcc, 0.0001);
    }

    @Test
    @DisplayName("상대 속도 기반 필요 감속도(브레이크) 계산 테스트")
    void testCalculateRequiredBraking() {
        // Given: 내 속도 72km/h(20m/s), 앞차 36km/h(10m/s), 거리 30m
        double mySpeed = 72.0;
        double targetSpeed = 36.0;
        double distance = 30.0;
        
        // When
        double braking = PhysicEngine.calculateRequiredBraking(mySpeed, targetSpeed, distance);
        
        // Then: a = (20^2 - 10^2) / (2 * 30) = 300 / 60 = 5.0m/s^2
        assertEquals(5.0, braking, 0.0001);
        
        // Given: 내가 더 느릴 때
        braking = PhysicEngine.calculateRequiredBraking(36.0, 72.0, 30.0);
        assertEquals(0.0, braking);
    }
}
