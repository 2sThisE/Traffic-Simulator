package com.traficsimulator.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 시뮬레이션의 전체 시간을 관리하는 글로벌 타이머입니다.
 */
public class GlobalTimer {
    private final List<Tickable> listeners = new ArrayList<>();
    private final Timeline timeline;
    private int totalTicks = 0;

    public GlobalTimer(double secondsPerTick) {
        timeline = new Timeline(new KeyFrame(Duration.seconds(secondsPerTick), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void addTickListener(Tickable listener) {
        listeners.add(listener);
    }

    public void start() {
        timeline.play();
    }

    public void stop() {
        timeline.pause();
    }

    /**
     * 수동으로 틱을 발생시킵니다. (주로 테스트용으로 사용됩니다.)
     */
    public void manualTick() {
        tick();
    }

    private void tick() {
        totalTicks++;
        for (Tickable listener : listeners) {
            listener.onTick();
        }
    }

    public int getTotalTicks() {
        return totalTicks;
    }
}
