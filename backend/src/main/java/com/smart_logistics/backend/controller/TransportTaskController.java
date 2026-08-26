package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskUpdateRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TrackPointResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.service.TransportTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/transport-tasks")
public class TransportTaskController {

    private final TransportTaskService transportTaskService;

    public TransportTaskController(TransportTaskService transportTaskService) {
        this.transportTaskService = transportTaskService;
    }

    /**
     * P0 GET /api/v1/transport-tasks
     * 按角色查询任务；司机和货主只能看到相关任务
     */
    @GetMapping
    public ApiResponse<PageResult<TransportTaskResponse>> listTransportTasks(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) TransportTaskStatus status,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long cargoId) {
        return ApiResponse.success(
                transportTaskService.listTransportTasks(page, pageSize, keyword, status, driverId, ownerId, vehicleId, cargoId));
    }

    /**
     * P0 POST /api/v1/transport-tasks
     * 绑定待运输货物和空闲车辆，生成运输任务
     */
    @PostMapping
    public ApiResponse<TransportTaskResponse> createTransportTask(
            @Valid @RequestBody TransportTaskCreateRequest request) {
        return ApiResponse.success(transportTaskService.createTransportTask(request));
    }

    /**
     * P0 GET /api/v1/transport-tasks/{id}
     * 查看任务、货物、车辆、司机、ETA、进度和实际时间
     */
    @GetMapping("/{id}")
    public ApiResponse<TransportTaskResponse> getTransportTask(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.getTransportTask(id));
    }

    /**
     * P0 GET /api/v1/transport-tasks/current
     * 返回当前用户正在执行或跟踪的任务，用于司机首页
     */
    @GetMapping("/current")
    public ApiResponse<List<TransportTaskResponse>> getCurrentTasks() {
        return ApiResponse.success(transportTaskService.getCurrentUserTasks());
    }

    /**
     * P0 PUT /api/v1/transport-tasks/{id}/status
     * 开始、完成、取消任务或上报异常，并完成三表状态联动
     */
    @PutMapping("/{id}/status")
    public ApiResponse<TransportTaskResponse> updateTransportTaskStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TransportTaskStatusUpdateRequest request) {
        return ApiResponse.success(
                transportTaskService.updateTransportTaskStatus(id, request));
    }

    /**
     * P1 GET /api/v1/transport-tasks/{id}/planned‑route
     * 返回司机地图需要的规划路线点
     */
    @GetMapping("/{id}/planned-route")
    public ApiResponse<PlannedRouteResponse> getPlannedRoute(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.getPlannedRoute(id));
    }

    /**
     * P1 GET /api/v1/transport-tasks/{id}/track‑points
     * 返回历史轨迹点，支持轨迹回放
     */
    @GetMapping("/{id}/track-points")
    public ApiResponse<List<TrackPointResponse>> getTrackPoints(
            @PathVariable @Positive Long id,
            @RequestParam OffsetDateTime startTime,
            @RequestParam OffsetDateTime endTime) {
        return ApiResponse.success(transportTaskService.getTrackPoints(id, startTime, endTime));
    }

    /**
     * P1 PUT /api/v1/transport-tasks/{id}
     * 在任务开始前修改起终点和计划时间
     */
    @PutMapping("/{id}")
    public ApiResponse<TransportTaskResponse> updateTransportTask(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TransportTaskUpdateRequest request) {
        return ApiResponse.success(transportTaskService.updateTransportTaskBasic(id, request));
    }
}