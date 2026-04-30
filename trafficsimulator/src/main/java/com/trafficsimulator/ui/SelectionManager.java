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
    private final List<TrafficLight> selectedTrafficLights = new ArrayList<>();
    private final List<Camera> selectedCameras = new ArrayList<>();
    private Road selectedRoad;
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
        selectTrafficLight(tl, false);
    }

    public void selectTrafficLight(TrafficLight tl, boolean multiSelect) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedVehicle = null;

        if (!multiSelect) {
            selectedLanes.clear();
            selectedTrafficLights.clear();
            selectedCameras.clear();
        }

        if (!selectedTrafficLights.contains(tl)) {
            selectedTrafficLights.add(tl);
        } else if (multiSelect) {
            selectedTrafficLights.remove(tl);
        }
        notifyChange();
    }

    public void selectCamera(Camera camera) {
        selectCamera(camera, false);
    }

    public void selectCamera(Camera camera, boolean multiSelect) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedVehicle = null;

        if (!multiSelect) {
            selectedLanes.clear();
            selectedTrafficLights.clear();
            selectedCameras.clear();
        }

        if (!selectedCameras.contains(camera)) {
            selectedCameras.add(camera);
        } else if (multiSelect) {
            selectedCameras.remove(camera);
        }
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
        this.selectedVehicle = null;
        
        if (!multiSelect) {
            selectedLanes.clear();
            selectedTrafficLights.clear();
            selectedCameras.clear();
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
        this.selectedTrafficLights.clear();
        this.selectedCameras.clear();
        this.selectedConnection = null;
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
    public List<TrafficLight> getSelectedTrafficLights() { return selectedTrafficLights; }
    public List<Camera> getSelectedCameras() { return selectedCameras; }
    public LaneConnection getSelectedConnection() { return selectedConnection; }
    public TrafficLight getSelectedTrafficLight() {
        if (selectedTrafficLights.isEmpty()) return null;
        return selectedTrafficLights.get(selectedTrafficLights.size() - 1);
    }
    public Camera getSelectedCamera() {
        if (selectedCameras.isEmpty()) return null;
        return selectedCameras.get(selectedCameras.size() - 1);
    }
    public Vehicle getSelectedVehicle() { return selectedVehicle; }
    
    public Lane getSelectedLane() {
        if (selectedLanes.isEmpty()) return null;
        return selectedLanes.get(selectedLanes.size() - 1);
    }
    
    public Object getSelectedObject() {
        if (selectedRoad != null) return selectedRoad;
        if (!selectedTrafficLights.isEmpty()) return getSelectedTrafficLight();
        if (!selectedCameras.isEmpty()) return getSelectedCamera();
        if (selectedVehicle != null) return selectedVehicle;
        if (!selectedLanes.isEmpty()) return selectedLanes;
        return null;
    }
    
    public boolean isSelected(Road road) { return selectedRoad == road; }
    public boolean isSelected(Lane lane) { return selectedLanes.contains(lane); }
    public boolean isSelected(TrafficLight trafficLight) { return selectedTrafficLights.contains(trafficLight); }
    public boolean isSelected(Camera camera) { return selectedCameras.contains(camera); }
}
