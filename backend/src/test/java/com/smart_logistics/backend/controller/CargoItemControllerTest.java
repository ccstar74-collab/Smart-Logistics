package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.request.CargoItemCreateRequest;
import com.smart_logistics.backend.dto.response.CargoItemResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.CargoItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CargoItemControllerTest {

    @Mock
    private CargoItemService cargoItemService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CargoItemController(cargoItemService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createCargoItemUsesPathCargoIdAndReturnsStandardResponse() throws Exception {
        when(cargoItemService.createCargoItem(any(Long.class),
                any(CargoItemCreateRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.cargoId").value(2))
                .andExpect(jsonPath("$.data.itemName").value("显示器"))
                .andExpect(jsonPath("$.data.quantity").value(10));

        ArgumentCaptor<CargoItemCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(CargoItemCreateRequest.class);
        verify(cargoItemService).createCargoItem(
                org.mockito.ArgumentMatchers.eq(2L),
                requestCaptor.capture()
        );
        assertEquals("显示器", requestCaptor.getValue().getItemName());
    }

    @Test
    void createCargoItemRejectsBlankItemName() throws Exception {
        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":" ","quantity":10,"unit":"台",
                                 "weight":35.50,"volume":0.80}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("itemName must not be blank"));
    }

    @Test
    void createCargoItemRejectsZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"显示器","quantity":0,"unit":"台",
                                 "weight":35.50,"volume":0.80}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("quantity must be greater than 0"));
    }

    @Test
    void createCargoItemRejectsNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"显示器","quantity":-1,"unit":"台",
                                 "weight":35.50,"volume":0.80}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("quantity must be greater than 0"));
    }

    @Test
    void createCargoItemRejectsNegativeWeight() throws Exception {
        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"显示器","quantity":10,"unit":"台",
                                 "weight":-0.01,"volume":0.80}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("weight must be greater than or equal to 0"));
    }

    @Test
    void createCargoItemRejectsNegativeVolume() throws Exception {
        mockMvc.perform(post("/api/v1/cargos/2/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"显示器","quantity":10,"unit":"台",
                                 "weight":35.50,"volume":-0.01}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("volume must be greater than or equal to 0"));
    }

    @Test
    void getCargoItemsReturnsStandardListResponse() throws Exception {
        when(cargoItemService.getCargoItemsByCargoId(2L))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/cargos/2/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].cargoId").value(2))
                .andExpect(jsonPath("$.data[0].itemName").value("显示器"));

        verify(cargoItemService).getCargoItemsByCargoId(2L);
    }

    @Test
    void getCargoItemsReturnsEmptyDataForExistingCargoWithoutItems() throws Exception {
        when(cargoItemService.getCargoItemsByCargoId(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cargos/2/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getCargoItemsReturnsNotFoundWhenCargoDoesNotExist() throws Exception {
        when(cargoItemService.getCargoItemsByCargoId(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found")
        );

        mockMvc.perform(get("/api/v1/cargos/99999/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("cargo not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getCargoItemReturnsItemBelongingToCargo() throws Exception {
        when(cargoItemService.getCargoItemByCargoIdAndId(2L, 10L))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/cargos/2/items/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.cargoId").value(2));

        verify(cargoItemService).getCargoItemByCargoIdAndId(2L, 10L);
    }

    @Test
    void getCargoItemReturnsNotFoundWhenItemDoesNotExist() throws Exception {
        when(cargoItemService.getCargoItemByCargoIdAndId(2L, 99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo item not found")
        );

        mockMvc.perform(get("/api/v1/cargos/2/items/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("cargo item not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getCargoItemHidesItemBelongingToAnotherCargo() throws Exception {
        when(cargoItemService.getCargoItemByCargoIdAndId(2L, 10L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo item not found")
        );

        mockMvc.perform(get("/api/v1/cargos/2/items/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("cargo item not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private String validRequestJson() {
        return """
                {"itemName":"显示器","quantity":10,"unit":"台",
                 "weight":35.50,"volume":0.80}
                """;
    }

    private CargoItemResponse response() {
        return new CargoItemResponse(
                10L,
                2L,
                "显示器",
                10,
                "台",
                new BigDecimal("35.50"),
                new BigDecimal("0.80")
        );
    }
}
