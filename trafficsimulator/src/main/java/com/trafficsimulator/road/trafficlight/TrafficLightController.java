package com.trafficsimulator.road.trafficlight;

import com.trafficsimulator.util.Tickable;
import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 내의 모든 신호등을 관리하고 틱을 전달하는 컨트롤러입니다.
 */
public class TrafficLightController implements Tickable {
    private final List<TrafficLight> trafficLights = new ArrayList<>();

    public void addTrafficLight(TrafficLight trafficLight) {
        trafficLights.add(trafficLight);
    }

    public void removeTrafficLight(TrafficLight trafficLight) {
        trafficLights.remove(trafficLight);
    }

    @Override
    public void onTick() {
        for (TrafficLight trafficLight : trafficLights) {
            trafficLight.nextTick();
        }
        // 나중에 여기서 UI 갱신 신호를 보낼 수도 있어! ❤️
    }

    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }
}
