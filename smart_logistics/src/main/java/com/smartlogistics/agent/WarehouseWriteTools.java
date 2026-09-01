package com.smartlogistics.agent;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes the small, explicitly allow-listed set of warehouse write operations. */
final class WarehouseWriteTools {
    interface Getter { Object get(String path, String token) throws IOException; }
    interface Writer { Object post(String path, Map<String,Object> body, String token, String idempotencyKey) throws IOException; }
    interface Updater { Object put(String path, Map<String,Object> body, String token, String idempotencyKey) throws IOException; }
    interface Geocoder { Map<String,Object> geocode(String address, String city) throws IOException; }

    private final Getter getter;
    private final Writer writer;
    private final Updater updater;
    private final Geocoder geocoder;

    WarehouseWriteTools(Getter getter, Writer writer) {
        this(getter, writer, (path,body,token,key)->{throw new IOException("未配置 PUT 更新器");}, (address, city) -> null);
    }

    WarehouseWriteTools(Getter getter, Writer writer, Geocoder geocoder) {
        this(getter,writer,(path,body,token,key)->{throw new IOException("未配置 PUT 更新器");},geocoder);
    }

    WarehouseWriteTools(Getter getter, Writer writer, Updater updater, Geocoder geocoder) {
        this.getter = getter;
        this.writer = writer;
        this.updater = updater;
        this.geocoder = geocoder;
    }

    BusinessDataService.BusinessAnswer execute(ToolSelection selection, String token) throws IOException {
        if (selection == null || !isWriteIntent(selection.intent)) return null;
        requireWarehouseManager(token);
        return switch (selection.intent) {
            case "VEHICLE_CREATE" -> createVehicle(selection, token);
            case "ASSIGN_VEHICLE_DRIVER" -> assignVehicleDriver(selection, token);
            case "CARGO_INBOUND" -> inbound(selection, token);
            case "CARGO_OUTBOUND", "CREATE_TRANSPORT_TASK" -> createTransportTask(selection, token);
            default -> null;
        };
    }

    private BusinessDataService.BusinessAnswer assignVehicleDriver(ToolSelection selection,String token)throws IOException{
        Map<String,Object> p=selection.parameters;Long vehicleId=positiveLong(p.get("vehicleId"));String plate=normalizePlate(p.get("plateNumber")),sim=text(p.get("simCode")).toLowerCase(Locale.ROOT);
        Map<String,Object> vehicle=null;if(vehicleId!=null)vehicle=findRecord("/api/v1/vehicles?page=1&pageSize=100","id",String.valueOf(vehicleId),token);else if(!plate.isEmpty())vehicle=findRecord("/api/v1/vehicles?page=1&pageSize=100","plateNumber",plate,token);else if(!sim.isEmpty())vehicle=findRecord("/api/v1/vehicles?page=1&pageSize=100","simCode",sim,token);if(vehicleId==null&&vehicle!=null)vehicleId=positiveLong(vehicle.get("id"));
        Long driverId=positiveLong(p.get("driverId"));String driverName=firstText(p.get("driverName"),p.get("name"));List<Map<String,Object>> drivers=optionRecords("/api/v1/drivers/options?status=ENABLED&unbound=true",token);Map<String,Object> driver=null;
        if(driverId!=null){for(Map<String,Object> item:drivers){Long id=positiveLong(item.get("driverId"));if(id==null)id=positiveLong(item.get("id"));if(driverId.equals(id)){driver=item;break;}}}
        else if(!driverName.isEmpty()){List<Map<String,Object>> matches=new ArrayList<>();for(Map<String,Object> item:drivers){if(sameText(driverName,item.get("name"))||sameText(driverName,item.get("username")))matches.add(item);}if(matches.size()==1){driver=matches.get(0);driverId=positiveLong(driver.get("driverId"));if(driverId==null)driverId=positiveLong(driver.get("id"));}else if(matches.size()>1){List<String> choices=new ArrayList<>();for(Map<String,Object> item:matches)choices.add("driverId="+fallback(item.get("driverId"),fallback(item.get("id"),"未知"))+"（"+fallback(item.get("name"),fallback(item.get("username"),"未命名"))+"）");return missing("assign_vehicle_driver","绑定车辆司机",List.of("唯一司机，当前姓名匹配多条："+String.join("、",choices)),p);}}
        List<String> missing=new ArrayList<>();if(vehicleId==null)missing.add(plate.isEmpty()&&sim.isEmpty()?"vehicleId、plateNumber或simCode（车辆）":"有效的车辆标识");if(driverId==null||driver==null)missing.add(driverName.isEmpty()&&positiveLong(p.get("driverId"))==null?"driverId或driverName（司机）":"有效且唯一的司机 "+fallback(driverName,String.valueOf(p.get("driverId"))));if(!missing.isEmpty())return missing("assign_vehicle_driver","绑定车辆司机",missing,p);
        Map<String,Object> body=new LinkedHashMap<>();body.put("driverId",driverId);String key=idempotencyKey(selection);Object data=unwrap(updater.put("/api/v1/vehicles/"+vehicleId+"/driver",body,token,key));String displayVehicle=vehicle==null?(!plate.isEmpty()?plate:(!sim.isEmpty()?sim:String.valueOf(vehicleId))):fallback(vehicle.get("plateNumber"),fallback(vehicle.get("simCode"),String.valueOf(vehicleId)));String displayDriver=fallback(driver.get("name"),fallback(driver.get("username"),String.valueOf(driverId)));return result("assign_vehicle_driver","/api/v1/vehicles/"+vehicleId+"/driver","车辆 "+displayVehicle+" 已绑定司机 "+displayDriver+"（司机ID "+driverId+"）。",data,key);
    }

