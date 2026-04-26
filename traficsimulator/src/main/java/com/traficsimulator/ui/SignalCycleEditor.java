package com.traficsimulator.ui;

import com.traficsimulator.road.traficlight.SignalSetting;
import com.traficsimulator.road.traficlight.TraficLight;
import com.traficsimulator.road.traficlight.TraficLightSignal;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 신호등의 상세 신호 주기를 설정하는 에디터 창입니다. ❤️
 */
public class SignalCycleEditor {
    private final TraficLight traficLight;
    private final Runnable onSave;
    private final ObservableList<SignalSetting> phases;

    public SignalCycleEditor(TraficLight tl, Runnable onSave) {
        this.traficLight = tl;
        this.onSave = onSave;
        
        // 현재 신호등이 가진 루프 정보를 복사해서 리스트로 만듦
        // (직접 수정하면 Cancel 했을 때 곤란하니까! ❤️)
        this.phases = FXCollections.observableArrayList();
        // 기존 신호등의 내부 리스트에 접근하기 위해 리플렉션을 쓰거나 getter가 필요함
        // 일단 TraficLight에 signalTime getter가 있다고 가정하고 진행할게!
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Edit Signal Cycle");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // --- 1. 왼쪽: 페이즈 리스트 ---
        ListView<SignalSetting> listView = new ListView<>(phases);
        listView.setPrefWidth(250);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SignalSetting item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    String signals = Arrays.toString(item.traficLightSignals());
                    setText(String.format("Phase %d: %s (%d ticks)", getIndex() + 1, signals, item.tick()));
                }
            }
        });

        // --- 2. 오른쪽: 상세 편집 영역 ---
        VBox editorArea = new VBox(15);
        editorArea.setPadding(new Insets(0, 0, 0, 15));
        editorArea.setAlignment(Pos.TOP_LEFT);

        Label durationLabel = new Label("Duration (Ticks):");
        TextField durationField = new TextField();
        
        Label signalsLabel = new Label("Select Signals for this Phase:");
        VBox signalCheckBoxes = new VBox(5);
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (TraficLightSignal sig : TraficLightSignal.values()) {
            CheckBox cb = new CheckBox(sig.name());
            checkBoxes.add(cb);
            signalCheckBoxes.getChildren().add(cb);
        }

        Button updateBtn = new Button("Update Selected Phase");
        updateBtn.setMaxWidth(Double.MAX_VALUE);
        updateBtn.setDisable(true);

        editorArea.getChildren().addAll(durationLabel, durationField, signalsLabel, signalCheckBoxes, updateBtn);

        // 리스트 선택 시 편집기에 정보 로드
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                durationField.setText(String.valueOf(newV.tick()));
                List<TraficLightSignal> currentSignals = Arrays.asList(newV.traficLightSignals());
                for (CheckBox cb : checkBoxes) {
                    cb.setSelected(currentSignals.contains(TraficLightSignal.valueOf(cb.getText())));
                }
                updateBtn.setDisable(false);
            } else {
                updateBtn.setDisable(true);
            }
        });

        // 페이즈 업데이트 로직
        updateBtn.setOnAction(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx != -1) {
                try {
                    int ticks = Integer.parseInt(durationField.getText());
                    List<TraficLightSignal> selected = new ArrayList<>();
                    for (CheckBox cb : checkBoxes) {
                        if (cb.isSelected()) selected.add(TraficLightSignal.valueOf(cb.getText()));
                    }
                    if (selected.isEmpty()) selected.add(TraficLightSignal.RED); // 기본값

                    phases.set(idx, new SignalSetting(selected.toArray(new TraficLightSignal[0]), ticks));
                    listView.refresh();
                } catch (NumberFormatException ex) {
                    // 에러 처리는 자기를 위해 생략(?) ❤️
                }
            }
        });

        // --- 3. 하단: 리스트 제어 및 최종 저장 버튼 ---
        HBox listControls = new HBox(10);
        listControls.setPadding(new Insets(10, 0, 0, 0));
        Button addBtn = new Button("Add Phase");
        Button delBtn = new Button("Remove Phase");
        listControls.getChildren().addAll(addBtn, delBtn);

        addBtn.setOnAction(e -> phases.add(new SignalSetting(new TraficLightSignal[]{TraficLightSignal.RED}, 10)));
        delBtn.setOnAction(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx != -1) phases.remove(idx);
        });

        VBox leftArea = new VBox(5, listView, listControls);
        root.setLeft(leftArea);
        root.setCenter(editorArea);

        HBox bottomBar = new HBox(15);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(15, 0, 0, 0));
        Button saveBtn = new Button("Apply to Traffic Light");
        saveBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        Button cancelBtn = new Button("Cancel");
        
        saveBtn.setOnAction(e -> {
            traficLight.addSignalLoop(new ArrayList<>(phases));
            onSave.run();
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());

        bottomBar.getChildren().addAll(cancelBtn, saveBtn);
        root.setBottom(bottomBar);

        stage.setScene(new Scene(root, 600, 450));
        stage.show();
    }
}
