package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class KeyboardHandler {
    private final EditorModeManager modeManager;
    private final RoadDrawingTool drawingTool;
    private final RoadManager roadManager;
    private final SelectionManager selectionManager;
    private final JunctionController junctionController; // 추가 ❤️
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
        LaneConnection selectedConn = selectionManager.getSelectedConnection();
        Lane selectedLane = selectionManager.getSelectedLane();
        Road selectedRoad = selectionManager.getSelectedRoad();

        // 1. 교차로 연결선 삭제 (최우선)
        if (selectedConn != null && selectedLane != null) {
            junctionController.deleteConnection(selectedLane, selectedConn);
            System.out.println("[KeyboardHandler] Deleted Connection.");
        } 
        // 2. 차선 삭제
        else if (selectedLane != null && selectedRoad != null) {
            int laneIdx = selectedRoad.getLaneNum(selectedLane);
            if (laneIdx != -1) {
                // 삭제 전 연결 정보 청소! ❤️
                junctionController.removeLaneConnections(selectedLane);
                selectedRoad.deleteLane(laneIdx);
                selectedRoad.refresh();
                System.out.println("[KeyboardHandler] Deleted Lane " + laneIdx);
            }
        } 
        // 3. 도로 삭제
        else if (selectedRoad != null) {
            // 도로 안의 모든 차선 연결 정보 청소! ❤️
            for (Lane lane : selectedRoad.getLaneList()) {
                junctionController.removeLaneConnections(lane);
            }
            roadManager.removeRoad(selectedRoad);
            System.out.println("[KeyboardHandler] Deleted Road and all its connections.");
        }

        selectionManager.clearSelection();
        onUpdate.run();
    }
}
