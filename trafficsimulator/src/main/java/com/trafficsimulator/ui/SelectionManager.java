package com.trafficsimulator.ui;

import com.trafficsimulator.debug.Vehicle;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.camera.Camera;
import java.util.ArrayList;
import java.util.List;

public class SelectionManager {
    private final List<Lane> selectedLanes = new ArrayList<>();
    private Road selectedRoad;
    private TrafficLight selectedTrafficLight;
    private Camera selectedCamera;
    private Vehicle selectedVehicle; // 추가
    private Lane highlightedLane; 
    private LaneConnection selectedConnection;
    private Runnable onSelectionChanged;

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public void selectRoad(Road road) {
        clearSelection();
        this.selectedRoad = road;
        notifyChange();
    }

    public void selectTrafficLight(TrafficLight tl) {
        clearSelection();
        this.selectedTrafficLight = tl;
        notifyChange();
    }

    public void selectCamera(Camera camera) {
        clearSelection();
        this.selectedCamera = camera;
        notifyChange();
    }

    public void selectVehicle(Vehicle vehicle) {
        clearSelection();
        this.selectedVehicle = vehicle;
        notifyChange();
    }

    public void selectLane(Road road, Lane lane, boolean multiSelect) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedTrafficLight = null;
        this.selectedCamera = null;
        this.selectedVehicle = null;
        
        if (!multiSelect) {
            selectedLanes.clear();
        }
        
        if (!selectedLanes.contains(lane)) {
            selectedLanes.add(lane);
        } else if (multiSelect) {
            selectedLanes.remove(lane);
        }
        notifyChange();
    }

    public void setHighlightedLane(Lane lane) {
        this.highlightedLane = lane;
        notifyChange();
    }

    public Lane getHighlightedLane() { return highlightedLane; }

    public void selectConnection(LaneConnection conn) {
        this.selectedConnection = conn;
        notifyChange();
    }

    public void clearSelection() {
        this.selectedRoad = null;
        this.selectedLanes.clear();
        this.selectedConnection = null;
        this.selectedTrafficLight = null;
        this.selectedCamera = null;
        this.selectedVehicle = null;
        this.highlightedLane = null;
        notifyChange();
    }

    private void notifyChange() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    public Road getSelectedRoad() { return selectedRoad; }
    public List<Lane> getSelectedLanes() { return selectedLanes; }
    public LaneConnection getSelectedConnection() { return selectedConnection; }
    public TrafficLight getSelectedTrafficLight() { return selectedTrafficLight; }
    public Camera getSelectedCamera() { return selectedCamera; }
    public Vehicle getSelectedVehicle() { return selectedVehicle; }
    
    public Lane getSelectedLane() {
        if (selectedLanes.isEmpty()) return null;
        return selectedLanes.get(selectedLanes.size() - 1);
    }
    
    public Object getSelectedObject() {
        if (selectedRoad != null) return selectedRoad;
        if (selectedTrafficLight != null) return selectedTrafficLight;
        if (selectedCamera != null) return selectedCamera;
        if (selectedVehicle != null) return selectedVehicle;
        if (!selectedLanes.isEmpty()) return selectedLanes;
        return null;
    }
    
    public boolean isSelected(Road road) { return selectedRoad == road; }
    public boolean isSelected(Lane lane) { return selectedLanes.contains(lane); }
}
