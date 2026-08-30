package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskUpdateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransportTaskService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final TransportTaskMapper transportTaskMapper;
    private final OwnerMapper ownerMapper;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final TransportTaskAvailabilityService availabilityService;
    private final BusinessDataScopeService dataScopeService;
    private final CurrentUserService currentUserService;
    private final TransportTaskStatusRecordService statusRecordService;
    private final EtaPlannedRouteService etaPlannedRouteService;
    private final TransportTaskRouteService taskRouteService;
    private final UserDisplayNameService userDisplayNameService;
    private final TransactionOperations transactionOperations;

    @Autowired
    public TransportTaskService(TransportTaskMapper transportTaskMapper,
                                OwnerMapper ownerMapper,
                                CargoService cargoService,
                                VehicleService vehicleService,
                                TransportTaskAvailabilityService availabilityService,
                                BusinessDataScopeService dataScopeService,
                                CurrentUserService currentUserService,
                                TransportTaskStatusRecordService statusRecordService,
                                EtaPlannedRouteService etaPlannedRouteService,
                                TransportTaskRouteService taskRouteService,
                                UserDisplayNameService userDisplayNameService,
                                PlatformTransactionManager transactionManager) {
        this(transportTaskMapper, ownerMapper, cargoService, vehicleService,
                availabilityService, dataScopeService, currentUserService,
                statusRecordService, etaPlannedRouteService, taskRouteService,
                userDisplayNameService,
                new TransactionTemplate(transactionManager));
    }

    TransportTaskService(TransportTaskMapper transportTaskMapper,
                         OwnerMapper ownerMapper,
                         CargoService cargoService,
                         VehicleService vehicleService,
                         TransportTaskAvailabilityService availabilityService,
                         BusinessDataScopeService dataScopeService,
                         CurrentUserService currentUserService,
                         TransportTaskStatusRecordService statusRecordService,
                         EtaPlannedRouteService etaPlannedRouteService,
                         TransportTaskRouteService taskRouteService,
                         UserDisplayNameService userDisplayNameService,
                         TransactionOperations transactionOperations) {
        this.transportTaskMapper = transportTaskMapper;
        this.ownerMapper = ownerMapper;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.availabilityService = availabilityService;
        this.dataScopeService = dataScopeService;
        this.currentUserService = currentUserService;
        this.statusRecordService = statusRecordService;
        this.etaPlannedRouteService = etaPlannedRouteService;
        this.taskRouteService = taskRouteService;
        this.userDisplayNameService = userDisplayNameService;
        this.transactionOperations = transactionOperations;
    }

    public TransportTaskResponse createTransportTask(TransportTaskCreateRequest request) {
        validatePlanTimes(request);
        validateCreateCoordinates(request);
        requireOwner(request.getOwnerId());
        Cargo cargo = cargoService.getCargoForTransport(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(request.getVehicleId());
        requireCargoStatus(cargo, CargoStatus.WAITING);
        requireCompatibleOwner(cargo, request.getOwnerId());
        requireVehicleStatus(vehicle, VehicleStatus.IDLE);
        vehicleService.requireTransportSimCode(vehicle);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());

        EtaPlannedRoute plannedRoute = etaPlannedRouteService.planRoute(
                request.getStartLongitude(), request.getStartLatitude(),
                request.getEndLongitude(), request.getEndLatitude());
        TransportTaskResponse response = transactionOperations.execute(status ->
                persistTransportTaskWithInitialRoute(request, plannedRoute));
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "transport task transaction returned no result");
        }
        return response;
    }

    private TransportTaskResponse persistTransportTaskWithInitialRoute(
            TransportTaskCreateRequest request, EtaPlannedRoute plannedRoute) {
        Cargo cargo = cargoService.getCargoForTransportForUpdate(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransportForUpdate(request.getVehicleId());
        requireCargoStatus(cargo, CargoStatus.WAITING);
        requireCompatibleOwner(cargo, request.getOwnerId());
        requireVehicleStatus(vehicle, VehicleStatus.IDLE);
        vehicleService.requireTransportSimCode(vehicle);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());

        return persistTransportTaskWithInitialRoute(new CreateValues(
                request.getOwnerId(), request.getCargoId(), request.getVehicleId(), null,
                request.getStartLocation().trim(), request.getStartLongitude(),
                request.getStartLatitude(), request.getEndLocation().trim(),
                request.getEndLongitude(), request.getEndLatitude(),
                request.getPlanStartTime(), request.getPlanEndTime()), cargo, plannedRoute);
    }

    TransportTaskResponse persistTransportTaskWithInitialRoute(
            CreateValues values, Cargo cargo, EtaPlannedRoute plannedRoute) {

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        String taskNo = generateTaskNo(now);
        ensureTaskNoAvailable(taskNo);
        cargoService.bindOwnerForTransport(cargo, values.ownerId());

        TransportTask task = new TransportTask();
        task.setTaskNo(taskNo);
        task.setCargoId(values.cargoId());
        task.setVehicleId(values.vehicleId());
        task.setOriginWarehouseId(values.originWarehouseId());
        task.setStartLocation(values.startLocation());
        task.setStartLongitude(values.startLongitude());
        task.setStartLatitude(values.startLatitude());
        task.setEndLocation(values.endLocation());
        task.setEndLongitude(values.endLongitude());
        task.setEndLatitude(values.endLatitude());
        task.setPlanStartTime(toDatabaseTime(values.planStartTime()));
        task.setPlanEndTime(toDatabaseTime(values.planEndTime()));
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
        taskRouteService.persistInitialActiveRoute(task.getId(), plannedRoute);
        return toResponse(getRequiredTransportTask(task.getId()));
    }

    public PageResult<TransportTaskResponse> listTransportTasks(long page, long pageSize,
                                                                 String keyword,
                                                                 TransportTaskStatus status) {
        return listTransportTasks(page, pageSize, keyword,
                status == null ? List.of() : List.of(status),
                null, null, null, null);
    }

    public PageResult<TransportTaskResponse> listTransportTasks(long page, long pageSize,
                                                                 String keyword,
                                                                 TransportTaskStatus status,
                                                                 Long driverId, Long ownerId,
                                                                 Long vehicleId, Long cargoId) {
        return listTransportTasks(page, pageSize, keyword,
                status == null ? List.of() : List.of(status),
                driverId, ownerId, vehicleId, cargoId);
    }

    public PageResult<TransportTaskResponse> listTransportTasks(long page, long pageSize,
                                                                 String keyword,
                                                                 List<TransportTaskStatus> statuses,
                                                                 Long driverId, Long ownerId,
                                                                 Long vehicleId, Long cargoId) {
        LambdaQueryWrapper<TransportTask> query = new LambdaQueryWrapper<>();
        dataScopeService.applyTaskScope(query, driverId, ownerId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(TransportTask::getTaskNo, normalizedKeyword)
                    .or()
                    .like(TransportTask::getStartLocation, normalizedKeyword)
                    .or()
                    .like(TransportTask::getEndLocation, normalizedKeyword));
        }
        if (statuses != null && !statuses.isEmpty()) {
            query.in(TransportTask::getStatus,
                    statuses.stream().map(Enum::name).toList());
        }
        if (driverId != null) {
            applyVehicleIds(query, dataScopeService.vehicleIdsForDriver(driverId));
        }
        if (ownerId != null) {
            applyCargoIds(query, dataScopeService.cargoIdsForOwner(ownerId));
        }
        if (vehicleId != null) {
            query.eq(TransportTask::getVehicleId, vehicleId);
        }
        if (cargoId != null) {
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

    public List<TransportTaskRouteResponse> listTransportTaskRoutes(Long id) {
        getRequiredTransportTask(id);
        return taskRouteService.findRoutesByTaskId(id).stream()
                .map(TransportTaskRouteResponse::from)
                .toList();
    }

    public TransportTaskRouteResponse createReadyRoute(Long id) {
        TransportTask task = getRequiredTransportTask(id);
        requireRouteMutationAllowed(parseStatus(task.getStatus()));
        validateTaskCoordinates(task);
        EtaPlannedRoute plannedRoute = etaPlannedRouteService.planRoute(
                task.getStartLongitude(), task.getStartLatitude(),
                task.getEndLongitude(), task.getEndLatitude());
        return TransportTaskRouteResponse.from(
                taskRouteService.persistReadyRoute(id, plannedRoute));
    }

    public TransportTaskRouteResponse activateReadyRoute(Long id, String routeId) {
        TransportTask task = getRequiredTransportTask(id);
        requireRouteMutationAllowed(parseStatus(task.getStatus()));
        return TransportTaskRouteResponse.from(
                taskRouteService.activateReadyRoute(id, routeId));
    }

    public TransportTaskResponse getCurrentTransportTask() {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        Long driverId = current.getDriverId();
        if (driverId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "driver identity is missing");
        }
        List<Long> vehicleIds = dataScopeService.vehicleIdsForDriver(driverId);
        if (vehicleIds.isEmpty()) {
            return null;
        }
        TransportTask task = findCurrentTask(vehicleIds, TransportTaskStatus.TRANSPORTING);
        if (task == null) {
            task = findCurrentTask(vehicleIds, TransportTaskStatus.WAITING);
        }
        return task == null ? null : toResponse(task);
    }

    @Transactional
    public TransportTaskResponse updateTransportTask(Long id,
                                                     TransportTaskUpdateRequest request) {
        validatePlanTimes(request.getPlanStartTime(), request.getPlanEndTime());
        validateCoordinatePair("start", request.getStartLongitude(), request.getStartLatitude());
        validateCoordinatePair("end", request.getEndLongitude(), request.getEndLatitude());
        TransportTask task = getRequiredTransportTask(id);
        if (parseStatus(task.getStatus()) != TransportTaskStatus.WAITING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "only waiting transport task can be modified");
        }
        String startLocation = request.getStartLocation().trim();
        requireWarehouseOriginSnapshotUnchanged(task, request, startLocation);
        String endLocation = request.getEndLocation().trim();
        boolean startLocationChanged = !Objects.equals(task.getStartLocation(), startLocation);
        boolean endLocationChanged = !Objects.equals(task.getEndLocation(), endLocation);
        task.setStartLocation(startLocation);
        task.setEndLocation(endLocation);
        applyCoordinateUpdate(task, request, startLocationChanged, endLocationChanged);
        task.setPlanStartTime(toDatabaseTime(request.getPlanStartTime()));
        task.setPlanEndTime(toDatabaseTime(request.getPlanEndTime()));
        task.setUpdatedAt(LocalDateTime.now(API_TIME_ZONE));
        if (transportTaskMapper.updateById(task) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to update transport task");
        }
        return toResponse(getRequiredTransportTask(id));
    }

    @Transactional
    public TransportTaskResponse updateTransportTaskStatusForDriver(
            Long id, TransportTaskStatusUpdateRequest request) {
        TransportTask task = getRequiredTransportTaskRaw(id);
        UserIdentityResponse currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() != UserRole.DRIVER
                || currentUser.getDriverId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "driver identity is missing");
        }
        Vehicle assignedVehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        if (!Objects.equals(assignedVehicle.getDriverId(), currentUser.getDriverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "task is not assigned to current driver");
        }
        TransportTaskStatus currentStatus = parseStatus(task.getStatus());
        TransportTaskStatus targetStatus = request.getStatus();
        boolean allowed = currentStatus == TransportTaskStatus.WAITING
                && targetStatus == TransportTaskStatus.TRANSPORTING
                || currentStatus == TransportTaskStatus.TRANSPORTING
                && targetStatus == TransportTaskStatus.COMPLETED;
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "driver is not allowed to report this task status transition");
        }
        return updateTransportTaskStatus(id, request);
    }

    @Transactional
    public TransportTaskResponse updateTransportTaskStatus(
            Long id, TransportTaskStatusUpdateRequest request) {
        TransportTask task = getRequiredTransportTaskRaw(id);
        TransportTaskStatus currentStatus = parseStatus(task.getStatus());
        TransportTaskStatus targetStatus = request.getStatus();
        validateTransition(currentStatus, targetStatus);

        Cargo cargo = cargoService.getCargoForTransport(task.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        validateAssociatedStatuses(currentStatus, cargo, vehicle);
        if (currentStatus == TransportTaskStatus.WAITING
                && targetStatus == TransportTaskStatus.TRANSPORTING) {
            vehicleService.requireTransportSimCode(vehicle);
            if (taskRouteService.getActiveRoute(id).isEmpty()) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT,
                        "task has no active planned route");
            }
        }

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        updateTaskStatus(task, currentStatus, targetStatus, now);
        applyAssociatedStatusChanges(task, currentStatus, targetStatus);
        statusRecordService.recordTransition(task, currentStatus, targetStatus, now);
        return toResponse(getRequiredTransportTaskRaw(id));
    }

    private void validatePlanTimes(TransportTaskCreateRequest request) {
        validatePlanTimes(request.getPlanStartTime(), request.getPlanEndTime());
    }

    Owner requireOwner(Long ownerId) {
        Owner owner = ownerMapper.selectById(ownerId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "owner not found");
        }
        return owner;
    }

    private void requireCompatibleOwner(Cargo cargo, Long ownerId) {
        if (cargo.getOwnerId() != null && !cargo.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo is already assigned to another owner");
        }
    }

    void validatePlanTimes(OffsetDateTime planStartTime, OffsetDateTime planEndTime) {
        if (planStartTime != null && planEndTime != null
                && planEndTime.isBefore(planStartTime)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "planEndTime must not be before planStartTime");
        }
    }

    private void validateCoordinatePair(String name, Double longitude, Double latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    name + "Longitude and " + name + "Latitude must be provided together");
        }
    }

    private void validateCreateCoordinates(TransportTaskCreateRequest request) {
        if (request.getStartLongitude() == null || request.getStartLatitude() == null
                || request.getEndLongitude() == null || request.getEndLatitude() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "start and end coordinates must be complete");
        }
        validateCoordinateRange("startLongitude", request.getStartLongitude(), -180, 180);
        validateCoordinateRange("startLatitude", request.getStartLatitude(), -90, 90);
        validateCoordinateRange("endLongitude", request.getEndLongitude(), -180, 180);
        validateCoordinateRange("endLatitude", request.getEndLatitude(), -90, 90);
    }

    private void validateTaskCoordinates(TransportTask task) {
        if (task.getStartLongitude() == null || task.getStartLatitude() == null
                || task.getEndLongitude() == null || task.getEndLatitude() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task route coordinates are incomplete");
        }
        validateCoordinateRange("startLongitude", task.getStartLongitude(), -180, 180);
        validateCoordinateRange("startLatitude", task.getStartLatitude(), -90, 90);
        validateCoordinateRange("endLongitude", task.getEndLongitude(), -180, 180);
        validateCoordinateRange("endLatitude", task.getEndLatitude(), -90, 90);
    }

    private void requireRouteMutationAllowed(TransportTaskStatus status) {
        if (status != TransportTaskStatus.WAITING
                && status != TransportTaskStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "route can only be changed for waiting or transporting task");
        }
    }

    void validateCoordinateRange(String name, double value,
                                 double minimum, double maximum) {
        validateCoordinateRange(name, value, minimum, maximum,
                ErrorCode.INVALID_PARAMETER);
    }

    static void validateCoordinateRange(String name, double value,
                                        double minimum, double maximum,
                                        ErrorCode errorCode) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new BusinessException(errorCode,
                    name + " is outside the valid range");
        }
    }

    private void applyCoordinateUpdate(TransportTask task,
                                       TransportTaskUpdateRequest request,
                                       boolean startLocationChanged,
                                       boolean endLocationChanged) {
        if (request.getStartLongitude() != null) {
            task.setStartLongitude(request.getStartLongitude());
            task.setStartLatitude(request.getStartLatitude());
        } else if (startLocationChanged) {
            task.setStartLongitude(null);
            task.setStartLatitude(null);
        }
        if (request.getEndLongitude() != null) {
            task.setEndLongitude(request.getEndLongitude());
            task.setEndLatitude(request.getEndLatitude());
        } else if (endLocationChanged) {
            task.setEndLongitude(null);
            task.setEndLatitude(null);
        }
    }

    private void requireWarehouseOriginSnapshotUnchanged(
            TransportTask task, TransportTaskUpdateRequest request, String startLocation) {
        if (task.getOriginWarehouseId() == null) {
            return;
        }
        boolean coordinatesChanged = request.getStartLongitude() != null
                && (!Objects.equals(task.getStartLongitude(), request.getStartLongitude())
                || !Objects.equals(task.getStartLatitude(), request.getStartLatitude()));
        if (!Objects.equals(task.getStartLocation(), startLocation) || coordinatesChanged) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse-origin task start snapshot cannot be modified");
        }
    }

    private TransportTask findCurrentTask(List<Long> vehicleIds,
                                          TransportTaskStatus status) {
        return transportTaskMapper.selectOne(new LambdaQueryWrapper<TransportTask>()
                .in(TransportTask::getVehicleId, vehicleIds)
                .eq(TransportTask::getStatus, status.name())
                .orderByAsc(TransportTask::getPlanStartTime)
                .orderByAsc(TransportTask::getId)
                .last("LIMIT 1"));
    }

    private void applyVehicleIds(LambdaQueryWrapper<TransportTask> query, List<Long> ids) {
        if (ids.isEmpty()) query.eq(TransportTask::getId, -1L);
        else query.in(TransportTask::getVehicleId, ids);
    }

    private void applyCargoIds(LambdaQueryWrapper<TransportTask> query, List<Long> ids) {
        if (ids.isEmpty()) query.eq(TransportTask::getId, -1L);
        else query.in(TransportTask::getCargoId, ids);
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
        TransportTask task = getRequiredTransportTaskRaw(id);
        dataScopeService.requireTaskAccess(task);
        return task;
    }

    private TransportTask getRequiredTransportTaskRaw(Long id) {
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
        Vehicle vehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        Map<Long, String> driverNames = vehicle.getDriverId() == null ? Map.of()
                : userDisplayNameService.getDriverNames(List.of(vehicle.getDriverId()));
        com.smart_logistics.backend.dto.TransportTaskRouteSnapshot activeRoute =
                taskRouteService.getActiveRoute(task.getId()).orElse(null);
        return new TransportTaskResponse(
                task.getId(), task.getTaskNo(), task.getCargoId(), task.getVehicleId(),
                task.getStartLocation(), task.getStartLongitude(), task.getStartLatitude(),
                task.getEndLocation(), task.getEndLongitude(), task.getEndLatitude(),
                toOffsetDateTime(task.getPlanStartTime()),
                toOffsetDateTime(task.getPlanEndTime()),
                toOffsetDateTime(task.getActualStartTime()),
                toOffsetDateTime(task.getActualEndTime()),
                parseStatus(task.getStatus()),
                toOffsetDateTime(task.getEstimatedArrivalTime()),
                toOffsetDateTime(task.getEtaCalculatedAt()),
                toOffsetDateTime(task.getCreatedAt()),
                toOffsetDateTime(task.getUpdatedAt()),
                vehicle.getDriverId(), driverNames.get(vehicle.getDriverId()),
                vehicle.getPlateNumber(),
                activeRoute == null ? null : activeRoute.routeId(),
                activeRoute == null ? null : activeRoute.routeVersion(),
                activeRoute == null ? null : activeRoute.status(),
                task.getOriginWarehouseId()
        );
    }

    record CreateValues(Long ownerId, Long cargoId, Long vehicleId,
                        Long originWarehouseId, String startLocation,
                        Double startLongitude, Double startLatitude,
                        String endLocation, Double endLongitude, Double endLatitude,
                        OffsetDateTime planStartTime, OffsetDateTime planEndTime) {
    }
}
