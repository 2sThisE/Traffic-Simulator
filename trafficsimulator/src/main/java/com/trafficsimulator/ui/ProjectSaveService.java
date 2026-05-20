package com.trafficsimulator.ui;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneConnection;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.SignalSetting;
import com.trafficsimulator.road.trafficlight.TrafficLight;

public class ProjectSaveService {
    private static final int SCHEMA_VERSION = 1;

    public void save(Path path,
                     RoadManager roadManager,
                     JunctionController junctionController,
                     double cameraX,
                     double cameraY,
                     double zoomFactor) throws IOException {
        String json = toJson(roadManager, junctionController, cameraX, cameraY, zoomFactor);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private String toJson(RoadManager roadManager,
                          JunctionController junctionController,
                          double cameraX,
                          double cameraY,
                          double zoomFactor) {
        List<Road> roads = roadManager.getRoadList();
        Map<Road, String> roadIds = new IdentityHashMap<>();
        Map<Lane, String> laneIds = new IdentityHashMap<>();
        indexRoadsAndLanes(roads, roadIds, laneIds);

        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.name("schemaVersion").value(SCHEMA_VERSION);
        json.name("savedAt").value(Instant.now().toString());
        json.name("viewport").beginObject()
                .name("cameraX").value(cameraX)
                .name("cameraY").value(cameraY)
                .name("zoomFactor").value(zoomFactor)
                .endObject();
        writeRoads(json.name("roads"), roads, roadIds, laneIds);
        writeConnections(json.name("connections"), junctionController, laneIds);
        writeTrafficLights(json.name("trafficLights"), roadManager.getTrafficLightList(), laneIds);
        writeCameras(json.name("cameras"), roadManager.getCameraList(), roadIds, laneIds);
        json.endObject();
        return json.toString();
    }

    private void indexRoadsAndLanes(List<Road> roads, Map<Road, String> roadIds, Map<Lane, String> laneIds) {
        for (int roadIndex = 0; roadIndex < roads.size(); roadIndex++) {
            Road road = roads.get(roadIndex);
            String roadId = "road-" + roadIndex;
            roadIds.put(road, roadId);
            List<Lane> lanes = road.getLaneList();
            for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
                laneIds.put(lanes.get(laneIndex), roadId + "-lane-" + laneIndex);
            }
        }
    }

    private void writeRoads(JsonWriter json, List<Road> roads, Map<Road, String> roadIds, Map<Lane, String> laneIds) {
        json.beginArray();
        for (Road road : roads) {
            json.beginObject();
            json.name("id").value(roadIds.get(road));
            json.name("startPoint");
            writePoint(json, road.getStartPoint());
            json.name("endPoint");
            writePoint(json, road.getEndPoint());
            json.name("control1");
            writePoint(json, road.getControl1());
            json.name("control2");
            writePoint(json, road.getControl2());
            json.name("oneWay").value(road.isOneWay());
            json.name("curved").value(road.isCurved());
            json.name("locked").value(road.isLock());
            json.name("limitSpeed").value(road.getLimitSpeed());
            json.name("roadLength").value(road.getRoadLength());
            json.name("laneWidth").value(road.getLaneWidth());
            json.name("pathPoints");
            writePoints(json, road.getPathPoints());
            writeLanes(json.name("lanes"), road.getLaneList(), laneIds);
            json.endObject();
        }
        json.endArray();
    }

    private void writeLanes(JsonWriter json, List<Lane> lanes, Map<Lane, String> laneIds) {
        json.beginArray();
        for (int index = 0; index < lanes.size(); index++) {
            Lane lane = lanes.get(index);
            json.beginObject();
            json.name("id").value(laneIds.get(lane));
            json.name("index").value(index);
            json.name("roadDirection").value(lane.isRoadDirection());
            json.name("drawStopLine").value(lane.isDrawStopLine());
            json.name("laneLength").value(lane.getLaneLength());
            json.name("laneTypes");
            writeEnumSet(json, lane.getLaneType());
            json.name("allowedVehicles");
            writeEnumSet(json, lane.getAllowVehicle());
            json.name("lanePath");
            writePoints(json, lane.getLanePath());
            json.endObject();
        }
        json.endArray();
    }

    private void writeConnections(JsonWriter json, JunctionController junctionController, Map<Lane, String> laneIds) {
        json.beginArray();
        for (Map.Entry<Lane, Set<LaneConnection>> entry : junctionController.getConnections().entrySet()) {
            String fromLaneId = laneIds.get(entry.getKey());
            if (fromLaneId == null) continue;

            for (LaneConnection connection : entry.getValue()) {
                String targetLaneId = laneIds.get(connection.targetLane());
                if (targetLaneId == null) continue;

                json.beginObject();
                json.name("fromLaneId").value(fromLaneId);
                json.name("targetLaneId").value(targetLaneId);
                json.name("laneType").value(connection.laneType().name());
                json.name("connectionPath");
                writePoints(json, connection.connectionPath());
                json.endObject();
            }
        }
        json.endArray();
    }

