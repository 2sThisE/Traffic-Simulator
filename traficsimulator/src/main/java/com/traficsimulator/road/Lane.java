package com.traficsimulator.road;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Lane {
    private boolean roadDirection; //도로 방향 t: 상행 f: 하행
    private HashSet<LaneType> laneType;
    private List<Point2D.Double> lanePath = new ArrayList<>();
    private double laneLength;
    /**
     * 도로의 방향과 차선번호를 지정합니다.
     * @param roadDirection
     * @param laneNum
     */
    public Lane(boolean roadDirection){
        this.roadDirection=roadDirection;
    }
    /**
     * 도로의 방향을 리턴합니다
     * ture: 상행
     * false: 하행
     * @return boolean
    */
    public boolean isRoadDirection() {
        return roadDirection;
    }

    /**
     * 도로의 방향을 리턴합니다
     * ture: 상행
     * false: 하행
     * @param boolean
    */
    public void setRoadDirection(boolean roadDirection) {
        this.roadDirection = roadDirection;
    }

    /**
     * 도로의 통행 방법을 결정합니다
     * @param lType
     */
    public void addLaneType(LaneType lType){
        laneType.add(lType);
    }

    /**
     * 해당 래인이 인자값으로 받은 통행이 가능한지 확인합니다
     * @param lType
     * @return boolean
     */
    public boolean checkLaneType(LaneType lType){
        return laneType.contains(lType);
    }

    /**
     * 길이를 업데이트 합니다
     * @param points
     * @param length
     */
    public void updatePath(List<Point2D.Double> points, double length) {
        this.lanePath = new ArrayList<>(points);
        this.laneLength = length;
    }

    public double getLaneLength() { return laneLength; }

    /**
     * 차량이 따라가야할 차선 중앙의 경로를 리턴합니다.
     * @return
     */
    public List<Point2D.Double> getLanePath() { return lanePath; }
}
