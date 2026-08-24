package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskServiceTest {

    @Mock
    private TransportTaskMapper transportTaskMapper;
    @Mock
    private CargoService cargoService;
    @Mock
    private VehicleService vehicleService;

    private TransportTaskService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "transport-task-test"),
                TransportTask.class
        );
        service = new TransportTaskService(transportTaskMapper, cargoService, vehicleService);
    }

    @Test
    void createTransportTaskGeneratesTaskNumberAndDefaultsToWaiting() {
        TransportTaskCreateRequest request = createRequest();
        TransportTask[] holder = new TransportTask[1];
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.WAITING));
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle(VehicleStatus.IDLE));
        when(transportTaskMapper.selectCount(any())).thenReturn(0L);
        when(transportTaskMapper.insert(any(TransportTask.class))).thenAnswer(invocation -> {
            TransportTask inserted = invocation.getArgument(0);
            inserted.setId(1L);
            holder[0] = inserted;
            return 1;
        });
        when(transportTaskMapper.selectById(1L)).thenAnswer(invocation -> holder[0]);

        TransportTaskResponse response = service.createTransportTask(request);

        ArgumentCaptor<TransportTask> captor = ArgumentCaptor.forClass(TransportTask.class);
        verify(transportTaskMapper).insert(captor.capture());
        TransportTask inserted = captor.getValue();
        assertTrue(inserted.getTaskNo().matches("T\\d{17}[0-9A-F]{8}"));
        assertEquals(TransportTaskStatus.WAITING.name(), inserted.getStatus());
        assertEquals("Shanghai", inserted.getStartLocation());
        assertNull(inserted.getActualStartTime());
        assertNull(inserted.getEstimatedArrivalTime());
        assertEquals(TransportTaskStatus.WAITING, response.getStatus());
        assertEquals("+08:00", response.getPlanStartTime().getOffset().toString());
    }

    @Test
    void createTransportTaskRejectsMissingCargo() {
        when(cargoService.getCargoForTransport(10L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(vehicleService, never()).getVehicleForTransport(any());
    }

    @Test
    void createTransportTaskRejectsMissingVehicle() {
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.WAITING));
        when(vehicleService.getVehicleForTransport(20L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createTransportTaskRejectsCargoThatIsNotWaiting() {
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.COMPLETED));
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle(VehicleStatus.IDLE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(transportTaskMapper, never()).insert(any(TransportTask.class));
    }

    @Test
    void createTransportTaskRejectsVehicleThatIsNotIdle() {
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.WAITING));
        when(vehicleService.getVehicleForTransport(20L))
                .thenReturn(vehicle(VehicleStatus.TRANSPORTING));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(transportTaskMapper, never()).insert(any(TransportTask.class));
    }

    @Test
    void createRejectsCargoOccupiedByWaitingOrTransportingTask() {
        stubCreateAssociations();
        when(transportTaskMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("cargo already has an active transport task", exception.getMessage());
        ArgumentCaptor<LambdaQueryWrapper<TransportTask>> captor = wrapperCaptor();
        verify(transportTaskMapper).selectCount(captor.capture());
        captor.getValue().getSqlSegment();
        assertTrue(captor.getValue().getParamNameValuePairs()
                .containsValue(TransportTaskStatus.WAITING.name()));
        assertTrue(captor.getValue().getParamNameValuePairs()
                .containsValue(TransportTaskStatus.TRANSPORTING.name()));
    }

    @Test
    void createRejectsVehicleOccupiedByWaitingOrTransportingTask() {
        stubCreateAssociations();
        when(transportTaskMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("vehicle already has an active transport task", exception.getMessage());
    }

    @Test
    void cancelledTaskDoesNotBlockCreateBecauseOnlyActiveStatusesAreQueried() {
        assertInactiveTaskDoesNotBlock(TransportTaskStatus.CANCELLED);
    }

    @Test
    void completedTaskDoesNotBlockCreateBecauseOnlyActiveStatusesAreQueried() {
        assertInactiveTaskDoesNotBlock(TransportTaskStatus.COMPLETED);
    }

    @Test
    void createRejectsGeneratedTaskNumberAlreadyPresentAtPrecheck() {
        stubCreateAssociations();
        when(transportTaskMapper.selectCount(any())).thenReturn(0L, 0L, 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("transport task number already exists", exception.getMessage());
    }

    @Test
    void createConvertsDatabaseUniqueRaceToDataConflict() {
        stubCreateAssociations();
        when(transportTaskMapper.selectCount(any())).thenReturn(0L);
        when(transportTaskMapper.insert(any(TransportTask.class)))
                .thenThrow(new DuplicateKeyException("duplicate task number"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(createRequest()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("transport task number already exists", exception.getMessage());
    }

    @Test
    void createRejectsPlanEndBeforePlanStart() {
        TransportTaskCreateRequest request = createRequest();
        request.setPlanEndTime(request.getPlanStartTime().minusMinutes(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        verify(cargoService, never()).getCargoForTransport(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsPageAndAppliesKeywordAndStatusFilters() {
        TransportTask task = task(1L, TransportTaskStatus.WAITING);
        when(transportTaskMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<TransportTask> page = invocation.getArgument(0);
                    page.setRecords(List.of(task));
                    page.setTotal(11);
                    return page;
                });

        PageResult<TransportTaskResponse> result = service.listTransportTasks(
                2, 5, "Shanghai", TransportTaskStatus.WAITING);

        assertEquals(1, result.getRecords().size());
        assertEquals(11, result.getTotal());
        assertEquals(2, result.getPage());
        ArgumentCaptor<LambdaQueryWrapper<TransportTask>> captor = wrapperCaptor();
        verify(transportTaskMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("task_no"));
        assertTrue(sql.contains("start_location"));
        assertTrue(sql.contains("end_location"));
        assertTrue(sql.contains("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsEmptyPage() {
        when(transportTaskMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PageResult<TransportTaskResponse> result = service.listTransportTasks(
                1, 10, null, null);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
    }

    @Test
    void getTransportTaskMapsAllFieldsIncludingEta() {
        TransportTask task = task(1L, TransportTaskStatus.TRANSPORTING);
        task.setEstimatedArrivalTime(LocalDateTime.of(2026, 8, 24, 15, 0));
        when(transportTaskMapper.selectById(1L)).thenReturn(task);

        TransportTaskResponse response = service.getTransportTask(1L);

        assertEquals("T202608230001", response.getTaskNo());
        assertEquals(OffsetDateTime.parse("2026-08-24T15:00:00+08:00"),
                response.getEstimatedArrivalTime());
        assertEquals(TransportTaskStatus.TRANSPORTING, response.getStatus());
    }

    @Test
    void getTransportTaskRejectsMissingTask() {
        when(transportTaskMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getTransportTask(999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("transport task not found", exception.getMessage());
    }

    @Test
    void waitingToTransportingUpdatesTaskCargoAndVehicleAndActualStart() {
        stubTransition(TransportTaskStatus.WAITING, TransportTaskStatus.TRANSPORTING);

        TransportTaskResponse response = service.updateTransportTaskStatus(
                1L, statusRequest(TransportTaskStatus.TRANSPORTING));

        assertEquals(TransportTaskStatus.TRANSPORTING, response.getStatus());
        ArgumentCaptor<LambdaUpdateWrapper<TransportTask>> captor = updateWrapperCaptor();
        verify(transportTaskMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("actual_start_time"));
        verify(cargoService).updateStatusForTransport(
                10L, CargoStatus.WAITING, CargoStatus.TRANSPORTING);
        verify(vehicleService).updateStatusForTransport(
                20L, VehicleStatus.IDLE, VehicleStatus.TRANSPORTING);
    }

    @Test
    void transportingToCompletedUpdatesTaskCargoAndReleasesVehicle() {
        stubTransition(TransportTaskStatus.TRANSPORTING, TransportTaskStatus.COMPLETED);

        service.updateTransportTaskStatus(1L, statusRequest(TransportTaskStatus.COMPLETED));

        ArgumentCaptor<LambdaUpdateWrapper<TransportTask>> captor = updateWrapperCaptor();
        verify(transportTaskMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("actual_end_time"));
        verify(cargoService).updateStatusForTransport(
                10L, CargoStatus.TRANSPORTING, CargoStatus.COMPLETED);
        verify(vehicleService).updateStatusForTransport(
                20L, VehicleStatus.TRANSPORTING, VehicleStatus.IDLE);
    }

    @Test
    void transportingToAbnormalUpdatesCargoButKeepsVehicleUnchanged() {
        stubTransition(TransportTaskStatus.TRANSPORTING, TransportTaskStatus.ABNORMAL);

        service.updateTransportTaskStatus(1L, statusRequest(TransportTaskStatus.ABNORMAL));

        verify(cargoService).updateStatusForTransport(
                10L, CargoStatus.TRANSPORTING, CargoStatus.ABNORMAL);
        verify(vehicleService, never()).updateStatusForTransport(any(), any(), any());
    }

    @Test
    void waitingToCancelledKeepsCargoAndVehicleUnchanged() {
        stubTransition(TransportTaskStatus.WAITING, TransportTaskStatus.CANCELLED);

        service.updateTransportTaskStatus(1L, statusRequest(TransportTaskStatus.CANCELLED));

        verify(cargoService, never()).updateStatusForTransport(any(), any(), any());
        verify(vehicleService, never()).updateStatusForTransport(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void invalidTransitionIsRejectedWithoutUpdates(TransportTaskStatus current,
                                                    TransportTaskStatus target) {
        when(transportTaskMapper.selectById(1L)).thenReturn(task(1L, current));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateTransportTaskStatus(1L, statusRequest(target)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(transportTaskMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void statusUpdateRequiresExistingAssociatedVehicle() {
        TransportTask task = task(1L, TransportTaskStatus.WAITING);
        when(transportTaskMapper.selectById(1L)).thenReturn(task);
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.WAITING));
        when(vehicleService.getVehicleForTransport(20L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateTransportTaskStatus(
                        1L, statusRequest(TransportTaskStatus.TRANSPORTING)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(transportTaskMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void statusUpdateFailureAfterTaskWritePropagatesForTransactionRollback() {
        stubTransitionReads(TransportTaskStatus.WAITING);
        when(transportTaskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        BusinessException failure = new BusinessException(
                ErrorCode.STATE_CONFLICT, "cargo status conflict");
        org.mockito.Mockito.doThrow(failure).when(cargoService)
                .updateStatusForTransport(10L, CargoStatus.WAITING, CargoStatus.TRANSPORTING);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateTransportTaskStatus(
                        1L, statusRequest(TransportTaskStatus.TRANSPORTING)));

        assertEquals(failure, exception);
        verify(transportTaskMapper).update(isNull(), any(Wrapper.class));
        verify(vehicleService, never()).updateStatusForTransport(any(), any(), any());
    }

    @Test
    void createAndStatusUpdateMethodsDeclareTransactionalBoundary() throws Exception {
        Method create = TransportTaskService.class.getMethod(
                "createTransportTask", TransportTaskCreateRequest.class);
        Method update = TransportTaskService.class.getMethod(
                "updateTransportTaskStatus", Long.class,
                TransportTaskStatusUpdateRequest.class);

        assertNotNull(create.getAnnotation(Transactional.class));
        assertNotNull(update.getAnnotation(Transactional.class));
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(TransportTaskStatus.WAITING, TransportTaskStatus.COMPLETED),
                Arguments.of(TransportTaskStatus.COMPLETED, TransportTaskStatus.TRANSPORTING),
                Arguments.of(TransportTaskStatus.CANCELLED, TransportTaskStatus.TRANSPORTING),
                Arguments.of(TransportTaskStatus.ABNORMAL, TransportTaskStatus.COMPLETED)
        );
    }

    private void stubCreateAssociations() {
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(CargoStatus.WAITING));
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle(VehicleStatus.IDLE));
    }

    private void assertInactiveTaskDoesNotBlock(TransportTaskStatus inactiveStatus) {
        stubCreateAssociations();
        TransportTask[] holder = new TransportTask[1];
        when(transportTaskMapper.selectCount(any())).thenReturn(0L);
        when(transportTaskMapper.insert(any(TransportTask.class))).thenAnswer(invocation -> {
            holder[0] = invocation.getArgument(0);
            holder[0].setId(1L);
            return 1;
        });
        when(transportTaskMapper.selectById(1L)).thenAnswer(invocation -> holder[0]);

        TransportTaskResponse response = service.createTransportTask(createRequest());

        assertEquals(TransportTaskStatus.WAITING, response.getStatus());
        assertTrue(inactiveStatus != TransportTaskStatus.WAITING
                && inactiveStatus != TransportTaskStatus.TRANSPORTING);
    }

    private void stubTransition(TransportTaskStatus current, TransportTaskStatus target) {
        stubTransitionReads(current);
        TransportTask after = task(1L, target);
        if (target == TransportTaskStatus.TRANSPORTING) {
            after.setActualStartTime(LocalDateTime.now());
        } else if (target == TransportTaskStatus.COMPLETED) {
            after.setActualEndTime(LocalDateTime.now());
        }
        when(transportTaskMapper.selectById(1L))
                .thenReturn(task(1L, current), after);
        when(transportTaskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    private void stubTransitionReads(TransportTaskStatus current) {
        when(transportTaskMapper.selectById(1L)).thenReturn(task(1L, current));
        CargoStatus cargoStatus = current == TransportTaskStatus.WAITING
                ? CargoStatus.WAITING : CargoStatus.TRANSPORTING;
        VehicleStatus vehicleStatus = current == TransportTaskStatus.WAITING
                ? VehicleStatus.IDLE : VehicleStatus.TRANSPORTING;
        when(cargoService.getCargoForTransport(10L)).thenReturn(cargo(cargoStatus));
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle(vehicleStatus));
    }

    private TransportTaskCreateRequest createRequest() {
        TransportTaskCreateRequest request = new TransportTaskCreateRequest();
        request.setCargoId(10L);
        request.setVehicleId(20L);
        request.setStartLocation(" Shanghai ");
        request.setEndLocation(" Beijing ");
        request.setPlanStartTime(OffsetDateTime.parse("2026-08-24T10:00:00+08:00"));
        request.setPlanEndTime(OffsetDateTime.parse("2026-08-24T15:00:00+08:00"));
        return request;
    }

    private TransportTaskStatusUpdateRequest statusRequest(TransportTaskStatus status) {
        TransportTaskStatusUpdateRequest request = new TransportTaskStatusUpdateRequest();
        request.setStatus(status);
        return request;
    }

    private TransportTask task(Long id, TransportTaskStatus status) {
        TransportTask task = new TransportTask();
        task.setId(id);
        task.setTaskNo("T202608230001");
        task.setCargoId(10L);
        task.setVehicleId(20L);
        task.setStartLocation("Shanghai");
        task.setEndLocation("Beijing");
        task.setStatus(status.name());
        task.setPlanStartTime(LocalDateTime.of(2026, 8, 24, 10, 0));
        task.setPlanEndTime(LocalDateTime.of(2026, 8, 24, 15, 0));
        task.setCreatedAt(LocalDateTime.of(2026, 8, 23, 10, 0));
        task.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 10, 0));
        return task;
    }

    private Cargo cargo(CargoStatus status) {
        Cargo cargo = new Cargo();
        cargo.setId(10L);
        cargo.setStatus(status.name());
        return cargo;
    }

    private Vehicle vehicle(VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(20L);
        vehicle.setStatus(status.name());
        return vehicle;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<LambdaQueryWrapper<TransportTask>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<LambdaUpdateWrapper<TransportTask>> updateWrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
