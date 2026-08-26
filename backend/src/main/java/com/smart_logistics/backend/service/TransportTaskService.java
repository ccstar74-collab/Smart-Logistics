package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskUpdateRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TrackPointResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TransportTaskService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            TransportTaskStatus.WAITING.name(),
            TransportTaskStatus.TRANSPORTING.name()
    );

    private final TransportTaskMapper transportTaskMapper;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final VehicleTraceService vehicleTraceService;

    public TransportTaskService(TransportTaskMapper transportTaskMapper,
                                CargoService cargoService,
                                VehicleService vehicleService,
                                VehicleTraceService vehicleTraceService) {
        this.transportTaskMapper = transportTaskMapper;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.vehicleTraceService = vehicleTraceService;
    }

    @Transactional
    public TransportTaskResponse createTransportTask(TransportTaskCreateRequest request) {
        validatePlanTimes(request);
        Cargo cargo = cargoService.getCargoForTransport(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(request.getVehicleId());
        requireCargoStatus(cargo, CargoStatus.WAITING);
        requireVehicleStatus(vehicle, VehicleStatus.IDLE);
        ensureCargoAvailable(request.getCargoId());
        ensureVehicleAvailable(request.getVehicleId());

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

    /**
     * P0 分页多条件查询任务
     */
    public PageResult<TransportTaskResponse> listTransportTasks(long page, long pageSize,
                                                                String keyword,
                                                                TransportTaskStatus status,
                                                                Long driverId,
                                                                Long ownerId,
                                                                Long vehicleId,
                                                                Long cargoId) {
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
        //if(driverId != null){
        //    query.eq(TransportTask::getDriverId, driverId);
   //     }
       // if(ownerId != null){
           // query.eq(TransportTask::getOwnerId, ownerId);
        //}
        if(vehicleId != null){
            query.eq(TransportTask::getVehicleId, vehicleId);
        }
        if(cargoId != null){
            query.eq(TransportTask::getCargoId, cargoId);
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

    /**
     * P0 获取当前用户正在执行/跟踪的任务（司机首页）
     * todo：对接SpringSecurity后，从上下文获取登录用户id、角色，取消注释下面的过滤条件
     */
    public List<TransportTaskResponse> getCurrentUserTasks() {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<>();
        query.in(TransportTask::getStatus, ACTIVE_STATUSES);
        // 示例逻辑，权限对接完成后打开：
        // LoginUser loginUser = SecurityUtil.getLoginUser();
        // if ("DRIVER".equals(loginUser.getRole())) {
        //     query.eq(TransportTask::getDriverId, loginUser.getUserId());
        // } else if ("OWNER".equals(loginUser.getRole())) {
        //     query.eq(TransportTask::getOwnerId, loginUser.getUserId());
        // }
        query.orderByDesc(TransportTask::getId);
        List<TransportTask> list = transportTaskMapper.selectList(query);
        return list.stream().map(this::toResponse).toList();
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

    /**
     * P1 任务未开始前修改任务起终点、计划时间，仅WAITING允许修改
     */
    @Transactional
    public TransportTaskResponse updateTransportTaskBasic(Long id, TransportTaskUpdateRequest request) {
        TransportTask task = getRequiredTransportTask(id);
        TransportTaskStatus currentStatus = parseStatus(task.getStatus());
        if(currentStatus != TransportTaskStatus.WAITING){
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "只有待派发状态的任务才允许修改");
        }
        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        LambdaUpdateWrapper<TransportTask> update = new LambdaUpdateWrapper<>();
        update.eq(TransportTask::getId, id);
        update.set(TransportTask::getStartLocation, request.getStartLocation().trim());
        update.set(TransportTask::getEndLocation, request.getEndLocation().trim());
        update.set(TransportTask::getPlanStartTime, toDatabaseTime(request.getPlanStartTime()));
        update.set(TransportTask::getPlanEndTime, toDatabaseTime(request.getPlanEndTime()));
        update.set(TransportTask::getUpdatedAt, now);
        transportTaskMapper.update(null, update);
        return toResponse(getRequiredTransportTask(id));
    }

    /**
     * P1 获取规划路线点，当前返回模拟数据
     */
    public PlannedRouteResponse getPlannedRoute(Long taskId) {
        getRequiredTransportTask(taskId);
        PlannedRouteResponse resp = new PlannedRouteResponse();
        resp.setPoints(List.of(
                new PlannedRouteResponse.RoutePoint(106.55,29.56,"起点"),
                new PlannedRouteResponse.RoutePoint(106.58,29.54,"途经点"),
                new PlannedRouteResponse.RoutePoint(106.61,29.52,"终点")
        ));
        return resp;
    }

    /**
     * P1 获取轨迹回放点，适配VehicleTraceService真实实现
     */
    public List<TrackPointResponse> getTrackPoints(Long taskId, OffsetDateTime startTime, OffsetDateTime endTime) {
        TransportTask task = getRequiredTransportTask(taskId);
        Vehicle vehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        if(vehicle == null){
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"车辆不存在");
        }
        // =====================重要=====================
        // 将 getVehicleCode() 修改为你Vehicle实体中对应sim编号的get方法，例如 getSimNo()
        String influxVehicleId = vehicle.getSimCode();

        long startTs = startTime.toInstant().toEpochMilli();
        long endTs = endTime.toInstant().toEpochMilli();

        List<VehicleTracePointDTO> originList = vehicleTraceService.getVehicleTrace(influxVehicleId, startTs, endTs);

        return originList.stream()
                .map(dto ->{
                    TrackPointResponse resp = new TrackPointResponse();
                    resp.setLat(dto.getLat());
                    resp.setLon(dto.getLon());
                    resp.setSpeed(dto.getSpeed());
                    resp.setHeading(dto.getHeading());
                    if(dto.getTimestamp() != null){
                        Instant instant = Instant.ofEpochMilli(dto.getTimestamp());
                        resp.setTimestamp(instant.atOffset(ZoneOffset.ofHours(8)));
                    }
                    return resp;
                }).toList();
    }

    private void validatePlanTimes(TransportTaskCreateRequest request) {
        if (request.getPlanStartTime() != null && request.getPlanEndTime() != null
                && request.getPlanEndTime().isBefore(request.getPlanStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "planEndTime must not be before planStartTime");
        }
    }

    private void ensureCargoAvailable(Long cargoId) {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<TransportTask>()
                .eq(TransportTask::getCargoId, cargoId)
                .in(TransportTask::getStatus, ACTIVE_STATUSES);
        if (transportTaskMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo already has an active transport task");
        }
    }

    private void ensureVehicleAvailable(Long vehicleId) {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<TransportTask>()
                .eq(TransportTask::getVehicleId, vehicleId)
                .in(TransportTask::getStatus, ACTIVE_STATUSES);
        if (transportTaskMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "vehicle already has an active transport task");
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