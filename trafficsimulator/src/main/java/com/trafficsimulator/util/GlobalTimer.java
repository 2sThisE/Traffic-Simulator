package com.trafficsimulator.util;

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
    public static final double TICKLATE = 0.1;

    /**
     * 초(seconds)를 현재 TICKLATE 기준 틱(ticks)으로 변환합니다. ❤️
     */
    public static int secondsToTicks(double seconds) {
        return (int) Math.max(1, Math.round(seconds / TICKLATE));
    }

    /**
     * 틱(ticks)을 현재 TICKLATE 기준 초(seconds)로 변환합니다. ❤️
     */
    public static double ticksToSeconds(int ticks) {
        return ticks * TICKLATE;
    }

    public GlobalTimer() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(TICKLATE), e -> tick()));
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
     * 진행된 모든 틱을 0으로 초기화합니다. ❤️
     */
    public void reset() {
        totalTicks = 0;
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
