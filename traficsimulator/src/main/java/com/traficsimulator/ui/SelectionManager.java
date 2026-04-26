package com.traficsimulator.ui;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import com.traficsimulator.road.traficlight.TraficLight;
import java.util.ArrayList;
import java.util.List;

public class SelectionManager {
    private final List<Lane> selectedLanes = new ArrayList<>();
    private Road selectedRoad;
    private TraficLight selectedTraficLight;
    private Lane highlightedLane; // 리스트에서 선택된 강조 차선 ❤️
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
        // 도로 선택은 해제하되, 이미 선택된 차선은 유지할 수 있게 함 ❤️
        this.selectedRoad = null;
        this.selectedConnection = null;
        this.selectedTraficLight = tl;
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
    
    public Lane getSelectedLane() {
        if (selectedLanes.isEmpty()) return null;
        return selectedLanes.get(selectedLanes.size() - 1);
    }
    
    public Object getSelectedObject() {
        if (selectedRoad != null) return selectedRoad;
        if (selectedTraficLight != null) return selectedTraficLight;
        if (!selectedLanes.isEmpty()) return selectedLanes;
        return null;
    }
    
    public boolean isSelected(Road road) { return selectedRoad == road; }
    public boolean isSelected(Lane lane) { return selectedLanes.contains(lane); }
}
