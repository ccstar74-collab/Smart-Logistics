package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.AlarmStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
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
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private BusinessDataScopeService dataScopeService;
    @Mock private TransportTaskMapper transportTaskMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private AlarmService alarmService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "alarm-test"),
                Alarm.class
        );
        alarmService = new AlarmService(alarmMapper, dataScopeService,
                transportTaskMapper, vehicleMapper, currentUserService, eventPublisher);
        org.mockito.Mockito.lenient().when(transportTaskMapper.selectById(15L))
                .thenReturn(task());
        org.mockito.Mockito.lenient().when(vehicleMapper.selectById(23L))
                .thenReturn(vehicle());
    }

    @Test
    void getAlarmReturnsExistingAlarmWithFrozenEnumsAndOffsetTime() {
        Alarm alarm = alarm(1L, AlarmStatus.UNHANDLED);
        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        AlarmResponse response = alarmService.getAlarm(1L);

        assertEquals(1L, response.getId());
        assertEquals(15L, response.getTaskId());
        assertEquals("T20260826001", response.getTaskNo());
        assertEquals(23L, response.getVehicleId());
        assertEquals("渝A33333", response.getPlateNumber());
        assertEquals(AlarmType.ROUTE_DEVIATION, response.getAlarmType());
        assertEquals(AlarmLevel.HIGH, response.getLevel());
        assertEquals(AlarmStatus.UNHANDLED, response.getStatus());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getAlarmThrowsNotFoundForMissingAlarm() {
        when(alarmMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> alarmService.getAlarm(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("alarm not found", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void alarmTaskVehicleAndOwnerFiltersComposeWithSecurityScope() {
        when(dataScopeService.taskIdsForOwner(3L)).thenReturn(List.of(30L));
        when(alarmMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        alarmService.listAlarms(1, 10, null, null, null, null,
                30L, 20L, 3L);

        verify(dataScopeService).applyAlarmScope(any(),
                org.mockito.ArgumentMatchers.eq(3L));
        ArgumentCaptor<LambdaQueryWrapper<Alarm>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("task_id"));
        assertTrue(captor.getValue().getSqlSegment().contains("vehicle_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(30L));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(20L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAlarmsReturnsPageAndAppliesAllFilters() {
        Alarm alarm = alarm(1L, AlarmStatus.UNHANDLED);
        when(alarmMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Alarm> page = invocation.getArgument(0);
                    page.setRecords(List.of(alarm));
                    page.setTotal(1);
                    return page;
                });

        PageResult<AlarmResponse> result = alarmService.listAlarms(
                2, 5, " deviation ", AlarmStatus.UNHANDLED,
                AlarmLevel.HIGH, AlarmType.ROUTE_DEVIATION
        );

        assertEquals(1, result.getRecords().size());
        assertEquals(23L, result.getRecords().getFirst().getVehicleId());
        assertEquals("渝A33333", result.getRecords().getFirst().getPlateNumber());
        assertEquals("T20260826001", result.getRecords().getFirst().getTaskNo());
        assertEquals(1, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getPageSize());

        ArgumentCaptor<LambdaQueryWrapper<Alarm>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        LambdaQueryWrapper<Alarm> query = wrapperCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("message"));
        assertTrue(query.getSqlSegment().contains("status"));
        assertTrue(query.getSqlSegment().contains("level"));
        assertTrue(query.getSqlSegment().contains("alarm_type"));
        assertTrue(query.getParamNameValuePairs().containsValue("%deviation%"));
        assertTrue(query.getParamNameValuePairs().containsValue(AlarmStatus.UNHANDLED.name()));
        assertTrue(query.getParamNameValuePairs().containsValue(AlarmLevel.HIGH.name()));
        assertTrue(query.getParamNameValuePairs().containsValue(AlarmType.ROUTE_DEVIATION.name()));
    }

    @Test
    void taskAlarmHistoryAppliesTaskScopeOrdersAndReusesAlarmResponse() {
        Alarm first = alarm(1L, AlarmStatus.PROCESSING);
        first.setDeviceCode("sim_019");
        first.setOccurredAt(LocalDateTime.of(2026, 8, 30, 10, 1));
        first.setLongitude(new BigDecimal("106.580123"));
        first.setLatitude(new BigDecimal("29.620456"));
        first.setCoordSystem("WGS84");
        Alarm second = alarm(2L, AlarmStatus.RESOLVED);
        second.setOccurredAt(LocalDateTime.of(2026, 8, 30, 10, 2));
        when(alarmMapper.selectList(any())).thenReturn(List.of(first, second));

        List<AlarmResponse> result = alarmService.listTaskAlarmHistory(15L);

        assertEquals(List.of(1L, 2L), result.stream().map(AlarmResponse::getId).toList());
        assertEquals("sim_019", result.getFirst().getDeviceCode());
        assertEquals(new BigDecimal("106.580123"), result.getFirst().getLongitude());
        assertEquals("WGS84", result.getFirst().getCoordSystem());
        verify(dataScopeService).requireTaskAccess(any(TransportTask.class));
        ArgumentCaptor<LambdaQueryWrapper<Alarm>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("task_id"));
        assertTrue(captor.getValue().getSqlSegment().contains("ORDER BY"));
    }

    @Test
    void dispatcherCanManuallyResolveWithRequiredAuditFields() {
        Alarm alarm = alarm(1L, AlarmStatus.UNHANDLED);
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DISPATCHER));
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(alarmMapper.updateById(any(Alarm.class))).thenReturn(1);

        AlarmResponse response = alarmService.updateStatus(1L, request(AlarmStatus.RESOLVED));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).updateById(captor.capture());
        assertEquals(AlarmStatus.RESOLVED.name(), captor.getValue().getStatus());
        assertEquals(7L, captor.getValue().getHandledBy());
        assertNotNull(captor.getValue().getHandledAt());
        assertEquals("False positive confirmed", captor.getValue().getResolutionRemark());
        assertEquals(AlarmStatus.RESOLVED, response.getStatus());
    }

    @Test
    void manualEndpointRejectsProcessingTransition() {
        Alarm alarm = alarm(1L, AlarmStatus.UNHANDLED);
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DISPATCHER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> alarmService.updateStatus(1L, request(AlarmStatus.PROCESSING)));
        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(alarmMapper, never()).updateById(any(Alarm.class));
    }

    @Test
    void ownerCannotManuallyResolveAlarm() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.OWNER));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> alarmService.updateStatus(1L, request(AlarmStatus.RESOLVED))
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(alarmMapper, never()).updateById(any(Alarm.class));
    }

    @Test
    void adminIsReadOnlyAndCannotManuallyResolveAlarm() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.ADMIN));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> alarmService.updateStatus(1L, request(AlarmStatus.RESOLVED))
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(alarmMapper, never()).updateById(any(Alarm.class));
    }

    @Test
    void updateStatusReportsDatabaseFailure() {
        Alarm alarm = alarm(1L, AlarmStatus.PROCESSING);
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DISPATCHER));
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(alarmMapper.updateById(any(Alarm.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> alarmService.updateStatus(1L, request(AlarmStatus.RESOLVED))
        );

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("alarm status update conflict", exception.getMessage());
    }

    @Test
    void getAlarmRejectsUnknownDatabaseEnum() {
        Alarm alarm = alarm(1L, AlarmStatus.UNHANDLED);
        alarm.setLevel("CRITICAL");
        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> alarmService.getAlarm(1L)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("invalid alarm level in database", exception.getMessage());
    }

    private Alarm alarm(Long id, AlarmStatus status) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setTaskId(15L);
        alarm.setVehicleId(23L);
        alarm.setAlarmType(AlarmType.ROUTE_DEVIATION.name());
        alarm.setLevel(AlarmLevel.HIGH.name());
        alarm.setMessage("Vehicle deviated from the planned route");
        alarm.setStatus(status.name());
        alarm.setConditionStatus(AlarmConditionStatus.ACTIVE.name());
        alarm.setCreatedAt(LocalDateTime.of(2026, 8, 23, 10, 30));
        return alarm;
    }

    private AlarmStatusUpdateRequest request(AlarmStatus status) {
        AlarmStatusUpdateRequest request = new AlarmStatusUpdateRequest();
        request.setStatus(status);
        request.setRemark("False positive confirmed");
        return request;
    }

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(15L);
        task.setTaskNo("T20260826001");
        task.setVehicleId(23L);
        return task;
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(23L);
        vehicle.setPlateNumber("渝A33333");
        return vehicle;
    }

    private UserIdentityResponse identity(UserRole role) {
        return new UserIdentityResponse(7L, "user", "User", null,
                role, UserStatus.ACTIVE, null,
                role == UserRole.OWNER ? 3L : null);
    }
}
