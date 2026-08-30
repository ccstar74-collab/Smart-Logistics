package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.service.AlarmService;
import com.smart_logistics.backend.service.CargoItemService;
import com.smart_logistics.backend.service.CargoService;
import com.smart_logistics.backend.service.CargoTypeService;
import com.smart_logistics.backend.service.DispatchCommandService;
import com.smart_logistics.backend.service.DriverService;
import com.smart_logistics.backend.service.OwnerService;
import com.smart_logistics.backend.service.OriginRecommendationService;
import com.smart_logistics.backend.service.RegistrationService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.TransportTaskReplanService;
import com.smart_logistics.backend.service.TransportTaskPlaybackService;
import com.smart_logistics.backend.service.UserService;
import com.smart_logistics.backend.service.VehicleService;
import com.smart_logistics.backend.service.VehicleLocationQueryService;
import com.smart_logistics.backend.service.WarehouseService;
import com.smart_logistics.backend.service.WarehouseTransportTaskCreateService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.TransportTaskStatusRecordService;
import com.smart_logistics.backend.service.route.MultiObjectiveRoutePlanningService;
import com.smart_logistics.backend.service.weather.RouteWeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=rbac-integration-test-secret-at-least-32-bytes-long",
        "app.jwt.expires-seconds=28800",
        "mqtt.enabled=false",
        "influxdb.bucket=test-vehicle-trace-bucket",
        "influxdb2.url=http://127.0.0.1:65535",
        "influxdb2.token=test-token-not-a-real-secret",
        "influxdb2.org=test-org",
        "influxdb2.bucket=test-gps-bucket"
})
@AutoConfigureMockMvc
class RbacAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;
    @MockitoBean private UserService userService;
    @MockitoBean private RegistrationService registrationService;
    @MockitoBean private CargoService cargoService;
    @MockitoBean private CargoTypeService cargoTypeService;
    @MockitoBean private CargoItemService cargoItemService;
    @MockitoBean private VehicleService vehicleService;
    @MockitoBean private TransportTaskService transportTaskService;
    @MockitoBean private TransportTaskReplanService transportTaskReplanService;
    @MockitoBean private TransportTaskPlaybackService transportTaskPlaybackService;
    @MockitoBean private AlarmService alarmService;
    @MockitoBean private DriverService driverService;
    @MockitoBean private OwnerService ownerService;
    @MockitoBean private OriginRecommendationService originRecommendationService;
    @MockitoBean private DispatchCommandService dispatchCommandService;
    @MockitoBean private VehicleLocationQueryService vehicleLocationQueryService;
    @MockitoBean private WarehouseService warehouseService;
    @MockitoBean private WarehouseTransportTaskCreateService warehouseTaskCreateService;
    @MockitoBean private TaskTrackQueryService taskTrackQueryService;
    @MockitoBean private TransportTaskStatusRecordService statusRecordService;
    @MockitoBean private MultiObjectiveRoutePlanningService multiObjectiveRoutePlanningService;
    @MockitoBean private RouteWeatherService routeWeatherService;

    @Test
    void phase5ReadEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/locations/latest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transport-tasks/1/track-points"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transport-tasks/1/playback"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transport-tasks/1/planned-route"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transport-tasks/1/route-data/weather"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cargos/1/status-records"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void allFormalRolesCanReachPhase5ReadContracts(UserRole role) throws Exception {
        String token = token(role);
        mockMvc.perform(get("/api/v1/vehicles/locations/latest")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/1/track-points")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/1/playback")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cargos/1/status-records")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/1/routes")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/1/route-data/weather")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void dispatcherCanCreateCandidatesAndRunFastRecovery() throws Exception {
        String token = token(UserRole.DISPATCHER);

        mockMvc.perform(post("/api/v1/transport-tasks/1/routes")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transport-tasks/1/routes/candidates")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/transport-tasks/1/routes/replan-from-latest-location")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replanJson()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"OWNER", "DRIVER", "WAREHOUSE_MANAGER", "ADMIN"})
    void nonDispatcherCannotMutateRouteVersions(UserRole role) throws Exception {
        String token = token(role);

        mockMvc.perform(post("/api/v1/transport-tasks/1/routes")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/transport-tasks/1/routes/candidates")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/transport-tasks/1/routes/replan-from-latest-location")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replanJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void directRouteActivationEndpointIsNotRegistered() {
        boolean directActivationRegistered = requestMappingHandlerMapping.getHandlerMethods()
                .keySet()
                .stream()
                .filter(mapping -> mapping.getMethodsCondition().getMethods().contains(RequestMethod.PUT))
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .anyMatch("/api/v1/transport-tasks/{id}/routes/{routeId}/activate"::equals);

        assertFalse(directActivationRegistered);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "DRIVER", "DISPATCHER"})
    void cargoDeleteRejectsRolesWithoutBaseDataMaintenancePermission(UserRole role)
            throws Exception {
        mockMvc.perform(delete("/api/v1/cargos/1").header("Authorization", token(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"WAREHOUSE_MANAGER", "ADMIN"})
    void cargoDeleteAllowsConfirmedMaintenanceRoles(UserRole role) throws Exception {
        mockMvc.perform(delete("/api/v1/cargos/1").header("Authorization", token(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"OWNER", "DRIVER", "WAREHOUSE_MANAGER", "ADMIN"})
    void nonDispatcherRolesCannotManuallyResolveAlarm(UserRole role) throws Exception {
        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"," +
                                "\"remark\":\"False positive\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCanManuallyResolveAlarmWithRemark() throws Exception {
        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .header("Authorization", token(UserRole.DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"," +
                                "\"remark\":\"False positive\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanReadBusinessDataButCannotWriteCargoOrTask() throws Exception {
        String token = token(UserRole.OWNER);
        mockMvc.perform(get("/api/v1/cargos").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/current").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/alarms").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cargos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/transport-tasks").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(taskJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverCanReadAndReportStatusButCannotMaintainBaseData() throws Exception {
        String token = token(UserRole.DRIVER);
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cargos").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks/current").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/transport-tasks/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANSPORTING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/vehicles/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseManagerMaintainsWarehouseDataButNotStatusOrAlarms() throws Exception {
        String token = token(UserRole.WAREHOUSE_MANAGER);
        mockMvc.perform(post("/api/v1/cargos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transport-tasks").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(taskJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(originRecommendationJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transport-tasks/from-warehouse")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseTaskJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/transport-tasks/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(taskBaseUpdateJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/cargos/1/items/2").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoItemJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/cargos/1/items/2").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/transport-tasks/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANSPORTING\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/alarms").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherReadsAllDispatchDataButCannotWrite() throws Exception {
        String token = token(UserRole.DISPATCHER);
        for (String path : new String[]{"/api/v1/cargos", "/api/v1/vehicles",
                "/api/v1/transport-tasks", "/api/v1/alarms"}) {
            mockMvc.perform(get(path).header("Authorization", token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/cargos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/vehicles/1").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"OWNER", "DRIVER", "DISPATCHER", "ADMIN"})
    void originRecommendationRejectsRolesWithoutTaskCreatePermission(UserRole role)
            throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(originRecommendationJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"OWNER", "DRIVER", "DISPATCHER", "ADMIN"})
    void warehouseTaskCreateRejectsRolesWithoutLegacyCreatePermission(UserRole role)
            throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks/from-warehouse")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseTaskJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"WAREHOUSE_MANAGER", "DISPATCHER", "ADMIN"})
    void operationalBaseDataRolesCanReadCargoTypesAndWarehouses(UserRole role)
            throws Exception {
        String token = token(role);
        mockMvc.perform(get("/api/v1/cargo-types").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warehouses").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warehouses/1").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "DRIVER"})
    void ownerAndDriverCannotReadOperationalBaseData(UserRole role) throws Exception {
        String token = token(role);
        mockMvc.perform(get("/api/v1/cargo-types").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/warehouses").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"WAREHOUSE_MANAGER", "ADMIN"})
    void warehouseManagerAndAdminCanCreateCargoTypes(UserRole role) throws Exception {
        mockMvc.perform(post("/api/v1/cargo-types")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Medical\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class,
            names = {"OWNER", "DRIVER", "DISPATCHER"})
    void otherRolesCannotCreateCargoTypes(UserRole role) throws Exception {
        mockMvc.perform(post("/api/v1/cargo-types")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Medical\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminHasExplicitBaseDataPermissionsButNotTaskOrAlarmMutation() throws Exception {
        String token = token(UserRole.ADMIN);
        mockMvc.perform(post("/api/v1/cargos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/vehicles").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/cargos/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cargoUpdateJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/vehicles/1/driver").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":9}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transport-tasks").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transport-tasks").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(taskJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/transport-tasks/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(taskBaseUpdateJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/transport-tasks/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANSPORTING\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/alarms").header("Authorization", token))
                .andExpect(status().isOk());
        // 管理员只读：可查看全部告警，不能手动消警、不能下发调度指令
        mockMvc.perform(put("/api/v1/alarms/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"," +
                                "\"remark\":\"False positive\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dispatch-commands").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dispatch-commands").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":15,\"commandType\":\"TEXT\"," +
                                "\"content\":\"Slow down\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "DRIVER"})
    void identityOptionsRejectOperationalConsumers(UserRole role) throws Exception {
        String token = token(role);
        mockMvc.perform(get("/api/v1/drivers/options").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/owners/options").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatchCommandEndpointsEnforceLocalRoleContract() throws Exception {
        String dispatcher = token(UserRole.DISPATCHER);
        mockMvc.perform(get("/api/v1/dispatch-commands")
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dispatch-commands")
                        .header("Authorization", dispatcher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":15,\"commandType\":\"TEXT\"," +
                                "\"content\":\"Slow down\"}"))
                .andExpect(status().isOk());

        String driver = token(UserRole.DRIVER);
        mockMvc.perform(get("/api/v1/drivers/me/dispatch-commands")
                        .header("Authorization", driver))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/dispatch-commands/101/status")
                        .header("Authorization", driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dispatch-commands")
                        .header("Authorization", driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":15,\"commandType\":\"TEXT\"," +
                                "\"content\":\"Forged\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyRealtimeDevelopmentEndpointsRemainDenied() throws Exception {
        String token = token(UserRole.ADMIN);
        mockMvc.perform(get("/api/gps/track/sim_019").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/vehicle/trace")
                        .header("Authorization", token)
                        .param("vehicleId", "sim_019")
                        .param("start", "1")
                        .param("end", "2"))
                .andExpect(status().isForbidden());
    }

    private String token(UserRole role) {
        long userId = role.ordinal() + 100L;
        when(userService.getActiveIdentity(userId)).thenReturn(new UserIdentityResponse(
                userId, role.name().toLowerCase(), role.name(), null,
                role, UserStatus.ACTIVE,
                role == UserRole.DRIVER ? 9L : null,
                role == UserRole.OWNER ? 3L : null));
        return "Bearer " + jwtService.generateToken(userId, role.name().toLowerCase(), role);
    }

    private String cargoJson() {
        return "{\"cargoNo\":\"C-1\",\"name\":\"Cargo\",\"ownerId\":3}";
    }

    private String vehicleJson() {
        return "{\"plateNumber\":\"沪A10001\",\"type\":\"Truck\",\"capacity\":10,"
                + "\"simCode\":\"sim_008\"}";
    }

    private String taskJson() {
        return "{\"cargoId\":10,\"ownerId\":3,\"vehicleId\":20,"
                + "\"startLocation\":\"A\",\"startLongitude\":106.735012,"
                + "\"startLatitude\":29.610634,\"endLocation\":\"B\","
                + "\"endLongitude\":106.759396,\"endLatitude\":29.620115}";
    }

    private String taskBaseUpdateJson() {
        return "{\"startLocation\":\"A2\",\"endLocation\":\"B2\"}";
    }

    private String originRecommendationJson() {
        return "{\"ownerId\":3,\"cargoTypeId\":10,\"endLocation\":\"B\","
                + "\"endLongitude\":106.759396,\"endLatitude\":29.620115}";
    }

    private String warehouseTaskJson() {
        return "{\"ownerId\":3,\"cargoTypeId\":10,\"originWarehouseId\":1,"
                + "\"cargoId\":10,\"vehicleId\":20,\"endLocation\":\"B\","
                + "\"endLongitude\":106.759396,\"endLatitude\":29.620115}";
    }

    private String cargoUpdateJson() {
        return "{\"name\":\"Updated Cargo\",\"weight\":10,\"volume\":2}";
    }

    private String replanJson() {
        return "{\"vehicleDeviceCode\":\"sim_019\",\"longitude\":106.580123,"
                + "\"latitude\":29.620456,\"coordinateSystem\":\"WGS84\","
                + "\"positionAt\":\"2026-08-28T12:00:01.123Z\"}";
    }

    private String cargoItemJson() {
        return "{\"itemName\":\"Item\",\"quantity\":1}";
    }
}
