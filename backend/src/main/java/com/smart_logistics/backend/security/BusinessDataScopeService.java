package com.smart_logistics.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class BusinessDataScopeService {

    private static final long IMPOSSIBLE_ID = -1L;

    private final CurrentUserService currentUserService;
    private final CargoMapper cargoMapper;
    private final VehicleMapper vehicleMapper;
    private final TransportTaskMapper transportTaskMapper;

    public BusinessDataScopeService(CurrentUserService currentUserService,
                                    CargoMapper cargoMapper,
                                    VehicleMapper vehicleMapper,
                                    TransportTaskMapper transportTaskMapper) {
        this.currentUserService = currentUserService;
        this.cargoMapper = cargoMapper;
        this.vehicleMapper = vehicleMapper;
        this.transportTaskMapper = transportTaskMapper;
    }

    public void applyCargoScope(LambdaQueryWrapper<Cargo> query, Long requestedOwnerId) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.OWNER) {
            requireMatchingIdentity(requestedOwnerId, current.getOwnerId(), "ownerId");
            query.eq(Cargo::getOwnerId, requireIdentity(current.getOwnerId(), "owner"));
        } else if (current.getRole() == UserRole.DRIVER) {
            inCargoIds(query, cargoIdsForDriver(requireIdentity(current.getDriverId(), "driver")));
        }
    }

    public void requireCargoAccess(Cargo cargo) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.OWNER
                && !Objects.equals(cargo.getOwnerId(), current.getOwnerId())) {
            forbidden();
        }
        if (current.getRole() == UserRole.DRIVER
                && !cargoIdsForDriver(requireIdentity(current.getDriverId(), "driver"))
                .contains(cargo.getId())) {
            forbidden();
        }
    }

    public void applyVehicleScope(LambdaQueryWrapper<Vehicle> query, Long requestedDriverId) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER) {
            requireMatchingIdentity(requestedDriverId, current.getDriverId(), "driverId");
            query.eq(Vehicle::getDriverId, requireIdentity(current.getDriverId(), "driver"));
        } else if (current.getRole() == UserRole.OWNER) {
            inVehicleIds(query, vehicleIdsForOwner(requireIdentity(current.getOwnerId(), "owner")));
        }
    }

    public void requireVehicleAccess(Vehicle vehicle) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER
                && !Objects.equals(vehicle.getDriverId(), current.getDriverId())) {
            forbidden();
        }
        if (current.getRole() == UserRole.OWNER
                && !vehicleIdsForOwner(requireIdentity(current.getOwnerId(), "owner"))
                .contains(vehicle.getId())) {
            forbidden();
        }
    }

    public void applyTaskScope(LambdaQueryWrapper<TransportTask> query,
                               Long requestedDriverId, Long requestedOwnerId) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER) {
            Long driverId = requireIdentity(current.getDriverId(), "driver");
            requireMatchingIdentity(requestedDriverId, driverId, "driverId");
            inTaskIds(query, taskIdsForDriver(driverId));
        } else if (current.getRole() == UserRole.OWNER) {
            Long ownerId = requireIdentity(current.getOwnerId(), "owner");
            requireMatchingIdentity(requestedOwnerId, ownerId, "ownerId");
            inTaskIds(query, taskIdsForOwner(ownerId));
        }
    }

    public void requireTaskAccess(TransportTask task) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER
                && !taskIdsForDriver(requireIdentity(current.getDriverId(), "driver"))
                .contains(task.getId())) {
            forbidden();
        }
        if (current.getRole() == UserRole.OWNER
                && !taskIdsForOwner(requireIdentity(current.getOwnerId(), "owner"))
                .contains(task.getId())) {
            forbidden();
        }
    }

    public void applyAlarmScope(LambdaQueryWrapper<Alarm> query, Long requestedOwnerId) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() == UserRole.DRIVER) {
            inAlarmTaskIds(query, taskIdsForDriver(requireIdentity(current.getDriverId(), "driver")));
        } else if (current.getRole() == UserRole.OWNER) {
            Long ownerId = requireIdentity(current.getOwnerId(), "owner");
            requireMatchingIdentity(requestedOwnerId, ownerId, "ownerId");
            inAlarmTaskIds(query, taskIdsForOwner(ownerId));
        }
    }

    public void requireAlarmAccess(Alarm alarm) {
        TransportTask task = transportTaskMapper.selectById(alarm.getTaskId());
        if (task == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "alarm references missing transport task");
        }
        requireTaskAccess(task);
    }

    public List<Long> vehicleIdsForDriver(Long driverId) {
        return vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getDriverId, driverId))
                .stream().map(Vehicle::getId).toList();
    }

    public List<Long> cargoIdsForDriver(Long driverId) {
        List<Long> vehicleIds = vehicleIdsForDriver(driverId);
        if (vehicleIds.isEmpty()) {
            return List.of();
        }
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getVehicleId, vehicleIds))
                .stream().map(TransportTask::getCargoId).distinct().toList();
    }

    public List<Long> taskIdsForDriver(Long driverId) {
        List<Long> vehicleIds = vehicleIdsForDriver(driverId);
        if (vehicleIds.isEmpty()) {
            return List.of();
        }
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getVehicleId, vehicleIds))
                .stream().map(TransportTask::getId).toList();
    }

    public List<Long> taskIdsForOwner(Long ownerId) {
        List<Long> cargoIds = cargoIdsForOwner(ownerId);
        if (cargoIds.isEmpty()) {
            return List.of();
        }
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getCargoId, cargoIds))
                .stream().map(TransportTask::getId).toList();
    }

    public List<Long> cargoIdsForOwner(Long ownerId) {
        return cargoMapper.selectList(new LambdaQueryWrapper<Cargo>()
                        .eq(Cargo::getOwnerId, ownerId))
                .stream().map(Cargo::getId).toList();
    }

    public List<Long> taskIdsForVehicle(Long vehicleId) {
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .eq(TransportTask::getVehicleId, vehicleId))
                .stream().map(TransportTask::getId).toList();
    }

    public List<Long> vehicleIdsForOwner(Long ownerId) {
        List<Long> taskIds = taskIdsForOwner(ownerId);
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return transportTaskMapper.selectBatchIds(taskIds).stream()
                .map(TransportTask::getVehicleId).distinct().toList();
    }

    private void inCargoIds(LambdaQueryWrapper<Cargo> query, Collection<Long> ids) {
        if (ids.isEmpty()) query.eq(Cargo::getId, IMPOSSIBLE_ID);
        else query.in(Cargo::getId, ids);
    }

    private void inVehicleIds(LambdaQueryWrapper<Vehicle> query, Collection<Long> ids) {
        if (ids.isEmpty()) query.eq(Vehicle::getId, IMPOSSIBLE_ID);
        else query.in(Vehicle::getId, ids);
    }

    private void inTaskIds(LambdaQueryWrapper<TransportTask> query, Collection<Long> ids) {
        if (ids.isEmpty()) query.eq(TransportTask::getId, IMPOSSIBLE_ID);
        else query.in(TransportTask::getId, ids);
    }

    private void inAlarmTaskIds(LambdaQueryWrapper<Alarm> query, Collection<Long> ids) {
        if (ids.isEmpty()) query.eq(Alarm::getTaskId, IMPOSSIBLE_ID);
        else query.in(Alarm::getTaskId, ids);
    }

    private Long requireIdentity(Long id, String identityName) {
        if (id == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    identityName + " identity is missing");
        }
        return id;
    }

    private void requireMatchingIdentity(Long requested, Long current, String field) {
        if (requested != null && !Objects.equals(requested, current)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    field + " is outside current user data scope");
        }
    }

    private void forbidden() {
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "resource is outside current user data scope");
    }
}
