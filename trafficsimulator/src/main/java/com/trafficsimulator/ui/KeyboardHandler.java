package com.trafficsimulator.ui;

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
        Camera selectedCam = selectionManager.getSelectedCamera();
        Lane highlightedLane = selectionManager.getHighlightedLane();
        LaneConnection selectedConn = selectionManager.getSelectedConnection();
        Lane selectedLane = selectionManager.getSelectedLane();
        Road selectedRoad = selectionManager.getSelectedRoad();

        if (selectedTL != null) {
            if (highlightedLane != null) {
                selectedTL.removeControlLane(highlightedLane);
                selectedTL.updatePositionToLanesCenter();
                selectionManager.setHighlightedLane(null);
            } else {
                roadManager.removeTrafficLight(selectedTL);
            }
        } else if (selectedCam != null) {
            if (highlightedLane != null) {
                selectedCam.removeTargetLane(highlightedLane);
                selectedCam.updateLocationToCenter();
                selectionManager.setHighlightedLane(null);
            } else {
                roadManager.removeCamera(selectedCam);
            }
        } else if (selectedConn != null && selectedLane != null) {
            junctionController.deleteConnection(selectedLane, selectedConn);
        } else if (selectedLane != null && selectedRoad != null) {
            int laneIdx = selectedRoad.getLaneNum(selectedLane);
            roadManager.removeLane(selectedRoad, laneIdx, junctionController);
        } else if (selectedRoad != null) {
            roadManager.removeRoad(selectedRoad, junctionController);
        }

        selectionManager.clearSelection();
        onUpdate.run();
    }
}