    private void writeTrafficLights(JsonWriter json, List<TrafficLight> trafficLights, Map<Lane, String> laneIds) {
        json.beginArray();
        for (int index = 0; index < trafficLights.size(); index++) {
            TrafficLight trafficLight = trafficLights.get(index);
            json.beginObject();
            json.name("id").value("traffic-light-" + index);
            json.name("coordinates");
            writePoint(json, trafficLight.getCoordinates());
            json.name("lightList");
            writeEnumSet(json, trafficLight.getLightList());
            json.name("controlLaneIds").beginArray();
            for (Lane lane : trafficLight.getControlLaneList()) {
                String laneId = laneIds.get(lane);
                if (laneId != null) json.value(laneId);
            }
            json.endArray();
            json.name("signalLoop").beginArray();
            for (SignalSetting signalSetting : trafficLight.getSignalTime()) {
                json.beginObject();
                json.name("signals");
                writeEnumArray(json, signalSetting.trafficLightSignals());
                json.name("durationSeconds").value(signalSetting.durationSeconds());
                json.endObject();
            }
            json.endArray();
            json.name("totalTick").value(trafficLight.getTotalTick());
            json.name("currentTick").value(trafficLight.getCurrentTick());
            json.name("currentSignalIndex").value(trafficLight.getCurrentSignalIndex());
            json.name("nextPhaseTick").value(trafficLight.getNextPhaseTick());
            json.endObject();
        }
        json.endArray();
    }

    private void writeCameras(JsonWriter json, List<Camera> cameras, Map<Road, String> roadIds, Map<Lane, String> laneIds) {
        json.beginArray();
        for (int index = 0; index < cameras.size(); index++) {
            Camera camera = cameras.get(index);
            json.beginObject();
            json.name("id").value("camera-" + index);
            json.name("roadId").value(roadIds.get(camera.getRoad()));
            json.name("limitSpeed").value(camera.getLimitSpeed());
            json.name("location");
            writePoint(json, camera.getLoc());
            json.name("targetLanes").beginArray();
            for (Map.Entry<Lane, Point2D.Double> entry : camera.getTargetLaneMap().entrySet()) {
                String laneId = laneIds.get(entry.getKey());
                if (laneId == null) continue;

                json.beginObject();
                json.name("laneId").value(laneId);
                json.name("enforcementPoint");
                writePoint(json, entry.getValue());
                json.endObject();
            }
            json.endArray();
            json.endObject();
        }
        json.endArray();
    }

    private void writePoint(JsonWriter json, Point2D.Double point) {
        if (point == null) {
            json.nullValue();
            return;
        }
        json.beginObject()
                .name("x").value(point.x)
                .name("y").value(point.y)
                .endObject();
    }

    private void writePoints(JsonWriter json, List<Point2D.Double> points) {
        json.beginArray();
        if (points != null) {
            for (Point2D.Double point : points) {
                writePoint(json, point);
            }
        }
        json.endArray();
    }

    private void writeEnumArray(JsonWriter json, Enum<?>[] values) {
        json.beginArray();
        if (values != null) {
            for (Enum<?> value : values) {
                json.value(value.name());
            }
        }
        json.endArray();
    }

    private void writeEnumSet(JsonWriter json, Set<? extends Enum<?>> values) {
        json.beginArray();
        if (values != null) {
            List<String> names = values.stream()
                    .map(Enum::name)
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
            for (String name : names) {
                json.value(name);
            }
        }
        json.endArray();
    }

    private static class JsonWriter {
        private final StringBuilder builder = new StringBuilder();
        private final List<Boolean> needsCommaStack = new ArrayList<>();
        private int indent = 0;

        JsonWriter beginObject() {
            beforeValue();
            builder.append("{");
            needsCommaStack.add(false);
            indent++;
            return this;
        }

        JsonWriter endObject() {
            indent--;
            if (needsCommaStack.remove(needsCommaStack.size() - 1)) newline();
            builder.append("}");
            afterValue();
            return this;
        }

        JsonWriter beginArray() {
            beforeValue();
            builder.append("[");
            needsCommaStack.add(false);
            indent++;
            return this;
        }

        JsonWriter endArray() {
            indent--;
            if (needsCommaStack.remove(needsCommaStack.size() - 1)) newline();
            builder.append("]");
            afterValue();
            return this;
        }

        JsonWriter name(String name) {
            beforeElement();
            builder.append("\"").append(escape(name)).append("\": ");
            return this;
        }

        JsonWriter value(String value) {
            if (value == null) return nullValue();
            beforeValue();
            builder.append("\"").append(escape(value)).append("\"");
            afterValue();
            return this;
        }

        JsonWriter value(double value) {
            beforeValue();
            builder.append(Double.isFinite(value) ? Double.toString(value) : "null");
            afterValue();
            return this;
        }

        JsonWriter value(int value) {
            beforeValue();
            builder.append(value);
            afterValue();
            return this;
        }

        JsonWriter value(boolean value) {
            beforeValue();
            builder.append(value);
            afterValue();
            return this;
        }

        JsonWriter nullValue() {
            beforeValue();
            builder.append("null");
            afterValue();
            return this;
        }

        private void beforeValue() {
            if (!needsCommaStack.isEmpty() && needsCommaStack.get(needsCommaStack.size() - 1)) {
                beforeElement();
            }
        }

        private void beforeElement() {
            int lastIndex = needsCommaStack.size() - 1;
            if (lastIndex >= 0) {
                if (needsCommaStack.get(lastIndex)) builder.append(",");
                newline();
                needsCommaStack.set(lastIndex, false);
            }
        }

        private void afterValue() {
            if (!needsCommaStack.isEmpty()) {
                needsCommaStack.set(needsCommaStack.size() - 1, true);
            }
        }

        private void newline() {
            builder.append("\n");
            builder.append("  ".repeat(Math.max(0, indent)));
        }

        private String escape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        @Override
        public String toString() {
            return builder.append("\n").toString();
        }
    }
}