    private BusinessDataService.BusinessAnswer createVehicle(ToolSelection selection, String token) throws IOException {
        Map<String,Object> p = selection.parameters;
        String plateNumber = normalizePlate(p.get("plateNumber"));
        String simCode = text(p.get("simCode")).toLowerCase(Locale.ROOT);
        String type = vehicleType(firstText(p.get("vehicleType"), p.get("type")));
        BigDecimal capacity = positiveDecimal(p.get("capacity"));
        Map<String,Object> warehouse = resolveWarehouse(p, token);
        Long warehouseId = warehouse == null ? null : positiveLong(warehouse.get("id"));
        List<String> missing = new ArrayList<>();
        if (plateNumber.isEmpty()) missing.add("plateNumber（车牌号）");
        if (simCode.isEmpty()) missing.add("simCode（GPS/SIM编号）");
        if (type.isEmpty()) missing.add("vehicleType（TRUCK、VAN或REFRIGERATED）");
        if (capacity == null) missing.add("capacity（载重，公斤）");
        if (warehouseId == null) missing.add(warehouseReferenceMissing(p));
        if (!missing.isEmpty()) return missing("vehicle_create", "新增车辆", missing, p);

        Map<String,Object> body = new LinkedHashMap<>();
        body.put("plateNumber", plateNumber);
        body.put("simCode", simCode);
        body.put("type", type);
        body.put("capacity", capacity);
        body.put("warehouseId", warehouseId);
        String key = idempotencyKey(selection);
        Object data = unwrap(writer.post("/api/v1/vehicles", body, token, key));
        Map<String,Object> vehicle = map(data);
        String savedPlate = vehicle == null ? plateNumber : fallback(vehicle.get("plateNumber"), plateNumber);
        String savedSim = vehicle == null ? simCode : fallback(vehicle.get("simCode"), simCode);
        return result("vehicle_create", "/api/v1/vehicles",
                "车辆 " + savedPlate + " 已添加，SIM编号 " + savedSim + "，车型 " + type
                        + "，载重 " + plain(capacity) + " 公斤，归属仓库 "
                        + warehouseDisplay(warehouse) + "。",
                data, key);
    }

