package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.dto.request.InitialRouteDecisionCreateRequest;
import com.smart_logistics.backend.dto.response.InitialRouteDecisionResponse;
import com.smart_logistics.backend.service.InitialRouteDecisionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/initial-route-decisions")
public class InitialRouteDecisionController {

    private final InitialRouteDecisionService decisionService;

    public InitialRouteDecisionController(InitialRouteDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<InitialRouteDecisionResponse> createDecision(
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody InitialRouteDecisionCreateRequest request) {
        return ApiResponse.success(
                decisionService.createDecision(request, idempotencyKey));
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<InitialRouteDecisionResponse> getDecision(
            @PathVariable @NotBlank @Size(max = 64) String decisionId) {
        return ApiResponse.success(decisionService.getDecision(decisionId));
    }
}
