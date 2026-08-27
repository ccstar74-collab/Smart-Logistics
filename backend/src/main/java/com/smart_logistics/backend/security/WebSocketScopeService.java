package com.smart_logistics.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebSocket推送的车辆权限范围计算。
 * 规则：
 * - 管理员：全部已登记业务车辆
 * - 调度员：所有待运输、运输中任务关联车辆
 * - 司机：本人驾驶的车辆
 * - 货主：本人订单对应活动任务的车辆
 * - 其他角色：无可见车辆
 * 未绑定业务车辆的模拟设备不在任何角色的范围内。
 */
@Service
public class WebSocketScopeService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            TransportTaskStatus.WAITING.name(),
            TransportTaskStatus.TRANSPORTING.name());

    private final VehicleMapper vehicleMapper;
    private final TransportTaskMapper transportTaskMapper;
    private final CargoMapper cargoMapper;

    public WebSocketScopeService(VehicleMapper vehicleMapper,
                                 TransportTaskMapper transportTaskMapper,
                                 CargoMapper cargoMapper) {
        this.vehicleMapper = vehicleMapper;
        this.transportTaskMapper = transportTaskMapper;
        this.cargoMapper = cargoMapper;
    }

    public VehicleScope resolve(UserIdentityResponse identity) {
        return switch (identity.getRole()) {
            case ADMIN -> VehicleScope.all();
            case DISPATCHER -> fromVehicleIds(activeTaskVehicleIds());
            case DRIVER -> identity.getDriverId() == null
                    ? VehicleScope.denyAll()
                    : fromVehicles(vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                            .eq(Vehicle::getDriverId, identity.getDriverId())));
            case OWNER -> identity.getOwnerId() == null
                    ? VehicleScope.denyAll()
                    : fromVehicleIds(ownerActiveTaskVehicleIds(identity.getOwnerId()));
            default -> VehicleScope.denyAll();
        };
    }

    private List<Long> activeTaskVehicleIds() {
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getStatus, ACTIVE_STATUSES))
                .stream()
                .map(TransportTask::getVehicleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> ownerActiveTaskVehicleIds(Long ownerId) {
        List<Long> cargoIds = cargoMapper.selectList(new LambdaQueryWrapper<Cargo>()
                        .eq(Cargo::getOwnerId, ownerId))
                .stream()
                .map(Cargo::getId)
                .toList();
        if (cargoIds.isEmpty()) {
            return List.of();
        }
        return transportTaskMapper.selectList(new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getCargoId, cargoIds)
                        .in(TransportTask::getStatus, ACTIVE_STATUSES))
                .stream()
                .map(TransportTask::getVehicleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private VehicleScope fromVehicleIds(List<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return VehicleScope.denyAll();
        }
        return fromVehicles(vehicleMapper.selectBatchIds(vehicleIds));
    }

    private VehicleScope fromVehicles(List<Vehicle> vehicles) {
        Set<String> simCodes = vehicles.stream()
                .map(Vehicle::getSimCode)
                .filter(simCode -> simCode != null && !simCode.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return VehicleScope.of(simCodes);
    }

    public record VehicleScope(boolean allowAll, Set<String> allowedSimCodes) {

        public static VehicleScope all() {
            return new VehicleScope(true, Set.of());
        }

        public static VehicleScope of(Set<String> simCodes) {
            return new VehicleScope(false, simCodes);
        }

        public static VehicleScope denyAll() {
            return new VehicleScope(false, Set.of());
        }
    }
}
