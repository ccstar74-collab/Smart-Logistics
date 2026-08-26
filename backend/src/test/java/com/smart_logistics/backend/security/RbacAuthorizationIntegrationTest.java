package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.service.AlarmService;
import com.smart_logistics.backend.service.CargoItemService;
import com.smart_logistics.backend.service.CargoService;
import com.smart_logistics.backend.service.DispatchCommandService;
import com.smart_logistics.backend.service.DriverService;
import com.smart_logistics.backend.service.OwnerService;
import com.smart_logistics.backend.service.RegistrationService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.UserService;
import com.smart_logistics.backend.service.VehicleService;
import com.smart_logistics.backend.service.VehicleLocationQueryService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.TransportTaskStatusRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=rbac-integration-test-secret-at-least-32-bytes-long",
        "app.jwt.expires-seconds=28800",
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
    @MockitoBean private UserService userService;
    @MockitoBean private RegistrationService registrationService;
    @MockitoBean private CargoService cargoService;
    @MockitoBean private CargoItemService cargoItemService;
    @MockitoBean private VehicleService vehicleService;
    @MockitoBean private TransportTaskService transportTaskService;
    @MockitoBean private AlarmService alarmService;
    @MockitoBean private DriverService driverService;
    @MockitoBean private OwnerService ownerService;
    @MockitoBean private DispatchCommandService dispatchCommandService;
    @MockitoBean private VehicleLocationQueryService vehicleLocationQueryService;
    @MockitoBean private TaskTrackQueryService taskTrackQueryService;
    @MockitoBean private TransportTaskStatusRecordService statusRecordService;

    @Test
    void phase5ReadEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/locations/latest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transport-tasks/1/track-points"))
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
        mockMvc.perform(get("/api/v1/cargos/1/status-records")
                        .header("Authorization", token))
                .andExpect(status().isOk());
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
    @EnumSource(UserRole.class)
    void alarmStatusMutationRemainsDeniedByFormalRoleContract(UserRole role) throws Exception {
        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden());
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
        mockMvc.perform(put("/api/v1/alarms/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
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
    void allExistingDispatchCommandWebEndpointsRemainDenied() throws Exception {
        String token = token(UserRole.DISPATCHER);
        mockMvc.perform(get("/api/v1/dispatch-commands").header("Authorization", token))
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
        return "{\"plateNumber\":\"沪A10001\",\"type\":\"Truck\",\"capacity\":10}";
    }

    private String taskJson() {
        return "{\"cargoId\":10,\"ownerId\":3,\"vehicleId\":20,"
                + "\"startLocation\":\"A\",\"endLocation\":\"B\"}";
    }

    private String taskBaseUpdateJson() {
        return "{\"startLocation\":\"A2\",\"endLocation\":\"B2\"}";
    }

    private String cargoUpdateJson() {
        return "{\"name\":\"Updated Cargo\",\"weight\":10,\"volume\":2}";
    }

    private String cargoItemJson() {
        return "{\"itemName\":\"Item\",\"quantity\":1}";
    }
}
