package com.trafficsimulator.ui;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;

public class CanvasTransformHandler {
    private final Pane centerContainer;
    
    private double cameraX = 0;
    private double cameraY = 0;
    private double zoomFactor = 1.0;
    
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 10.0;

    private double lastMouseX;
    private double lastMouseY;

    public interface TransformListener {
        void onTransformChanged(double x, double y, double zoom);
    }

    private final TransformListener listener;

    public CanvasTransformHandler(Pane centerContainer, TransformListener listener) {
        this.centerContainer = centerContainer;
        this.listener = listener;
        
        setupZoom();
    }

    private void setupZoom() {
        centerContainer.setOnScroll((ScrollEvent event) -> {
            // 이제 Ctrl 없이 휠만 돌려도 줌!
            double delta = event.getDeltaY();
            if (delta > 0) {
                zoomFactor = Math.min(MAX_ZOOM, zoomFactor * 1.1);
            } else {
                zoomFactor = Math.max(MIN_ZOOM, zoomFactor / 1.1);
            }
            
            notifyListener();
            event.consume();
        });
    }

    public void handleMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY) { // 오른쪽 클릭일 때만 시작점 저장
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
        }
    }

    public void handleMouseDragged(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY) { // 오른쪽 클릭 드래그일 때만 이동
            double deltaX = e.getSceneX() - lastMouseX;
            double deltaY = e.getSceneY() - lastMouseY;

            cameraX += deltaX;
            cameraY += deltaY;

            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
            
            notifyListener();
        }
    }

    private void notifyListener() {
        listener.onTransformChanged(cameraX, cameraY, zoomFactor);
    }
}
