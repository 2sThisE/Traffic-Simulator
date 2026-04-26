package com.traficsimulator.ui;

import com.traficsimulator.road.JunctionController;
import com.traficsimulator.road.Lane;
import com.traficsimulator.road.LaneConnection;
import com.traficsimulator.road.LaneType;
import com.traficsimulator.road.Road;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

public class PropertyManager {
    private final GridPane grid;
    private final Runnable onPropertyChanged;
    private final JunctionController junctionController;
    private final SelectionManager selectionManager;

    public PropertyManager(GridPane grid, JunctionController jc, SelectionManager sm, Runnable onPropertyChanged) {
        this.grid = grid;
        this.junctionController = jc;
        this.selectionManager = sm;
        this.onPropertyChanged = onPropertyChanged;
    }

    public void updateProperties(Object selected, Road parentRoad) {
        grid.getChildren().clear();
        if (selected == null) return;

        if (selected instanceof Road road) {
            renderRoadProperties(road);
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
        
        addInfo("Total Length", String.format("%.1f px", road.getRoadLength()));
        
        addProperty("Speed Limit (km/h)", String.valueOf(road.getLimitSpeed()), true, val -> {
            try {
                road.setLimitSpeed(Integer.parseInt(val));
            } catch (NumberFormatException e) {
            }
        });

        addInfo("Lane Width", String.format("%.1f px", road.getLaneWidth()));
        
        addSeparator();
        
        // --- 도로 잠금 기능 (Is Locked) --- ❤️
        addCheckBox("Is Locked", road.isLock(), selected -> {
            road.setMoveable(selected); // Road 클래스의 메서드명이 setMoveable임
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
        
        // --- 차선 추가 기능 복구! --- ❤️
        addTitle("Lanes Management");
        
        HBox addButtons = new HBox(5);
        Button addUp = new Button("+ Lane (Up)");
        addUp.setOnAction(e -> {
            road.addLane(true, road.getLaneList().size());
            refreshAll(road);
        });
        
        Button addDown = new Button("+ Lane (Down)");
        addDown.setOnAction(e -> {
            road.addLane(false, road.getLaneList().size());
            refreshAll(road);
        });
        addButtons.getChildren().addAll(addUp, addDown);
        grid.add(addButtons, 0, grid.getRowCount(), 2, 1);

        // 현재 차선 목록 및 삭제 버튼
        List<Lane> lanes = road.getLaneList();
        for (int i = 0; i < lanes.size(); i++) {
            int index = i;
            Lane lane = lanes.get(i);
            String labelStr = String.format("Lane %d (%s)", i, lane.isRoadDirection() ? "Up" : "Down");
            
            Button delBtn = new Button("Del");
            delBtn.setStyle("-fx-text-fill: red;");
            delBtn.setOnAction(e -> {
                // 삭제 전 연결 정보 청소! ❤️
                junctionController.removeLaneConnections(lane);
                road.deleteLane(index);
                refreshAll(road);
            });
            
            int row = grid.getRowCount();
            grid.add(new Label(labelStr), 0, row);
            grid.add(delBtn, 1, row);
        }
    }

    private void renderLaneProperties(Lane lane, Road road) {
        addTitle("Lane Properties");
        
        addInfo("Length", String.format("%.1f px", lane.getLaneLength()));

        CheckBox dirCb = new CheckBox("Direction (Up)");
        dirCb.setSelected(lane.isRoadDirection());
        dirCb.setOnAction(e -> {
            lane.setRoadDirection(dirCb.isSelected());
            if (road != null) refreshAll(road);
            else onPropertyChanged.run();
        });
        grid.add(dirCb, 0, grid.getRowCount(), 2, 1);
        
        // --- 부모 도로 선택 기능 복구! --- ❤️
        if (road != null) {
            addSeparator();
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
        road.refresh(); // 모델 갱신
        updateProperties(road, null); // 속성창 갱신
        onPropertyChanged.run(); // 캔버스 갱신
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
