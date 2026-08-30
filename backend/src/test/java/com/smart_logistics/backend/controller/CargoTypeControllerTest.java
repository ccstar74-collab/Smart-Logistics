package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoTypeCreateRequest;
import com.smart_logistics.backend.dto.response.CargoTypeResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.CargoTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CargoTypeControllerTest {

    @Mock
    private CargoTypeService cargoTypeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CargoTypeController(cargoTypeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createAcceptsCompleteRequestAndReturnsAllFields() throws Exception {
        when(cargoTypeService.createCargoType(any(CargoTypeCreateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/cargo-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Medical ","unit":"box","unitWeight":12.5,
                                 "unitVolume":3.2,"description":"Fragile"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("Medical"))
                .andExpect(jsonPath("$.data.unit").value("box"))
                .andExpect(jsonPath("$.data.unitWeight").value(12.5))
                .andExpect(jsonPath("$.data.unitVolume").value(3.2))
                .andExpect(jsonPath("$.data.description").value("Fragile"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/cargo-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("name must not be blank"));
    }

    @Test
    void duplicateNameUsesUnifiedConflictResponse() throws Exception {
        when(cargoTypeService.createCargoType(any()))
                .thenThrow(new BusinessException(ErrorCode.DATA_CONFLICT,
                        "cargo type name already exists"));

        mockMvc.perform(post("/api/v1/cargo-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Medical\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void listReturnsPageAndPassesKeyword() throws Exception {
        when(cargoTypeService.listCargoTypes(2, 5, "Medical"))
                .thenReturn(new PageResult<>(List.of(response()), 6, 2, 5));

        mockMvc.perform(get("/api/v1/cargo-types")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "Medical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("Medical"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(cargoTypeService).listCargoTypes(2, 5, "Medical");
    }

    private CargoTypeResponse response() {
        return new CargoTypeResponse(10L, "Medical", "box",
                new BigDecimal("12.50"), new BigDecimal("3.20"), "Fragile",
                OffsetDateTime.parse("2026-08-30T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-30T10:00:00+08:00"));
    }
}
