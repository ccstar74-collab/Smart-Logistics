package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.AlarmStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.service.AlarmService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<AlarmResponse>> listAlarms(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) AlarmStatus status,
            @RequestParam(required = false) AlarmLevel level,
            @RequestParam(required = false) AlarmType alarmType,
            @RequestParam(required = false) @Positive Long taskId,
            @RequestParam(required = false) @Positive Long vehicleId,
            @RequestParam(required = false) @Positive Long ownerId) {
        return ApiResponse.success(
                alarmService.listAlarms(page, pageSize, keyword, status, level, alarmType,
                        taskId, vehicleId, ownerId)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','DISPATCHER','ADMIN')")
    public ApiResponse<AlarmResponse> getAlarm(@PathVariable @Positive Long id) {
        return ApiResponse.success(alarmService.getAlarm(id));
    }

    // 告警处理权限明确限定为调度员；管理员只读，司机/货主只能查看与自己相关的告警
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ApiResponse<AlarmResponse> updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AlarmStatusUpdateRequest request) {
        return ApiResponse.success(alarmService.updateStatus(id, request));
    }
}
