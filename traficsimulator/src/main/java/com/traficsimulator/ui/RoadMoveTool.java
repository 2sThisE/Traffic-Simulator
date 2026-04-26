package com.traficsimulator.ui;

import com.traficsimulator.road.Road;
import javafx.scene.input.MouseEvent;
import java.awt.geom.Point2D;

public class RoadMoveTool {
    private Road roadToMove;
    private Point2D.Double lastWorldPt;
    private final Runnable onUpdate;

    public RoadMoveTool(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void startMoving(Road road, Point2D.Double startWorldPt) {
        this.roadToMove = road;
        this.lastWorldPt = startWorldPt;
    }

    public void handleMouseDragged(Point2D.Double currentWorldPt) {
        if (roadToMove != null && lastWorldPt != null) {
            double dx = currentWorldPt.x - lastWorldPt.x;
            double dy = currentWorldPt.y - lastWorldPt.y;

            roadToMove.move(dx, dy);

            this.lastWorldPt = currentWorldPt;
            onUpdate.run();
        }
    }

    public void stopMoving() {
        this.roadToMove = null;
        this.lastWorldPt = null;
    }

    public boolean isMoving() {
        return roadToMove != null;
    }
}
