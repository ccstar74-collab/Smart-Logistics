package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.AlarmStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.AlarmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlarmControllerTest {

    @Mock
    private AlarmService alarmService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlarmController(alarmService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsStandardPageAndPassesAllFilters() throws Exception {
        when(alarmService.listAlarms(
                2, 5, "deviation", AlarmStatus.UNHANDLED,
                AlarmLevel.HIGH, AlarmType.ROUTE_DEVIATION
        )).thenReturn(new PageResult<>(List.of(response(AlarmStatus.UNHANDLED)), 6, 2, 5));

        mockMvc.perform(get("/api/v1/alarms")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "deviation")
                        .param("status", "UNHANDLED")
                        .param("level", "HIGH")
                        .param("alarmType", "ROUTE_DEVIATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].alarmType")
                        .value("ROUTE_DEVIATION"))
                .andExpect(jsonPath("$.data.records[0].level").value("HIGH"))
                .andExpect(jsonPath("$.data.records[0].status").value("UNHANDLED"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(alarmService).listAlarms(
                2, 5, "deviation", AlarmStatus.UNHANDLED,
                AlarmLevel.HIGH, AlarmType.ROUTE_DEVIATION
        );
    }

    @Test
    void getAlarmReturnsStandardResponse() throws Exception {
        when(alarmService.getAlarm(1L)).thenReturn(response(AlarmStatus.UNHANDLED));

        mockMvc.perform(get("/api/v1/alarms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.taskId").value(15))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-08-23T10:30:00+08:00"));
    }

    @Test
    void getMissingAlarmReturnsUnifiedNotFoundResponse() throws Exception {
        when(alarmService.getAlarm(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alarm not found")
        );

        mockMvc.perform(get("/api/v1/alarms/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("alarm not found"));
    }

    @Test
    void updateStatusReturnsUpdatedAlarm() throws Exception {
        when(alarmService.updateStatus(any(Long.class), any(AlarmStatusUpdateRequest.class)))
                .thenReturn(response(AlarmStatus.PROCESSING));

        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PROCESSING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        verify(alarmService).updateStatus(any(Long.class), any(AlarmStatusUpdateRequest.class));
    }

    @Test
    void updateStatusRejectsMissingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("status must not be null"));
    }

    @Test
    void updateStatusRejectsUnknownStatus() throws Exception {
        mockMvc.perform(put("/api/v1/alarms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLOSED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("invalid request parameter or body"));
    }

    @Test
    void listRejectsLegacyLevelEnum() throws Exception {
        mockMvc.perform(get("/api/v1/alarms").param("level", "CRITICAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private AlarmResponse response(AlarmStatus status) {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-23T10:30:00+08:00");
        return new AlarmResponse(
                1L,
                15L,
                "real_001",
                AlarmType.ROUTE_DEVIATION,
                AlarmLevel.HIGH,
                "Vehicle deviated from the planned route",
                status,
                "device",
                createdAt,
                null,
                status == AlarmStatus.UNHANDLED ? null : createdAt.plusMinutes(1),
                createdAt,
                status == AlarmStatus.RESOLVED ? createdAt.plusMinutes(2) : null
        );
    }
}