    private BusinessDataService.BusinessAnswer inbound(ToolSelection selection, String token) throws IOException {
        Map<String,Object> p = selection.parameters;
        String cargoNo = text(p.get("cargoNo"));
        String name = firstText(p.get("cargoName"), p.get("name"));
        BigDecimal weight = positiveDecimal(p.get("weight"));
        BigDecimal volume = positiveDecimal(p.get("volume"));
        Map<String,Object> cargoType = resolveCargoType(p, token);
        Long cargoTypeId = cargoType == null ? null : positiveLong(cargoType.get("id"));
        Map<String,Object> warehouse = resolveWarehouse(p, token);
        Long warehouseId = warehouse == null ? null : positiveLong(warehouse.get("id"));
        List<String> missing = new ArrayList<>();
        if (cargoNo.isEmpty()) missing.add("cargoNo（货物编号）");
        if (name.isEmpty()) missing.add("cargoName（货物名称）");
        if (weight == null) missing.add("weight（重量，公斤）");
        if (volume == null) missing.add("volume（体积，立方米）");
        if (cargoTypeId == null) missing.add(cargoTypeReferenceMissing(p));
        if (warehouseId == null) missing.add(warehouseReferenceMissing(p));
        if (!missing.isEmpty()) return missing("cargo_inbound", "办理入库", missing, p);

        Map<String,Object> body = new LinkedHashMap<>();
        body.put("cargoNo", cargoNo);
        body.put("name", name);
        String description = text(p.get("description"));
        if (!description.isEmpty()) body.put("description", description);
        body.put("weight", weight);
        body.put("volume", volume);
        body.put("cargoTypeId", cargoTypeId);
        body.put("warehouseId", warehouseId);
        String key = idempotencyKey(selection);
        Object data = unwrap(writer.post("/api/v1/cargos", body, token, key));
        Map<String,Object> result = map(data);
        String savedNo = result == null ? cargoNo : fallback(result.get("cargoNo"), cargoNo);
        String savedName = result == null ? name : fallback(result.get("name"), name);
        return result("cargo_inbound", "/api/v1/cargos",
                "货物 " + savedNo + "（" + savedName + "）已办理入库，重量 " + plain(weight)
                        + " 公斤，体积 " + plain(volume) + " 立方米，货物种类 "
                        + optionDisplay(cargoType) + "，入库仓库 " + warehouseDisplay(warehouse) + "。",
                data, key);
    }

    private Map<String,Object> resolveWarehouse(Map<String,Object> parameters, String token) throws IOException {
        return resolveOption("/api/v1/warehouses?page=1&pageSize=100", parameters,
                "warehouseId", "warehouseName", "warehouseNo", token);
    }

    private Map<String,Object> resolveCargoType(Map<String,Object> parameters, String token) throws IOException {
        return resolveOption("/api/v1/cargo-types?page=1&pageSize=100", parameters,
                "cargoTypeId", "cargoTypeName", null, token);
    }

