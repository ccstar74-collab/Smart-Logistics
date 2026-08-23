package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.DispatchCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DispatchCommandControllerTest {

    @Mock
    private DispatchCommandService dispatchCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DispatchCommandController(dispatchCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsStandardPageAndPassesIndependentFilters() throws Exception {
        when(dispatchCommandService.listCommands(
                2, 5, "backup", DispatchCommandStatus.PENDING,
                15L, 1L, "ROUTE_CHANGE"
        )).thenReturn(new PageResult<>(List.of(response()), 6, 2, 5));

        mockMvc.perform(get("/api/v1/dispatch-commands")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "backup")
                        .param("status", "PENDING")
                        .param("taskId", "15")
                        .param("vehicleId", "1")
                        .param("commandType", "ROUTE_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].taskId").value(15))
                .andExpect(jsonPath("$.data.records[0].vehicleId").value(1))
                .andExpect(jsonPath("$.data.records[0].commandType").value("ROUTE_CHANGE"))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.total").value(6));

        verify(dispatchCommandService).listCommands(
                2, 5, "backup", DispatchCommandStatus.PENDING,
                15L, 1L, "ROUTE_CHANGE"
        );
    }

    @Test
    void getCommandReturnsStandardResponse() throws Exception {
        when(dispatchCommandService.getCommand(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/dispatch-commands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-08-23T10:30:00+08:00"));
    }

    @Test
    void getMissingCommandReturnsUnifiedNotFoundResponse() throws Exception {
        when(dispatchCommandService.getCommand(99999L)).thenThrow(
                new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "dispatch command not found"
                )
        );

        mockMvc.perform(get("/api/v1/dispatch-commands/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("dispatch command not found"));
    }

    @Test
    void listRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch-commands").param("status", "SENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("invalid request parameter or body"));
    }

    private DispatchCommandResponse response() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-23T10:30:00+08:00");
        return new DispatchCommandResponse(
                1L,
                15L,
                1L,
                7L,
                8L,
                "ROUTE_CHANGE",
                "Switch to backup route B",
                DispatchCommandStatus.PENDING,
                createdAt.plusMinutes(1),
                null,
                createdAt
        );
    }
}
