package com.trafficsimulator.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.SignalSetting;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.util.UnitConverter;

class ProjectSaveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAllMainProjectComponentsAsJson() throws Exception {
        RoadManager roadManager = new RoadManager();
        JunctionController junctionController = new JunctionController();

        Road firstRoad = new Road(new Point2D.Double(0, 0), new Point2D.Double(UnitConverter.toPixel(100.0), 0), true);
        firstRoad.setLimitSpeed(60);
        firstRoad.addLane(true, 0);
        Road secondRoad = new Road(new Point2D.Double(UnitConverter.toPixel(100.0), 0), new Point2D.Double(UnitConverter.toPixel(200.0), 0), true);
        secondRoad.setLimitSpeed(60);
        secondRoad.addLane(true, 0);
        roadManager.addRoad(firstRoad);
        roadManager.addRoad(secondRoad);

        Lane firstLane = firstRoad.getLane(0);
        Lane secondLane = secondRoad.getLane(0);
        junctionController.addConnection(firstLane, secondLane, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(firstLane);
        trafficLight.setCoordinates(new Point2D.Double(UnitConverter.toPixel(100.0), 0));
        trafficLight.addSignalLoop(List.of(new SignalSetting(new TrafficLightSignal[]{TrafficLightSignal.RED}, 10.0)));
        roadManager.addTrafficLight(trafficLight);

        Camera camera = new Camera(firstRoad, new Point2D.Double(UnitConverter.toPixel(50.0), 0), firstLane);
        roadManager.addCamera(camera);

        Path savePath = tempDir.resolve("project.traffic.json");
        new ProjectSaveService().save(savePath, roadManager, junctionController, 10.0, 20.0, 1.5);

        String saved = Files.readString(savePath);
        assertTrue(saved.contains("\"roads\""));
        assertTrue(saved.contains("\"connections\""));
        assertTrue(saved.contains("\"trafficLights\""));
        assertTrue(saved.contains("\"cameras\""));
        assertFalse(saved.contains("\"vehicles\""));
        assertTrue(saved.contains("\"viewport\""));
    }

    @Test
    void loadsSavedProjectBackIntoManagers() throws Exception {
        RoadManager sourceRoadManager = new RoadManager();
        JunctionController sourceJunctionController = new JunctionController();

        Road road = new Road(new Point2D.Double(0, 0), new Point2D.Double(UnitConverter.toPixel(100.0), 0), true);
        road.setLimitSpeed(60);
        road.addLane(true, 0);
        road.addLane(true, 1);
        sourceRoadManager.addRoad(road);

        Lane lane0 = road.getLane(0);
        Lane lane1 = road.getLane(1);
        sourceJunctionController.addConnection(lane0, lane1, LaneType.STRAIGHT);

        TrafficLight trafficLight = new TrafficLight();
        trafficLight.addControlLane(lane0);
        trafficLight.setCoordinates(new Point2D.Double(100.0, 200.0));
        sourceRoadManager.addTrafficLight(trafficLight);

        Camera camera = new Camera(road, new Point2D.Double(300.0, lane0.getLanePath().get(0).y), lane0);
        sourceRoadManager.addCamera(camera);

        Path savePath = tempDir.resolve("roundtrip.traffic.json");
        new ProjectSaveService().save(savePath, sourceRoadManager, sourceJunctionController, 11.0, 22.0, 1.25);

        RoadManager loadedRoadManager = new RoadManager();
        JunctionController loadedJunctionController = new JunctionController();
        LoadedProject loaded = new ProjectLoadService().load(savePath, loadedRoadManager, loadedJunctionController);

        assertEquals(1, loadedRoadManager.getRoadList().size());
        assertEquals(2, loadedRoadManager.getRoadList().get(0).getLaneList().size());
        assertEquals(1, loadedRoadManager.getTrafficLightList().size());
        assertEquals(1, loadedRoadManager.getCameraList().size());
        assertEquals(11.0, loaded.cameraX(), 0.001);
        assertEquals(22.0, loaded.cameraY(), 0.001);
        assertEquals(1.25, loaded.zoomFactor(), 0.001);
        assertTrue(loadedJunctionController.getConnectionList(loadedRoadManager.getRoadList().get(0).getLane(0)) != null);
    }
}
