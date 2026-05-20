package com.trafficsimulator.ui;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.trafficsimulator.road.JunctionController;
import com.trafficsimulator.road.Lane;
import com.trafficsimulator.road.LaneType;
import com.trafficsimulator.road.Road;
import com.trafficsimulator.road.camera.Camera;
import com.trafficsimulator.road.trafficlight.SignalSetting;
import com.trafficsimulator.road.trafficlight.TrafficLight;
import com.trafficsimulator.road.trafficlight.TrafficLightSignal;
import com.trafficsimulator.vehicle.VehicleType;

public class ProjectLoadService {

    public LoadedProject load(Path path,
                              RoadManager roadManager,
                              JunctionController junctionController) throws IOException {
        Object rootValue = new JsonParser(Files.readString(path, StandardCharsets.UTF_8)).parse();
        Map<String, Object> root = asObject(rootValue, "root");
        Map<String, Road> roadsById = new HashMap<>();
        Map<String, Lane> lanesById = new HashMap<>();

        roadManager.clear();
        junctionController.clear();

        loadRoads(root, roadManager, roadsById, lanesById);
        loadConnections(root, junctionController, lanesById);
        loadTrafficLights(root, roadManager, lanesById);
        loadCameras(root, roadManager, roadsById, lanesById);

        Map<String, Object> viewport = objectOrEmpty(root.get("viewport"));
        double cameraX = number(viewport.get("cameraX"), 0.0);
        double cameraY = number(viewport.get("cameraY"), 0.0);
        double zoomFactor = number(viewport.get("zoomFactor"), 1.0);
        return new LoadedProject(cameraX, cameraY, zoomFactor);
    }

