package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import com.smart_logistics.backend.dto.request.DispatchCommandStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DispatchCommandService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<TransportTaskStatus> DISPATCHABLE_TASK_STATUSES =
            Set.of(TransportTaskStatus.WAITING, TransportTaskStatus.TRANSPORTING);

    private final DispatchCommandMapper dispatchCommandMapper;
    private final TransportTaskMapper transportTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final DriverService driverService;
    private final UserDisplayNameService userDisplayNameService;
    private final CurrentUserService currentUserService;
    private final TransportTaskRouteService routeService;

    public DispatchCommandService(DispatchCommandMapper dispatchCommandMapper,
                                  TransportTaskMapper transportTaskMapper,
                                  VehicleMapper vehicleMapper,
                                  DriverService driverService,
                                  UserDisplayNameService userDisplayNameService,
                                  CurrentUserService currentUserService,
                                  TransportTaskRouteService routeService) {
        this.dispatchCommandMapper = dispatchCommandMapper;
        this.transportTaskMapper = transportTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.driverService = driverService;
        this.userDisplayNameService = userDisplayNameService;
        this.currentUserService = currentUserService;
        this.routeService = routeService;
    }

    @Transactional
    public DispatchCommandResponse createCommand(DispatchCommandCreateRequest request) {
        UserIdentityResponse creator = requireDispatcherOrAdmin();
        TransportTask task = lockTask(request.getTaskId());
        TransportTaskStatus taskStatus = parseTaskStatus(task.getStatus());
        if (!DISPATCHABLE_TASK_STATUSES.contains(taskStatus)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "dispatch command requires a waiting or transporting task");
        }

        Vehicle vehicle = vehicleMapper.selectOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getId, task.getVehicleId())
                .last("FOR UPDATE"));
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found");
        }
        if (vehicle.getDriverId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "task vehicle has no assigned driver");
        }
        driverService.requireActiveDriver(vehicle.getDriverId());

        TransportTaskRouteSnapshot targetRoute = validateTargetRoute(
                request.getCommandType(), request.getRouteId(), task.getId());
        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        DispatchCommand command = new DispatchCommand();
        command.setTaskId(task.getId());
        command.setTargetDriverId(vehicle.getDriverId());
        command.setVehicleId(vehicle.getId());
        command.setTargetRouteId(targetRoute == null ? null : targetRoute.routeId());
        command.setCommandType(request.getCommandType().name());
        command.setContent(request.getContent().trim());
        command.setStatus(DispatchCommandStatus.SENT.name());
        command.setCreatedBy(creator.getId());
        command.setSentAt(now);
        command.setCreatedAt(now);
        if (dispatchCommandMapper.insert(command) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to create dispatch command");
        }
        return toResponse(command, task, vehicle, targetRoute);
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchCommandResponse> listCommands(
            long page, long pageSize, String keyword, DispatchCommandStatus status,
            Long taskId, Long driverId, DispatchCommandType commandType) {
        requireDispatcherOrAdmin();
        return queryCommands(page, pageSize, keyword, status, taskId, driverId, commandType);
    }

    @Transactional(readOnly = true)
    public PageResult<DispatchCommandResponse> listMyCommands(
            long page, long pageSize, DispatchCommandStatus status) {
        Long driverId = requireCurrentDriverId();
        return queryCommands(page, pageSize, null, status, null, driverId, null);
    }

    @Transactional(readOnly = true)
    public DispatchCommandResponse getCommand(Long id) {
        DispatchCommand command = getRequiredCommand(id);
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER) {
            if (current.getDriverId() == null
                    || !Objects.equals(command.getTargetDriverId(), current.getDriverId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "dispatch command belongs to another driver");
            }
        } else if (current.getRole() != UserRole.DISPATCHER
                && current.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "current role cannot view dispatch commands");
        }
        return toResponse(command);
    }

    @Transactional
    public DispatchCommandResponse updateStatus(
            Long id, DispatchCommandStatusUpdateRequest request) {
        Long driverId = requireCurrentDriverId();
        DispatchCommand command = lockCommand(id);
        if (!Objects.equals(command.getTargetDriverId(), driverId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "dispatch command belongs to another driver");
        }

        DispatchCommandStatus current = parseStatus(command.getStatus());
        DispatchCommandStatus target = request.getStatus();
        requireValidTransition(current, target);
        if (parseCommandType(command.getCommandType()) == DispatchCommandType.ROUTE_CHANGE
                && target == DispatchCommandStatus.EXECUTING) {
            if (!StringUtils.hasText(command.getTargetRouteId())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT,
                        "route change command has no target route");
            }
            TransportTaskRouteSnapshot route = routeService
                    .getRouteByRouteId(command.getTargetRouteId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "target route not found"));
            requireRouteForTaskReady(route, command.getTaskId());
            routeService.activateReadyRoute(command.getTaskId(), command.getTargetRouteId());
        }

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        command.setStatus(target.name());
        command.setFeedback(trimToNull(request.getFeedback()));
        applyTransitionTime(command, target, now);
        if (dispatchCommandMapper.updateById(command) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "dispatch command status update conflict");
        }
        return toResponse(command);
    }

    private PageResult<DispatchCommandResponse> queryCommands(
            long page, long pageSize, String keyword, DispatchCommandStatus status,
            Long taskId, Long driverId, DispatchCommandType commandType) {
        LambdaQueryWrapper<DispatchCommand> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(DispatchCommand::getContent, keyword.trim());
        }
        if (status != null) query.eq(DispatchCommand::getStatus, status.name());
        if (taskId != null) query.eq(DispatchCommand::getTaskId, taskId);
        if (driverId != null) query.eq(DispatchCommand::getTargetDriverId, driverId);
        if (commandType != null) query.eq(DispatchCommand::getCommandType, commandType.name());
        query.orderByDesc(DispatchCommand::getCreatedAt).orderByDesc(DispatchCommand::getId);
        Page<DispatchCommand> entityPage = dispatchCommandMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<DispatchCommandResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse).toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    private TransportTaskRouteSnapshot validateTargetRoute(
            DispatchCommandType type, String routeId, Long taskId) {
        if (type == DispatchCommandType.TEXT) {
            if (StringUtils.hasText(routeId)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "TEXT command must not include routeId");
            }
            return null;
        }
        if (!StringUtils.hasText(routeId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "ROUTE_CHANGE command requires routeId");
        }
        TransportTaskRouteSnapshot route = routeService.getRouteByRouteId(routeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "target route not found"));
        requireRouteForTaskReady(route, taskId);
        return route;
    }

    private void requireRouteForTaskReady(TransportTaskRouteSnapshot route, Long taskId) {
        if (!Objects.equals(route.taskId(), taskId)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "target route belongs to another transport task");
        }
        if (route.status() != TransportTaskRouteStatus.READY) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "target route is no longer READY");
        }
    }

    private void requireValidTransition(DispatchCommandStatus current,
                                        DispatchCommandStatus target) {
        boolean valid = current == DispatchCommandStatus.SENT
                && (target == DispatchCommandStatus.ACKNOWLEDGED
                || target == DispatchCommandStatus.REJECTED)
                || current == DispatchCommandStatus.ACKNOWLEDGED
                && (target == DispatchCommandStatus.EXECUTING
                || target == DispatchCommandStatus.REJECTED)
                || current == DispatchCommandStatus.EXECUTING
                && target == DispatchCommandStatus.COMPLETED;
        if (!valid) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "illegal dispatch command status transition: "
                            + current + " -> " + target);
        }
    }

    private void applyTransitionTime(DispatchCommand command,
                                     DispatchCommandStatus target,
                                     LocalDateTime now) {
        if (target == DispatchCommandStatus.ACKNOWLEDGED) command.setAcknowledgedAt(now);
        else if (target == DispatchCommandStatus.EXECUTING) command.setExecutingAt(now);
        else if (target == DispatchCommandStatus.COMPLETED) command.setCompletedAt(now);
        else if (target == DispatchCommandStatus.REJECTED) command.setRejectedAt(now);
    }

    private UserIdentityResponse requireDispatcherOrAdmin() {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() != UserRole.DISPATCHER && current.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "current role cannot dispatch commands");
        }
        return current;
    }

    private Long requireCurrentDriverId() {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() != UserRole.DRIVER || current.getDriverId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "driver identity is missing");
        }
        return current.getDriverId();
    }

    private TransportTask lockTask(Long taskId) {
        TransportTask task = transportTaskMapper.selectOne(
                new LambdaQueryWrapper<TransportTask>()
                        .eq(TransportTask::getId, taskId).last("FOR UPDATE"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "transport task not found");
        }
        return task;
    }

    private DispatchCommand lockCommand(Long id) {
        DispatchCommand command = dispatchCommandMapper.selectOne(
                new LambdaQueryWrapper<DispatchCommand>()
                        .eq(DispatchCommand::getId, id).last("FOR UPDATE"));
        if (command == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "dispatch command not found");
        }
        return command;
    }

    private DispatchCommand getRequiredCommand(Long id) {
        DispatchCommand command = dispatchCommandMapper.selectById(id);
        if (command == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "dispatch command not found");
        }
        return command;
    }

    private DispatchCommandResponse toResponse(DispatchCommand command) {
        TransportTask task = transportTaskMapper.selectById(command.getTaskId());
        Vehicle vehicle = vehicleMapper.selectById(command.getVehicleId());
        if (task == null || vehicle == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "dispatch command references missing task or vehicle");
        }
        TransportTaskRouteSnapshot route = command.getTargetRouteId() == null ? null
                : routeService.getRouteByRouteId(command.getTargetRouteId()).orElse(null);
        return toResponse(command, task, vehicle, route);
    }

    private DispatchCommandResponse toResponse(DispatchCommand command,
                                               TransportTask task,
                                               Vehicle vehicle,
                                               TransportTaskRouteSnapshot route) {
        Map<Long, String> names = userDisplayNameService.getDriverNames(
                List.of(command.getTargetDriverId()));
        return new DispatchCommandResponse(
                command.getId(), command.getTaskId(), task.getTaskNo(),
                command.getTargetDriverId(), names.get(command.getTargetDriverId()),
                command.getVehicleId(), vehicle.getPlateNumber(),
                route == null ? null : route.routeId(),
                route == null ? null : route.routeVersion(),
                route == null ? null : route.status(),
                parseCommandType(command.getCommandType()), command.getContent(),
                parseStatus(command.getStatus()), command.getFeedback(),
                command.getCreatedBy(), toOffsetDateTime(command.getSentAt()),
                toOffsetDateTime(command.getCreatedAt()),
                toOffsetDateTime(command.getAcknowledgedAt()),
                toOffsetDateTime(command.getExecutingAt()),
                toOffsetDateTime(command.getCompletedAt()),
                toOffsetDateTime(command.getRejectedAt()));
    }

    private DispatchCommandStatus parseStatus(String status) {
        try {
            return DispatchCommandStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid dispatch command status in database");
        }
    }

    private DispatchCommandType parseCommandType(String commandType) {
        try {
            return DispatchCommandType.valueOf(commandType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid dispatch command type in database");
        }
    }

    private TransportTaskStatus parseTaskStatus(String status) {
        try {
            return TransportTaskStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid transport task status in database");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
