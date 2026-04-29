package com.trafficsimulator.ui;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.util.Navigate;
import com.trafficsimulator.util.UnitConverter;
import com.trafficsimulator.vehicle.VehicleType;
import com.trafficsimulator.debug.Vehicle;

import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trafficsimulator.road.camera.Camera; // 추가 ❤️

public class PropertyManager {
    private final GridPane grid;
    private final Runnable onPropertyChanged;
    private final JunctionController junctionController;
    private final SelectionManager selectionManager;
    private final RoadManager roadManager;
    private final Map<TrafficLight, Stage> openEditors = new HashMap<>(); 

    public PropertyManager(GridPane grid, JunctionController jc, SelectionManager sm, RoadManager rm, Runnable onPropertyChanged) {
        this.grid = grid;
        this.junctionController = jc;
        this.selectionManager = sm;
        this.roadManager = rm;
        this.onPropertyChanged = onPropertyChanged;
    }

    public void updateProperties(Object selected, Road parentRoad) {
        grid.getChildren().clear();
        if (selected == null) return;

        if (selected instanceof Road road) {
            renderRoadProperties(road);
        } else if (selected instanceof TrafficLight tl) {
            renderTrafficLightProperties(tl);
        } else if (selected instanceof Camera cam) { // 카메라 속성창 연결 ❤️
            renderCameraProperties(cam);
        } else if (selected instanceof Vehicle vehicle) {
            renderVehicleProperties(vehicle);
        } else if (selected instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Lane) {
            @SuppressWarnings("unchecked")
            List<Lane> lanes = (List<Lane>) list;
            if (lanes.size() >= 2) {
                renderJunctionSetup(lanes);
            } else if (lanes.size() == 1) {
                renderLaneProperties(lanes.get(0), parentRoad);
            }
        } else if (selected instanceof Lane lane) {
            renderLaneProperties(lane, parentRoad);
        }
    }

    /**
     * 차량 속성창을 렌더링합니다. ❤️
     */
    private void renderVehicleProperties(Vehicle vehicle) {
        addTitle("Vehicle Properties");
        
        addInfo("Type", vehicle.getType().name());
        addInfo("Position", String.format("%.1f, %.1f", vehicle.getX(), vehicle.getY()));
        addProperty("Speed (km/h)", String.valueOf(vehicle.getSpeedKmh()), true, val -> {
            try {
                vehicle.setSpeedKmh(Double.parseDouble(val));
            } catch (NumberFormatException e) {
            }
        });

        addSeparator();
        
        // 1. 경로 지우기 버튼
        Button clearPathBtn = new Button("Clear Current Path");
        clearPathBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        clearPathBtn.setMaxWidth(Double.MAX_VALUE);
        clearPathBtn.setOnAction(e -> {
            vehicle.getPath().clear();
            vehicle.getLogicalRoute().clear(); // 논리적 경로도 함께 지워야 자유로운 배치가 가능함 ❤️
            onPropertyChanged.run();
            updateProperties(vehicle, null);
        });
        grid.add(clearPathBtn, 0, grid.getRowCount(), 2, 1);

        addSeparator();
        
        // 2. 가능한 경로 리스트 (현재 위치 기반)
        addTitle("Available Routes from Current Lane");
        
        RoadManager.HitResult hit = roadManager.findHit(new Point2D.Double(vehicle.getX(), vehicle.getY()));
        if (hit.lane != null) {
            List<List<Set<Lane>>> allRoutes = Navigate.calculateAllRoutes(hit.lane, roadManager, junctionController);
            if (!allRoutes.isEmpty()) {
                ListView<List<Set<Lane>>> listView = new ListView<>(FXCollections.observableArrayList(allRoutes));
                listView.setPrefHeight(200);
                listView.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(List<Set<Lane>> item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            // 경로 요약 표시 (예: "Route 1: [Road A] -> [Road B]")
                            StringBuilder sb = new StringBuilder("Route " + (getIndex() + 1) + ": ");
                            for (int i = 0; i < item.size(); i++) {
                                Set<Lane> phase = item.get(i);
                                Lane l = phase.iterator().next();
                                Road r = roadManager.findRoadByLane(l);
                                if (r != null) {
                                    sb.append(r.hashCode() % 100);
                                    if (i < item.size() - 1) sb.append(" -> ");
                                }
                            }
                            setText(sb.toString());
                        }
                    }
                });
                
                listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                    if (newV != null) {
                        List<Point2D.Double> pathPoints = Navigate.generateTotalPathPoints(newV, junctionController);
                        vehicle.setPath(pathPoints);
                        vehicle.setLogicalRoute(newV); // 논리적 경로 정보 추가 저장 ❤️
                        onPropertyChanged.run();
                    }
                });
                grid.add(listView, 0, grid.getRowCount(), 2, 1);
            } else {
                grid.add(new Label("No routes found from this lane."), 0, grid.getRowCount(), 2, 1);
            }
        } else {
            grid.add(new Label("Vehicle is not on any lane."), 0, grid.getRowCount(), 2, 1);
        }
    }

    /**
     * 카메라 속성창을 렌더링합니다. ❤️
     */
    private void renderCameraProperties(Camera cam) {
        addTitle("Enforcement Camera");
        
        addInfo("Position", String.format("%.1f, %.1f", cam.getLoc().x, cam.getLoc().y));
        
        // 1. 단속 속도 수정
        addProperty("Speed Limit (km/h)", String.valueOf(cam.getLimitSpeed()), true, val -> {
            try {
                cam.setLimitSpeed(Integer.parseInt(val));
            } catch (NumberFormatException e) {
            }
        });

        addSeparator();
        
        // 2. 감시 차선 등록 버튼
        List<Lane> selectedLanes = selectionManager.getSelectedLanes();
        if (!selectedLanes.isEmpty()) {
            Button regBtn = new Button("Register " + selectedLanes.size() + " Lanes to Camera");
            regBtn.setStyle("-fx-background-color: #273c75; -fx-text-fill: white; -fx-font-weight: bold;");
            regBtn.setMaxWidth(Double.MAX_VALUE);
            regBtn.setOnAction(e -> {
                for (Lane l : selectedLanes) {
                    cam.addTargetLane(l);
                }
                onPropertyChanged.run();
                updateProperties(cam, null);
            });
            grid.add(regBtn, 0, grid.getRowCount(), 2, 1);
            addSeparator();
        }

        // 3. 감시 중인 차선 리스트
        addTitle("Monitored Lanes");
        Set<Lane> registeredLanes = cam.getTargetLaneMap().keySet();
        if (!registeredLanes.isEmpty()) {
            ListView<Lane> listView = new ListView<>(FXCollections.observableArrayList(registeredLanes));
            listView.setPrefHeight(120);
            listView.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Lane item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setText(null);
                    else setText("Lane ID: " + (item.hashCode() % 1000) + " (" + (item.isRoadDirection() ? "Up" : "Down") + ")");
                }
            });
            listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                selectionManager.setHighlightedLane(newV); // 리스트 아이템 클릭 시 해당 차선 강조 ❤️
                onPropertyChanged.run();
            });
            grid.add(listView, 0, grid.getRowCount(), 2, 1);
        } else {
            grid.add(new Label("No lanes registered."), 0, grid.getRowCount(), 2, 1);
        }

        addSeparator();
        
        Button delCamBtn = new Button("Delete This Camera");
        delCamBtn.setStyle("-fx-background-color: #e84118; -fx-text-fill: white; -fx-font-weight: bold;");
        delCamBtn.setMaxWidth(Double.MAX_VALUE);
        delCamBtn.setOnAction(e -> {
            roadManager.removeCamera(cam);
            selectionManager.clearSelection();
            onPropertyChanged.run();
        });
        grid.add(delCamBtn, 0, grid.getRowCount(), 2, 1);
    }

    private void renderTrafficLightProperties(TrafficLight tl) {
        addTitle("Traffic Light Properties");
        
        if (tl.getCoordinates() != null) {
            addInfo("Position", String.format("%.1f, %.1f", tl.getCoordinates().x, tl.getCoordinates().y));
        }

        addSeparator();
        
        List<Lane> selectedLanes = selectionManager.getSelectedLanes();
        if (!selectedLanes.isEmpty()) {
            Button regBtn = new Button("Register " + selectedLanes.size() + " Selected Lanes");
            regBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
            regBtn.setMaxWidth(Double.MAX_VALUE);
            regBtn.setOnAction(e -> {
                for (Lane l : selectedLanes) {
                    boolean alreadyRegistered = false;
                    for (TrafficLight otherTL : roadManager.getTrafficLightList()) {
                        if (otherTL.getControlLaneList().contains(l)) {
                            alreadyRegistered = true;
                            break;
                        }
                    }
                    if (!alreadyRegistered) {
                        tl.addControlLane(l);
                    }
                }
                tl.updatePositionToLanesCenter();
                onPropertyChanged.run();
                updateProperties(tl, null);
            });
            grid.add(regBtn, 0, grid.getRowCount(), 2, 1);
            addSeparator();
        }

        addTitle("Registered Lanes");
        Set<Lane> registeredLanes = tl.getControlLaneList();
        if (registeredLanes != null && !registeredLanes.isEmpty()) {
            ListView<Lane> listView = new ListView<>(FXCollections.observableArrayList(registeredLanes));
            listView.setPrefHeight(120);
            listView.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Lane item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setText(null);
                    else setText("Lane ID: " + (item.hashCode() % 1000) + " (" + (item.isRoadDirection() ? "Up" : "Down") + ")");
                }
            });
            listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                selectionManager.setHighlightedLane(newV);
                onPropertyChanged.run();
            });
            grid.add(listView, 0, grid.getRowCount(), 2, 1);
        } else {
            grid.add(new Label("No lanes registered."), 0, grid.getRowCount(), 2, 1);
        }

        addSeparator();
        
        addTitle("Available Signals");
        for (TrafficLightSignal signal : TrafficLightSignal.values()) {
            CheckBox signalCb = new CheckBox(signal.name());
            signalCb.setSelected(tl.getLightList().contains(signal));
            signalCb.setOnAction(e -> {
                if (signalCb.isSelected()) tl.addLight(signal);
                else tl.deleteLight(signal);
                onPropertyChanged.run();
            });
            grid.add(signalCb, 0, grid.getRowCount(), 2, 1);
        }

        addSeparator();
        
        Button editCycleBtn = new Button("Edit Signal Cycle Details");
        editCycleBtn.setMaxWidth(Double.MAX_VALUE);
        editCycleBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        editCycleBtn.setOnAction(e -> showSignalCycleEditor(tl));
        grid.add(editCycleBtn, 0, grid.getRowCount(), 2, 1);
    }

    private void showSignalCycleEditor(TrafficLight tl) {
        if (openEditors.containsKey(tl)) {
            Stage existingStage = openEditors.get(tl);
            if (existingStage.isShowing()) {
                existingStage.toFront();
                return;
            } else {
                openEditors.remove(tl);
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/trafficsimulator/ui/signal_cycle_editor.fxml"));
            Parent root = loader.load();
            
            SignalCycleEditorController controller = loader.getController();
            controller.initData(tl, onPropertyChanged);
            
            Stage stage = new Stage();
            stage.initModality(Modality.NONE); 
            stage.setTitle("Cycle Editor - Light@" + Integer.toHexString(tl.hashCode()));
            stage.setScene(new Scene(root));
            
            stage.setOnCloseRequest(e -> openEditors.remove(tl));
            
            stage.show();
            openEditors.put(tl, stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void renderJunctionSetup(List<Lane> lanes) {
        addTitle("Junction Setup");
        
        Lane from = lanes.get(0);
        Lane to = lanes.get(lanes.size() - 1);
        
        addInfo("From Lane", "ID: " + from.hashCode() % 1000);
        addInfo("To Lane", "ID: " + to.hashCode() % 1000);
        
        addSeparator();
        
        grid.add(new Label("Connection Type:"), 0, grid.getRowCount());
        ComboBox<LaneType> typeBox = new ComboBox<>(FXCollections.observableArrayList(LaneType.values()));
        typeBox.setValue(LaneType.STRAIGHT);
        grid.add(typeBox, 1, grid.getRowCount() - 1);
        
        Button regBtn = new Button("Register Connection");
        regBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        regBtn.setMaxWidth(Double.MAX_VALUE);
        regBtn.setOnAction(e -> {
            junctionController.addConnection(from, to, typeBox.getValue());
            onPropertyChanged.run();
        });
        grid.add(regBtn, 0, grid.getRowCount(), 2, 1);
    }

    private void renderRoadProperties(Road road) {
        addTitle("Road Properties");
        
        double lengthKm = UnitConverter.toMeter(road.getRoadLength()) / 1000.0;
        addInfo("Total Length", String.format("%.3f km", lengthKm));
        
        addProperty("Speed Limit (km/h)", String.valueOf(road.getLimitSpeed()), true, val -> {
            try {
                road.setLimitSpeed(Integer.parseInt(val));
            } catch (NumberFormatException e) {
            }
        });

        addInfo("Lane Width", String.format("%.1f px", road.getLaneWidth()));
        
        addSeparator();
        
        addCheckBox("Is Locked", road.isLock(), selected -> {
            road.setMoveable(selected);
            refreshAll(road);
        });

        boolean curved = road.getPathPoints().size() > 2;
        addCheckBox("Is Curved", curved, selected -> {
            if (selected) {
                Point2D.Double p1 = road.getStartPoint();
                Point2D.Double p2 = road.getEndPoint();
                Point2D.Double c1 = new Point2D.Double(p1.x + (p2.x - p1.x) * 0.3, p1.y + (p2.y - p1.y) * 0.3 - 50);
                Point2D.Double c2 = new Point2D.Double(p1.x + (p2.x - p1.x) * 0.6, p1.y + (p2.y - p1.y) * 0.6 + 50);
                road.setCurved(true, c1, c2);
            } else {
                road.setCurved(false, null, null);
            }
            refreshAll(road);
        });

        addSeparator();
        addTitle("Lanes Management");
        
        HBox addButtons = new HBox(5);
        Button addUp = new Button("+ Lane (Up)");
        addUp.setOnAction(e -> {
            road.addLane(true, road.getLaneList().size());
            refreshAll(road);
        });
        
        Button addDown = new Button("+ Lane (Down)");
        addDown.setOnAction(e -> {
            road.addLane(false, 0);
            refreshAll(road);
        });
        addButtons.getChildren().addAll(addUp, addDown);
        grid.add(addButtons, 0, grid.getRowCount(), 2, 1);

        List<Lane> lanes = road.getLaneList();
        for (int i = 0; i < lanes.size(); i++) {
            int index = i;
            Lane lane = lanes.get(i);
            String labelStr = String.format("Lane %d (%s)", i, lane.isRoadDirection() ? "Up" : "Down");
            
            Button delBtn = new Button("Del");
            delBtn.setStyle("-fx-text-fill: red;");
            delBtn.setOnAction(e -> {
                roadManager.removeLane(road, index, junctionController);
                refreshAll(road);
            });
            
            int row = grid.getRowCount();
            grid.add(new Label(labelStr), 0, row);
            grid.add(delBtn, 1, row);
        }
    }

    private void renderLaneProperties(Lane lane, Road road) {
        addTitle("Lane Properties");
        
        double lengthKm = UnitConverter.toMeter(lane.getLaneLength()) / 1000.0;
        addInfo("Length", String.format("%.3f km", lengthKm));

        CheckBox dirCb = new CheckBox("Direction (Up)");
        dirCb.setSelected(lane.isRoadDirection());
        dirCb.setOnAction(e -> {
            lane.setRoadDirection(dirCb.isSelected());
            if (road != null) refreshAll(road);
            else onPropertyChanged.run();
        });
        grid.add(dirCb, 0, grid.getRowCount(), 2, 1);

        // --- 정지선 표시 설정 추가! ❤️ ---
        CheckBox stopLineCb = new CheckBox("Show Stop Line");
        stopLineCb.setSelected(lane.isDrawStopLine());
        stopLineCb.setOnAction(e -> {
            lane.setDrawStopLine(stopLineCb.isSelected());
            onPropertyChanged.run();
        });
        grid.add(stopLineCb, 0, grid.getRowCount(), 2, 1);

        addSeparator();
        
        // --- 허용 차량 종류 설정 (VehicleType) ❤️ ---
        addTitle("Allowed Vehicles");
        for (VehicleType vt : VehicleType.values()) {
            CheckBox vtCb = new CheckBox(vt.name());
            vtCb.setSelected(lane.getAllowVehicle().contains(vt)); 
            vtCb.setOnAction(e -> {
                if (vtCb.isSelected()) {
                    lane.setAllowVehicle(vt);
                } else {
                    lane.removeAllowVehicle(vt);
                }
                onPropertyChanged.run();
            });
            grid.add(vtCb, 0, grid.getRowCount(), 2, 1);
        }
        
        if (road != null) {
            addSeparator();
            
            Button delLaneBtn = new Button("Delete This Lane");
            delLaneBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
            delLaneBtn.setMaxWidth(Double.MAX_VALUE);
            delLaneBtn.setOnAction(e -> {
                int idx = road.getLaneNum(lane);
                if (idx != -1) {
                    roadManager.removeLane(road, idx, junctionController);
                    selectionManager.clearSelection();
                    refreshAll(road);
                }
            });
            grid.add(delLaneBtn, 0, grid.getRowCount(), 2, 1);

            Button selectRoadBtn = new Button("Select Parent Road");
            selectRoadBtn.setMaxWidth(Double.MAX_VALUE);
            selectRoadBtn.setOnAction(e -> {
                selectionManager.selectRoad(road);
            });
            grid.add(selectRoadBtn, 0, grid.getRowCount(), 2, 1);
        }

        addSeparator();
        addTitle("Registered Connections");
        
        Set<LaneConnection> conns = junctionController.getConnectionList(lane);
        if (conns != null && !conns.isEmpty()) {
            ListView<LaneConnection> listView = new ListView<>(FXCollections.observableArrayList(conns));
            listView.setPrefHeight(150);
            listView.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(LaneConnection item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setText(null);
                    else setText(item.laneType() + " -> ID:" + item.targetLane().hashCode() % 1000);
                }
            });
            listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                selectionManager.selectConnection(newV);
                onPropertyChanged.run();
            });
            grid.add(listView, 0, grid.getRowCount(), 2, 1);
        } else {
            grid.add(new Label("No connections found."), 0, grid.getRowCount(), 2, 1);
        }
    }

    private void refreshAll(Road road) {
        road.refresh();
        updateProperties(road, null);
        onPropertyChanged.run();
    }

    private void addTitle(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0; -fx-font-size: 13px;");
        grid.add(label, 0, grid.getRowCount(), 2, 1);
    }

    private void addInfo(String name, String value) {
        int row = grid.getRowCount();
        grid.add(new Label(name + ":"), 0, row);
        Label valLabel = new Label(value);
        valLabel.setStyle("-fx-text-fill: #555;");
        grid.add(valLabel, 1, row);
    }

    private void addProperty(String name, String value, boolean editable, java.util.function.Consumer<String> onApply) {
        int row = grid.getRowCount();
        grid.add(new Label(name + ":"), 0, row);
        
        if (editable) {
            javafx.scene.control.TextField tf = new javafx.scene.control.TextField(value);
            tf.setPrefWidth(100);
            tf.setOnAction(e -> {
                onApply.accept(tf.getText());
                onPropertyChanged.run();
            });
            tf.focusedProperty().addListener((obs, oldV, newV) -> {
                if (!newV) {
                    onApply.accept(tf.getText());
                    onPropertyChanged.run();
                }
            });
            grid.add(tf, 1, row);
        } else {
            Label valLabel = new Label(value);
            valLabel.setStyle("-fx-text-fill: #7f8c8d;");
            grid.add(valLabel, 1, row);
        }
    }

    private void addSeparator() {
        grid.add(new Separator(), 0, grid.getRowCount(), 2, 1);
    }

    private void addCheckBox(String name, boolean selected, java.util.function.Consumer<Boolean> onApply) {
        int row = grid.getRowCount();
        CheckBox cb = new CheckBox(name);
        cb.setSelected(selected);
        cb.setOnAction(e -> {
            onApply.accept(cb.isSelected());
            onPropertyChanged.run();
        });
        grid.add(cb, 0, row, 2, 1);
    }
}
