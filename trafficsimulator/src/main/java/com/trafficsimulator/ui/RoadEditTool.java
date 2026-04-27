package com.trafficsimulator.ui;

import com.trafficsimulator.road.Road;
import java.awt.geom.Point2D;

public class RoadEditTool {
    private Road roadToEdit;
    private RoadManager.PointType pointType;
    private final Runnable onUpdate;

    public RoadEditTool(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void startEditing(Road road, RoadManager.PointType type) {
        this.roadToEdit = road;
        this.pointType = type;
    }

    public void handleMouseDragged(Point2D.Double currentWorldPt) {
        if (roadToEdit != null && pointType != null) {
            switch (pointType) {
                case START -> roadToEdit.setStartPoint(currentWorldPt);
                case END -> roadToEdit.setEndPoint(currentWorldPt);
                case CONTROL1 -> roadToEdit.setCurved(true, currentWorldPt, roadToEdit.getControl2());
                case CONTROL2 -> roadToEdit.setCurved(true, roadToEdit.getControl1(), currentWorldPt);
            }
            onUpdate.run();
        }
    }

    public void stopEditing() {
        this.roadToEdit = null;
        this.pointType = null;
    }

    public boolean isEditing() {
        return roadToEdit != null;
    }
}
