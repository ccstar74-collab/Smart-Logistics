package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TransportTaskService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final TransportTaskMapper transportTaskMapper;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final TransportTaskAvailabilityService availabilityService;

    public TransportTaskService(TransportTaskMapper transportTaskMapper,
                                CargoService cargoService,
                                VehicleService vehicleService,
                                TransportTaskAvailabilityService availabilityService) {
        this.transportTaskMapper = transportTaskMapper;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.availabilityService = availabilityService;
    }

    @Transactional
    public TransportTaskResponse createTransportTask(TransportTaskCreateRequest request) {
        validatePlanTimes(request);
        Cargo cargo = cargoService.getCargoForTransport(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(request.getVehicleId());
        requireCargoStatus(cargo, CargoStatus.WAITING);
        requireVehicleStatus(vehicle, VehicleStatus.IDLE);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        String taskNo = generateTaskNo(now);
        ensureTaskNoAvailable(taskNo);

        TransportTask task = new TransportTask();
        task.setTaskNo(taskNo);
        task.setCargoId(request.getCargoId());
        task.setVehicleId(request.getVehicleId());
        task.setStartLocation(request.getStartLocation().trim());
        task.setEndLocation(request.getEndLocation().trim());
        task.setPlanStartTime(toDatabaseTime(request.getPlanStartTime()));
        task.setPlanEndTime(toDatabaseTime(request.getPlanEndTime()));
        task.setStatus(TransportTaskStatus.WAITING.name());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        try {
            if (transportTaskMapper.insert(task) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "failed to create transport task");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateTaskNo(exception);
        }
        return toResponse(getRequiredTransportTask(task.getId()));
    }

    public PageResult<TransportTaskResponse> listTransportTasks(long page, long pageSize,
                                                                 String keyword,
                                                                 TransportTaskStatus status) {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(TransportTask::getTaskNo, normalizedKeyword)
                    .or()
                    .like(TransportTask::getStartLocation, normalizedKeyword)
                    .or()
                    .like(TransportTask::getEndLocation, normalizedKeyword));
        }
        if (status != null) {
            query.eq(TransportTask::getStatus, status.name());
        }
        query.orderByDesc(TransportTask::getId);

        Page<TransportTask> entityPage = transportTaskMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<TransportTaskResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public TransportTaskResponse getTransportTask(Long id) {
        return toResponse(getRequiredTransportTask(id));
    }

    @Transactional
    public TransportTaskResponse updateTransportTaskStatus(
            Long id, TransportTaskStatusUpdateRequest request) {
        TransportTask task = getRequiredTransportTask(id);
        TransportTaskStatus currentStatus = parseStatus(task.getStatus());
        TransportTaskStatus targetStatus = request.getStatus();
        validateTransition(currentStatus, targetStatus);

        Cargo cargo = cargoService.getCargoForTransport(task.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        validateAssociatedStatuses(currentStatus, cargo, vehicle);

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        updateTaskStatus(task, currentStatus, targetStatus, now);
        applyAssociatedStatusChanges(task, currentStatus, targetStatus);
        return toResponse(getRequiredTransportTask(id));
    }

    private void validatePlanTimes(TransportTaskCreateRequest request) {
        if (request.getPlanStartTime() != null && request.getPlanEndTime() != null
                && request.getPlanEndTime().isBefore(request.getPlanStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "planEndTime must not be before planStartTime");
        }
    }

    private void ensureTaskNoAvailable(String taskNo) {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<TransportTask>()
                .eq(TransportTask::getTaskNo, taskNo);
        if (transportTaskMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "transport task number already exists");
        }
    }

    private String generateTaskNo(LocalDateTime now) {
        String randomPart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "T" + TASK_NO_TIME_FORMAT.format(now) + randomPart;
    }

    private void validateTransition(TransportTaskStatus currentStatus,
                                    TransportTaskStatus targetStatus) {
        boolean valid = switch (currentStatus) {
            case WAITING -> targetStatus == TransportTaskStatus.TRANSPORTING
                    || targetStatus == TransportTaskStatus.CANCELLED;
            case TRANSPORTING -> targetStatus == TransportTaskStatus.COMPLETED
                    || targetStatus == TransportTaskStatus.ABNORMAL;
            case COMPLETED, ABNORMAL, CANCELLED -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "invalid transport task status transition");
        }
    }

    private void validateAssociatedStatuses(TransportTaskStatus currentStatus,
                                            Cargo cargo, Vehicle vehicle) {
        if (currentStatus == TransportTaskStatus.WAITING) {
            requireCargoStatus(cargo, CargoStatus.WAITING);
            requireVehicleStatus(vehicle, VehicleStatus.IDLE);
        } else if (currentStatus == TransportTaskStatus.TRANSPORTING) {
            requireCargoStatus(cargo, CargoStatus.TRANSPORTING);
            requireVehicleStatus(vehicle, VehicleStatus.TRANSPORTING);
        }
    }

    private void updateTaskStatus(TransportTask task, TransportTaskStatus currentStatus,
                                  TransportTaskStatus targetStatus, LocalDateTime now) {
        LambdaUpdateWrapper<TransportTask> update = new LambdaUpdateWrapper<TransportTask>()
                .eq(TransportTask::getId, task.getId())
                .eq(TransportTask::getStatus, currentStatus.name())
                .set(TransportTask::getStatus, targetStatus.name())
                .set(TransportTask::getUpdatedAt, now);
        if (targetStatus == TransportTaskStatus.TRANSPORTING) {
            update.set(TransportTask::getActualStartTime, now);
        } else if (targetStatus == TransportTaskStatus.COMPLETED) {
            update.set(TransportTask::getActualEndTime, now);
        }
        if (transportTaskMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task status conflict");
        }
    }

    private void applyAssociatedStatusChanges(TransportTask task,
                                              TransportTaskStatus currentStatus,
                                              TransportTaskStatus targetStatus) {
        if (currentStatus == TransportTaskStatus.WAITING
                && targetStatus == TransportTaskStatus.TRANSPORTING) {
            cargoService.updateStatusForTransport(
                    task.getCargoId(), CargoStatus.WAITING, CargoStatus.TRANSPORTING);
            vehicleService.updateStatusForTransport(
                    task.getVehicleId(), VehicleStatus.IDLE, VehicleStatus.TRANSPORTING);
        } else if (currentStatus == TransportTaskStatus.TRANSPORTING
                && targetStatus == TransportTaskStatus.COMPLETED) {
            cargoService.updateStatusForTransport(
                    task.getCargoId(), CargoStatus.TRANSPORTING, CargoStatus.COMPLETED);
            vehicleService.updateStatusForTransport(
                    task.getVehicleId(), VehicleStatus.TRANSPORTING, VehicleStatus.IDLE);
        } else if (currentStatus == TransportTaskStatus.TRANSPORTING
                && targetStatus == TransportTaskStatus.ABNORMAL) {
            cargoService.updateStatusForTransport(
                    task.getCargoId(), CargoStatus.TRANSPORTING, CargoStatus.ABNORMAL);
        }
    }

    private void requireCargoStatus(Cargo cargo, CargoStatus expectedStatus) {
        if (!expectedStatus.name().equals(cargo.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "cargo status does not allow this operation");
        }
    }

    private void requireVehicleStatus(Vehicle vehicle, VehicleStatus expectedStatus) {
        if (!expectedStatus.name().equals(vehicle.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "vehicle status does not allow this operation");
        }
    }

    private TransportTask getRequiredTransportTask(Long id) {
        TransportTask task = transportTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "transport task not found");
        }
        return task;
    }

    private BusinessException duplicateTaskNo(DuplicateKeyException cause) {
        BusinessException exception = new BusinessException(
                ErrorCode.DATA_CONFLICT, "transport task number already exists");
        exception.initCause(cause);
        return exception;
    }

    private TransportTaskStatus parseStatus(String status) {
        try {
            return TransportTaskStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid transport task status in database");
        }
    }

    private LocalDateTime toDatabaseTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(API_TIME_ZONE).toLocalDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }

    private TransportTaskResponse toResponse(TransportTask task) {
        return new TransportTaskResponse(
                task.getId(), task.getTaskNo(), task.getCargoId(), task.getVehicleId(),
                task.getStartLocation(), task.getEndLocation(),
                toOffsetDateTime(task.getPlanStartTime()),
                toOffsetDateTime(task.getPlanEndTime()),
                toOffsetDateTime(task.getActualStartTime()),
                toOffsetDateTime(task.getActualEndTime()),
                parseStatus(task.getStatus()),
                toOffsetDateTime(task.getEstimatedArrivalTime()),
                toOffsetDateTime(task.getCreatedAt()),
                toOffsetDateTime(task.getUpdatedAt())
        );
    }
}
