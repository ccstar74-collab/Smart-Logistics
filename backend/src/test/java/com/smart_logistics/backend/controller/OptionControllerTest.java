package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.response.DriverOptionResponse;
import com.smart_logistics.backend.dto.response.OwnerOptionResponse;
import com.smart_logistics.backend.service.DriverService;
import com.smart_logistics.backend.service.DispatchCommandService;
import com.smart_logistics.backend.service.OwnerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OptionControllerTest {

    @Mock private DriverService driverService;
    @Mock private DispatchCommandService dispatchCommandService;
    @Mock private OwnerService ownerService;

    @Test
    void driverOptionsEndpointReturnsStableDto() throws Exception {
        when(driverService.listOptions()).thenReturn(
                List.of(new DriverOptionResponse(3L, 8L, "Driver Name")));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DriverController(driverService, dispatchCommandService)).build();
        mockMvc.perform(get("/api/v1/drivers/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].driverId").value(3))
                .andExpect(jsonPath("$.data[0].userId").value(8))
                .andExpect(jsonPath("$.data[0].name").value("Driver Name"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    void ownerOptionsEndpointReturnsStableDto() throws Exception {
        when(ownerService.listOptions()).thenReturn(List.of(
                new OwnerOptionResponse(2L, 5L, "Owner Name", "Acme Logistics")));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OwnerController(ownerService)).build();
        mockMvc.perform(get("/api/v1/owners/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ownerId").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(5))
                .andExpect(jsonPath("$.data[0].name").value("Owner Name"))
                .andExpect(jsonPath("$.data[0].companyName").value("Acme Logistics"));
    }
}
