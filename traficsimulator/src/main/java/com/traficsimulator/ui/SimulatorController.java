package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.Road;
import com.traficsimulator.road.traficlight.TraficLight;
import com.traficsimulator.road.camera.Camera; // 추가 ❤️

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.geometry.Insets;
import javafx.scene.input.MouseEvent;

import java.awt.geom.Point2D;

public class SimulatorController {

    @FXML private BorderPane rootPane;
    @FXML private TreeView<String> componentTreeView;
    @FXML private Pane centerContainer;
    @FXML private Pane canvasContainer;
    @FXML private Canvas mainCanvas;
    @FXML private GridPane propertyGrid;

    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Label statusLabel;
    @FXML private Label tickLabel;

    private CanvasRenderer renderer;
    private RoadManager roadManager;
    private SelectionManager selectionManager;
    private PropertyManager propertyManager;
    private JunctionController junctionController;
    private com.traficsimulator.road.traficlight.TraficLightController traficLightController;
    private com.traficsimulator.util.GlobalTimer globalTimer;
    private RoadDrawingTool drawingTool;
    private RoadMoveTool moveTool;
    private RoadEditTool editTool;
    private EditorModeManager modeManager;
    private KeyboardHandler keyboardHandler;
    private CanvasTransformHandler transformHandler;

    private boolean isSimulationRunning = false; 
    private boolean isDraggingObject = false; // 신호등/카메라 드래그용 ❤️
    private double cameraX = 0;
    private double cameraY = 0;
    private double zoomFactor = 1.0;
    private RoadManager.PointHit hoveredPoint;

    @FXML
    public void initialize() {
        roadManager = new RoadManager();
        selectionManager = new SelectionManager();
        junctionController = new JunctionController();
        traficLightController = new com.traficsimulator.road.traficlight.TraficLightController();
        
        globalTimer = new com.traficsimulator.util.GlobalTimer(1.0);
        globalTimer.addTickListener(traficLightController);
        globalTimer.addTickListener(() -> {
            javafx.application.Platform.runLater(() -> {
                tickLabel.setText("Ticks: " + globalTimer.getTotalTicks());
                requestRender();
            });
        });

        renderer = new CanvasRenderer(mainCanvas);
        drawingTool = new RoadDrawingTool(roadManager, junctionController, this::requestRender);
        
        moveTool = new RoadMoveTool(() -> {
            roadManager.refreshStaticObjectPositions();
            refreshAllConnections();
            requestRender();
        });
        editTool = new RoadEditTool(() -> {
            roadManager.refreshStaticObjectPositions();
            refreshAllConnections();
            requestRender();
        });

        modeManager = new EditorModeManager(componentTreeView);
        keyboardHandler = new KeyboardHandler(modeManager, drawingTool, roadManager, selectionManager, junctionController, this::requestRender);
        propertyManager = new PropertyManager(propertyGrid, junctionController, selectionManager, roadManager, () -> {
            roadManager.refreshStaticObjectPositions();
            refreshAllConnections();
            requestRender();
        });
        
        mainCanvas.widthProperty().bind(centerContainer.widthProperty());
        mainCanvas.heightProperty().bind(centerContainer.heightProperty());

        transformHandler = new CanvasTransformHandler(centerContainer, this::updateTransform);

        selectionManager.setOnSelectionChanged(() -> {
            propertyManager.updateProperties(selectionManager.getSelectedObject(), selectionManager.getSelectedRoad());
        });

        setupComponentTree();
        setupCanvasEvents();
        
        if (rootPane != null) {
            keyboardHandler.install(rootPane);
        }

        requestRender(); 
    }

    @FXML
    private void handleStartSimulation() {
        if (isSimulationRunning) return;
        selectionManager.clearSelection();
        isSimulationRunning = true;
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        statusLabel.setText("Simulation Running... (Editing Locked)");
        statusLabel.setStyle("-fx-text-fill: #2ecc71;");
        
        traficLightController.getTraficLights().clear();
        for (TraficLight tl : roadManager.getTraficLightList()) {
            tl.resetCurrentTick();
            traficLightController.addTraficLight(tl);
        }
        globalTimer.start();
        requestRender();
    }

    @FXML
    private void handleStopSimulation() {
        if (!isSimulationRunning) return;
        isSimulationRunning = false;
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        statusLabel.setText("Editor Mode");
        statusLabel.setStyle("-fx-text-fill: #888888;");
        
        globalTimer.stop();
        globalTimer.reset();
        for (TraficLight tl : roadManager.getTraficLightList()) {
            tl.resetCurrentTick();
        }
        tickLabel.setText("Ticks: 0");
        requestRender();
    }

    private void refreshAllConnections() {
        for (Road road : roadManager.getRoadList()) {
            for (Lane lane : road.getLaneList()) {
                junctionController.refreshConnections(lane);
            }
        }
    }

    private void updateTransform(double x, double y, double zoom) {
        this.cameraX = x;
        this.cameraY = y;
        this.zoomFactor = zoom;
        requestRender();
    }

    private void setupComponentTree() {
        TreeItem<String> root = new TreeItem<>("Components");
        root.setExpanded(true);
        root.getChildren().add(new TreeItem<>("Road"));
        root.getChildren().add(new TreeItem<>("Traffic Light"));
        root.getChildren().add(new TreeItem<>("Camera")); // 추가 ❤️
        componentTreeView.setRoot(root);
        componentTreeView.setShowRoot(false);
    }

    private void setupCanvasEvents() {
        mainCanvas.setOnMouseMoved(e -> {
            if (isSimulationRunning) return;
            if (modeManager.getMode() == EditorMode.SELECT) {
                Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);
                RoadManager.PointHit hit = roadManager.findNearestPoint(worldPt, 10.0 / zoomFactor);
                if (hit != null && hit.road != selectionManager.getSelectedRoad()) {
                    hit = null;
                }
                if (hit != hoveredPoint) {
                    hoveredPoint = hit;
                    requestRender();
                }
            }
        });

