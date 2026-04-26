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
                } else {
                    setMode(EditorMode.SELECT);
                }
            }
        });
    }

    public void setMode(EditorMode mode) {
        if (currentMode.get() == mode) return; // 같은 모드면 무시 ❤️
        
        currentMode.set(mode);
        
        // SELECT 모드로 바뀔 때만 선택 해제하되, 이미 해제된 상태면 무시
        if (mode == EditorMode.SELECT && !treeView.getSelectionModel().isEmpty()) {
            // JavaFX 스레드 충돌 방지를 위해 체크 후 해제
            treeView.getSelectionModel().clearSelection();
        }
    }

    public EditorMode getMode() {
        return currentMode.get();
    }

    public ObjectProperty<EditorMode> modeProperty() {
        return currentMode;
    }
}
