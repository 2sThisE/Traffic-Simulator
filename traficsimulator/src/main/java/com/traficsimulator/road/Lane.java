package com.traficsimulator.road;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.traficsimulator.vehicle.VehicleType;

public class Lane {
    private boolean roadDirection; //도로 방향 t: 상행 f: 하행
    private HashSet<LaneType> laneType;
    private List<Point2D.Double> lanePath = new ArrayList<>();
    private double laneLength;
    private Set<VehicleType> allowVehicle=new HashSet<>();
    private boolean drawStopLine=true;
    
    /**
     * 도로의 방향과 차선번호를 지정합니다.
     * @param roadDirection
     * @param laneNum
     */
    public Lane(boolean roadDirection){
        this.roadDirection=roadDirection;
        allowVehicle.add(VehicleType.NOMAL);
        allowVehicle.add(VehicleType.LIGHT_TRUCK);
        allowVehicle.add(VehicleType.HEAVY_TRUCK);
        allowVehicle.add(VehicleType.BUS);
    }

    /**
     * 통행가능한 차량을 추가합니다
     * @param vt
     */
    public void setAllowVehicle(VehicleType vt){allowVehicle.add(vt);}

    /**
     * 통행가능한 차량에서 제외합니다
     * @param vt
     */
    public void removeAllowVehicle(VehicleType vt){allowVehicle.remove(vt);}

    /**
     * 통행가능한 차량 모든 리스트를 출력합니다.
     * @return
     */
    public Set<VehicleType> getAllowVehicle(){return allowVehicle;}

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

    public boolean isDrawStopLine() {
        return drawStopLine;
    }
    public void setDrawStopLine(boolean drawStopLine) {
        this.drawStopLine = drawStopLine;
    }

    
}
