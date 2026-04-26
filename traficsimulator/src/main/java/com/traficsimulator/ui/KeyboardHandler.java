package com.traficsimulator.ui;

import java.util.List;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import com.traficsimulator.road.camera.Camera;
import com.traficsimulator.road.traficlight.TraficLight;
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
        TraficLight selectedTL = selectionManager.getSelectedTraficLight();
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
                roadManager.removeTraficLight(selectedTL);
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
            List<TraficLight> lightsToRemove = new java.util.ArrayList<>();
            for (TraficLight tl : roadManager.getTraficLightList()) {
                for (Lane lane : lanesToDelete) tl.removeControlLane(lane);
                if (tl.getControlLaneList().isEmpty()) lightsToRemove.add(tl);
                else tl.updatePositionToLanesCenter();
            }
            for (TraficLight tl : lightsToRemove) roadManager.removeTraficLight(tl);

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
