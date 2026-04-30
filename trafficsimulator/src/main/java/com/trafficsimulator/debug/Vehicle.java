package com.trafficsimulator.debug;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.ui.RoadManager;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.DriverPersonality;
import com.trafficsimulator.vehicle.VehicleLight;
import com.trafficsimulator.vehicle.VehicleType;

import javafx.scene.paint.Color;

/**
 * 실제 미터 단위를 기반으로 외형과 물리 상태를 관리하는 차량 객체입니다.
 */
public class Vehicle {
    private double x, y;          // 중심 좌표 (Pixel)
    private double angle;         // 회전 각도 (Degree)
    private double previousX, previousY;
    private double previousAngle;
    private double width;         // 차량 길이 (Pixel)
    private double height;        // 차량 너비 (Pixel)
    private double speedKmh;      // 현재 속도 (km/h)
    private Color color;          // 차량 색상
    private VehicleType type;     // 차량 종류
    private DriverPersonality driverPersonality;
    private boolean selected = false; // 선택 여부
    private final EnumSet<VehicleLight> lights = EnumSet.noneOf(VehicleLight.class);

    private boolean crashed = false; // 충돌 여부 ❤️
    private int crashTickCounter = 0; // 충돌 후 경과 틱 ❤️

    private final Autopilot autopilot; // 차량의 주행 지능 ❤️

    private double boundingRadius; // 캐싱된 바운딩 반경 ❤️
    private transient Shape cachedShape = null;
    private double lastShapeX = Double.NaN;
    private double lastShapeY = Double.NaN;
    private double lastShapeAngle = Double.NaN;

    public Vehicle(double x, double y, double angle, VehicleType type) {
        this(x, y, angle, type, DriverPersonality.AVERAGE);
    }

    public Vehicle(double x, double y, double angle, VehicleType type, DriverPersonality driverPersonality) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.previousX = x;
        this.previousY = y;
        this.previousAngle = angle;
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
        this.boundingRadius = Math.sqrt(this.width * this.width / 4.0 + this.height * this.height / 4.0);

        this.autopilot = new Autopilot(this);
    }

    /**
     * 차량 충돌 처리 ❤️
     */
    public void setCrashed() {
        if (!this.crashed) {
            this.crashed = true;
            this.speedKmh = 0;
            this.color = Color.RED;
            setLight(VehicleLight.EMERGENCY, true);
            savePreviousTransform(); // 충돌 시 제자리에 고정 (움찔거림 방지) ❤️
        }
    }

    public boolean isCrashed() {
        return crashed;
    }

    public int getCrashTickCounter() {
        return crashTickCounter;
    }

    public void incrementCrashTick() {
        this.crashTickCounter++;
    }

    /**
     * 충돌 감지를 위한 회전된 사각형 영역 반환 ❤️
     */
    public Shape getShape() {
        if (cachedShape == null || x != lastShapeX || y != lastShapeY || angle != lastShapeAngle) {
            AffineTransform at = new AffineTransform();
            at.translate(x, y);
            at.rotate(Math.toRadians(angle));
            Rectangle2D rect = new Rectangle2D.Double(-width / 2.0, -height / 2.0, width, height);
            cachedShape = at.createTransformedShape(rect);
            lastShapeX = x;
            lastShapeY = y;
            lastShapeAngle = angle;
        }
        return cachedShape;
    }

    public double getBoundingRadius() {
        return boundingRadius;
    }

    /**
     * 현재 차량이 위치한(또는 목표로 하는 가장 가까운 페이즈의) 도로를 반환합니다.
     */
    public Road getCurrentRoad(RoadManager roadManager) {
        List<Set<Lane>> route = autopilot.getLogicalRoute();
        if (route == null) return null;
        int idx = autopilot.getCurrentPhaseIndex();
        if (idx < 0 || idx >= route.size()) return null;
        Set<Lane> currentPhase = route.get(idx);
        if (currentPhase == null || currentPhase.isEmpty()) return null;
        return roadManager.findRoadByLane(currentPhase.iterator().next());
    }

    /**
     * 오토파일럿 로직을 통해 차량의 위치를 업데이트합니다. ❤️
     */
    public void updatePosition(JunctionController junctionController) {
        if (crashed) return; // 충돌 시 이동 불가 ❤️
        savePreviousTransform();
        autopilot.updatePosition(junctionController);
    }

    public void savePreviousTransform() {
        this.previousX = x;
        this.previousY = y;
        this.previousAngle = angle;
    }

    /**
     * 오토파일럿 로직을 통해 감지 영역을 업데이트합니다. ❤️
     */
    public void updateVisionArea(RoadManager roadManager, JunctionController junctionController, List<Vehicle> vehicles) {
        if (crashed) return; // 충돌 시 센서 끄기 ❤️
        autopilot.updateVisionArea(roadManager, junctionController, vehicles);
    }

    public void updateSpeedControl(RoadManager roadManager) {
        if (crashed) return; // 충돌 시 속도 제어 불가 ❤️
        autopilot.updateSpeedControl(roadManager);
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     */
    public void snapToNearestPoint(Point2D.Double target) {
        if (crashed) return;
        autopilot.snapToNearestPoint(target, null);
    }

    /**
     * 특정 좌표와 가장 가까운 경로상의 위치로 차량을 이동시킵니다.
     */
    public void snapToNearestPoint(Point2D.Double target, JunctionController junctionController) {
        if (crashed) return;
        autopilot.snapToNearestPoint(target, junctionController);
    }

    /**
     * 동적인 주행 경로를 업데이트합니다. ❤️
     */
    public void updateDynamicPath(JunctionController junctionController, List<Vehicle> vehicles) {
        if (crashed) return;
        autopilot.updateDynamicPath(junctionController, vehicles);
    }

    // Getter 및 Setter
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }
    public double getInterpolatedX(double alpha) { return lerp(previousX, x, clamp01(alpha)); }
    public double getInterpolatedY(double alpha) { return lerp(previousY, y, clamp01(alpha)); }
    public double getInterpolatedAngle(double alpha) {
        double clampedAlpha = clamp01(alpha);
        double delta = ((angle - previousAngle + 540.0) % 360.0) - 180.0;
        return previousAngle + delta * clampedAlpha;
    }
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
    public Set<VehicleLight> getLights() { return Collections.unmodifiableSet(lights); }
    public boolean hasLight(VehicleLight light) { return lights.contains(light); }
    public void setLight(VehicleLight light, boolean on) {
        if (on) lights.add(light);
        else lights.remove(light);
    }
    public void clearLights(VehicleLight... targetLights) {
        for (VehicleLight light : targetLights) {
            lights.remove(light);
        }
    }

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

    private double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
