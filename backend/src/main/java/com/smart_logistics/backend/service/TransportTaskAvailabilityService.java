package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransportTaskAvailabilityService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            TransportTaskStatus.WAITING.name(),
            TransportTaskStatus.TRANSPORTING.name());

    private final TransportTaskMapper transportTaskMapper;

    public TransportTaskAvailabilityService(TransportTaskMapper transportTaskMapper) {
        this.transportTaskMapper = transportTaskMapper;
    }

    public void ensureCargoAvailable(Long cargoId) {
        if (hasActiveCargoTask(cargoId)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo already has an active transport task");
        }
    }

    public void ensureVehicleAvailable(Long vehicleId) {
        if (hasActiveVehicleTask(vehicleId)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "vehicle already has an active transport task");
        }
    }

    public boolean hasActiveCargoTask(Long cargoId) {
        return transportTaskMapper.selectCount(activeQuery()
                .eq(TransportTask::getCargoId, cargoId)) > 0;
    }

    public boolean hasActiveVehicleTask(Long vehicleId) {
        return transportTaskMapper.selectCount(activeQuery()
                .eq(TransportTask::getVehicleId, vehicleId)) > 0;
    }

    public Set<Long> findActiveCargoIds(Collection<Long> cargoIds) {
        List<Long> ids = distinctIds(cargoIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        return transportTaskMapper.selectList(activeQuery()
                        .in(TransportTask::getCargoId, ids)
                        .select(TransportTask::getCargoId)).stream()
                .map(TransportTask::getCargoId)
                .collect(Collectors.toSet());
    }

    public Set<Long> findActiveVehicleIds(Collection<Long> vehicleIds) {
        List<Long> ids = distinctIds(vehicleIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        return transportTaskMapper.selectList(activeQuery()
                        .in(TransportTask::getVehicleId, ids)
                        .select(TransportTask::getVehicleId)).stream()
                .map(TransportTask::getVehicleId)
                .collect(Collectors.toSet());
    }

    private LambdaQueryWrapper<TransportTask> activeQuery() {
        return new LambdaQueryWrapper<TransportTask>()
                .in(TransportTask::getStatus, ACTIVE_STATUSES);
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
