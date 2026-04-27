package com.trafficsimulator.ui;

import java.util.List;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class KeyboardHandler {
    private final EditorModeManager modeManager;
    private final RoadDrawingTool drawingTool;
    private final RoadManager roadManager;
    private final SelectionManager selectionManager;
    private final JunctionController junctionController;
    private final Runnable onUpdate;

    public KeyboardHandler(EditorModeManager modeManager, RoadDrawingTool drawingTool, 
                           RoadManager roadManager, SelectionManager selectionManager,
                           JunctionController junctionController, Runnable onUpdate) {
        this.modeManager = modeManager;
        this.drawingTool = drawingTool;
        this.roadManager = roadManager;
        this.selectionManager = selectionManager;
        this.junctionController = junctionController;
        this.onUpdate = onUpdate;
    }

    public void install(Node root) {
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                handleEscKey();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                handleDeleteKey();
                event.consume();
            }
        });
    }

    private void handleEscKey() {
        if (drawingTool.isDrawing()) {
            drawingTool.resetDrawing();
        } else if (modeManager.getMode() != EditorMode.SELECT) {
            modeManager.setMode(EditorMode.SELECT);
        }
        onUpdate.run();
    }

    private void handleDeleteKey() {
        TrafficLight selectedTL = selectionManager.getSelectedTrafficLight();
        Camera selectedCam = selectionManager.getSelectedCamera(); // 카메라 추가 ❤️
        Lane highlightedLane = selectionManager.getHighlightedLane(); 
        LaneConnection selectedConn = selectionManager.getSelectedConnection();
        Lane selectedLane = selectionManager.getSelectedLane();
        Road selectedRoad = selectionManager.getSelectedRoad();

        // 1. 신호등 관련 삭제
        if (selectedTL != null) {
            if (highlightedLane != null) {
                selectedTL.removeControlLane(highlightedLane);
                selectedTL.updatePositionToLanesCenter();
                selectionManager.setHighlightedLane(null);
            } else {
                roadManager.removeTrafficLight(selectedTL);
            }
        }
        // 2. 카메라 관련 삭제 ❤️
        else if (selectedCam != null) {
            if (highlightedLane != null) {
                // 특정 차선만 감시 대상에서 제외
                selectedCam.removeTargetLane(highlightedLane);
                selectedCam.updateLocationToCenter(); // 중앙 다시 맞추기!
                selectionManager.setHighlightedLane(null);
            } else {
                // 카메라 본체 삭제
                roadManager.removeCamera(selectedCam);
            }
        }
        // 3. 교차로 연결선 삭제
        else if (selectedConn != null && selectedLane != null) {
            junctionController.deleteConnection(selectedLane, selectedConn);
        } 
        // 4. 차선 삭제
        else if (selectedLane != null && selectedRoad != null) {
            int laneIdx = selectedRoad.getLaneNum(selectedLane);
            if (laneIdx != -1) {
                junctionController.removeLaneConnections(selectedLane);
                selectedRoad.deleteLane(laneIdx);
                selectedRoad.refresh();
            }
        } 
        // 5. 도로 삭제 (연쇄 삭제 로직 포함)
        else if (selectedRoad != null) {
            List<Lane> lanesToDelete = selectedRoad.getLaneList();
            
            // 신호등 정리
            List<TrafficLight> lightsToRemove = new java.util.ArrayList<>();
            for (TrafficLight tl : roadManager.getTrafficLightList()) {
                for (Lane lane : lanesToDelete) tl.removeControlLane(lane);
                if (tl.getControlLaneList().isEmpty()) lightsToRemove.add(tl);
                else tl.updatePositionToLanesCenter();
            }
            for (TrafficLight tl : lightsToRemove) roadManager.removeTrafficLight(tl);

            // 카메라 정리 ❤️
            List<Camera> camerasToRemove = new java.util.ArrayList<>();
            for (Camera cam : roadManager.getCameraList()) {
                for (Lane lane : lanesToDelete) cam.removeTargetLane(lane);
                if (cam.getTargetLaneMap().isEmpty()) camerasToRemove.add(cam);
                else cam.updateLocationToCenter();
            }
            for (Camera cam : camerasToRemove) roadManager.removeCamera(cam);

            for (Lane lane : selectedRoad.getLaneList()) junctionController.removeLaneConnections(lane);
            roadManager.removeRoad(selectedRoad);
        }

        selectionManager.clearSelection();
        onUpdate.run();
    }
}
