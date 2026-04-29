package com.trafficsimulator.ui;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

class RoadManagerDeletionTest {

    @Test
    void removeLaneClearsConnectionsAndRegisteredObjects() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road roadA = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        roadA.addLane(true, 0);
        Road roadB = new Road(new Point2D.Double(100, 0), new Point2D.Double(200, 0), false);
        roadB.addLane(true, 0);
        roadManager.addRoad(roadA);
        roadManager.addRoad(roadB);

        Lane laneA = roadA.getLane(0);
        Lane laneB = roadB.getLane(0);
        junctionController.addConnection(laneA, laneB, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(laneB);
        roadManager.addTrafficLight(trafficLight);

        Camera camera = new Camera(roadB, laneB.getLanePath().get(0), laneB);
        roadManager.addCamera(camera);

        Lane removedLane = roadManager.removeLane(roadB, 0, junctionController);

        assertSame(laneB, removedLane);
        assertTrue(roadB.getLaneList().isEmpty());
        assertNull(junctionController.getConnectionList(laneA));
        assertTrue(roadManager.getTrafficLightList().isEmpty());
        assertTrue(roadManager.getCameraList().isEmpty());
    }

    @Test
    void removeRoadClearsAllLaneConnections() {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road roadA = new Road(new Point2D.Double(0, 0), new Point2D.Double(100, 0), false);
        roadA.addLane(true, 0);
        Road roadB = new Road(new Point2D.Double(100, 0), new Point2D.Double(200, 0), false);
        roadB.addLane(true, 0);
        Road roadC = new Road(new Point2D.Double(200, 0), new Point2D.Double(300, 0), false);
        roadC.addLane(true, 0);
        roadManager.addRoad(roadA);
        roadManager.addRoad(roadB);
        roadManager.addRoad(roadC);

        Lane laneA = roadA.getLane(0);
        Lane laneB = roadB.getLane(0);
        Lane laneC = roadC.getLane(0);
        junctionController.addConnection(laneA, laneB, LaneType.STRAIGHT);
        junctionController.addConnection(laneB, laneC, LaneType.STRAIGHT);

        roadManager.removeRoad(roadB, junctionController);

        assertFalse(roadManager.getRoadList().contains(roadB));
        assertNull(junctionController.getConnectionList(laneA));
        assertNull(junctionController.getConnectionList(laneB));
    }
}
