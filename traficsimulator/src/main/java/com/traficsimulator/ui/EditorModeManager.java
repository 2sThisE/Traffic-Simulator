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
            if (newVal != null && "Road".equals(newVal.getValue())) {
                setMode(EditorMode.DRAW_ROAD);
            } else {
                setMode(EditorMode.SELECT);
            }
        });
    }

    public void setMode(EditorMode mode) {
        currentMode.set(mode);
        if (mode == EditorMode.SELECT) {
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
