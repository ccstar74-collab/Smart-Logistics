package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import com.smart_logistics.backend.dto.request.DispatchCommandStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.service.DispatchCommandService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DispatchCommandControllerTest {

    @Mock private DispatchCommandService dispatchCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DispatchCommandController(dispatchCommandService))
                .setValidator(validator).build();
    }

    @Test
    void postAcceptsServerOwnedIdentityContract() throws Exception {
        when(dispatchCommandService.createCommand(argThat(request ->
                request.getTaskId() == 15L
                        && request.getAlarmId() == 35L
                        && request.getCommandType() == DispatchCommandType.TEXT)))
                .thenReturn(response(DispatchCommandType.TEXT, DispatchCommandStatus.SENT));

        mockMvc.perform(post("/api/v1/dispatch-commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alarmId\":35,\"taskId\":15,\"commandType\":\"TEXT\"," +
                                "\"content\":\"Slow down\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alarmId").value(35))
                .andExpect(jsonPath("$.data.targetDriverId").value(2))
                .andExpect(jsonPath("$.data.vehicleId").value(16))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    void patchBindsDriverStatusRequest() throws Exception {
        when(dispatchCommandService.updateStatus(
                org.mockito.ArgumentMatchers.eq(101L),
                argThat(request -> request.getStatus() == DispatchCommandStatus.ACKNOWLEDGED)))
                .thenReturn(response(DispatchCommandType.TEXT,
                        DispatchCommandStatus.ACKNOWLEDGED));

        mockMvc.perform(patch("/api/v1/dispatch-commands/101/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"," +
                                "\"feedback\":\"Received\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
    }

    @Test
    void listPassesTaskDriverStatusAndTypeFilters() throws Exception {
        when(dispatchCommandService.listCommands(1, 10, null,
                DispatchCommandStatus.SENT, 15L, 2L, DispatchCommandType.ROUTE_CHANGE))
                .thenReturn(new PageResult<>(List.of(response(
                        DispatchCommandType.ROUTE_CHANGE, DispatchCommandStatus.SENT)), 1, 1, 10));

        mockMvc.perform(get("/api/v1/dispatch-commands")
                        .param("taskId", "15").param("driverId", "2")
                        .param("status", "SENT").param("commandType", "ROUTE_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        verify(dispatchCommandService).listCommands(1, 10, null,
                DispatchCommandStatus.SENT, 15L, 2L, DispatchCommandType.ROUTE_CHANGE);
    }

    @Test
    void unknownStatusReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch-commands").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private DispatchCommandResponse response(DispatchCommandType type,
                                             DispatchCommandStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-27T10:30:00+08:00");
        return new DispatchCommandResponse(
                101L, 35L, 15L, "T20260826001", 2L, "Li Si", 16L, "YuA8888",
                type == DispatchCommandType.ROUTE_CHANGE ? "route_v2" : null,
                type == DispatchCommandType.ROUTE_CHANGE ? 2 : null,
                type == DispatchCommandType.ROUTE_CHANGE
                        ? com.smart_logistics.backend.enums.TransportTaskRouteStatus.READY : null,
                type, "Slow down", status, null, 7L, now, now,
                status == DispatchCommandStatus.ACKNOWLEDGED ? now : null,
                null, null, null);
    }
}
