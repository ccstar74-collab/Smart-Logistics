package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.response.CargoStatusRecordResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.TransportTaskStatusRecord;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.TransportTaskStatusRecordMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TransportTaskStatusRecordService {
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TransportTaskStatusRecordMapper recordMapper;
    private final CargoMapper cargoMapper;
    private final BusinessDataScopeService dataScopeService;
    private final CurrentUserService currentUserService;

    public TransportTaskStatusRecordService(TransportTaskStatusRecordMapper recordMapper,
                                            CargoMapper cargoMapper,
                                            BusinessDataScopeService dataScopeService,
                                            CurrentUserService currentUserService) {
        this.recordMapper = recordMapper;
        this.cargoMapper = cargoMapper;
        this.dataScopeService = dataScopeService;
        this.currentUserService = currentUserService;
    }

    public void recordTransition(TransportTask task, TransportTaskStatus fromStatus,
                                 TransportTaskStatus toStatus, LocalDateTime changedAt) {
        UserIdentityResponse operator = currentUserService.getCurrentUser();
        TransportTaskStatusRecord record = new TransportTaskStatusRecord();
        record.setTaskId(task.getId());
        record.setCargoId(task.getCargoId());
        record.setFromStatus(fromStatus.name());
        record.setToStatus(toStatus.name());
        record.setOperatorUserId(operator.getId());
        record.setOperatorRole(operator.getRole().name());
        record.setChangedAt(changedAt);
        if (recordMapper.insert(record) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to record transport task status transition");
        }
    }

    public List<CargoStatusRecordResponse> listCargoStatusRecords(Long cargoId) {
        Cargo cargo = cargoMapper.selectById(cargoId);
        if (cargo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found");
        }
        dataScopeService.requireCargoAccess(cargo);
        return recordMapper.selectList(new LambdaQueryWrapper<TransportTaskStatusRecord>()
                        .eq(TransportTaskStatusRecord::getCargoId, cargoId)
                        .orderByAsc(TransportTaskStatusRecord::getChangedAt)
                        .orderByAsc(TransportTaskStatusRecord::getId)).stream()
                .map(this::toResponse).toList();
    }

    private CargoStatusRecordResponse toResponse(TransportTaskStatusRecord record) {
        try {
            return new CargoStatusRecordResponse(record.getId(), record.getTaskId(),
                    record.getCargoId(), TransportTaskStatus.valueOf(record.getFromStatus()),
                    TransportTaskStatus.valueOf(record.getToStatus()), record.getOperatorUserId(),
                    UserRole.valueOf(record.getOperatorRole()),
                    record.getChangedAt().atZone(API_TIME_ZONE).toOffsetDateTime());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid transport task status record in database");
        }
    }
}