    private Map<String,Object> resolveOption(String path, Map<String,Object> parameters,
                                             String idField, String nameField, String numberField,
                                             String token) throws IOException {
        Long expectedId = positiveLong(parameters.get(idField));
        String expectedName = text(parameters.get(nameField));
        String expectedNumber = numberField == null ? "" : text(parameters.get(numberField));
        if (expectedId == null && expectedName.isEmpty() && expectedNumber.isEmpty()) return null;
        List<Map<String,Object>> records = optionRecords(path, token);
        List<Map<String,Object>> matches = new ArrayList<>();
        for (Map<String,Object> record : records) {
            Long id = positiveLong(record.get("id"));
            if (expectedId != null && expectedId.equals(id)) matches.add(record);
            else if (expectedId == null && !expectedNumber.isEmpty()
                    && expectedNumber.equalsIgnoreCase(text(record.get("warehouseNo")))) matches.add(record);
            else if (expectedId == null && expectedNumber.isEmpty() && !expectedName.isEmpty()
                    && expectedName.equalsIgnoreCase(text(record.get("name")))) matches.add(record);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static String warehouseReferenceMissing(Map<String,Object> p) {
        return positiveLong(p.get("warehouseId")) == null && text(p.get("warehouseName")).isEmpty()
                && text(p.get("warehouseNo")).isEmpty()
                ? "warehouseId、warehouseName或warehouseNo（仓库）" : "有效且唯一的仓库";
    }

    private static String cargoTypeReferenceMissing(Map<String,Object> p) {
        return positiveLong(p.get("cargoTypeId")) == null && text(p.get("cargoTypeName")).isEmpty()
                ? "cargoTypeId或cargoTypeName（货物种类）" : "有效且唯一的货物种类";
    }

    private static String optionDisplay(Map<String,Object> option) {
        return fallback(option == null ? null : option.get("name"),
                option == null ? "未知" : fallback(option.get("id"), "未知"));
    }

    private static String warehouseDisplay(Map<String,Object> warehouse) {
        if (warehouse == null) return "未知";
        String name = fallback(warehouse.get("name"), text(warehouse.get("id")));
        String no = text(warehouse.get("warehouseNo"));
        return no.isEmpty() ? name : name + "（" + no + "）";
    }

    private BusinessDataService.BusinessAnswer createTransportTask(ToolSelection selection, String token) throws IOException {
        Map<String,Object> p = selection.parameters;
        String cargoNo = text(p.get("cargoNo"));
        Long cargoId = positiveLong(p.get("cargoId"));
        Map<String,Object> cargo = cargoId == null ? findRecord("/api/v1/cargos?page=1&pageSize=100", "cargoNo", cargoNo, token) : null;
        if (cargoId == null && cargo != null) cargoId = positiveLong(cargo.get("id"));

        Long vehicleId = positiveLong(p.get("vehicleId"));
        String plate = normalizePlate(p.get("plateNumber"));
        String simCode = text(p.get("simCode")).toLowerCase(Locale.ROOT);
        Map<String,Object> vehicle = vehicleId == null && !plate.isEmpty() ? findRecord("/api/v1/vehicles?page=1&pageSize=100", "plateNumber", plate, token)
                : (vehicleId == null && !simCode.isEmpty() ? findRecord("/api/v1/vehicles?page=1&pageSize=100", "simCode", simCode, token) : null);
        if (vehicleId == null && vehicle != null) vehicleId = positiveLong(vehicle.get("id"));
        String vehicleIssue = "";
        if (vehicle != null && vehicleId != null) {
            if (positiveLong(vehicle.get("driverId")) == null) {
                vehicleIssue = "已绑定司机的运输车辆（" + (!plate.isEmpty() ? plate : simCode) + "尚未绑定司机）";
            } else {
                String status = text(vehicle.get("status")).toUpperCase(Locale.ROOT);
                if (!status.isEmpty() && !"IDLE".equals(status)) {
                    vehicleIssue = "状态为IDLE的空闲运输车辆（当前车辆状态为" + status + "）";
                }
            }
        }

        Long ownerId = positiveLong(p.get("ownerId"));
        if (ownerId == null && cargo != null) ownerId = positiveLong(cargo.get("ownerId"));
        String ownerName = text(p.get("ownerName"));
        if (ownerId == null && !ownerName.isEmpty()) {
            Map<String,Object> owner = findRecord("/api/v1/owners/options?status=ENABLED", "name", ownerName, token);
            if (owner == null) owner = findRecord("/api/v1/owners/options?status=ENABLED", "username", ownerName, token);
            if (owner != null) {
                ownerId = positiveLong(owner.get("ownerId"));
                if (ownerId == null) ownerId = positiveLong(owner.get("id"));
            }
        }
        String startLocation = text(p.get("startLocation"));
        Double startLongitude = coordinate(p.get("startLongitude"), -180, 180);
        Double startLatitude = coordinate(p.get("startLatitude"), -90, 90);
        String startCity = text(p.get("startCity"));
        String endLocation = text(p.get("endLocation"));
        Double endLongitude = coordinate(p.get("endLongitude"), -180, 180);
        Double endLatitude = coordinate(p.get("endLatitude"), -90, 90);
        String endCity = text(p.get("endCity"));
        String planStartTime = validTime(p.get("planStartTime"));
        String planEndTime = validTime(p.get("planEndTime"));

        String startGeocodeIssue = "";
        if (!startLocation.isEmpty() && (startLongitude == null || startLatitude == null)) {
            try {
                Map<String,Object> resolved = geocoder.geocode(startLocation, startCity);
                if (resolved == null) startGeocodeIssue = "高德无法解析的起点地址“" + startLocation + "”";
                else if (Boolean.TRUE.equals(resolved.get("ambiguous")) && startCity.isEmpty()) {
                    startGeocodeIssue = "更详细的起点地址或startCity（“" + startLocation + "”存在多个候选）";
                } else {
                    startLongitude = coordinate(resolved.get("longitude"), -180, 180);
                    startLatitude = coordinate(resolved.get("latitude"), -90, 90);
                }
            } catch (IOException e) {
                startGeocodeIssue = "可由高德解析的起点地址“" + startLocation + "”（" + e.getMessage() + "）";
            }
        }
        String endGeocodeIssue = "";
        if (!endLocation.isEmpty() && (endLongitude == null || endLatitude == null)) {
            try {
                Map<String,Object> resolved = geocoder.geocode(endLocation, endCity);
                if (resolved == null) endGeocodeIssue = "高德无法解析的目的地地址“" + endLocation + "”";
                else if (Boolean.TRUE.equals(resolved.get("ambiguous")) && endCity.isEmpty()) {
                    endGeocodeIssue = "更详细的目的地地址或endCity（“" + endLocation + "”存在多个候选）";
                } else {
                    endLongitude = coordinate(resolved.get("longitude"), -180, 180);
                    endLatitude = coordinate(resolved.get("latitude"), -90, 90);
                }
            } catch (IOException e) {
                endGeocodeIssue = "可由高德解析的目的地地址“" + endLocation + "”（" + e.getMessage() + "）";
            }
        }

        List<String> missing = new ArrayList<>();
        if (cargoId == null) missing.add(cargoNo.isEmpty() ? "cargoNo（货物编号）" : "有效的货物编号 " + cargoNo);
        if (ownerId == null) missing.add(ownerName.isEmpty() ? "ownerId或ownerName（货主）" : "有效的货主 " + ownerName);
        if (vehicleId == null) missing.add(plate.isEmpty() && simCode.isEmpty() ? "vehicleId、plateNumber或simCode（运输车辆）" : "有效的运输车辆标识");
        else if (!vehicleIssue.isEmpty()) missing.add(vehicleIssue);
        if (startLocation.isEmpty()) missing.add("startLocation（起点）");
        else if (startLongitude == null || startLatitude == null) missing.add(startGeocodeIssue.isEmpty() ? "可解析的起点文字地址" : startGeocodeIssue);
        if (endLocation.isEmpty()) missing.add("endLocation（目的地）");
        else if (endLongitude == null || endLatitude == null) missing.add(endGeocodeIssue.isEmpty() ? "可解析的目的地文字地址" : endGeocodeIssue);
        if (planStartTime.isEmpty()) missing.add("planStartTime（ISO 8601计划开始时间）");
        if (planEndTime.isEmpty()) missing.add("planEndTime（ISO 8601计划结束时间）");
        if (!planStartTime.isEmpty() && !planEndTime.isEmpty()
                && !OffsetDateTime.parse(planEndTime).isAfter(OffsetDateTime.parse(planStartTime))) {
            missing.add("晚于计划开始时间的planEndTime");
        }
        if (!missing.isEmpty()) return missing(
                "CARGO_OUTBOUND".equals(selection.intent) ? "cargo_outbound" : "create_transport_task",
                "办理出库并创建运输订单", missing, p);

        Map<String,Object> body = new LinkedHashMap<>();
        body.put("cargoId", cargoId); body.put("ownerId", ownerId); body.put("vehicleId", vehicleId);
        body.put("startLocation", startLocation); body.put("startLongitude", startLongitude); body.put("startLatitude", startLatitude);
        body.put("endLocation", endLocation); body.put("endLongitude", endLongitude); body.put("endLatitude", endLatitude);
        body.put("planStartTime", planStartTime); body.put("planEndTime", planEndTime);
        String key = idempotencyKey(selection);
        Object data = unwrap(writer.post("/api/v1/transport-tasks", body, token, key));
        Map<String,Object> task = map(data);
        String taskNo = task == null ? "已创建" : fallback(task.get("taskNo"), fallback(task.get("id"), "已创建"));
        String displayCargo = cargoNo.isEmpty() ? String.valueOf(cargoId) : cargoNo;
        String displayVehicle = !plate.isEmpty() ? plate : (!simCode.isEmpty() ? simCode : String.valueOf(vehicleId));
        return result("CARGO_OUTBOUND".equals(selection.intent) ? "cargo_outbound" : "create_transport_task",
                "/api/v1/transport-tasks",
                "货物 " + displayCargo + " 已办理出库并创建运输订单 " + taskNo + "，运输车辆 "
                        + displayVehicle + "，从“" + startLocation + "”运往“" + endLocation + "”。",
                data, key);
    }

    private void requireWarehouseManager(String token) throws IOException {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessDataService.BusinessApiException(401, "WAREHOUSE_LOGIN_REQUIRED", "办理仓库业务前请先登录仓库管理员账号");
        }
        Map<String,Object> me = map(unwrap(getter.get("/api/v1/users/me", token)));
        String role = me == null ? "" : text(me.get("role")).toUpperCase(Locale.ROOT);
        if (!"WAREHOUSE_MANAGER".equals(role)) {
            throw new BusinessDataService.BusinessApiException(403, "WAREHOUSE_MANAGER_REQUIRED", "只有仓库管理员可以新增车辆、绑定司机、办理入库、出库和创建运输订单");
        }
    }

    private Map<String,Object> findRecord(String path, String field, String expected, String token) throws IOException {
        if (expected == null || expected.isBlank()) return null;
        Map<String,Object> page = map(unwrap(getter.get(path, token)));
        if (page == null || !(page.get("records") instanceof List<?> records)) return null;
        for (Object item : records) {
            Map<String,Object> record = map(item);
            if (record != null && normalize(field, expected).equals(normalize(field, text(record.get(field))))) return record;
        }
        return null;
    }

    private List<Map<String,Object>> optionRecords(String path,String token)throws IOException{Object data=unwrap(getter.get(path,token));List<?> values=data instanceof List<?> list?list:(data instanceof Map<?,?> page&&page.get("records") instanceof List<?> records?records:List.of());List<Map<String,Object>> out=new ArrayList<>();for(Object value:values){Map<String,Object> item=map(value);if(item!=null)out.add(item);}return out;}

    private static BusinessDataService.BusinessAnswer missing(String tool, String operation, List<String> fields, Map<String,Object> supplied) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("executed", false); data.put("requiresInput", true); data.put("missingFields", fields); data.put("suppliedParameters", supplied);
        Map<String,Object> meta = meta(tool, "not-executed", data, null);
        return new BusinessDataService.BusinessAnswer("还不能" + operation + "，请补充：" + String.join("、", fields) + "。", meta);
    }

