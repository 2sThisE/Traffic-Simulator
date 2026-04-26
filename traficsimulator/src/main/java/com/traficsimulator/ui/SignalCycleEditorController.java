package com.traficsimulator.ui;

import com.traficsimulator.road.traficlight.SignalSetting;
import com.traficsimulator.road.traficlight.TraficLight;
import com.traficsimulator.road.traficlight.TraficLightSignal;
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

    private TraficLight traficLight;
    private Runnable onSave;
    private final ObservableList<SignalSetting> tempPhases = FXCollections.observableArrayList();
    private final List<CheckBox> signalCheckBoxes = new ArrayList<>();

    @FXML
    public void initialize() {
        // 모든 신호 타입에 대한 체크박스 생성
        for (TraficLightSignal sig : TraficLightSignal.values()) {
            CheckBox cb = new CheckBox(sig.name());
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
                    String signals = Arrays.stream(item.traficLightSignals())
                                          .map(Enum::name)
                                          .collect(Collectors.joining(", "));
                    setText(String.format("Phase %d: [%s] (%d ticks)", getIndex() + 1, signals, item.tick()));
                }
            }
        });

        phaseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                durationField.setText(String.valueOf(newV.tick()));
                List<TraficLightSignal> currentSignals = Arrays.asList(newV.traficLightSignals());
                for (CheckBox cb : signalCheckBoxes) {
                    cb.setSelected(currentSignals.contains(TraficLightSignal.valueOf(cb.getText())));
                }
                updatePhaseBtn.setDisable(false);
            } else {
                updatePhaseBtn.setDisable(true);
            }
        });
    }

    public void initData(TraficLight tl, Runnable onSave) {
        this.traficLight = tl;
        this.onSave = onSave;
        
        // 1. 기존 신호 복사해서 가져오기
        if (tl.getSignalTime() != null) {
            tempPhases.addAll(tl.getSignalTime());
        }

        // 2. 신호등에 등록된 신호만 활성화 ❤️
        for (CheckBox cb : signalCheckBoxes) {
            TraficLightSignal sig = TraficLightSignal.valueOf(cb.getText());
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
        tempPhases.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 10));
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
                // 1. 기간(Ticks) 유효성 검사 ❤️
                String durationText = durationField.getText();
                if (durationText == null || durationText.trim().isEmpty()) {
                    return;
                }
                
                int ticks = Integer.parseInt(durationText.trim());
                if (ticks <= 0) {
                    return;
                }

                // 2. 신호 선택 유효성 검사 ❤️
                List<TraficLightSignal> selected = new ArrayList<>();
                for (CheckBox cb : signalCheckBoxes) {
                    if (cb.isSelected()) selected.add(TraficLightSignal.valueOf(cb.getText()));
                }
                
                if (selected.isEmpty()) {
                    return;
                }

                // 모든 검사를 통과하면 업데이트!
                tempPhases.set(idx, new SignalSetting(selected.toArray(new TraficLightSignal[0]), ticks));
                phaseListView.refresh();
                
            } catch (NumberFormatException e) {
                showAlert("Invalid Input", "Please enter a valid number for ticks.");
            }
        }
    }

    @FXML
    private void handleSave() {
        if (tempPhases.isEmpty()) {
            showAlert("No Phases", "Please add at least one signal phase.");
            return;
        }
        traficLight.addSignalLoop(new ArrayList<>(tempPhases));
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
