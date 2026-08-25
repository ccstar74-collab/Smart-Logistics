package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.TransportTaskStatusRecord;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.TransportTaskStatusRecordMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskStatusRecordServiceTest {
    @Mock private TransportTaskStatusRecordMapper recordMapper;
    @Mock private CargoMapper cargoMapper;
    @Mock private BusinessDataScopeService dataScopeService;
    @Mock private CurrentUserService currentUserService;
    private TransportTaskStatusRecordService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "status-record"), TransportTaskStatusRecord.class);
        service = new TransportTaskStatusRecordService(recordMapper, cargoMapper,
                dataScopeService, currentUserService);
    }

    @Test
    void recordsOperatorAndAuthoritativeTransition() {
        when(currentUserService.getCurrentUser()).thenReturn(new UserIdentityResponse(
                7L, "driver", "Driver", null, UserRole.DRIVER,
                UserStatus.ACTIVE, 3L, null));
        when(recordMapper.insert(any(TransportTaskStatusRecord.class))).thenReturn(1);
        TransportTask task = new TransportTask();
        task.setId(10L);
        task.setCargoId(20L);
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 25, 10, 0);

        service.recordTransition(task, TransportTaskStatus.WAITING,
                TransportTaskStatus.TRANSPORTING, changedAt);

        ArgumentCaptor<TransportTaskStatusRecord> captor =
                ArgumentCaptor.forClass(TransportTaskStatusRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getOperatorUserId());
        assertEquals("DRIVER", captor.getValue().getOperatorRole());
        assertEquals("TRANSPORTING", captor.getValue().getToStatus());
    }

    @Test
    void appliesCargoScopeAndOrdersRecordsDeterministically() {
        Cargo cargo = new Cargo();
        cargo.setId(20L);
        when(cargoMapper.selectById(20L)).thenReturn(cargo);
        when(recordMapper.selectList(any())).thenReturn(List.of(record()));

        var result = service.listCargoStatusRecords(20L);

        assertEquals(1, result.size());
        verify(dataScopeService).requireCargoAccess(cargo);
        ArgumentCaptor<LambdaQueryWrapper<TransportTaskStatusRecord>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("ORDER BY"));
    }

    private TransportTaskStatusRecord record() {
        TransportTaskStatusRecord record = new TransportTaskStatusRecord();
        record.setId(1L);
        record.setTaskId(10L);
        record.setCargoId(20L);
        record.setFromStatus("WAITING");
        record.setToStatus("TRANSPORTING");
        record.setOperatorUserId(7L);
        record.setOperatorRole("DRIVER");
        record.setChangedAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        return record;
    }
}
