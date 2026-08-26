package com.smartlogistics.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File-backed CARLA vehicle snapshot tool. The JSON is reloaded whenever its timestamp changes. */
final class VehicleRealtimeService {
    private static final Pattern DEVICE_PATTERN = Pattern.compile("(?i)\\bsim[_-]?(\\d{1,3})\\b");
    private static final Pattern PLATE_PATTERN = Pattern.compile("([\\u4E00-\\u9FFF][A-Za-z][A-Za-z0-9]{5})");
    private static final Pattern VEHICLE_ID_PATTERN = Pattern.compile(
            "(?i)(?:vehicle\\s*id|vehicleid|车辆编号|车辆id|车辆|货车|卡车)\\s*[:：#]?\\s*(\\d{1,3})");
    private static final Pattern NUMBERED_VEHICLE_PATTERN = Pattern.compile("(\\d{1,3})\\s*号(?:货车|车辆|车)");

    private final Path dataDirectory;
    private final Path vehiclesFile;
    private final Path locationsFile;
    private volatile State state = State.empty();

    VehicleRealtimeService(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.vehiclesFile = this.dataDirectory.resolve("vehicles_latest_api.json");
        this.locationsFile = this.dataDirectory.resolve("locations.json");
        try {
            refreshIfChanged();
        } catch (IOException e) {
            state = State.failed(e.getMessage());
        }
    }

