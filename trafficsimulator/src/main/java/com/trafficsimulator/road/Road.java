package com.trafficsimulator.road;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import com.trafficsimulator.util.UnitConverter;

public class Road {
    private Point2D.Double startPoint;
    private Point2D.Double endPoint;
    private Point2D.Double control1;
    private Point2D.Double control2;
    private boolean isLock=false;
    private boolean oneWay;
    private List<Lane> laneList = new ArrayList<>();
    private int limitSpeed;
    private double roadLength;
    private boolean isCurved;
    private List<Point2D.Double> pathPoints = new ArrayList<>();
    private double laneWidth = UnitConverter.toPixel(3.2); // 3.2m


    /**
     * 곡선도로용 생성자
     * @param startPoint
     * @param endPoint
     * @param control1
     * @param control2
     * @param oneWay
     */
    public Road(Point2D.Double startPoint, Point2D.Double endPoint, 
                Point2D.Double control1, Point2D.Double control2, 
                boolean oneWay) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.control1 = control1;
        this.control2 = control2;
        this.oneWay = oneWay;
        this.isCurved = true;
        
        refresh(); // 초기화 시 계산 실행
    }

    /**
     * 직전 도로용 생성자
     * @param startPoint
     * @param endPoint
     * @param oneWay
     */
    public Road(Point2D.Double startPoint, Point2D.Double endPoint, boolean oneWay) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.oneWay = oneWay;
        this.isCurved = false;
        
        refresh(); // 초기화 시 계산 실행
    }
    
    /**
     * 차선을 추가합니다
     * @param direction
     * @param laneNum
     */
    public void addLane(boolean direction, int laneNum){
        if(isLock) return;
        laneList.add(laneNum,new Lane(direction));
        refresh(); 
    }
    /**
     * 차선을 삭제합니다
     * @param laneNum
     */
    public Lane deleteLane(int laneNum){
        if(isLock) return null;
        Lane removedLane = laneList.remove(laneNum);
        refresh();
        return removedLane;
    }

    /**
     * 도로의 곡선 유무와 베지어커브의 제어점을 수정합니다
     * @param isCurved
     * @param control1
     * @param control2
     */
    public void setCurved(boolean isCurved, Point2D.Double control1, Point2D.Double control2){
        if(isLock) return;
        this.isCurved = isCurved;
        this.control1 = control1;
        this.control2 = control2;
        refresh();
    }

    /**
     * 도로의 모든 좌표를 특정 거리만큼 이동시킵니다.
     * @param dx 이동할 X 거리
     * @param dy 이동할 Y 거리
     */
    public void move(double dx, double dy) {
        if(isLock)return;
        startPoint.x += dx;
        startPoint.y += dy;
        endPoint.x += dx;
        endPoint.y += dy;
        
        if (control1 != null) {
            control1.x += dx;
            control1.y += dy;
        }
        if (control2 != null) {
            control2.x += dx;
            control2.y += dy;
        }
        
        refresh();
    
    }

    /**
     * 도로의 상태(곡선여부, 제어점, 차선 수 등)가 변했을 때 모든 데이터를 갱신합니다.
     */
    public void refresh() {
        this.pathPoints.clear();
        
        if (isCurved) {
            calculateCurvedPoints();
        } else {
            calculateStraightPoints();
        }
        // 직선이든 곡선이든, 결정된 중심선(pathPoints)을 바탕으로 차선들을 배치함
        updateAllLanes();
    }

    private void calculateStraightPoints() {
        // 직선이라도 차선 오프셋 계산을 위해 중간 점들이 있으면 좋지만, 
        // 최소한 시작과 끝은 있어야 법선 벡터가 나옵니다.
        pathPoints.add(startPoint);
        pathPoints.add(endPoint);
        this.roadLength = startPoint.distance(endPoint);
    }

    private void calculateCurvedPoints() {
        int segments = 100;
        double totalDist = 0;
        for (int i = 0; i <= segments; i++) {
            pathPoints.add(calculateBezier(i / (double) segments));
        }
        
        // 중심선 길이 계산
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            totalDist += pathPoints.get(i).distance(pathPoints.get(i+1));
        }
        this.roadLength = totalDist;
    }

    private void updateAllLanes() {
        if (pathPoints.size() < 2) return; // 경로가 없으면 계산 불가

        for (int i = 0; i < laneList.size(); i++) {
            double offset = (i - (laneList.size() - 1) / 2.0) * laneWidth;
            generateLanePath(laneList.get(i), offset);
        }
    }

    private void generateLanePath(Lane lane, double offset) {
        List<Point2D.Double> specificPath = new ArrayList<>();
        double totalDist = 0;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Point2D.Double p1 = pathPoints.get(i);
            Point2D.Double p2 = pathPoints.get(i + 1);

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            
            double nx = -dy / len;
            double ny = dx / len;

            Point2D.Double offsetP = new Point2D.Double(p1.x + nx * offset, p1.y + ny * offset);
            specificPath.add(offsetP);

            if (i > 0) {
                totalDist += specificPath.get(i-1).distance(offsetP);
            }
        }

        // 마지막 점 처리
        Point2D.Double lastCenterP = pathPoints.get(pathPoints.size() - 1);
        Point2D.Double secondLastCenterP = pathPoints.get(pathPoints.size() - 2);
        
        double ldx = lastCenterP.x - secondLastCenterP.x;
        double ldy = lastCenterP.y - secondLastCenterP.y;
        double llen = Math.sqrt(ldx * ldx + ldy * ldy);
        double lnx = -ldy / llen;
        double lny = ldx / llen;

        Point2D.Double lastOffsetP = new Point2D.Double(lastCenterP.x + lnx * offset, lastCenterP.y + lny * offset);
        specificPath.add(lastOffsetP);
        totalDist += specificPath.get(specificPath.size() - 2).distance(lastOffsetP);

        lane.updatePath(specificPath, totalDist);
    }

    private Point2D.Double calculateBezier(double t) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double x = (uu * u * startPoint.x) + (3 * uu * t * control1.x) + (3 * u * tt * control2.x) + (tt * t * endPoint.x);
        double y = (uu * u * startPoint.y) + (3 * uu * t * control1.y) + (3 * u * tt * control2.y) + (tt * t * endPoint.y);
        return new Point2D.Double(x, y);
    }

    public void setLimitSpeed(int limitSpeed) {
        this.limitSpeed = limitSpeed;
    }

    public void setOneWay(boolean oneWay) {
        this.oneWay = oneWay;
        refresh();
    }

    public void setLaneWidth(double laneWidth) {
        this.laneWidth = laneWidth;
        refresh();
    }

    public void setStartPoint(Point2D.Double startPoint) {
        if(isLock) return;
        this.startPoint = startPoint;
        refresh();
    }

    public void setEndPoint(Point2D.Double endPoint) {
        if(isLock) return;
        this.endPoint = endPoint;
        refresh();
    }

    public void setMoveable(boolean lock){this.isLock=lock;}

    public Point2D.Double getStartPoint() { return startPoint; }
    public Point2D.Double getEndPoint() { return endPoint; }
    public Point2D.Double getControl1() { return control1; }
    public Point2D.Double getControl2() { return control2; }

    // Getter들...
    public double getRoadLength() { return roadLength; }
    public List<Point2D.Double> getPathPoints() { return pathPoints; }
    public boolean isLock(){return isLock;}
    
    public List<Lane> getLaneList() { return laneList; }
    public Lane getLane(int laneNum){return laneList.get(laneNum);}
    public int getLaneNum(Lane lane){return laneList.indexOf(lane);}
    public int getLimitSpeed(){return limitSpeed;}
    public double getLaneWidth() { return laneWidth; }

    /**
     * 해당 차선에서 변경 가능한 모든 차선 리스트를 반환합니다
     * @param lane
     * @return List<Lane>
     */
    public List<Lane> getCanChangeLaneList(Lane lane){
        int lanenum=laneList.indexOf(lane);
        List<Lane> returnLane=new ArrayList<>();
        for(int i=lanenum-1; i>=0; i--){
            Lane tmpLane=laneList.get(i);
            if(tmpLane.isRoadDirection()!=lane.isRoadDirection()) break;
            returnLane.add(tmpLane);
        }
        for(int i=lanenum; i<laneList.size(); i++){
            Lane tmpLane=laneList.get(i);
            if(tmpLane.isRoadDirection()!=lane.isRoadDirection()) break;
            returnLane.add(tmpLane);
        }
        return returnLane;
    }

    /**
     * 절대 차선 번호를 기준으로 변경가능한 모든 차선 리스트를 반환합니다.
     * @param lanenum
     * @return List<Lane>
     */
    public List<Lane> getCanChangeLaneList(int lanenum){
        Lane lane=laneList.get(lanenum);
        List<Lane> returnLane=new ArrayList<>();
        for(int i=lanenum-1; i>=0; i--){
            Lane tmpLane=laneList.get(i);
            if(tmpLane.isRoadDirection()!=lane.isRoadDirection()) break;
            returnLane.add(tmpLane);
        }
        for(int i=lanenum; i<laneList.size(); i++){
            Lane tmpLane=laneList.get(i);
            if(tmpLane.isRoadDirection()!=lane.isRoadDirection()) break;
            returnLane.add(tmpLane);
        }
        return returnLane;
    }
}
