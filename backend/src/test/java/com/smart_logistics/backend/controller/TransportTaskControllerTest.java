package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskReplanRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.PlaybackActualTrackResponse;
import com.smart_logistics.backend.dto.response.PlaybackTrackPointResponse;
import com.smart_logistics.backend.dto.response.TransportTaskPlaybackResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.TransportTaskReplanService;
import com.smart_logistics.backend.service.TransportTaskPlaybackService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransportTaskControllerTest {

    @Mock
    private TransportTaskService transportTaskService;
    @Mock
    private TaskTrackQueryService taskTrackQueryService;
    @Mock
    private EtaPlannedRouteService etaPlannedRouteService;
    @Mock
    private TransportTaskReplanService transportTaskReplanService;
    @Mock
    private TransportTaskPlaybackService transportTaskPlaybackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MethodValidationPostProcessor methodValidation = new MethodValidationPostProcessor();
        methodValidation.setProxyTargetClass(true);
        methodValidation.setValidator(validator);
        methodValidation.afterPropertiesSet();
        Object controller = methodValidation.postProcessAfterInitialization(
                new TransportTaskController(transportTaskService, taskTrackQueryService,
                        etaPlannedRouteService, transportTaskReplanService,
                        transportTaskPlaybackService),
                "transportTaskController");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsGeneratedTaskNumberAndWaitingStatus() throws Exception {
        when(transportTaskService.createTransportTask(any(TransportTaskCreateRequest.class)))
                .thenReturn(response(TransportTaskStatus.WAITING));

        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.taskNo").value("T202608230001"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    void trackPointsUsesOfficialTaskEndpoint() throws Exception {
        when(taskTrackQueryService.getTrackPoints(1L)).thenReturn(List.of(
                new VehicleLocationResponse(20L, "沪A10001", 121.5, 31.2,
                        20.0, 45.0, OffsetDateTime.parse("2026-08-25T10:00:00+08:00"),
                        false, 1L)));
        mockMvc.perform(get("/api/v1/transport-tasks/1/track-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskId").value(1))
                .andExpect(jsonPath("$.data[0].longitude").value(121.5));
    }

    @Test
    void playbackReturnsTaskScopedAggregateWithExplicitTrackCoordinates() throws Exception {
        when(transportTaskPlaybackService.getPlayback(1L)).thenReturn(
                new TransportTaskPlaybackResponse(
                        response(TransportTaskStatus.COMPLETED),
                        new PlaybackActualTrackResponse("WGS84", List.of(
                                new PlaybackTrackPointResponse(106.580123, 29.620456,
                                        0.0, 90.0,
                                        OffsetDateTime.parse("2026-08-30T10:00:00+08:00")))),
                        List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/transport-tasks/1/playback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.id").value(1))
                .andExpect(jsonPath("$.data.actualTrack.coordinateSystem").value("WGS84"))
                .andExpect(jsonPath("$.data.actualTrack.points[0].longitude")
                        .value(106.580123))
                .andExpect(jsonPath("$.data.routeVersions").isArray())
                .andExpect(jsonPath("$.data.alarms").isArray())
                .andExpect(jsonPath("$.data.dispatchCommands").isArray())
                .andExpect(jsonPath("$.data.events").isArray());
    }

    @Test
    void playbackPreservesTaskDataScopeFailure() throws Exception {
        when(transportTaskPlaybackService.getPlayback(1L)).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN,
                        "resource is outside current user data scope"));

        mockMvc.perform(get("/api/v1/transport-tasks/1/playback"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void plannedRouteReturnsFrontendReadyGcj02Polyline() throws Exception {
        TransportTaskResponse task = response(TransportTaskStatus.WAITING);
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(etaPlannedRouteService.getResponse(task)).thenReturn(
                new PlannedRouteResponse(1L, "route_fixed", 1,
                        TransportTaskRouteStatus.ACTIVE,
                        "sim_000", "AMAP", "GCJ02", 5500, 720,
                        OffsetDateTime.parse("2026-08-26T16:00:00+08:00"),
                        List.of(
                                List.of(106.57, 29.49),
                                List.of(106.61, 29.52))));

        mockMvc.perform(get("/api/v1/transport-tasks/1/planned-route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value("route_fixed"))
                .andExpect(jsonPath("$.data.routeVersion").value(1))
                .andExpect(jsonPath("$.data.routeStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.vehicleDeviceCode").value("sim_000"))
                .andExpect(jsonPath("$.data.provider").value("AMAP"))
                .andExpect(jsonPath("$.data.coordinateSystem").value("GCJ02"))
                .andExpect(jsonPath("$.data.distanceMeters").value(5500))
                .andExpect(jsonPath("$.data.points[0][0]").value(106.57))
                .andExpect(jsonPath("$.data.points[0][1]").value(29.49))
                .andExpect(jsonPath("$.data.points.length()").value(2));
    }

    @Test
    void replanResponseIsImmediatelyVisibleThroughExistingPlannedRouteEndpoint()
            throws Exception {
        TransportTaskResponse task = response(TransportTaskStatus.TRANSPORTING);
        PlannedRouteResponse replacement = new PlannedRouteResponse(
                1L, "route_replanned", 4, TransportTaskRouteStatus.ACTIVE,
                "sim_000", "AMAP", "GCJ02", 4_200, 540,
                OffsetDateTime.parse("2026-08-28T16:00:00+08:00"),
                List.of(List.of(106.58, 29.50), List.of(106.61, 29.52)));
        when(transportTaskReplanService.replanFromLatestLocation(
                org.mockito.ArgumentMatchers.eq(1L),
                any(TransportTaskReplanRequest.class)))
                .thenReturn(replacement);
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(etaPlannedRouteService.getResponse(task)).thenReturn(replacement);

        mockMvc.perform(post(
                        "/api/v1/transport-tasks/1/routes/replan-from-latest-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReplanJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value("route_replanned"))
                .andExpect(jsonPath("$.data.routeVersion").value(4))
                .andExpect(jsonPath("$.data.routeStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/transport-tasks/1/planned-route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value("route_replanned"))
                .andExpect(jsonPath("$.data.routeVersion").value(4))
                .andExpect(jsonPath("$.data.routeStatus").value("ACTIVE"));
    }

    @Test
    void replanRejectsUnsupportedCoordinateSystemAndPositionWithoutOffset()
            throws Exception {
        mockMvc.perform(post(
                        "/api/v1/transport-tasks/1/routes/replan-from-latest-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReplanJson().replace("WGS84", "GCJ02")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("coordinateSystem must be WGS84"));

        mockMvc.perform(post(
                        "/api/v1/transport-tasks/1/routes/replan-from-latest-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReplanJson().replace("Z\"", "\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transportTaskReplanService);
    }

    @Test
    void plannedRoutePreservesTaskDataScopeFailure() throws Exception {
        when(transportTaskService.getTransportTask(1L)).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN,
                        "resource is outside current user data scope"));

        mockMvc.perform(get("/api/v1/transport-tasks/1/planned-route"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(etaPlannedRouteService);
    }

    @Test
    void plannedRouteUnavailableDoesNotReturnSuccessfulEmptyPoints() throws Exception {
        TransportTaskResponse task = response(TransportTaskStatus.WAITING);
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(etaPlannedRouteService.getResponse(task)).thenThrow(
                new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                        "planned route is unavailable"));

        mockMvc.perform(get("/api/v1/transport-tasks/1/planned-route"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void routeListReturnsAllVersionsInServiceOrder() throws Exception {
        when(transportTaskService.listTransportTaskRoutes(1L)).thenReturn(List.of(
                routeResponse("route_v1", 1, TransportTaskRouteStatus.ACTIVE),
                routeResponse("route_v2", 2, TransportTaskRouteStatus.READY)));

        mockMvc.perform(get("/api/v1/transport-tasks/1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].routeId").value("route_v1"))
                .andExpect(jsonPath("$.data[0].routeStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].routeVersion").value(2))
                .andExpect(jsonPath("$.data[1].routeStatus").value("READY"));
    }

    @Test
    void createReadyRouteReturnsVersionedSnapshot() throws Exception {
        when(transportTaskService.createReadyRoute(1L)).thenReturn(
                routeResponse("route_v2", 2, TransportTaskRouteStatus.READY));

        mockMvc.perform(post("/api/v1/transport-tasks/1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value("route_v2"))
                .andExpect(jsonPath("$.data.routeVersion").value(2))
                .andExpect(jsonPath("$.data.routeStatus").value("READY"));
    }

    @Test
    void routeListPreservesTaskDataScopeFailure() throws Exception {
        when(transportTaskService.listTransportTaskRoutes(1L)).thenThrow(
                new BusinessException(ErrorCode.FORBIDDEN,
                        "resource is outside current user data scope"));

        mockMvc.perform(get("/api/v1/transport-tasks/1/routes"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void createRejectsMissingCargoId() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":30,"vehicleId":20,"startLocation":"Shanghai",
                                 "startLongitude":106.735012,"startLatitude":29.610634,
                                 "endLocation":"Beijing",
                                 "endLongitude":106.759396,"endLatitude":29.620115}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("cargoId must not be null"));
    }

    @Test
    void createRejectsMissingOwnerId() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoId":10,"vehicleId":20,"startLocation":"Shanghai",
                                 "startLongitude":106.735012,"startLatitude":29.610634,
                                 "endLocation":"Beijing",
                                 "endLongitude":106.759396,"endLatitude":29.620115}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("ownerId must not be null"));
    }

    @Test
    void createRejectsBlankStartLocation() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoId":10,"ownerId":30,"vehicleId":20,"startLocation":" ",
                                 "startLongitude":106.735012,"startLatitude":29.610634,
                                 "endLocation":"Beijing",
                                 "endLongitude":106.759396,"endLatitude":29.620115}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("startLocation must not be blank"));
    }

    @Test
    void createRejectsMissingStartLongitude() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLatitude\":29.610634,\"endLongitude\":106.759396,"
                        + "\"endLatitude\":29.620115,",
                "startLongitude must not be null");
    }

    @Test
    void createRejectsMissingStartLatitude() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLongitude\":106.735012,\"endLongitude\":106.759396,"
                        + "\"endLatitude\":29.620115,",
                "startLatitude must not be null");
    }

    @Test
    void createRejectsMissingEndLongitude() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLongitude\":106.735012,\"startLatitude\":29.610634,"
                        + "\"endLatitude\":29.620115,",
                "endLongitude must not be null");
    }

    @Test
    void createRejectsMissingEndLatitude() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLongitude\":106.735012,\"startLatitude\":29.610634,"
                        + "\"endLongitude\":106.759396,",
                "endLatitude must not be null");
    }

    @Test
    void createRejectsLongitudeOutsideRange() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLongitude\":180.000001,\"startLatitude\":29.610634,"
                        + "\"endLongitude\":106.759396,\"endLatitude\":29.620115,",
                "startLongitude must not exceed 180");
    }

    @Test
    void createRejectsLatitudeOutsideRange() throws Exception {
        assertCreateCoordinateValidation(
                "\"startLongitude\":106.735012,\"startLatitude\":-90.000001,"
                        + "\"endLongitude\":106.759396,\"endLatitude\":29.620115,",
                "startLatitude must be at least -90");
    }

    @Test
    void listReturnsPageAndPassesStatusAndKeyword() throws Exception {
        when(transportTaskService.listTransportTasks(
                2, 5, "Shanghai", List.of(TransportTaskStatus.WAITING,
                        TransportTaskStatus.TRANSPORTING),
                null, null, null, null))
                .thenReturn(new PageResult<>(
                        List.of(response(TransportTaskStatus.WAITING)), 6, 2, 5));

        mockMvc.perform(get("/api/v1/transport-tasks")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "Shanghai")
                        .param("status", "WAITING,TRANSPORTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].taskNo").value("T202608230001"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(transportTaskService).listTransportTasks(
                2, 5, "Shanghai", List.of(TransportTaskStatus.WAITING,
                        TransportTaskStatus.TRANSPORTING),
                null, null, null, null);
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/transport-tasks").param("status", "IN_TRANSIT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("invalid request parameter or body"));
    }

    @Test
    void getReturnsDetailIncludingEta() throws Exception {
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(response(TransportTaskStatus.TRANSPORTING));

        mockMvc.perform(get("/api/v1/transport-tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.estimatedArrivalTime")
                        .value("2026-08-24T15:00:00+08:00"));
    }

    @Test
    void getMissingTaskReturnsUnifiedNotFound() throws Exception {
        when(transportTaskService.getTransportTask(999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "transport task not found"));

        mockMvc.perform(get("/api/v1/transport-tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("transport task not found"));
    }

    @Test
    void getRejectsNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/v1/transport-tasks/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void updateWaitingToTransportingReturnsTransporting() throws Exception {
        assertStatusUpdate(TransportTaskStatus.TRANSPORTING);
    }

    @Test
    void updateTransportingToCompletedReturnsCompleted() throws Exception {
        assertStatusUpdate(TransportTaskStatus.COMPLETED);
    }

    @Test
    void updateTransportingToAbnormalReturnsAbnormal() throws Exception {
        assertStatusUpdate(TransportTaskStatus.ABNORMAL);
    }

    @Test
    void updateWaitingToCancelledReturnsCancelled() throws Exception {
        assertStatusUpdate(TransportTaskStatus.CANCELLED);
    }

    @Test
    void updateIllegalTransitionReturnsConflict() throws Exception {
        when(transportTaskService.updateTransportTaskStatusForDriver(
                org.mockito.ArgumentMatchers.eq(1L),
                any(TransportTaskStatusUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STATE_CONFLICT,
                        "invalid transport task status transition"));

        mockMvc.perform(put("/api/v1/transport-tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message")
                        .value("invalid transport task status transition"));
    }

    @Test
    void updateMissingTaskReturnsNotFound() throws Exception {
        when(transportTaskService.updateTransportTaskStatusForDriver(
                org.mockito.ArgumentMatchers.eq(999L),
                any(TransportTaskStatusUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "transport task not found"));

        mockMvc.perform(put("/api/v1/transport-tasks/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANSPORTING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void updateRejectsInvalidEnumValue() throws Exception {
        mockMvc.perform(put("/api/v1/transport-tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void updateRejectsMissingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/transport-tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status must not be null"));
    }

    private void assertStatusUpdate(TransportTaskStatus statusValue) throws Exception {
        when(transportTaskService.updateTransportTaskStatusForDriver(
                org.mockito.ArgumentMatchers.eq(1L),
                any(TransportTaskStatusUpdateRequest.class)))
                .thenReturn(response(statusValue));

        mockMvc.perform(put("/api/v1/transport-tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusValue.name() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(statusValue.name()));
    }

    private String validCreateJson() {
        return """
                {"cargoId":10,"ownerId":30,"vehicleId":20,"startLocation":"Shanghai",
                 "startLongitude":106.735012,"startLatitude":29.610634,
                 "endLocation":"Beijing","endLongitude":106.759396,"endLatitude":29.620115,
                 "plannedStartTime":"2026-08-24T10:00:00+08:00",
                 "planEndTime":"2026-08-24T15:00:00+08:00"}
                """;
    }

    private String validReplanJson() {
        return """
                {"vehicleDeviceCode":"sim_019","longitude":106.580123,
                 "latitude":29.620456,"coordinateSystem":"WGS84",
                 "positionAt":"2026-08-28T12:00:01.123Z"}
                """;
    }

    private void assertCreateCoordinateValidation(String coordinateFields,
                                                  String expectedMessage) throws Exception {
        String body = "{\"cargoId\":10,\"ownerId\":30,\"vehicleId\":20,"
                + "\"startLocation\":\"Shanghai\"," + coordinateFields
                + "\"endLocation\":\"Beijing\"}";
        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    private TransportTaskResponse response(TransportTaskStatus status) {
        return new TransportTaskResponse(
                1L, "T202608230001", 10L, 20L, "Shanghai", "Beijing",
                OffsetDateTime.parse("2026-08-24T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-24T15:00:00+08:00"),
                status == TransportTaskStatus.WAITING ? null
                        : OffsetDateTime.parse("2026-08-24T10:05:00+08:00"),
                status == TransportTaskStatus.COMPLETED
                        ? OffsetDateTime.parse("2026-08-24T14:50:00+08:00") : null,
                status,
                OffsetDateTime.parse("2026-08-24T15:00:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:00:00+08:00")
        );
    }

    private TransportTaskRouteResponse routeResponse(
            String routeId, int routeVersion, TransportTaskRouteStatus status) {
        return new TransportTaskRouteResponse(
                routeId, 1L, routeVersion, status, "AMAP", "GCJ02",
                5_500, 720,
                OffsetDateTime.parse("2026-08-26T16:00:00+08:00"),
                List.of(List.of(106.5701, 29.4901),
                        List.of(106.6101, 29.5201)));
    }
}
