package com.traficsimulator.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TreeView;

public class EditorModeManager {
    private final ObjectProperty<EditorMode> currentMode = new SimpleObjectProperty<>(EditorMode.SELECT);
    private final TreeView<String> treeView;

    public EditorModeManager(TreeView<String> treeView) {
        this.treeView = treeView;
        
        // 트리뷰 선택 변경 감지해서 모드 자동 전환
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String value = newVal.getValue();
                if ("Road".equals(value)) {
                    setMode(EditorMode.DRAW_ROAD);
                } else if ("Traffic Light".equals(value)) {
                    setMode(EditorMode.ADD_TRAFFIC_LIGHT);
                } else if ("Camera".equals(value)) { // 카메라 모드 추가 ❤️
                    setMode(EditorMode.ADD_CAMERA);
                } else {
                    setMode(EditorMode.SELECT);
                }
            }
        });
    }

    public void setMode(EditorMode mode) {
        if (currentMode.get() == mode) return; 
        
        currentMode.set(mode);
        
        // SELECT 모드로 바뀔 때만 선택 해제
        // JavaFX 내부 버그(IndexOutOfBounds) 방지를 위해 
        // 선택이 실제 있는 경우에만 안전하게 해제 ❤️
        if (mode == EditorMode.SELECT) {
            javafx.application.Platform.runLater(() -> {
                if (!treeView.getSelectionModel().isEmpty()) {
                    treeView.getSelectionModel().clearSelection();
                }
            });
        }
    }

    public EditorMode getMode() {
        return currentMode.get();
    }

    public ObjectProperty<EditorMode> modeProperty() {
        return currentMode;
    }
}