    private static BusinessDataService.BusinessAnswer result(String tool, String endpoint, String answer, Object data, String key) {
        return new BusinessDataService.BusinessAnswer(answer, meta(tool, endpoint, data, key));
    }

    private static Map<String,Object> meta(String tool, String endpoint, Object data, String key) {
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("tool", tool); meta.put("sourceType", "CLOUD_SPRING_BOOT_MYSQL"); meta.put("readOnly", false);
        meta.put("writeOperation", true); meta.put("endpoint", endpoint); meta.put("data", data);
        if (key != null) meta.put("idempotencyKey", key);
        return meta;
    }

    @SuppressWarnings("unchecked")
    private static Object unwrap(Object raw) throws IOException {
        Map<String,Object> root = map(raw);
        if (root == null) throw new IOException("业务接口返回格式错误");
        int code = intValue(root.get("code"), -1);
        if (code != 0 && (code < 200 || code >= 300)) throw new IOException("业务接口返回失败：" + fallback(root.get("message"), "code=" + code));
        return root.get("data");
    }

    static boolean isWriteIntent(String intent) {
        return "VEHICLE_CREATE".equals(intent) || "CARGO_INBOUND".equals(intent)
                || "CARGO_OUTBOUND".equals(intent) || "CREATE_TRANSPORT_TASK".equals(intent)
                || "ASSIGN_VEHICLE_DRIVER".equals(intent);
    }

