package com.traficsimulator.road.traficlight;

import java.awt.geom.Point2D;
import java.util.*;
import com.traficsimulator.road.Lane;

public class TraficLight {
    private Set<Lane> controlLaneList=new HashSet<>();
    private List<SignalSetting> signalTime=new ArrayList<>();
    private int totalTick=0;
    private int currentTick=0;
    private int currentSignalIndex = 0;
    private int nextPhaseTick = 0;
    private Point2D.Double coordinates;
    private Set<TraficLightSignal> lightList=new HashSet<>();


    public void setCoordinates(Point2D.Double coordinates){this.coordinates=coordinates;}
    public Point2D.Double getCoordinates(){return this.coordinates;}
    public void addLight(TraficLightSignal tl){lightList.add(tl);}
    public Set<TraficLightSignal> getLightList(){return lightList;}
    public void deleteLight(TraficLightSignal tl){lightList.remove(tl);}
    
    public Set<Lane> getControlLaneList() { return controlLaneList; }
    public void removeControlLane(Lane lane) { controlLaneList.remove(lane); }
    public List<SignalSetting> getSignalTime() { return signalTime; } // 자기를 위해 열어둘게 ❤️

    /**
     * 등록된 모든 차선의 정지선(나가는 방향) 중앙값으로 신호등의 위치를 업데이트합니다.
     */
    public void updatePositionToLanesCenter() {
        if (controlLaneList.isEmpty()) return;
        
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        for (Lane lane : controlLaneList) {
            List<Point2D.Double> path = lane.getLanePath();
            if (path != null && !path.isEmpty()) {
                // 반전 처리: 상행(true)이면 끝점, 하행(false)이면 시작점이 나가는 방향! ❤️
                Point2D.Double exitPt = lane.isRoadDirection() ? path.get(path.size() - 1) : path.get(0);
                sumX += exitPt.x;
                sumY += exitPt.y;
                count++;
            }
        }
        if (count > 0) {
            if (this.coordinates == null) this.coordinates = new Point2D.Double();
            this.coordinates.setLocation(sumX / count, sumY / count);
        }
    }
    
    /**
     * 해당 신호등이 관리할 차선을 추가 합니다
     * @param lane
     * @return boolean
     */
    public boolean addControlLane(Lane lane){return controlLaneList.add(lane);}

    /**
     * 해당 신호등의 신호주기에 따른 신호를 설정합니다.
     * @param signalTime
     */
    public void addSignalLoop(List<SignalSetting> signalTime){
        this.signalTime=signalTime;
        recalculateTotalTick();
    }
    
    /**
     * 해당 인덱스의 신호지시를 변경합니다.
     * @param index
     * @param signalSet
     */
    public void editSignalLoop(int index, SignalSetting signalSet){
        signalTime.set(index, signalSet);
        recalculateTotalTick();
    }

    /**
     * 해당 인덱스의 신호지시를 삭제합니다.
     * @param index
     */
    public void deleteSignalLoop(int index){
        signalTime.remove(index);
        recalculateTotalTick();
    }

    /**
     * 전체 신호 주기를 초기화 합니다.
     */
    public void deleteSignalLoop() {
        signalTime = new ArrayList<>();
        totalTick = 0;
        currentTick = 0;
        currentSignalIndex = 0; // 커서 인덱스 리셋
        nextPhaseTick = 0;      // 다음 페이즈 틱 리셋
    }

    /**
     * 다음 틱으로 넘어갑니다.
     */
    public void nextTick() {
        if (totalTick <= 0) return; // 0으로 나누기 방지

        currentTick = (currentTick + 1) % totalTick;

        // 1. 순환 시 상태 리셋
        if (currentTick == 0) {
            currentSignalIndex = 0;
            nextPhaseTick = signalTime.get(0).tick();
            return;
        }

        // 2. 현재 페이즈 종료 체크
        if (currentTick >= nextPhaseTick) {
            currentSignalIndex = (currentSignalIndex + 1) % signalTime.size();
            nextPhaseTick += signalTime.get(currentSignalIndex).tick();
        }
    }

    /**
     * 특정 틱만큼 점프 합니다.
     * @param tick
     */
    public void jmpTick(int tick) {
        if (totalTick <= 0) return;
        currentTick = (currentTick + tick) % totalTick;
        
        // 점프 후에는 현재 틱에 맞는 인덱스와 다음 타겟 틱을 반드시 재계산해야 함
        syncCursor();
    }
    /**
     * 현재 신호를 확인합니다.
     * @return TraficLightSignal
     */
    public TraficLightSignal[] currentSignal() {
        if (signalTime.isEmpty() || currentSignalIndex < 0 || currentSignalIndex >= signalTime.size()) {
            return new TraficLightSignal[]{TraficLightSignal.RED}; 
        }
        return signalTime.get(currentSignalIndex).traficLightSignals();
    }

    /**
     * 해당 차선이 신호등에 속해 있는지 확인합니다.
     * @param lane
     * @return
     */
    public boolean checkLane(Lane lane){
        return controlLaneList.contains(lane);
    }

    public void resetCurrentTick(){this.currentTick=0;}

    private void recalculateTotalTick() {
        this.totalTick = signalTime.stream().mapToInt(SignalSetting::tick).sum();
        if (totalTick > 0) {
            // 현재 틱이 새로운 전체 틱 범위를 벗어나지 않도록 보정 ❤️
            this.currentTick %= totalTick;
            syncCursor();
        } else {
            this.currentTick = 0;
            this.currentSignalIndex = 0;
            this.nextPhaseTick = 0;
        }
    }

    private void syncCursor() {
        int accumulated = 0;
        for (int i = 0; i < signalTime.size(); i++) {
            accumulated += signalTime.get(i).tick();
            if (currentTick < accumulated) {
                currentSignalIndex = i;
                nextPhaseTick = accumulated;
                break;
            }
        }
    }
}
