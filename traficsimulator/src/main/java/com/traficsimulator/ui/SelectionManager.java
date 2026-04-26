package com.traficsimulator.ui;

import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import java.util.ArrayList;
import java.util.List;

public class SelectionManager {
    private final List<Lane> selectedLanes = new ArrayList<>();
    private Road selectedRoad;
    private LaneConnection selectedConnection; // 선택된 특정 연결 정보 ❤️
    private Runnable onSelectionChanged;

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public void selectRoad(Road road) {
        clearSelection();
        this.selectedRoad = road;
        notifyChange();
    }

    public void selectLane(Road road, Lane lane, boolean multiSelect) {
        this.selectedRoad = null;
        this.selectedConnection = null; // 차선 선택 시 연결선 선택은 초기화
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

    public void selectConnection(LaneConnection conn) {
        this.selectedConnection = conn;
        notifyChange();
    }

    public void clearSelection() {
        this.selectedRoad = null;
        this.selectedLanes.clear();
        this.selectedConnection = null;
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
    
    public Lane getSelectedLane() {
        if (selectedLanes.isEmpty()) return null;
        return selectedLanes.get(selectedLanes.size() - 1);
    }
    
    public Object getSelectedObject() {
        if (selectedRoad != null) return selectedRoad;
        if (!selectedLanes.isEmpty()) return selectedLanes;
        return null;
    }
    
    public boolean isSelected(Road road) { return selectedRoad == road; }
    public boolean isSelected(Lane lane) { return selectedLanes.contains(lane); }
}
