package com.smartlogistics.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BusinessDataService {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final String baseUrl;
    private volatile String token;
    private final String username;
    private final String password;
    private final int timeoutMillis;
    private volatile RealtimeVehicleService realtime;
    private volatile AmapPlaceService amap = new AmapPlaceService("", "");
    void startRealtime(String url) { realtime = new RealtimeVehicleService(url, this::realtimeToken); }
    void configureAmap(String key, String endpoint) { amap = new AmapPlaceService(key, endpoint); }
    void configureAmap(String key, String reverseEndpoint, String geocodeEndpoint) {
        amap = new AmapPlaceService(key, reverseEndpoint, geocodeEndpoint);
    }

    BusinessDataService(String baseUrl, String token, int timeoutMillis) {
        String normalized = stripTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        this.baseUrl = normalized.endsWith("/api/v1")
                ? normalized.substring(0, normalized.length() - "/api/v1".length())
                : normalized;
        this.token = token == null ? "" : token.trim();
        this.username = "";
        this.password = "";
        this.timeoutMillis = Math.max(1000, timeoutMillis);
    }

    BusinessDataService(String baseUrl, String token, String username, String password, int timeoutMillis) {
        String normalized = stripTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        this.baseUrl = normalized.endsWith("/api/v1")
                ? normalized.substring(0, normalized.length() - "/api/v1".length())
                : normalized;
        this.token = token == null ? "" : token.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.timeoutMillis = Math.max(1000, timeoutMillis);
    }

    boolean enabled() {
        return !baseUrl.isEmpty();
    }

    private synchronized String realtimeToken(boolean refresh) throws IOException {
        if (refresh && !username.isEmpty()) token = "";
        if (token.isEmpty() && !username.isEmpty()) login();
        if (token.isEmpty()) throw new IOException("实时 WebSocket 需要 BUSINESS_API_TOKEN 或业务服务账号");
        return token;
    }

    BusinessAnswer answerBySelection(ToolSelection selection, String requestToken) throws IOException {
        if(selection==null || !enabled()) return null;
        if(WarehouseWriteTools.isWriteIntent(selection.intent)) {
            return new WarehouseWriteTools(this::get, this::post, this::put, amap::geocode).execute(selection, requestToken);
        }
        if("GET_VEHICLE_LOCATION".equals(selection.intent)) {
            Object value=selection.parameters.get("simCode"); String sim=value==null?"":String.valueOf(value).toLowerCase(Locale.ROOT);
            if(sim.matches("sim_\\d+")) return locationAnswer(sim,requestToken);
            String plate=normalizePlate(selection.parameters.get("plateNumber"));
            if(!plate.isEmpty()) return locationAnswerByPlate(plate,requestToken);
            Object vehicleId=selection.parameters.get("vehicleId");
            if(vehicleId instanceof Number && ((Number)vehicleId).longValue()>0)return locationAnswer(String.valueOf(((Number)vehicleId).longValue()),requestToken);
            if(vehicleId!=null && String.valueOf(vehicleId).matches("[1-9]\\d*"))return locationAnswer(String.valueOf(vehicleId),requestToken);
            return null;
        }
        return new ReadOnlyBusinessTools(this::get).execute(selection,requestToken);
    }
    BusinessAnswer answerIfBusinessQuery(String question) throws IOException {
        return answerIfBusinessQuery(question, "");
    }

    BusinessAnswer answerIfBusinessQuery(String question, String requestToken) throws IOException {
        ToolSelection writeFallback = WarehouseWriteTools.fallbackSelection(question);
        if (writeFallback != null) {
            if (!enabled()) throw new IOException("未配置云端业务 API，请设置 BUSINESS_API_BASE_URL");
            return new WarehouseWriteTools(this::get, this::post, this::put, amap::geocode).execute(writeFallback, requestToken);
        }
        if (enabled()) {
            BusinessAnswer routed = new ReadOnlyBusinessTools(this::get).answer(question, requestToken);
            if (routed != null) return routed;
        }
        if (question != null && asksForSpecificVehicleLocation(question)) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("sim_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(question);
            if (m.find()) return locationAnswer(m.group(), requestToken);
            String plate=plateFromQuestion(question); if(plate!=null) return locationAnswerByPlate(plate,requestToken);
        }
        Resource resource = Resource.fromQuestion(question);
        if (resource == null) return null;
        if (!enabled()) throw new IOException("未配置云端业务 API，请设置 BUSINESS_API_BASE_URL");

        if (vehicleIdFromQuestion(question) != null && asksForSpecificVehicleLocation(question)) {
            String vehicleId = vehicleIdFromQuestion(question);
            if (vehicleId != null) return locationAnswer(vehicleId, requestToken);
        }
        String path = resource.path + "?page=1&pageSize=100";
        Map<String, Object> page = getPage(path, requestToken);
        List<Map<String, Object>> records = records(page);
        String answer = formatAnswer(resource, records, integer(page.get("total"), records.size()), question);

        Map<String, Object> toolData = new LinkedHashMap<String, Object>();
        toolData.put("tool", "cloud_business_lookup");
        toolData.put("sourceType", "CLOUD_SPRING_BOOT_MYSQL");
        toolData.put("resource", resource.key);
        toolData.put("readOnly", Boolean.TRUE);
        toolData.put("endpoint", path);
        toolData.put("data", page);
        return new BusinessAnswer(answer, toolData);
    }

    @SuppressWarnings("unchecked")
    private BusinessAnswer locationAnswer(String vehicleId, String requestToken) throws IOException {
        if (vehicleId.startsWith("sim_") && realtime != null) { Map<String,Object> p=realtime.get(vehicleId); if (p!=null) return realtimeAnswer(vehicleId,p); }
        if (vehicleId.startsWith("sim_")) {
            try { return latestLocationBySim(vehicleId,requestToken); }
            catch (BusinessApiException e) { throw e; }
            catch (IOException e) { System.err.println("Realtime REST fallback failed for "+vehicleId+": "+e.getMessage()); return new BusinessAnswer("暂未收到 " + vehicleId + " 的实时 GPS 数据，请稍后重试。", Map.of("tool","realtime_vehicle_websocket","sourceType","BACKEND_WEBSOCKET","available",false,"vehicleId",vehicleId)); }
        }
        String path = "/api/v1/vehicles/" + vehicleId + "/location-history?startTime=" + encodeTime(OffsetDateTime.now(ZoneOffset.UTC).minusHours(12)) + "&endTime=" + encodeTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        Object value = get(path, requestToken);
        if (!(value instanceof Map)) throw new IOException("车辆位置 API 返回格式错误");
        Map<String, Object> response = (Map<String, Object>) value;
        int responseCode=integer(response.get("code"),-1);if(responseCode!=0&&responseCode!=200) throw new IOException("车辆位置 API 返回失败");
        Object raw = response.get("data");
        if (!(raw instanceof Map)) throw new IOException("车辆位置 API 缺少 data");
        Map<String, Object> location = (Map<String, Object>) repairMojibake(raw);
        double longitude = number(location.get("longitude"));
        double latitude = number(location.get("latitude"));
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude) || (longitude == 0 && latitude == 0))
            throw new IOException("车辆位置 API 暂无有效经纬度");
        Map<String, Object> vehicle = new LinkedHashMap<String, Object>();
        vehicle.put("id", integer(location.get("vehicleId"), Integer.parseInt(vehicleId)));
        vehicle.put("plateNumber", location.get("plateNumber"));
        Map<String, Object> gps = new LinkedHashMap<String, Object>();
        gps.put("longitude", longitude); gps.put("latitude", latitude);
        gps.put("speed", location.get("speed")); gps.put("direction", location.get("direction"));
        gps.put("collectedAt", location.get("collectedAt")); gps.put("online", location.get("online"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("type", "VEHICLE_LOCATION"); data.put("vehicle", vehicle); data.put("location", gps);
        Map<String, Object> toolData = new LinkedHashMap<String, Object>();
        toolData.put("tool", "cloud_business_lookup"); toolData.put("sourceType", "CLOUD_SPRING_BOOT_MYSQL");
        toolData.put("resource", "vehicle_location"); toolData.put("readOnly", Boolean.TRUE); toolData.put("endpoint", path); toolData.put("data", data);
        return new BusinessAnswer("车辆 " + text(vehicle.get("plateNumber"), vehicleId) + " 当前位置：经度 " + longitude + "，纬度 " + latitude + "，速度 " + text(location.get("speed"), "未知") + " km/h，方向 " + text(location.get("direction"), "未知") + "°，采集时间 " + text(location.get("collectedAt"), "未知") + "。", toolData);
    }

    @SuppressWarnings("unchecked")
    private BusinessAnswer latestLocationBySim(String simCode,String requestToken)throws IOException{
        IOException last=null;Map<String,Object> vehicle=null;
        try{vehicle=new ReadOnlyBusinessTools(this::get).vehicleRecordBySim(simCode,requestToken);}catch(IOException e){last=e;}
        Long vehicleId=vehicle==null?null:longValue(vehicle.get("id"));
        if(vehicleId!=null){String path="/api/v1/vehicles/"+vehicleId+"/location/latest";try{return latestLocationResponse(simCode,vehicleId,path,get(path,requestToken));}catch(IOException e){last=e;}}
        String batchPath="/api/v1/vehicles/locations/latest";try{return latestLocationResponse(simCode,vehicleId,batchPath,get(batchPath,requestToken));}catch(IOException e){last=e;}
        String legacyPath="/api/v1/vehicles/by-sim-code/"+URLEncoder.encode(simCode,StandardCharsets.UTF_8)+"/location/latest";try{return latestLocationResponse(simCode,vehicleId,legacyPath,get(legacyPath,requestToken));}catch(IOException e){last=e;}
        throw last==null?new IOException("最新位置接口暂无位置数据"):last;
    }

    @SuppressWarnings("unchecked")
    private BusinessAnswer latestLocationResponse(String simCode,Long expectedVehicleId,String path,Object raw)throws IOException{
        Map<String,Object> root=raw instanceof Map?(Map<String,Object>)raw:null;if(root==null)throw new IOException("最新位置接口返回格式错误");
        int code=integer(root.get("code"),-1);if(code!=0&&code!=200)throw new IOException("最新位置接口返回失败："+text(root.get("message"),"code="+code));
        Object dataValue=repairMojibake(root.get("data"));Map<String,Object> data=null;
        if(dataValue instanceof Map<?,?> map)data=(Map<String,Object>)map;
        else if(dataValue instanceof List<?> values){for(Object item:values){if(!(item instanceof Map<?,?> map))continue;Map<String,Object> candidate=(Map<String,Object>)map;Long id=longValue(candidate.get("vehicleId"));String candidateSim=text(candidate.get("simCode"),"");if((expectedVehicleId!=null&&expectedVehicleId.equals(id))||simCode.equalsIgnoreCase(candidateSim)){data=candidate;break;}}}
        if(data==null)throw new IOException("最新位置接口暂无对应车辆位置");
        Map<String,Object> point=data.get("location") instanceof Map?(Map<String,Object>)data.get("location"):data;Map<String,Object> normalized=new LinkedHashMap<>(point);
        normalized.putIfAbsent("vehicleId",data.get("vehicleId"));normalized.put("simCode",simCode);
        String coordinateSystem=text(data.get("coordinateSystem"),text(data.get("coordSystem"),"WGS84"));normalized.put("coordSystem",coordinateSystem);
        double longitude=number(normalized.get("longitude")),latitude=number(normalized.get("latitude"));if(!Double.isFinite(longitude)||!Double.isFinite(latitude)||(longitude==0&&latitude==0))throw new IOException("最新位置接口暂无有效经纬度");
        BusinessAnswer answer=realtimeAnswer(simCode,normalized);Map<String,Object> tool=new LinkedHashMap<>(answer.toolData);tool.put("sourceType","BACKEND_REST_LATEST_LOCATION");tool.put("endpoint",path);tool.put("restFallback",true);return new BusinessAnswer(answer.answer,tool);
    }

    private BusinessAnswer locationAnswerByPlate(String plate,String requestToken)throws IOException{
        Map<String,Object> vehicle=new ReadOnlyBusinessTools(this::get).vehicleRecordByPlate(plate,requestToken);
        if(vehicle==null)return vehicleLookupFailure(plate,null,"未找到车牌号为 "+plate+" 的车辆。");
        String sim=text(vehicle.get("simCode"),"").toLowerCase(Locale.ROOT);
        if(!sim.matches("sim_\\d+"))return vehicleLookupFailure(plate,null,"已找到车辆 "+plate+"，但该车尚未绑定 GPS 设备。");
        BusinessAnswer located=locationAnswer(sim,requestToken);
        Map<String,Object> tool=new LinkedHashMap<>(located.toolData);
        tool.put("plateNumber",plate);tool.put("simCode",sim);tool.put("vehicleId",vehicle.get("id"));
        Object rawData=tool.get("data");
        if(rawData instanceof Map<?,?>){
            @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)rawData;
            Map<String,Object> displayVehicle=new LinkedHashMap<>();displayVehicle.put("id",vehicle.get("id"));displayVehicle.put("plateNumber",plate);displayVehicle.put("simCode",sim);data.put("vehicle",displayVehicle);
        }
        return new BusinessAnswer(located.answer.replace(sim,plate),tool);
    }

    private static BusinessAnswer vehicleLookupFailure(String plate,String sim,String answer){
        Map<String,Object> tool=new LinkedHashMap<>();tool.put("tool","realtime_vehicle_websocket");tool.put("sourceType","BACKEND_WEBSOCKET");tool.put("available",false);tool.put("plateNumber",plate);if(sim!=null)tool.put("simCode",sim);return new BusinessAnswer(answer,tool);
    }

    @SuppressWarnings("unchecked")
    private BusinessAnswer realtimeAnswer(String id, Map<String,Object> p) {
        Map<String,Object> loc=new LinkedHashMap<>();
        loc.put("longitude",p.get("longitude")); loc.put("latitude",p.get("latitude"));
        loc.put("speed",p.get("speed")); loc.put("direction",p.get("direction"));
        loc.put("collectedAt",p.get("collectedAt")); loc.put("online",p.getOrDefault("online",true)); String coordSystem=text(p.get("coordSystem"),text(p.get("coordinateSystem"),"WGS84"));loc.put("coordSystem",coordSystem);
        Map<String,Object> data=new LinkedHashMap<>(); data.put("type","VEHICLE_LOCATION");
        Map<String,Object> vehicle=new LinkedHashMap<>();vehicle.put("id",p.get("vehicleId"));vehicle.put("simCode",text(p.get("simCode"),id));vehicle.put("plateNumber",id);data.put("vehicle",vehicle);data.put("location",loc);
        Map<String,Object> place=null;
        try {
            double longitude=number(p.get("longitude")), latitude=number(p.get("latitude"));
            if(Double.isFinite(longitude) && Double.isFinite(latitude)) { double[] display="WGS84".equalsIgnoreCase(coordSystem)?wgs84ToGcj02(longitude,latitude):new double[]{longitude,latitude};place=amap.reverse(display[0],display[1]); }
        } catch(Exception e) { System.err.println("Amap reverse geocoding failed: " + e.getMessage()); }
        if(place!=null && !place.isEmpty()) data.put("place",place);
        Map<String,Object> tool=new LinkedHashMap<>(); tool.put("tool","realtime_vehicle_websocket");
        tool.put("sourceType","BACKEND_WEBSOCKET"); tool.put("available",true); tool.put("data",data);
        return new BusinessAnswer(AmapPlaceService.describe(id,place),tool);
    }
    private static double[] wgs84ToGcj02(double longitude,double latitude){
        if(longitude<72.004||longitude>137.8347||latitude<0.8293||latitude>55.8271)return new double[]{longitude,latitude};
        double a=6378245.0,ee=0.00669342162296594323,dLat=transformLatitude(longitude-105,latitude-35),dLon=transformLongitude(longitude-105,latitude-35),radLat=latitude/180*Math.PI,magic=Math.sin(radLat);magic=1-ee*magic*magic;double sqrtMagic=Math.sqrt(magic);dLat=(dLat*180)/((a*(1-ee))/(magic*sqrtMagic)*Math.PI);dLon=(dLon*180)/(a/sqrtMagic*Math.cos(radLat)*Math.PI);return new double[]{longitude+dLon,latitude+dLat};
    }
    private static double transformLatitude(double x,double y){double ret=-100+2*x+3*y+.2*y*y+.1*x*y+.2*Math.sqrt(Math.abs(x));ret+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;ret+=(20*Math.sin(y*Math.PI)+40*Math.sin(y/3*Math.PI))*2/3;ret+=(160*Math.sin(y/12*Math.PI)+320*Math.sin(y*Math.PI/30))*2/3;return ret;}
    private static double transformLongitude(double x,double y){double ret=300+x+2*y+.1*x*x+.1*x*y+.1*Math.sqrt(Math.abs(x));ret+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;ret+=(20*Math.sin(x*Math.PI)+40*Math.sin(x/3*Math.PI))*2/3;ret+=(150*Math.sin(x/12*Math.PI)+300*Math.sin(x/30*Math.PI))*2/3;return ret;}
    private static Map<String, Object> latestLocation(List<?> values) throws IOException {
        Map<String, Object> latest = null;
        String latestTime = "";
        for (Object item : values) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> candidate = (Map<String, Object>) item;
            String time = text(candidate.get("collectedAt"), "");
            if (latest == null || time.compareTo(latestTime) > 0) { latest = candidate; latestTime = time; }
        }
        if (latest == null) throw new IOException("车辆历史轨迹中暂无位置记录");
        return latest;
    }

    private static String encodeTime(OffsetDateTime time) throws IOException {
        try { return URLEncoder.encode(time.toString(), "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new IOException("无法编码时间参数", e); }
    }
    private static boolean asksForSpecificVehicleLocation(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return Resource.containsAny(q, "位置", "在哪", "坐标", "经纬度", "实时") && (vehicleIdFromQuestion(question) != null || plateFromQuestion(question)!=null);
    }

    private static String plateFromQuestion(String question){if(question==null)return null;java.util.regex.Matcher m=java.util.regex.Pattern.compile("([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]\\s*[A-Za-z]\\s*[A-Za-z0-9]{5,6})").matcher(question);return m.find()?normalizePlate(m.group(1)):null;}
    private static String normalizePlate(Object value){return value==null?"":String.valueOf(value).replaceAll("\\s+","").toUpperCase(Locale.ROOT);}

    private static String vehicleIdFromQuestion(String question) {
        if (question == null) return null;
        java.util.regex.Matcher sim = java.util.regex.Pattern.compile("(sim_\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(question);
        if (sim.find()) return sim.group(1);
        java.util.regex.Matcher numeric = java.util.regex.Pattern.compile("(?:车辆|车)\\\\s*(?:id\\\\s*)?(\\\\d+)").matcher(question);
        return numeric.find() ? numeric.group(1) : null;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> getPage(String path, String requestToken) throws IOException {
        Object value = get(path, requestToken);
        if (!(value instanceof Map)) throw new IOException("云端业务 API 返回格式不是 JSON 对象");
        Map<String, Object> response = (Map<String, Object>) value;
        int code = integer(response.get("code"), -1);
        if (code != 0 && code != 200) {
            throw new IOException("云端业务 API 返回失败：" + text(response.get("message"), "code=" + code));
        }
        Object data = response.get("data");
        if (!(data instanceof Map)) throw new IOException("云端业务 API 响应缺少 data 对象");
        return (Map<String, Object>) repairMojibake(data);
    }

    private Object get(String path, String requestToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "smart-logistics-agent/1.0");
        String effectiveToken = requestToken == null ? "" : requestToken.trim();
        if (effectiveToken.isEmpty()) {
            if (token.isEmpty() && !username.isEmpty()) login();
            effectiveToken = token;
        }
        if (!effectiveToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + effectiveToken);
        try {
            int status = connection.getResponseCode();
            String body = readUtf8(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status == 401 && requestToken != null && !requestToken.trim().isEmpty()) {
                throw new BusinessApiException(401, "BUSINESS_UNAUTHORIZED", "当前登录已失效，请重新登录");
            }
            if (status == 403) {
                throw new BusinessApiException(403, "BUSINESS_FORBIDDEN", "当前账号没有权限查看这类信息");
            }
            if (status == 401 && !username.isEmpty()) {
                login();
                return get(path, requestToken);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("云端业务 API HTTP " + status
                        + (body.isEmpty() ? "" : "：" + safeMessage(body)));
            }
            try {
                return Json.parse(body);
            } catch (IllegalArgumentException e) {
                throw new IOException("云端业务 API 返回了无法解析的 JSON", e);
            }
        } finally {
            connection.disconnect();
        }
    }

    private Object post(String path, Map<String,Object> body, String requestToken, String idempotencyKey) throws IOException {
        return write("POST",path,body,requestToken,idempotencyKey);
    }

    private Object put(String path, Map<String,Object> body, String requestToken, String idempotencyKey) throws IOException {
        return write("PUT",path,body,requestToken,idempotencyKey);
    }

    private Object write(String method,String path,Map<String,Object> body,String requestToken,String idempotencyKey)throws IOException{
        String effectiveToken = requestToken == null ? "" : requestToken.trim();
        if (effectiveToken.isEmpty()) {
            throw new BusinessApiException(401, "WAREHOUSE_LOGIN_REQUIRED", "办理仓库业务前请先登录仓库管理员账号");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "smart-logistics-agent/1.0");
        connection.setRequestProperty("Authorization", "Bearer " + effectiveToken);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) connection.setRequestProperty("Idempotency-Key", idempotencyKey);
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            String responseBody = readUtf8(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status == 401) throw new BusinessApiException(401, "BUSINESS_UNAUTHORIZED", "当前登录已失效，请重新登录");
            if (status == 403) throw new BusinessApiException(403, "WAREHOUSE_MANAGER_REQUIRED", "只有仓库管理员可以新增车辆、绑定司机、办理入库、出库和创建运输订单");
            if (status < 200 || status >= 300) {
                throw new IOException("云端业务 API HTTP " + status
                        + (responseBody.isEmpty() ? "" : "：" + safeMessage(responseBody)));
            }
            try { return Json.parse(responseBody); }
            catch (IllegalArgumentException e) { throw new IOException("云端业务 API 返回了无法解析的 JSON", e); }
        } finally {
            connection.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void login() throws IOException {
        if (username.isEmpty()) throw new IOException("云端业务 API 需要 JWT，但未配置 BUSINESS_API_USERNAME");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("username", username);
        body.put("password", password);
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/auth/login").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        int status = connection.getResponseCode();
        String responseBody = readUtf8(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
        if (status < 200 || status >= 300) {
            throw new IOException("云端业务登录失败 HTTP " + status + "：" + safeMessage(responseBody));
        }
        Object parsed;
        try { parsed = Json.parse(responseBody); }
        catch (IllegalArgumentException e) { throw new IOException("云端业务登录返回非法 JSON", e); }
        if (!(parsed instanceof Map)) throw new IOException("云端业务登录响应格式非法");
        Object data = ((Map<String, Object>) parsed).get("data");
        if (!(data instanceof Map)) throw new IOException("云端业务登录响应缺少 data");
        Object accessToken = ((Map<String, Object>) data).get("accessToken");
        if (accessToken == null || String.valueOf(accessToken).trim().isEmpty()) {
            throw new IOException("云端业务登录响应缺少 accessToken");
        }
        token = String.valueOf(accessToken).trim();
        connection.disconnect();
    }

    private static String readUtf8(InputStream input) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        try (InputStream stream = input) {
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("云端业务 API 响应过大");
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> page) throws IOException {
        Object value = page.get("records");
        if (!(value instanceof List)) throw new IOException("云端业务 API 分页响应缺少 records 数组");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) value) if (item instanceof Map) result.add((Map<String, Object>) item);
        return result;
    }

    private static String formatAnswer(Resource resource, List<Map<String, Object>> records,
                                       int total, String question) {
        if (records.isEmpty()) return "云端业务系统当前没有" + resource.chineseName + "记录。";
        Map<String, Object> mentioned = findMentioned(records, question);
        if (mentioned != null) {
            return "查询结果：\n" + formatRecord(resource, mentioned);
        }
        StringBuilder answer = new StringBuilder();
        answer.append("云端业务系统共有 ").append(total).append(" 条")
                .append(resource.chineseName).append("记录：\n");
        int limit = Math.min(records.size(), 20);
        for (int i = 0; i < limit; i++) {
            answer.append(i + 1).append(". ").append(formatRecord(resource, records.get(i))).append('\n');
        }
        if (records.size() > limit) answer.append("其余 ").append(records.size() - limit).append(" 条未展开。\n");
        return answer.toString();
    }

    private static String formatRecord(Resource resource, Map<String, Object> record) {
        if (resource == Resource.VEHICLES) {
            return text(record.get("plateNumber"), "未命名车辆") + "（ID " + text(record.get("id"), "--")
                    + "，类型 " + text(record.get("type"), "--") + "），载重 " + text(record.get("capacity"), "--")
                    + "，状态 " + text(record.get("status"), "--") + "，司机 "
                    + text(record.get("driverId"), "未绑定") + "，最新定位 " + coordinate(record) + "。";
        }
        if (resource == Resource.CARGOS) {
            return text(record.get("cargoNo"), "未编号货物") + "（ID " + text(record.get("id"), "--")
                    + "，名称 " + text(record.get("name"), "--") + "），重量 " + text(record.get("weight"), "--")
                    + "，体积 " + text(record.get("volume"), "--") + "，状态 "
                    + text(record.get("status"), "--") + "。";
        }
        if (resource == Resource.TASKS) {
            return text(record.get("taskNo"), "未编号任务") + "（ID " + text(record.get("id"), "--")
                    + "，货物 " + text(record.get("cargoId"), "--") + "，车辆 "
                    + text(record.get("vehicleId"), "--") + "），"
                    + text(record.get("startLocation"), "--") + " → " + text(record.get("endLocation"), "--")
                    + "，状态 " + text(record.get("status"), "--") + "，计划时间 "
                    + text(record.get("planStartTime"), "--") + " 至 "
                    + text(record.get("planEndTime"), "--") + "。";
        }
        return "告警 ID " + text(record.get("id"), "--") + "，类型 " + text(record.get("type"), "--")
                + "，级别 " + text(record.get("level"), "--") + "，状态 "
                + text(record.get("status"), "--") + "，车辆 " + text(record.get("vehicleId"), "--")
                + "，时间 " + text(first(record, "alarmTime", "createdAt", "occurredAt"), "--") + "。";
    }

    private static String coordinate(Map<String, Object> record) {
        Object longitude = record.get("lastLongitude");
        Object latitude = record.get("lastLatitude");
        if (longitude == null || latitude == null) return "暂无";
        return "经度 " + longitude + "、纬度 " + latitude + "（"
                + text(record.get("lastUpdatedAt"), "更新时间未知") + "）";
    }

    private static Map<String, Object> findMentioned(List<Map<String, Object>> records, String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (Map<String, Object> record : records) {
            for (String key : new String[]{"plateNumber", "cargoNo", "taskNo"}) {
                Object value = record.get(key);
                if (value != null && String.valueOf(value).length() >= 3
                        && q.contains(String.valueOf(value).toLowerCase(Locale.ROOT))) return record;
            }
        }
        return null;
    }

    private static Object repairMojibake(Object value) {
        if (value instanceof String) return repairString((String) value);
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (List<?>) value) result.add(repairMojibake(item));
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), repairMojibake(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    private static String repairString(String value) {
        int markers = 0;
        boolean latin1Only = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 255) latin1Only = false;
            if ("ÃÂäºæçåèéïð".indexOf(c) >= 0 || (c >= 0x80 && c <= 0x9f)) markers++;
        }
        if (!latin1Only || markers < 2) return value;
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return repaired.indexOf('\uFFFD') >= 0 ? value : repaired;
    }

    private static Object first(Map<String, Object> record, String... keys) {
        for (String key : keys) if (record.get(key) != null) return record.get(key);
        return null;
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return Double.NaN; }
    }
    private static Long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }
    private static String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return fallback;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isInfinite(number) && !Double.isNaN(number) && number == Math.rint(number)) {
                return String.valueOf((long) number);
            }
        }
        return String.valueOf(value);
    }

    private static String safeMessage(String body) {
        String line = body.replace('\r', ' ').replace('\n', ' ').trim();
        return line.length() <= 300 ? line : line.substring(0, 300) + "…";
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    static final class BusinessApiException extends IOException {
        final int status;
        final String code;
        BusinessApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    static final class BusinessAnswer {
        final String answer;
        final Map<String, Object> toolData;
        BusinessAnswer(String answer, Map<String, Object> toolData) {
            this.answer = answer;
            this.toolData = toolData;
        }
    }

    private enum Resource {
        VEHICLES("vehicles", "车辆", "/api/v1/vehicles"),
        CARGOS("cargos", "货物", "/api/v1/cargos"),
        TASKS("transportTasks", "运输任务", "/api/v1/transport-tasks"),
        ALARMS("alarms", "告警", "/api/v1/alarms");

        final String key;
        final String chineseName;
        final String path;
        Resource(String key, String chineseName, String path) {
            this.key = key;
            this.chineseName = chineseName;
            this.path = path;
        }

        static Resource fromQuestion(String question) {
            if (question != null && question.toLowerCase(Locale.ROOT).matches(".*sim_\\d+.*")) return VEHICLES;
            String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
            if (containsAny(q, "告警", "报警", "alarm")) return ALARMS;
            if (containsAny(q, "运输任务", "运单", "订单", "任务列表", "task")) return TASKS;
            if (containsAny(q, "货物列表", "货物状态", "货物信息", "货物记录",
                    "货物有哪些", "cargo-", "cargo")) return CARGOS;
            if (containsAny(q, "车辆列表", "车辆档案", "车辆信息", "车辆记录",
                    "系统车辆", "云端车辆", "有多少辆车", "有哪些车辆", "全部车辆", "所有车辆",
                    "车辆状态", "车辆位置", "车辆分布",
                    "test-a", "vehicle")) {
                return VEHICLES;
            }
            boolean mentionsVehicle = containsAny(q, "车辆", "货车", "车牌");
            boolean asksVehicleFact = containsAny(q, "状态", "位置", "在哪", "经纬度", "坐标", "分布", "在线");
            if (mentionsVehicle && asksVehicleFact) {
                return VEHICLES;
            }
            return null;
        }

        private static boolean containsAny(String value, String... terms) {
            for (String term : terms) if (value.contains(term)) return true;
            return false;
        }
    }
}
