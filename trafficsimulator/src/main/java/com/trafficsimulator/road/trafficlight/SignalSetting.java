package com.trafficsimulator.road.trafficlight;

/**
 * 신호등의 한 페이즈 설정을 저장합니다.
 * @param trafficLightSignals 현재 신호 상태
 * @param durationSeconds 해당 신호가 유지될 시간 (초) ❤️
 */
public record SignalSetting(TrafficLightSignal[] trafficLightSignals, double durationSeconds) {}
