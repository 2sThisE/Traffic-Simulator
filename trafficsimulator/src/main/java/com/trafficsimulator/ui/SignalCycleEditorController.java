package com.trafficsimulator.ui;

import com.trafficsimulator.road.trafficlight.SignalSetting;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SignalCycleEditorController {

    @FXML private ListView<SignalSetting> phaseListView;
    @FXML private TextField durationField;
    @FXML private FlowPane signalsFlowPane;
    @FXML private Button updatePhaseBtn;
    @FXML private Label warningLabel; 

    private TrafficLight trafficLight;
    private Runnable onSave;
    private final ObservableList<SignalSetting> tempPhases = FXCollections.observableArrayList();
    private final List<CheckBox> signalCheckBoxes = new ArrayList<>();

    @FXML
    public void initialize() {
        // 모든 신호 타입에 대한 체크박스 생성
        for (TrafficLightSignal sig : TrafficLightSignal.values()) {
            CheckBox cb = new CheckBox(sig.name());
            cb.setOnAction(e -> updateWarning()); 
            signalCheckBoxes.add(cb);
            signalsFlowPane.getChildren().add(cb);
        }

        phaseListView.setItems(tempPhases);
        phaseListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SignalSetting item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    String signals = Arrays.stream(item.trafficLightSignals())
                                          .map(Enum::name)
                                          .collect(Collectors.joining(", "));
                    // 틱 대신 초 단위로 표시 ❤️
                    setText(String.format("Phase %d: [%s] (%.1f sec)", getIndex() + 1, signals, item.durationSeconds()));
                }
            }
        });

        phaseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                durationField.setText(String.valueOf(newV.durationSeconds()));
                List<TrafficLightSignal> currentSignals = Arrays.asList(newV.trafficLightSignals());
                for (CheckBox cb : signalCheckBoxes) {
                    cb.setSelected(currentSignals.contains(TrafficLightSignal.valueOf(cb.getText())));
                }
                updatePhaseBtn.setDisable(false);
                updateWarning(); 
            } else {
                updatePhaseBtn.setDisable(true);
                if (warningLabel != null) warningLabel.setVisible(false);
            }
        });
    }

    private void updateWarning() {
        if (warningLabel == null) return;

        boolean hasYellow = false;
        boolean hasRed = false;

        for (CheckBox cb : signalCheckBoxes) {
            if (cb.isSelected()) {
                if ("YELLOW".equals(cb.getText())) hasYellow = true;
                if ("RED".equals(cb.getText())) hasRed = true;
            }
        }

        if (hasYellow && !hasRed) {
            warningLabel.setText("⚠️ 경고: 황색 단독 신호는 '직진 차량'의 통행을 허용합니다! \n좌회전 신호 이후 '좌회전 차량만' 정리하려면 [RED + YELLOW]를 선택하세요.");
            warningLabel.setVisible(true);
        } else {
            warningLabel.setVisible(false);
        }
    }

    public void initData(TrafficLight tl, Runnable onSave) {
        this.trafficLight = tl;
        this.onSave = onSave;
        
        if (tl.getSignalTime() != null) {
            tempPhases.addAll(tl.getSignalTime());
        }

        for (CheckBox cb : signalCheckBoxes) {
            TrafficLightSignal sig = TrafficLightSignal.valueOf(cb.getText());
            cb.setDisable(!tl.getLightList().contains(sig));
        }
    }

    @FXML
    private void handleMoveUp() {
        int idx = phaseListView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            Collections.swap(tempPhases, idx, idx - 1);
            phaseListView.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void handleMoveDown() {
        int idx = phaseListView.getSelectionModel().getSelectedIndex();
        if (idx != -1 && idx < tempPhases.size() - 1) {
            Collections.swap(tempPhases, idx, idx + 1);
            phaseListView.getSelectionModel().select(idx + 1);
        }
    }

    @FXML
    private void handleAddPhase() {
        // 기본값을 3.0초로 추가 ❤️
        tempPhases.add(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 3.0));
        phaseListView.getSelectionModel().selectLast();
    }

    @FXML
    private void handleRemovePhase() {
        int idx = phaseListView.getSelectionModel().getSelectedIndex();
        if (idx != -1) tempPhases.remove(idx);
    }

    @FXML
    private void handleUpdatePhase() {
        int idx = phaseListView.getSelectionModel().getSelectedIndex();
        if (idx != -1) {
            try {
                // 초(Seconds) 유효성 검사 ❤️
                String durationText = durationField.getText();
                if (durationText == null || durationText.trim().isEmpty()) {
                    return;
                }
                
                double seconds = Double.parseDouble(durationText.trim());
                if (seconds <= 0) {
                    return;
                }

                List<TrafficLightSignal> selected = new ArrayList<>();
                for (CheckBox cb : signalCheckBoxes) {
                    if (cb.isSelected()) selected.add(TrafficLightSignal.valueOf(cb.getText()));
                }
                
                if (selected.isEmpty()) {
                    return;
                }

                tempPhases.set(idx, new SignalSetting(selected.toArray(new TrafficLightSignal[0]), seconds));
                phaseListView.refresh();
                
            } catch (NumberFormatException e) {
                showAlert("Invalid Input", "Please enter a valid number for seconds.");
            }
        }
    }

    @FXML
    private void handleSave() {
        if (tempPhases.isEmpty()) {
            showAlert("No Phases", "Please add at least one signal phase.");
            return;
        }
        trafficLight.addSignalLoop(new ArrayList<>(tempPhases));
        if (onSave != null) onSave.run();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) phaseListView.getScene().getWindow()).close();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
