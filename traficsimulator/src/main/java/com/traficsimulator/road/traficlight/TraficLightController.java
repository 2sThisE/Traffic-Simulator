package com.traficsimulator.road.traficlight;

import com.traficsimulator.util.Tickable;
import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 내의 모든 신호등을 관리하고 틱을 전달하는 컨트롤러입니다.
 */
public class TraficLightController implements Tickable {
    private final List<TraficLight> traficLights = new ArrayList<>();

    public void addTraficLight(TraficLight traficLight) {
        traficLights.add(traficLight);
    }

    public void removeTraficLight(TraficLight traficLight) {
        traficLights.remove(traficLight);
    }

    @Override
    public void onTick() {
        for (TraficLight traficLight : traficLights) {
            traficLight.nextTick();
        }
        // 나중에 여기서 UI 갱신 신호를 보낼 수도 있어! ❤️
    }

    public List<TraficLight> getTraficLights() {
        return traficLights;
    }
}
