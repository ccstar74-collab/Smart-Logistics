package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskAvailabilityServiceTest {

    @Mock private TransportTaskMapper transportTaskMapper;

    private TransportTaskAvailabilityService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "availability-test"), TransportTask.class);
        service = new TransportTaskAvailabilityService(transportTaskMapper);
    }

    @Test
    void cargoConflictUsesOnlyWaitingAndTransportingAsActive() {
        when(transportTaskMapper.selectCount(any())).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.ensureCargoAvailable(10L));
        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("cargo already has an active transport task", exception.getMessage());
        assertOnlyActiveStatusesInCapturedQuery();
    }

    @Test
    void vehicleConflictUsesSameActiveStatusDefinition() {
        when(transportTaskMapper.selectCount(any())).thenReturn(1L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.ensureVehicleAvailable(20L));
        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertOnlyActiveStatusesInCapturedQuery();
    }

    @Test
    void completedAndCancelledHistoryDoNotAppearInActiveQuery() {
        when(transportTaskMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.hasActiveCargoTask(10L));
        ArgumentCaptor<LambdaQueryWrapper<TransportTask>> captor = wrapperCaptor();
        verify(transportTaskMapper).selectCount(captor.capture());
        var values = flattenedValues(captor.getValue());
        assertTrue(values.contains(TransportTaskStatus.WAITING.name()));
        assertTrue(values.contains(TransportTaskStatus.TRANSPORTING.name()));
        assertFalse(values.contains(TransportTaskStatus.COMPLETED.name()));
        assertFalse(values.contains(TransportTaskStatus.CANCELLED.name()));
    }

    @Test
    void batchLookupReturnsOccupiedIds() {
        TransportTask first = new TransportTask();
        first.setVehicleId(20L);
        TransportTask second = new TransportTask();
        second.setVehicleId(21L);
        when(transportTaskMapper.selectList(any())).thenReturn(List.of(first, second));
        assertEquals(Set.of(20L, 21L), service.findActiveVehicleIds(List.of(20L, 21L, 22L)));
    }

    private void assertOnlyActiveStatusesInCapturedQuery() {
        ArgumentCaptor<LambdaQueryWrapper<TransportTask>> captor = wrapperCaptor();
        verify(transportTaskMapper).selectCount(captor.capture());
        var values = flattenedValues(captor.getValue());
        assertTrue(values.contains(TransportTaskStatus.WAITING.name()));
        assertTrue(values.contains(TransportTaskStatus.TRANSPORTING.name()));
        assertFalse(values.contains(TransportTaskStatus.COMPLETED.name()));
        assertFalse(values.contains(TransportTaskStatus.CANCELLED.name()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<LambdaQueryWrapper<TransportTask>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private Set<Object> flattenedValues(LambdaQueryWrapper<TransportTask> wrapper) {
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values().stream()
                .flatMap(value -> value instanceof java.util.Collection<?> collection
                        ? collection.stream() : java.util.stream.Stream.of(value))
                .collect(java.util.stream.Collectors.toSet());
    }
}
