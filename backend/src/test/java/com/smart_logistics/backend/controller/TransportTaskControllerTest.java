package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
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
                new TransportTaskController(transportTaskService, taskTrackQueryService),
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
    void createRejectsMissingCargoId() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":30,"vehicleId":20,"startLocation":"Shanghai",
                                 "endLocation":"Beijing"}
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
                                 "endLocation":"Beijing"}
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
                                 "endLocation":"Beijing"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("startLocation must not be blank"));
    }

    @Test
    void listReturnsPageAndPassesStatusAndKeyword() throws Exception {
        when(transportTaskService.listTransportTasks(
                2, 5, "Shanghai", TransportTaskStatus.WAITING,
                null, null, null, null))
                .thenReturn(new PageResult<>(
                        List.of(response(TransportTaskStatus.WAITING)), 6, 2, 5));

        mockMvc.perform(get("/api/v1/transport-tasks")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "Shanghai")
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].taskNo").value("T202608230001"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(transportTaskService).listTransportTasks(
                2, 5, "Shanghai", TransportTaskStatus.WAITING,
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
                 "endLocation":"Beijing",
                 "planStartTime":"2026-08-24T10:00:00+08:00",
                 "planEndTime":"2026-08-24T15:00:00+08:00"}
                """;
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
}