    static ToolSelection fallbackSelection(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return null;
        boolean inbound = q.contains("入库") && (q.contains("将") || q.contains("把") || q.contains("办理"));
        boolean outbound = q.contains("出库") && (q.contains("将") || q.contains("把") || q.contains("办理") || q.contains("安排"));
        boolean createTask = (q.contains("创建订单") || q.contains("创建运输订单") || q.contains("创建运输任务"));
        boolean createVehicle = (q.contains("新增") || q.contains("添加") || q.contains("录入"))
                && (q.contains("车辆") || q.contains("货车") || q.contains("卡车") || q.contains("厢式车")
                || q.contains("面包车") || q.contains("冷链车") || q.contains("冷藏车"));
        boolean assignDriver=(q.contains("绑定")||q.contains("分配")||q.contains("指定")||q.contains("更换")||q.contains("改为"))&&q.contains("司机")&&(q.contains("车辆")||!capture(q,"([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]\\s*[A-Za-z]\\s*[A-Za-z0-9]{5,6})").isEmpty()||q.toLowerCase(Locale.ROOT).contains("sim_"));
        if (!inbound && !outbound && !createTask && !createVehicle && !assignDriver) return null;
        Map<String,Object> p = new LinkedHashMap<>();
        String cargoNo = capture(q, "(?i)(CG[A-Z0-9_-]{5,})");
        if (!cargoNo.isEmpty()) p.put("cargoNo", cargoNo.toUpperCase(Locale.ROOT));
        String plate = capture(q, "([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]\\s*[A-Za-z]\\s*[A-Za-z0-9]{5,6})");
        if (!plate.isEmpty()) p.put("plateNumber", normalizePlate(plate));
        if(assignDriver){String sim=capture(q,"(?i)(sim[_-]?[A-Za-z0-9]+)");if(!sim.isEmpty())p.put("simCode",sim.toLowerCase(Locale.ROOT));String vehicleId=capture(q,"(?:车辆|车)\\s*(?:ID|id)\\s*(?:为|是|=)?\\s*(\\d+)");if(!vehicleId.isEmpty())p.put("vehicleId",Long.valueOf(vehicleId));String driverId=capture(q,"司机\\s*(?:ID|id)\\s*(?:为|是|=)?\\s*(\\d+)");if(!driverId.isEmpty())p.put("driverId",Long.valueOf(driverId));String driverName=capture(q,"(?:绑定给|分配给|指定为|更换为|改为)?\\s*司机(?:姓名)?(?:为|是|叫)?\\s*([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9_]{1,19})");if(!driverName.isEmpty())p.put("driverName",driverName);return new ToolSelection("ASSIGN_VEHICLE_DRIVER",.92,p,false,"");}
        if (createVehicle) {
            extractWarehouseReference(q, p);
            String sim = capture(q, "(?i)(sim[_-]?[A-Za-z0-9]+)");
            if (!sim.isEmpty()) p.put("simCode", sim.toLowerCase(Locale.ROOT));
            String capacity = capture(q, "(?:载重|载重量|容量)(?:为|是)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:吨|公斤|千克|kg|t)");
            if (!capacity.isEmpty()) {
                Matcher unit = Pattern.compile("(?:载重|载重量|容量)(?:为|是)?\\s*\\d+(?:\\.\\d+)?\\s*(吨|公斤|千克|kg|t)", Pattern.CASE_INSENSITIVE).matcher(q);
                BigDecimal value = new BigDecimal(capacity);
                if (unit.find() && ("吨".equals(unit.group(1)) || "t".equalsIgnoreCase(unit.group(1)))) {
                    value = value.multiply(BigDecimal.valueOf(1000));
                }
                p.put("capacity", value);
            }
            String type = vehicleType(q);
            if (!type.isEmpty()) p.put("vehicleType", type);
            return new ToolSelection("VEHICLE_CREATE", 0.90, p, false, "");
        }
        if (inbound) {
            extractWarehouseReference(q, p);
            String cargoTypeId = capture(q, "(?:货物种类|货物类型|品类)\\s*(?:ID|id)\\s*(?:为|是|=)?\\s*(\\d+)");
            if (!cargoTypeId.isEmpty()) p.put("cargoTypeId", Long.valueOf(cargoTypeId));
            String cargoTypeName = capture(q, "(?:货物种类|货物类型|品类)(?:为|是|:|：)\\s*([\\p{IsHan}A-Za-z0-9_-]{1,40})");
            if (!cargoTypeName.isEmpty()) p.put("cargoTypeName", cargoTypeName);
            String weight = capture(q, "(?:重量(?:为|是)?\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:公斤|千克|kg)");
            String volume = capture(q, "(?:体积(?:为|是)?\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:立方米|m³|m3)");
            if (!weight.isEmpty()) p.put("weight", new BigDecimal(weight));
            if (!volume.isEmpty()) p.put("volume", new BigDecimal(volume));
            if (!cargoNo.isEmpty()) {
                int start = q.toUpperCase(Locale.ROOT).indexOf(cargoNo.toUpperCase(Locale.ROOT)) + cargoNo.length();
                String tail = start >= cargoNo.length() && start <= q.length() ? q.substring(start) : "";
                String name = tail.replaceFirst("^(?:号|的)?", "").split("办理入库|入库|，|,|重量|体积", 2)[0].trim();
                if (!name.isEmpty()) p.put("cargoName", name);
            }
            return new ToolSelection("CARGO_INBOUND", 0.90, p, false, "");
        }
        return new ToolSelection(outbound ? "CARGO_OUTBOUND" : "CREATE_TRANSPORT_TASK", 0.85, p, false, "");
    }

