package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmAssociationServiceTest {

    @Mock private VehicleMapper vehicleMapper;
    @Mock private TransportTaskMapper taskMapper;

    private AlarmAssociationService service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "association-vehicle"), Vehicle.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "association-task"),
                TransportTask.class);
        service = new AlarmAssociationService(vehicleMapper, taskMapper);
    }

    @Test
    void resolvesSimCodeToVehicleAndTransportingTask() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(23L);
        TransportTask task = new TransportTask();
        task.setId(15L);
        task.setVehicleId(23L);
        task.setStatus(TransportTaskStatus.TRANSPORTING.name());
        when(vehicleMapper.selectOne(any(Wrapper.class))).thenReturn(vehicle);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        AlarmAssociationService.AlarmAssociation association = service.resolve("sim_019");

        assertEquals(23L, association.vehicleId());
        assertEquals(15L, association.taskId());
    }

    @Test
    void unknownDeviceRemainsADeviceLevelAlarm() {
        when(vehicleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        AlarmAssociationService.AlarmAssociation association = service.resolve("unknown_1");

        assertNull(association.vehicleId());
        assertNull(association.taskId());
        verify(taskMapper, never()).selectOne(any(Wrapper.class));
    }
}
