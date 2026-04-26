package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.Road;
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
        Lane highlightedLane = selectionManager.getHighlightedLane(); // 추가 ❤️
        LaneConnection selectedConn = selectionManager.getSelectedConnection();
        Lane selectedLane = selectionManager.getSelectedLane();
        Road selectedRoad = selectionManager.getSelectedRoad();

        // 1. 신호등 관련 삭제 ❤️
        if (selectedTL != null) {
            if (highlightedLane != null) {
                // 리스트에서 선택된 차선만 등록 해제!
                selectedTL.removeControlLane(highlightedLane);
                selectedTL.updatePositionToLanesCenter();
                selectionManager.setHighlightedLane(null);
                System.out.println("[KeyboardHandler] Unregistered Lane from Traffic Light.");
            } else {
                // 강조된 차선이 없으면 신호등 전체 삭제
                roadManager.removeTraficLight(selectedTL);
                System.out.println("[KeyboardHandler] Deleted Traffic Light.");
            }
        }
        // 2. 교차로 연결선 삭제
        else if (selectedConn != null && selectedLane != null) {
            junctionController.deleteConnection(selectedLane, selectedConn);
            System.out.println("[KeyboardHandler] Deleted Connection.");
        } 
        // 3. 차선 삭제
        else if (selectedLane != null && selectedRoad != null) {
            int laneIdx = selectedRoad.getLaneNum(selectedLane);
            if (laneIdx != -1) {
                junctionController.removeLaneConnections(selectedLane);
                selectedRoad.deleteLane(laneIdx);
                selectedRoad.refresh();
                System.out.println("[KeyboardHandler] Deleted Lane " + laneIdx);
            }
        } 
        // 4. 도로 삭제
        else if (selectedRoad != null) {
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