        mainCanvas.setOnMousePressed(e -> {
            mainCanvas.requestFocus();
            if (isSimulationRunning) {
                transformHandler.handleMousePressed(e);
                return;
            }
            Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);
            if (e.getButton() == MouseButton.PRIMARY) {
                boolean shiftDown = e.isShiftDown();
                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMousePressed(e, "Road", cameraX, cameraY, zoomFactor);
                } else if (modeManager.getMode() == EditorMode.ADD_TRAFFIC_LIGHT) {
                    RoadManager.HitResult hit = roadManager.findHit(worldPt);
                    if (hit.lane != null) {
                        TraficLight tl = new TraficLight();
                        tl.addControlLane(hit.lane);
                        tl.setCoordinates(worldPt);
                        tl.updatePositionToLanesCenter();
                        roadManager.addTraficLight(tl);
                        selectionManager.selectTraficLight(tl);
                        modeManager.setMode(EditorMode.SELECT); 
                    }
                } else if (modeManager.getMode() == EditorMode.ADD_CAMERA) { // 카메라 추가 ❤️
                    RoadManager.HitResult hit = roadManager.findHit(worldPt);
                    if (hit.lane != null) {
                        Camera cam = new Camera(hit.road, worldPt, hit.lane);
                        roadManager.addCamera(cam);
                        selectionManager.selectCamera(cam);
                        modeManager.setMode(EditorMode.SELECT);
                    }
                } else {
                    // 1. 포인트 핸들
                    RoadManager.PointHit pointHit = roadManager.findNearestPoint(worldPt, 10.0 / zoomFactor);
                    if (pointHit != null && pointHit.road == selectionManager.getSelectedRoad() && !pointHit.road.isLock() && !shiftDown) {
                        editTool.startEditing(pointHit.road, pointHit.type);
                    } else {
                        // 2. 신호등 체크
                        TraficLight tlHit = roadManager.findTraficLightHit(worldPt, 15.0 / zoomFactor);
                        if (tlHit != null) {
                            selectionManager.selectTraficLight(tlHit);
                            isDraggingObject = true;
                        } else {
                            // 3. 카메라 체크 ❤️
                            Camera camHit = roadManager.findCameraHit(worldPt, 15.0 / zoomFactor);
                            if (camHit != null) {
                                selectionManager.selectCamera(camHit);
                                isDraggingObject = true;
                            } else {
                                // 4. 도로/차선
                                RoadManager.HitResult hit = roadManager.findHit(worldPt);
                                if (hit.road != null) {
                                    if (shiftDown) {
                                        if (hit.lane != null) selectionManager.selectLane(hit.road, hit.lane, true);
                                    } else {
                                        if (selectionManager.getSelectedRoad() == hit.road) {
                                            if (e.getClickCount() == 2 && hit.lane != null) selectionManager.selectLane(hit.road, hit.lane, false);
                                            if (!hit.road.isLock()) moveTool.startMoving(hit.road, worldPt);
                                        } else {
                                            if (e.getClickCount() == 2) selectionManager.selectRoad(hit.road);
                                            else if (hit.lane != null) selectionManager.selectLane(hit.road, hit.lane, false);
                                            else selectionManager.selectRoad(hit.road);
                                            if (selectionManager.getSelectedRoad() == hit.road && !hit.road.isLock()) moveTool.startMoving(hit.road, worldPt);
                                        }
                                    }
                                } else {
                                    if (!shiftDown) selectionManager.clearSelection();
                                }
                            }
                        }
                    }
                    requestRender();
                }
            }
            transformHandler.handleMousePressed(e);
        });

        mainCanvas.setOnMouseDragged(e -> {
            if (isSimulationRunning) {
                transformHandler.handleMouseDragged(e);
                return;
            }
            Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);
            if (e.getButton() == MouseButton.PRIMARY) {
                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMouseDragged(e, cameraX, cameraY, zoomFactor);
                } else if (editTool.isEditing()) {
                    editTool.handleMouseDragged(worldPt);
                    roadManager.refreshStaticObjectPositions();
                } else if (moveTool.isMoving()) {
                    moveTool.handleMouseDragged(worldPt);
                    roadManager.refreshStaticObjectPositions();
                } else if (isDraggingObject) {
                    if (selectionManager.getSelectedTraficLight() != null) {
                        selectionManager.getSelectedTraficLight().setCoordinates(worldPt);
                    } else if (selectionManager.getSelectedCamera() != null) {
                        // 자석처럼 도로 위 스냅! ❤️
                        Point2D.Double snapped = roadManager.findNearestPointOnAnyRoad(worldPt);
                        selectionManager.getSelectedCamera().setLoc(snapped);
                    }
                }
            }
            transformHandler.handleMouseDragged(e);
            requestRender();
        });

        mainCanvas.setOnMouseReleased(e -> {
            if (isSimulationRunning) return;
            if (e.getButton() == MouseButton.PRIMARY) {
                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMouseReleased(e, cameraX, cameraY, zoomFactor);
                }
                editTool.stopEditing();
                moveTool.stopMoving();
                isDraggingObject = false;
            }
            requestRender();
        });
    }

    private void requestRender() {
        renderer.render(
            roadManager.getRoadList(),
            roadManager.getTraficLightList(),
            roadManager.getCameraList(), // 카메라 추가 ❤️
            drawingTool.getDragStart(),
            drawingTool.getCurrentMouse(),
            drawingTool.isDrawing(),
            cameraX, cameraY, zoomFactor,
            selectionManager,
            hoveredPoint,
            junctionController
        );
    }
}
