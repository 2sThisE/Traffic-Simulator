package com.trafficsimulator.ui;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightController;
import com.trafficsimulator.road.camera.Camera; // 추가 ❤️
import com.trafficsimulator.util.GlobalTimer;

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
    private TrafficLightController trafficLightController;
    private GlobalTimer globalTimer;
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
        trafficLightController = new TrafficLightController();
        
        globalTimer = new GlobalTimer();
        globalTimer.addTickListener(trafficLightController);
        globalTimer.addTickListener(() -> {
            javafx.application.Platform.runLater(() -> {
                double seconds = com.trafficsimulator.util.GlobalTimer.ticksToSeconds(globalTimer.getTotalTicks());
                tickLabel.setText(String.format("Time: %.1fs", seconds));
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
        
        trafficLightController.getTrafficLights().clear();
        for (TrafficLight tl : roadManager.getTrafficLightList()) {
            tl.resetCurrentTick();
            trafficLightController.addTrafficLight(tl);
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
        for (TrafficLight tl : roadManager.getTrafficLightList()) {
            tl.resetCurrentTick();
        }
        tickLabel.setText("Time: 0.0s");
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
                        TrafficLight tl = new TrafficLight();
                        tl.addControlLane(hit.lane);
                        tl.setCoordinates(worldPt);
                        tl.updatePositionToLanesCenter();
                        roadManager.addTrafficLight(tl);
                        selectionManager.selectTrafficLight(tl);
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
                        TrafficLight tlHit = roadManager.findTrafficLightHit(worldPt, 15.0 / zoomFactor);
                        if (tlHit != null) {
                            selectionManager.selectTrafficLight(tlHit);
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
                    if (selectionManager.getSelectedTrafficLight() != null) {
                        selectionManager.getSelectedTrafficLight().setCoordinates(worldPt);
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
            roadManager.getTrafficLightList(),
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