    synchronized void refreshIfChanged() throws IOException {
        if (!Files.isRegularFile(vehiclesFile)) {
            throw new IOException("CARLA 车辆数据文件不存在：" + vehiclesFile);
        }
        long vehiclesModified = Files.getLastModifiedTime(vehiclesFile).toMillis();
        long locationsModified = Files.isRegularFile(locationsFile)
                ? Files.getLastModifiedTime(locationsFile).toMillis() : -1L;
        State current = state;
        if (current.error == null
                && current.vehiclesModified == vehiclesModified
                && current.locationsModified == locationsModified) {
            return;
        }

        List<Location> locations = loadLocations();
        Map<String, Object> root = Json.object(readUtf8(vehiclesFile));
        Object data = root.get("data");
        if (!(data instanceof List)) throw new IOException("CARLA 车辆数据缺少 data 数组");

        List<VehicleSnapshot> vehicles = new ArrayList<VehicleSnapshot>();
        for (Object item : (List<?>) data) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) item;
            vehicles.add(VehicleSnapshot.from(values, nearest(values, locations)));
        }
        if (vehicles.isEmpty()) throw new IOException("CARLA 车辆数据为空");
        state = new State(Collections.unmodifiableList(vehicles), vehiclesModified,
                locationsModified, System.currentTimeMillis(), null);
    }

    boolean available() {
        refreshQuietly();
        return state.error == null && !state.vehicles.isEmpty();
    }

    int vehicleCount() {
        refreshQuietly();
        return state.vehicles.size();
    }

    String error() {
        refreshQuietly();
        return state.error;
    }

    Path dataDirectory() { return dataDirectory; }

    List<Map<String, Object>> latestVehicleMaps() throws IOException {
        refreshIfChanged();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (VehicleSnapshot vehicle : state.vehicles) result.add(vehicle.toMap());
        return result;
    }

    VehicleSnapshot find(String identifier) throws IOException {
        refreshIfChanged();
        if (identifier == null || identifier.trim().isEmpty()) return null;
        String clean = normalizeIdentifier(identifier);
        for (VehicleSnapshot vehicle : state.vehicles) {
            if (clean.equalsIgnoreCase(vehicle.deviceCode)
                    || clean.equalsIgnoreCase(vehicle.plateNumber)
                    || clean.equals(String.valueOf(vehicle.vehicleId))) {
                return vehicle;
            }
        }
        return null;
    }

    RealtimeAnswer answerIfVehicleQuery(String question) throws IOException {
        if (!isVehicleRealtimeQuestion(question)) return null;
        refreshIfChanged();

        if (isAllVehiclesQuestion(question)) {
            int online = 0;
            for (VehicleSnapshot vehicle : state.vehicles) if (vehicle.online) online++;
            Map<String, Object> toolData = new LinkedHashMap<String, Object>();
            toolData.put("tool", "vehicle_realtime_lookup");
            toolData.put("sourceType", "CARLA_SIMULATION");
            toolData.put("vehicleCount", state.vehicles.size());
            toolData.put("onlineCount", online);
            toolData.put("vehicles", latestVehicleMaps());
            String answer = "CARLA 模拟数据中共有 " + state.vehicles.size() + " 辆车，当前在线 " + online
                    + " 辆。结构化实时坐标已随本次回答返回，可供地图页面直接渲染。"
                    + "数据来自模拟快照，不代表真实道路车辆。";
            return new RealtimeAnswer(answer, toolData);
        }

        String identifier = extractIdentifier(question);
        if (identifier == null) {
            Map<String, Object> toolData = new LinkedHashMap<String, Object>();
            toolData.put("tool", "vehicle_realtime_lookup");
            toolData.put("sourceType", "CARLA_SIMULATION");
            toolData.put("vehicleCount", state.vehicles.size());
            return new RealtimeAnswer(
                    "请提供车辆标识，例如设备编号 sim_000、车牌渝A10000，或车辆编号 1。"
                            + "当前 CARLA 测试数据包含 " + state.vehicles.size() + " 辆车。",
                    toolData);
        }

        VehicleSnapshot vehicle = find(identifier);
        if (vehicle == null) {
            Map<String, Object> toolData = new LinkedHashMap<String, Object>();
            toolData.put("tool", "vehicle_realtime_lookup");
            toolData.put("sourceType", "CARLA_SIMULATION");
            toolData.put("requestedIdentifier", identifier);
            toolData.put("found", Boolean.FALSE);
            return new RealtimeAnswer("没有找到车辆“" + identifier + "”的 CARLA 实时位置记录。", toolData);
        }

        Map<String, Object> toolData = new LinkedHashMap<String, Object>();
        toolData.put("tool", "vehicle_realtime_lookup");
        toolData.put("sourceType", "CARLA_SIMULATION");
        toolData.put("found", Boolean.TRUE);
        toolData.put("vehicle", vehicle.toMap());
        return new RealtimeAnswer(formatAnswer(vehicle), toolData);
    }

    Map<String, Object> metadata() {
        refreshQuietly();
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("sourceType", "CARLA_SIMULATION");
        metadata.put("dataDirectory", dataDirectory.toString());
        metadata.put("vehicleCount", state.vehicles.size());
        metadata.put("loadedAtEpochMs", state.loadedAt);
        metadata.put("error", state.error);
        return metadata;
    }

    private void refreshQuietly() {
        try {
            refreshIfChanged();
        } catch (IOException e) {
            State current = state;
            state = new State(current.vehicles, current.vehiclesModified,
                    current.locationsModified, current.loadedAt, e.getMessage());
        }
    }

    private List<Location> loadLocations() throws IOException {
        if (!Files.isRegularFile(locationsFile)) return Collections.emptyList();
        Object value = Json.parse(readUtf8(locationsFile));
        if (!(value instanceof List)) throw new IOException("CARLA locations.json 必须是数组");
        List<Location> locations = new ArrayList<Location>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            locations.add(new Location(string(map.get("locationId")), string(map.get("name")),
                    decimal(map.get("longitude")), decimal(map.get("latitude"))));
        }
        return locations;
    }

    private static LocationDistance nearest(Map<String, Object> vehicle, List<Location> locations) {
        double longitude = decimal(vehicle.get("longitude"));
        double latitude = decimal(vehicle.get("latitude"));
        Location nearest = null;
        double distance = Double.MAX_VALUE;
        for (Location location : locations) {
            double candidate = haversineMeters(latitude, longitude, location.latitude, location.longitude);
            if (candidate < distance) {
                distance = candidate;
                nearest = location;
            }
        }
        return nearest == null ? null : new LocationDistance(nearest, Math.round(distance));
    }

    private static String formatAnswer(VehicleSnapshot vehicle) {
        StringBuilder answer = new StringBuilder();
        answer.append("车辆 ").append(vehicle.plateNumber)
                .append("（设备 ").append(vehicle.deviceCode)
                .append("，车辆编号 ").append(vehicle.vehicleId).append("）")
                .append("的最新 CARLA 模拟位置为：经度 ").append(vehicle.longitude)
                .append("，纬度 ").append(vehicle.latitude).append("。");
        if (vehicle.nearest != null) {
            answer.append("最近的规划节点是 ").append(vehicle.nearest.location.name)
                    .append("（").append(vehicle.nearest.location.locationId).append("），约 ")
                    .append(vehicle.nearest.distanceMeters).append(" 米。");
        }
        answer.append("车辆状态 ").append(vehicle.status)
                .append("，").append(vehicle.online ? "在线" : "离线")
                .append("，速度值 ").append(vehicle.speed)
                .append("，方向角 ").append(vehicle.direction).append("°")
                .append("；记录时间 ").append(vehicle.recordedAt).append("。")
                .append("该结果来自测试快照，不代表真实道路车辆。");
        return answer.toString();
    }

    private static boolean isVehicleRealtimeQuestion(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean vehicleMention = q.contains("车辆") || q.contains("货车") || q.contains("卡车")
                || q.contains("车牌") || DEVICE_PATTERN.matcher(q).find() || PLATE_PATTERN.matcher(question).find();
        String[] intents = {"实时", "位置", "在哪", "到哪", "经纬", "坐标", "状态", "速度", "方向", "在线", "分布"};
        if (!vehicleMention) return false;
        for (String intent : intents) if (q.contains(intent)) return true;
        return isAllVehiclesQuestion(q);
    }

    private static boolean isAllVehiclesQuestion(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return q.contains("所有车辆") || q.contains("全部车辆") || q.contains("车辆分布")
                || q.contains("所有货车") || q.contains("全部货车");
    }

    private static String extractIdentifier(String question) {
        Matcher device = DEVICE_PATTERN.matcher(question);
        if (device.find()) return String.format(Locale.ROOT, "sim_%03d", Integer.parseInt(device.group(1)));
        Matcher plate = PLATE_PATTERN.matcher(question);
        if (plate.find()) return plate.group(1).toUpperCase(Locale.ROOT);
        Matcher vehicleId = VEHICLE_ID_PATTERN.matcher(question);
        if (vehicleId.find()) return vehicleId.group(1);
        Matcher numbered = NUMBERED_VEHICLE_PATTERN.matcher(question);
        if (numbered.find()) return numbered.group(1);
        return null;
    }

    private static String normalizeIdentifier(String identifier) {
        String clean = identifier.trim();
        Matcher device = DEVICE_PATTERN.matcher(clean);
        if (device.matches()) return String.format(Locale.ROOT, "sim_%03d", Integer.parseInt(device.group(1)));
        return clean;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000.0;
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String readUtf8(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static long integer(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value)); }
    private static double decimal(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value)); }
    private static boolean bool(Object value) { return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value)); }

    static final class RealtimeAnswer {
        final String answer;
        final Map<String, Object> toolData;
        RealtimeAnswer(String answer, Map<String, Object> toolData) {
            this.answer = answer;
            this.toolData = toolData;
        }
    }

    static final class VehicleSnapshot {
        final long vehicleId;
        final String deviceCode;
        final String plateNumber;
        final String status;
        final boolean online;
        final Object taskId;
        final double longitude;
        final double latitude;
        final double speed;
        final double direction;
        final String recordedAt;
        final LocationDistance nearest;

        VehicleSnapshot(long vehicleId, String deviceCode, String plateNumber, String status,
                        boolean online, Object taskId, double longitude, double latitude,
                        double speed, double direction, String recordedAt, LocationDistance nearest) {
            this.vehicleId = vehicleId;
            this.deviceCode = deviceCode;
            this.plateNumber = plateNumber;
            this.status = status;
            this.online = online;
            this.taskId = taskId;
            this.longitude = longitude;
            this.latitude = latitude;
            this.speed = speed;
            this.direction = direction;
            this.recordedAt = recordedAt;
            this.nearest = nearest;
        }

        static VehicleSnapshot from(Map<String, Object> values, LocationDistance nearest) {
            return new VehicleSnapshot(integer(values.get("vehicleId")), string(values.get("deviceCode")),
                    string(values.get("plateNumber")), string(values.get("status")), bool(values.get("online")),
                    values.get("taskId"), decimal(values.get("longitude")), decimal(values.get("latitude")),
                    decimal(values.get("speed")), decimal(values.get("direction")), string(values.get("recordedAt")), nearest);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("vehicleId", vehicleId);
            map.put("deviceCode", deviceCode);
            map.put("plateNumber", plateNumber);
            map.put("status", status);
            map.put("online", online);
            map.put("taskId", taskId);
            map.put("longitude", longitude);
            map.put("latitude", latitude);
            map.put("speed", speed);
            map.put("direction", direction);
            map.put("recordedAt", recordedAt);
            if (nearest != null) map.put("nearestLocation", nearest.toMap());
            return map;
        }
    }

    private static final class Location {
        final String locationId;
        final String name;
        final double longitude;
        final double latitude;
        Location(String locationId, String name, double longitude, double latitude) {
            this.locationId = locationId;
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    private static final class LocationDistance {
        final Location location;
        final long distanceMeters;
        LocationDistance(Location location, long distanceMeters) {
            this.location = location;
            this.distanceMeters = distanceMeters;
        }
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("locationId", location.locationId);
            map.put("name", location.name);
            map.put("longitude", location.longitude);
            map.put("latitude", location.latitude);
            map.put("distanceMeters", distanceMeters);
            return map;
        }
    }

    private static final class State {
        final List<VehicleSnapshot> vehicles;
        final long vehiclesModified;
        final long locationsModified;
        final long loadedAt;
        final String error;

        State(List<VehicleSnapshot> vehicles, long vehiclesModified, long locationsModified,
              long loadedAt, String error) {
            this.vehicles = vehicles;
            this.vehiclesModified = vehiclesModified;
            this.locationsModified = locationsModified;
            this.loadedAt = loadedAt;
            this.error = error;
        }
        static State empty() { return new State(Collections.<VehicleSnapshot>emptyList(), -1L, -1L, 0L, null); }
        static State failed(String error) { return new State(Collections.<VehicleSnapshot>emptyList(), -1L, -1L, 0L, error); }
    }
}