    private static void extractWarehouseReference(String question, Map<String,Object> parameters) {
        String warehouseId = capture(question, "(?:归属仓库|入库仓库|仓库)\\s*(?:ID|id)\\s*(?:为|是|=)?\\s*(\\d+)");
        if (!warehouseId.isEmpty()) {
            parameters.put("warehouseId", Long.valueOf(warehouseId));
            return;
        }
        String warehouseNo = capture(question, "(?:仓库编号|仓库号)(?:为|是|:|：)?\\s*([A-Za-z0-9_-]{2,40})");
        if (!warehouseNo.isEmpty()) {
            parameters.put("warehouseNo", warehouseNo);
            return;
        }
        String warehouseName = capture(question, "(?:归属仓库|入库仓库|放入|存入|入库到)(?:为|是|:|：)?\\s*([\\p{IsHan}A-Za-z0-9_-]{1,40}?)(?:仓库)?(?:，|,|。|；|;|载重|重量|体积|$)");
        if (!warehouseName.isEmpty()) parameters.put("warehouseName", warehouseName);
    }

    private static String capture(String value, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value);
        if (!matcher.find()) return "";
        return matcher.groupCount() > 0 ? matcher.group(1).trim() : matcher.group().trim();
    }

    private static String idempotencyKey(ToolSelection selection) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((selection.intent + "\n" + Json.stringify(selection.parameters)).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("agent-");
            for (int i = 0; i < 16; i++) out.append(String.format("%02x", hash[i]));
            return out.toString();
        } catch (Exception e) {
            throw new IOException("无法生成写操作幂等键", e);
        }
    }

    private static String normalize(String field, String value) { return "plateNumber".equals(field) ? normalizePlate(value) : text(value).toUpperCase(Locale.ROOT); }
    private static boolean sameText(String expected,Object actual){return text(expected).equalsIgnoreCase(text(actual));}
    private static String vehicleType(Object value) {
        String type = text(value).toUpperCase(Locale.ROOT);
        if (type.contains("REFRIGERATED") || type.contains("冷链") || type.contains("冷藏")) return "REFRIGERATED";
        if (type.contains("VAN") || type.contains("厢式") || type.contains("面包车")) return "VAN";
        if (type.contains("TRUCK") || type.contains("货车") || type.contains("卡车")) return "TRUCK";
        return "";
    }
    private static String normalizePlate(Object value) { return text(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    private static String validTime(Object value) { String s = text(value); if (s.isEmpty()) return ""; try { return OffsetDateTime.parse(s).toString(); } catch (Exception e) { return ""; } }
    private static Double coordinate(Object value, double min, double max) { if (value == null) return null; try { double n = value instanceof Number ? ((Number)value).doubleValue() : Double.parseDouble(String.valueOf(value)); return Double.isFinite(n) && n >= min && n <= max ? n : null; } catch (Exception e) { return null; } }
    private static BigDecimal positiveDecimal(Object value) { if (value == null) return null; try { BigDecimal n = new BigDecimal(String.valueOf(value)); return n.signum() > 0 ? n.stripTrailingZeros() : null; } catch (Exception e) { return null; } }
    private static Long positiveLong(Object value) { if (value == null) return null; try { long n = value instanceof Number ? ((Number)value).longValue() : Long.parseLong(String.valueOf(value)); return n > 0 ? n : null; } catch (Exception e) { return null; } }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String firstText(Object first, Object second) { String value = text(first); return value.isEmpty() ? text(second) : value; }
    private static String fallback(Object value, String fallback) { String s = text(value); return s.isEmpty() ? fallback : s; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static int intValue(Object value, int fallback) { if (value instanceof Number) return ((Number)value).intValue(); try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return value instanceof Map ? (Map<String,Object>)value : null; }
}
