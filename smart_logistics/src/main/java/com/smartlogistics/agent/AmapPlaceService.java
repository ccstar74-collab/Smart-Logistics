package com.smartlogistics.agent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AmapPlaceService {
    private static final long CACHE_MS = 5 * 60 * 1000L;
    private final String key;
    private final String reverseEndpoint;
    private final String geocodeEndpoint;
    private final HttpClient client;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    AmapPlaceService(String key, String endpoint) {
        this(key, endpoint, "");
    }

    AmapPlaceService(String key, String reverseEndpoint, String geocodeEndpoint) {
        this.key = key == null ? "" : key.trim();
        this.reverseEndpoint = reverseEndpoint == null || reverseEndpoint.isBlank()
                ? "https://restapi.amap.com/v3/geocode/regeo" : reverseEndpoint.trim();
        this.geocodeEndpoint = geocodeEndpoint == null || geocodeEndpoint.isBlank()
                ? "https://restapi.amap.com/v3/geocode/geo" : geocodeEndpoint.trim();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    boolean enabled() { return !key.isEmpty(); }

    Map<String,Object> reverse(double longitude, double latitude) throws IOException {
        if (!enabled()) return null;
        String cacheKey = String.format(java.util.Locale.ROOT, "%.4f,%.4f", longitude, latitude);
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - hit.at < CACHE_MS) return hit.place;
        String url = reverseEndpoint + "?key=" + encode(key) + "&location="
                + encode(longitude + "," + latitude) + "&extensions=all&radius=1000&roadlevel=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try { response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("高德位置解析被中断", e); }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("高德位置解析 HTTP " + response.statusCode());
        Map<String,Object> root = Json.object(response.body());
        if (!"1".equals(String.valueOf(root.get("status")))) throw new IOException("高德位置解析失败");
        Map<String,Object> regeo = map(root.get("regeocode"));
        if (regeo == null) return null;
        Map<String,Object> place = new LinkedHashMap<>();
        String address = string(regeo.get("formatted_address"));
        Map<String,Object> component = map(regeo.get("addressComponent"));
        if (!address.isEmpty()) place.put("formattedAddress", address);
        if (component != null) {
            putText(place, "province", component.get("province"));
            putText(place, "city", component.get("city"));
            putText(place, "district", component.get("district"));
            putText(place, "township", component.get("township"));
        }
        Map<String,Object> poi = nearest(regeo.get("pois"));
        if (poi != null) {
            putText(place, "landmark", poi.get("name"));
            putNumber(place, "distanceMeters", poi.get("distance"));
            putText(place, "direction", poi.get("direction"));
        }
        Map<String,Object> road = nearest(regeo.get("roads"));
        if (road != null) putText(place, "road", road.get("name"));
        place.put("source", "AMAP_REVERSE_GEOCODING");
        cache.put(cacheKey, new CacheEntry(place));
        return place;
    }

    Map<String,Object> geocode(String address, String city) throws IOException {
        if (!enabled() || address == null || address.isBlank()) return null;
        String normalizedAddress = address.trim();
        String normalizedCity = city == null ? "" : city.trim();
        String cacheKey = "geo:" + normalizedCity + ":" + normalizedAddress;
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - hit.at < CACHE_MS) return hit.place;
        StringBuilder url = new StringBuilder(geocodeEndpoint).append("?key=").append(encode(key))
                .append("&address=").append(encode(normalizedAddress));
        if (!normalizedCity.isEmpty()) url.append("&city=").append(encode(normalizedCity));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString())).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try { response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("高德地址解析被中断", e); }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("高德地址解析 HTTP " + response.statusCode());
        Map<String,Object> root = Json.object(response.body());
        if (!"1".equals(String.valueOf(root.get("status")))) {
            throw new IOException("高德地址解析失败：" + string(root.get("info")));
        }
        Object raw = root.get("geocodes");
        if (!(raw instanceof List<?> geocodes) || geocodes.isEmpty()) return null;
        Map<String,Object> first = map(geocodes.get(0));
        if (first == null) return null;
        String location = string(first.get("location"));
        String[] pair = location.split(",", -1);
        if (pair.length != 2) return null;
        double longitude = number(pair[0]);
        double latitude = number(pair[1]);
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) return null;
        Map<String,Object> place = new LinkedHashMap<>();
        place.put("longitude", longitude);
        place.put("latitude", latitude);
        putText(place, "formattedAddress", first.get("formatted_address"));
        putText(place, "province", first.get("province"));
        putText(place, "city", first.get("city"));
        putText(place, "district", first.get("district"));
        putText(place, "level", first.get("level"));
        place.put("candidateCount", geocodes.size());
        place.put("ambiguous", geocodes.size() > 1);
        place.put("source", "AMAP_GEOCODING");
        cache.put(cacheKey, new CacheEntry(place));
        return place;
    }

    static String describe(String vehicleId, Map<String,Object> place) {
        if (place == null || place.isEmpty()) return "车辆 " + vehicleId + " 的实时位置已获取，请查看地图标记。";
        String landmark = string(place.get("landmark"));
        String address = string(place.get("formattedAddress"));
        String road = string(place.get("road"));
        String base = !address.isEmpty() ? address : (!road.isEmpty() ? road : "当前位置");
        if (!landmark.isEmpty()) {
            String distance = string(place.get("distanceMeters"));
            return "车辆 " + vehicleId + " 当前位于" + base + "，靠近" + landmark
                    + (distance.isEmpty() ? "。" : "，距离约 " + distance + " 米。");
        }
        return "车辆 " + vehicleId + " 当前位于" + base + "附近。";
    }

    private static Map<String,Object> nearest(Object value) {
        if (!(value instanceof List<?> list)) return null;
        Map<String,Object> best = null; double distance = Double.MAX_VALUE;
        for (Object item : list) { Map<String,Object> map=map(item); if(map==null || string(map.get("name")).isEmpty()) continue;
            double d=number(map.get("distance")); if(best==null || d<distance){best=map; distance=d;} }
        return best;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return value instanceof Map ? (Map<String,Object>) value : null; }
    private static void putText(Map<String,Object> out,String key,Object value){String s=string(value);if(!s.isEmpty())out.put(key,s);}
    private static void putNumber(Map<String,Object> out,String key,Object value){double d=number(value);if(Double.isFinite(d))out.put(key,Math.round(d));}
    private static double number(Object value){try{return Double.parseDouble(String.valueOf(value));}catch(Exception e){return Double.NaN;}}
    private static String string(Object value){if(value==null || value instanceof List<?>)return "";return String.valueOf(value).trim();}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static final class CacheEntry { final Map<String,Object> place; final long at=System.currentTimeMillis(); CacheEntry(Map<String,Object> p){place=p;} }
}
