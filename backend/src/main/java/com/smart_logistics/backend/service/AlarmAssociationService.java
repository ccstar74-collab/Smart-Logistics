package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AlarmAssociationService {

    private final VehicleMapper vehicleMapper;
    private final TransportTaskMapper transportTaskMapper;

    public AlarmAssociationService(VehicleMapper vehicleMapper,
                                   TransportTaskMapper transportTaskMapper) {
        this.vehicleMapper = vehicleMapper;
        this.transportTaskMapper = transportTaskMapper;
    }

    public AlarmAssociation resolve(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            return new AlarmAssociation(null, null);
        }
        Vehicle vehicle = vehicleMapper.selectOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getSimCode, deviceCode.trim()));
        if (vehicle == null) {
            return new AlarmAssociation(null, null);
        }
        TransportTask task = transportTaskMapper.selectOne(
                new LambdaQueryWrapper<TransportTask>()
                        .eq(TransportTask::getVehicleId, vehicle.getId())
                        .eq(TransportTask::getStatus, TransportTaskStatus.TRANSPORTING.name())
                        .orderByDesc(TransportTask::getCreatedAt)
                        .orderByDesc(TransportTask::getId)
                        .last("LIMIT 1"));
        return new AlarmAssociation(vehicle.getId(), task == null ? null : task.getId());
    }

    public record AlarmAssociation(Long vehicleId, Long taskId) {
    }
}
