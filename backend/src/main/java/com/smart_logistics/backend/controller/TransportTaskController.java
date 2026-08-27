package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskUpdateRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/transport-tasks")
public class TransportTaskController {

    private final TransportTaskService transportTaskService;
    private final TaskTrackQueryService taskTrackQueryService;
    private final EtaPlannedRouteService etaPlannedRouteService;

    public TransportTaskController(TransportTaskService transportTaskService,
                                   TaskTrackQueryService taskTrackQueryService,
                                   EtaPlannedRouteService etaPlannedRouteService) {
        this.transportTaskService = transportTaskService;
        this.taskTrackQueryService = taskTrackQueryService;
        this.etaPlannedRouteService = etaPlannedRouteService;
    }

    @GetMapping("/{id}/track-points")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<VehicleLocationResponse>> getTrackPoints(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(taskTrackQueryService.getTrackPoints(id));
    }

    @GetMapping("/{id}/planned-route")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PlannedRouteResponse> getPlannedRoute(
            @PathVariable @Positive Long id) {
        TransportTaskResponse task = transportTaskService.getTransportTask(id);
        return ApiResponse.success(etaPlannedRouteService.getResponse(task));
    }

    @GetMapping("/{id}/routes")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<TransportTaskRouteResponse>> listRoutes(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.listTransportTaskRoutes(id));
    }

    @PostMapping("/{id}/routes")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ApiResponse<TransportTaskRouteResponse> createReadyRoute(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.createReadyRoute(id));
    }

    @PutMapping("/{id}/routes/{routeId}/activate")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ApiResponse<TransportTaskRouteResponse> activateReadyRoute(
            @PathVariable @Positive Long id,
            @PathVariable @NotBlank @Size(max = 64) String routeId) {
        return ApiResponse.success(transportTaskService.activateReadyRoute(id, routeId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<TransportTaskResponse>> listTransportTasks(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) List<TransportTaskStatus> status,
            @RequestParam(required = false) @Positive Long driverId,
            @RequestParam(required = false) @Positive Long ownerId,
            @RequestParam(required = false) @Positive Long vehicleId,
            @RequestParam(required = false) @Positive Long cargoId) {
        return ApiResponse.success(
                transportTaskService.listTransportTasks(page, pageSize, keyword, status,
                        driverId, ownerId, vehicleId, cargoId));
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<TransportTaskResponse> createTransportTask(
            @Valid @RequestBody TransportTaskCreateRequest request) {
        return ApiResponse.success(transportTaskService.createTransportTask(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<TransportTaskResponse> getTransportTask(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.getTransportTask(id));
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TransportTaskResponse> getCurrentTransportTask() {
        return ApiResponse.success(transportTaskService.getCurrentTransportTask());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<TransportTaskResponse> updateTransportTask(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TransportTaskUpdateRequest request) {
        return ApiResponse.success(transportTaskService.updateTransportTask(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TransportTaskResponse> updateTransportTaskStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TransportTaskStatusUpdateRequest request) {
        return ApiResponse.success(
                transportTaskService.updateTransportTaskStatusForDriver(id, request));
    }
}