    private void loadRoads(Map<String, Object> root,
                           RoadManager roadManager,
                           Map<String, Road> roadsById,
                           Map<String, Lane> lanesById) {
        for (Object item : arrayOrEmpty(root.get("roads"))) {
            Map<String, Object> roadJson = asObject(item, "road");
            Point2D.Double start = point(roadJson.get("startPoint"));
            Point2D.Double end = point(roadJson.get("endPoint"));
            Point2D.Double control1 = point(roadJson.get("control1"));
            Point2D.Double control2 = point(roadJson.get("control2"));
            boolean curved = bool(roadJson.get("curved"), false);
            Road road = curved
                    ? new Road(start, end, control1, control2, bool(roadJson.get("oneWay"), true))
                    : new Road(start, end, bool(roadJson.get("oneWay"), true));
            road.setLimitSpeed((int) number(roadJson.get("limitSpeed"), 0.0));
            road.setLaneWidth(number(roadJson.get("laneWidth"), road.getLaneWidth()));

            List<Object> lanes = arrayOrEmpty(roadJson.get("lanes"));
            for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
                Map<String, Object> laneJson = asObject(lanes.get(laneIndex), "lane");
                Lane lane = loadLane(road, laneJson, laneIndex);
                String laneId = string(laneJson.get("id"), null);
                if (laneId != null) {
                    lanesById.put(laneId, lane);
                }
            }

            road.setMoveable(bool(roadJson.get("locked"), false));
            roadManager.addRoad(road);
            String roadId = string(roadJson.get("id"), null);
            if (roadId != null) {
                roadsById.put(roadId, road);
            }
        }
    }

    private Lane loadLane(Road road, Map<String, Object> laneJson, int laneIndex) {
        road.addLane(bool(laneJson.get("roadDirection"), true), laneIndex);
        Lane lane = road.getLane(laneIndex);
        lane.setRoadDirection(bool(laneJson.get("roadDirection"), lane.isRoadDirection()));
        lane.setDrawStopLine(bool(laneJson.get("drawStopLine"), lane.isDrawStopLine()));
        lane.clearLaneTypes();
        for (Object value : arrayOrEmpty(laneJson.get("laneTypes"))) {
            lane.addLaneType(LaneType.valueOf(string(value, "")));
        }
        lane.clearAllowVehicle();
        for (Object value : arrayOrEmpty(laneJson.get("allowedVehicles"))) {
            lane.setAllowVehicle(VehicleType.valueOf(string(value, "")));
        }
        return lane;
    }

    private void loadConnections(Map<String, Object> root, JunctionController junctionController, Map<String, Lane> lanesById) {
        for (Object item : arrayOrEmpty(root.get("connections"))) {
            Map<String, Object> connectionJson = asObject(item, "connection");
            Lane fromLane = lanesById.get(string(connectionJson.get("fromLaneId"), ""));
            Lane targetLane = lanesById.get(string(connectionJson.get("targetLaneId"), ""));
            if (fromLane == null || targetLane == null) continue;
            junctionController.addConnection(fromLane, targetLane, LaneType.valueOf(string(connectionJson.get("laneType"), "STRAIGHT")));
        }
    }

    private void loadTrafficLights(Map<String, Object> root, RoadManager roadManager, Map<String, Lane> lanesById) {
        for (Object item : arrayOrEmpty(root.get("trafficLights"))) {
            Map<String, Object> trafficLightJson = asObject(item, "trafficLight");
            TrafficLight trafficLight = new TrafficLight();
            for (Object laneIdValue : arrayOrEmpty(trafficLightJson.get("controlLaneIds"))) {
                Lane lane = lanesById.get(string(laneIdValue, ""));
                if (lane != null) trafficLight.addControlLane(lane);
            }
            for (TrafficLightSignal signal : TrafficLightSignal.values()) {
                trafficLight.deleteLight(signal);
            }
            for (Object value : arrayOrEmpty(trafficLightJson.get("lightList"))) {
                trafficLight.addLight(TrafficLightSignal.valueOf(string(value, "")));
            }
            List<SignalSetting> signalLoop = new ArrayList<>();
            for (Object phaseValue : arrayOrEmpty(trafficLightJson.get("signalLoop"))) {
                Map<String, Object> phaseJson = asObject(phaseValue, "signalPhase");
                List<TrafficLightSignal> signals = new ArrayList<>();
                for (Object signalValue : arrayOrEmpty(phaseJson.get("signals"))) {
                    signals.add(TrafficLightSignal.valueOf(string(signalValue, "")));
                }
                signalLoop.add(new SignalSetting(signals.toArray(TrafficLightSignal[]::new), number(phaseJson.get("durationSeconds"), 0.0)));
            }
            if (!signalLoop.isEmpty()) {
                trafficLight.addSignalLoop(signalLoop);
            }
            trafficLight.resetCurrentTick();
            trafficLight.jmpTick((int) number(trafficLightJson.get("currentTick"), 0.0));
            trafficLight.setCoordinates(point(trafficLightJson.get("coordinates")));
            roadManager.addTrafficLight(trafficLight);
        }
    }

    private void loadCameras(Map<String, Object> root,
                             RoadManager roadManager,
                             Map<String, Road> roadsById,
                             Map<String, Lane> lanesById) {
        for (Object item : arrayOrEmpty(root.get("cameras"))) {
            Map<String, Object> cameraJson = asObject(item, "camera");
            Road road = roadsById.get(string(cameraJson.get("roadId"), ""));
            if (road == null) continue;
            List<Object> targetLanes = arrayOrEmpty(cameraJson.get("targetLanes"));
            if (targetLanes.isEmpty()) continue;
            Lane firstLane = lanesById.get(string(asObject(targetLanes.get(0), "cameraTarget").get("laneId"), ""));
            if (firstLane == null) continue;
            Camera camera = new Camera(road, point(cameraJson.get("location")), firstLane);
            camera.setLimitSpeed((int) number(cameraJson.get("limitSpeed"), road.getLimitSpeed()));
            camera.getTargetLaneMap().clear();
            for (Object targetValue : targetLanes) {
                Map<String, Object> targetJson = asObject(targetValue, "cameraTarget");
                Lane lane = lanesById.get(string(targetJson.get("laneId"), ""));
                Point2D.Double enforcementPoint = point(targetJson.get("enforcementPoint"));
                if (lane != null && enforcementPoint != null) {
                    camera.getTargetLaneMap().put(lane, enforcementPoint);
                }
            }
            Point2D.Double location = point(cameraJson.get("location"));
            if (location != null) camera.getLoc().setLocation(location);
            roadManager.addCamera(camera);
        }
    }

    private Point2D.Double point(Object value) {
        if (value == null) return null;
        Map<String, Object> object = asObject(value, "point");
        return new Point2D.Double(number(object.get("x"), 0.0), number(object.get("y"), 0.0));
    }

    private Map<String, Object> objectOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typed;
        }
        return Map.of();
    }

    private Map<String, Object> asObject(Object value, String label) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typed;
        }
        throw new IllegalArgumentException("Expected object for " + label);
    }

    private List<Object> arrayOrEmpty(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private String string(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static class JsonParser {
        private final String text;
        private int index = 0;

        JsonParser(String text) {
            this.text = text;
        }

        Object parse() {
            Object value = readValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Unexpected trailing characters");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= text.length()) throw error("Unexpected end of file");
            char c = text.charAt(index);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't') return readLiteral("true", Boolean.TRUE);
            if (c == 'f') return readLiteral("false", Boolean.FALSE);
            if (c == 'n') return readLiteral("null", null);
            if (c == '-' || Character.isDigit(c)) return readNumber();
            throw error("Unexpected character");
        }

        private Map<String, Object> readObject() {
            index++;
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return object;
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                object.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return object;
                expect(',');
            }
        }

        private List<Object> readArray() {
            index++;
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return array;
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (consume(']')) return array;
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') return builder.toString();
                if (c == '\\') {
                    if (index >= text.length()) throw error("Unterminated escape");
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(readUnicodeEscape());
                        default -> throw error("Invalid escape");
                    }
                } else {
                    builder.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private char readUnicodeEscape() {
            if (index + 4 > text.length()) throw error("Invalid unicode escape");
            String hex = text.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) throw error("Invalid literal");
            index += literal.length();
            return value;
        }

        private Number readNumber() {
            int start = index;
            if (consume('-')) {}
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            if (consume('.')) {
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            }
            return Double.parseDouble(text.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private boolean consume(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("Expected '" + expected + "'");
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}
