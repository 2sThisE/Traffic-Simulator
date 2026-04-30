package com.trafficsimulator.debug;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleType;

import javafx.scene.paint.Color;

/**
 * 실제 미터 단위를 기반으로 외형과 물리 상태를 관리하는 차량 객체입니다.
 */
public class Vehicle {
    private double x, y;          // 중심 좌표 (Pixel)
    private double angle;         // 회전 각도 (Degree)
    private double width;         // 차량 길이 (Pixel)
    private double height;        // 차량 너비 (Pixel)
    private double speedKmh;      // 현재 속도 (km/h)
    private Color color;          // 차량 색상
    private VehicleType type;     // 차량 종류
    private DriverPersonality driverPersonality;
    private boolean selected = false; // 선택 여부

    private final Autopilot autopilot; // 차량의 주행 지능 ❤️

    public Vehicle(double x, double y, double angle, VehicleType type) {
        this(x, y, angle, type, DriverPersonality.AVERAGE);
    }

    public Vehicle(double x, double y, double angle, VehicleType type, DriverPersonality driverPersonality) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.type = type;
        this.driverPersonality = driverPersonality;
        this.speedKmh = 0; // 초기 속도 0
        
        // 실제 미터 단위 규격 설정 및 픽셀 변환
        double lengthInMeters;
        double widthInMeters;

        switch (type) {
            case BUS -> {
                lengthInMeters = 6.0;
                widthInMeters = 1.9;
                this.color = Color.web("#2ecc71");
            }
            case HEAVY_TRUCK -> {
                lengthInMeters = 6.0;
                widthInMeters = 1.9;
                this.color = Color.web("#6e6e6e");
            }
            case LIGHT_TRUCK -> {
                lengthInMeters = 5.2;
                widthInMeters = 1.9;
                this.color = Color.web("#95a5a6");
            }
            default -> { // NORMAL (승용차)
                lengthInMeters = 4.7;
                widthInMeters = 1.8;
                this.color = Color.web("#3498db");
            }
        }

        this.width = UnitConverter.toPixel(lengthInMeters);
        this.height = UnitConverter.toPixel(widthInMeters);

        this.autopilot = new Autopilot(this);
    }

    /**
     * 오토파일럿 로직을 통해 차량의 위치를 업데이트합니다. ❤️
     */
    public void updatePosition(JunctionController junctionController) {
        autopilot.updatePosition(junctionController);
    }

    /**
     * 오토파일럿 로직을 통해 감지 영역을 업데이트합니다. ❤️
     */
    public void updateVisionArea(RoadManager roadManager, JunctionController junctionController, List<Vehicle> vehicles) {
        autopilot.updateVisionArea(roadManager, junctionController, vehicles);
    }

    public void updateSpeedControl(RoadManager roadManager) {
        autopilot.updateSpeedControl(roadManager);
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     */
    public void snapToNearestPoint(Point2D.Double target) {
        autopilot.snapToNearestPoint(target, null);
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     */
    public void snapToNearestPoint(Point2D.Double target, JunctionController junctionController) {
        autopilot.snapToNearestPoint(target, junctionController);
    }

    /**
     * 동적인 주행 경로를 업데이트합니다. ❤️
     */
    public void updateDynamicPath(JunctionController junctionController, List<Vehicle> vehicles) {
        autopilot.updateDynamicPath(junctionController, vehicles);
    }

    // Getter 및 Setter
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(double speedKmh) { this.speedKmh = speedKmh; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public VehicleType getType() { return type; }
    public DriverPersonality getDriverPersonality() { return driverPersonality; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    // Autopilot 위임 Getter ❤️
    public List<Point2D.Double> getPath() { return autopilot.getPath(); }
    public void setPath(List<Point2D.Double> path) { autopilot.setPath(path); }
    public List<Set<Lane>> getLogicalRoute() { return autopilot.getLogicalRoute(); }
    public void setLogicalRoute(List<Set<Lane>> route) { autopilot.setLogicalRoute(route); }
    public int getCurrentPhaseIndex() { return autopilot.getCurrentPhaseIndex(); }
    public void setCurrentPhaseIndex(int index) { autopilot.setCurrentPhaseIndex(index); }
    public List<Point2D.Double> getForwardVisionArea() { return autopilot.getForwardVisionArea(); }
    public List<Point2D.Double> getSideVisionArea() { return autopilot.getSideVisionArea(); }
    public List<Point2D.Double> getVehicleVisionArea() { return autopilot.getVehicleVisionArea(); }
    public boolean isRouteFinished() { return autopilot.isRouteFinished(); }
    public Autopilot getAutopilot() { return autopilot; }
}
