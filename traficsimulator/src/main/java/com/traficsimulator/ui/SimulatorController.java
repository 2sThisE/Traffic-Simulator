package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.Road;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

import java.awt.geom.Point2D;

public class SimulatorController {

    @FXML
    private BorderPane rootPane;
    @FXML
    private TreeView<String> componentTreeView;
    @FXML
    private Pane centerContainer;
    @FXML
    private Pane canvasContainer;
    @FXML
    private Canvas mainCanvas;
    @FXML
    private GridPane propertyGrid;

    private CanvasRenderer renderer;
    private RoadManager roadManager;
    private SelectionManager selectionManager;
    private PropertyManager propertyManager;
    private JunctionController junctionController;
    private RoadDrawingTool drawingTool;
    private RoadMoveTool moveTool;
    private RoadEditTool editTool;
    private EditorModeManager modeManager;
    private KeyboardHandler keyboardHandler;
    private CanvasTransformHandler transformHandler;

    private double cameraX = 0;
    private double cameraY = 0;
    private double zoomFactor = 1.0;
    private RoadManager.PointHit hoveredPoint;

    @FXML
    public void initialize() {
        roadManager = new RoadManager();
        selectionManager = new SelectionManager();
        junctionController = new JunctionController();
        renderer = new CanvasRenderer(mainCanvas);
        drawingTool = new RoadDrawingTool(roadManager, junctionController, this::requestRender);
        
        moveTool = new RoadMoveTool(() -> {
            refreshAllConnections();
            requestRender();
        });
        editTool = new RoadEditTool(() -> {
            refreshAllConnections();
            requestRender();
        });

        modeManager = new EditorModeManager(componentTreeView);
        keyboardHandler = new KeyboardHandler(modeManager, drawingTool, roadManager, selectionManager, junctionController, this::requestRender);
        propertyManager = new PropertyManager(propertyGrid, junctionController, selectionManager, () -> {
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
        componentTreeView.setRoot(root);
        componentTreeView.setShowRoot(false);
    }

    private void setupCanvasEvents() {
        mainCanvas.setOnMouseMoved(e -> {
            if (modeManager.getMode() == EditorMode.SELECT) {
                Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);
                RoadManager.PointHit hit = roadManager.findNearestPoint(worldPt, 10.0 / zoomFactor);
                
                // 점 조절 효과 강화: 선택된 도로의 점일 때만 호버링 표시 ❤️
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
            Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);

            if (e.getButton() == MouseButton.PRIMARY) {
                boolean shiftDown = e.isShiftDown();

                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMousePressed(e, "Road", cameraX, cameraY, zoomFactor);
                } else {
                    // 1. 점 편집 시도 (선택된 도로의 점만 + 잠금 체크) ❤️
                    RoadManager.PointHit pointHit = roadManager.findNearestPoint(worldPt, 10.0 / zoomFactor);
                    if (pointHit != null && pointHit.road == selectionManager.getSelectedRoad() && !pointHit.road.isLock() && !shiftDown) {
                        editTool.startEditing(pointHit.road, pointHit.type);
                    } else {
                        // 2. 일반 선택 및 이동
                        RoadManager.HitResult hit = roadManager.findHit(worldPt);
                        if (hit.road != null) {
                            if (shiftDown) {
                                if (hit.lane != null) selectionManager.selectLane(hit.road, hit.lane, true);
                            } else {
                                if (e.getClickCount() == 2) {
                                    selectionManager.selectRoad(hit.road);
                                } else {
                                    if (hit.lane != null) {
                                        selectionManager.selectLane(hit.road, hit.lane, false);
                                    } else {
                                        selectionManager.selectRoad(hit.road);
                                    }
                                }
                                // 이동도 잠기지 않았을 때만 시작 ❤️
                                if (!hit.road.isLock()) {
                                    moveTool.startMoving(hit.road, worldPt);
                                }
                            }
                        } else {
                            if (!shiftDown) selectionManager.clearSelection();
                        }
                    }
                    requestRender();
                }
            }
            transformHandler.handleMousePressed(e);
        });

        mainCanvas.setOnMouseDragged(e -> {
            Point2D.Double worldPt = drawingTool.screenToWorld(e.getX(), e.getY(), cameraX, cameraY, zoomFactor);
            
            if (e.getButton() == MouseButton.PRIMARY) {
                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMouseDragged(e, cameraX, cameraY, zoomFactor);
                } else if (editTool.isEditing()) {
                    editTool.handleMouseDragged(worldPt);
                } else if (moveTool.isMoving()) {
                    moveTool.handleMouseDragged(worldPt);
                }
            }
            transformHandler.handleMouseDragged(e);
        });

        mainCanvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (modeManager.getMode() == EditorMode.DRAW_ROAD) {
                    drawingTool.handleMouseReleased(e, cameraX, cameraY, zoomFactor);
                }
                editTool.stopEditing();
                moveTool.stopMoving();
            }
        });
    }

    private void requestRender() {
        renderer.render(
            roadManager.getRoadList(),
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
