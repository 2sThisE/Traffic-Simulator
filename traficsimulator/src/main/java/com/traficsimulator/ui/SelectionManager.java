package com.traficsimulator.ui;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import com.traficsimulator.road.traficlight.TraficLight;
import com.traficsimulator.road.camera.Camera;
import java.util.ArrayList;
import java.util.List;

public class SelectionManager {
    private final List<Lane> selectedLanes = new ArrayList<>();
    private Road selectedRoad;
    private TraficLight selectedTraficLight;
    private Camera selectedCamera; // 추가 ❤️
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

    public void selectTraficLight(TraficLight tl) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedCamera = null;
        this.selectedTraficLight = tl;
        notifyChange();
    }

    public void selectCamera(Camera camera) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedTraficLight = null;
        this.selectedCamera = camera;
        notifyChange();
    }

    public void selectLane(Road road, Lane lane, boolean multiSelect) {
        this.selectedRoad = null;
        this.selectedConnection = null;
        
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
        this.selectedTraficLight = null;
        this.selectedCamera = null;
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
    public TraficLight getSelectedTraficLight() { return selectedTraficLight; }
    public Camera getSelectedCamera() { return selectedCamera; } // 추가 ❤️
    
    public Lane getSelectedLane() {
        if (selectedLanes.isEmpty()) return null;
        return selectedLanes.get(selectedLanes.size() - 1);
    }
    
    public Object getSelectedObject() {
        if (selectedRoad != null) return selectedRoad;
        if (selectedTraficLight != null) return selectedTraficLight;
        if (selectedCamera != null) return selectedCamera; // 우선순위 추가 ❤️
        if (!selectedLanes.isEmpty()) return selectedLanes;
        return null;
    }
    
    public boolean isSelected(Road road) { return selectedRoad == road; }
    public boolean isSelected(Lane lane) { return selectedLanes.contains(lane); }
}
