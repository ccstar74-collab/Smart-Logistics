package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.WarehouseTransportTaskCreateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.CargoType;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseTransportTaskCreateServiceTest {

    @Mock private TransportTaskService transportTaskService;
    @Mock private CargoTypeService cargoTypeService;
    @Mock private WarehouseService warehouseService;
    @Mock private CargoService cargoService;
    @Mock private VehicleService vehicleService;
    @Mock private TransportTaskAvailabilityService availabilityService;
    @Mock private EtaPlannedRouteService etaPlannedRouteService;
    @Mock private TransactionOperations transactionOperations;

    private WarehouseTransportTaskCreateService service;
    private Warehouse warehouse;
    private Cargo cargo;
    private Vehicle vehicle;
    private EtaPlannedRoute plannedRoute;
    private TransportTaskResponse expectedResponse;

    @BeforeEach
    void setUp() {
        warehouse = warehouse(WarehouseStatus.ACTIVE);
        cargo = cargo();
        vehicle = vehicle();
        plannedRoute = new EtaPlannedRoute(
                List.of(new EtaCoordinate(106.735012, 29.610634),
                        new EtaCoordinate(106.80, 29.70)),
                9_000, Duration.ofSeconds(1_000));
        expectedResponse = response();

        org.mockito.Mockito.lenient().when(cargoTypeService.requireCargoType(40L))
                .thenReturn(new CargoType());
        org.mockito.Mockito.lenient().when(warehouseService.requireActiveWarehouse(1L))
                .thenReturn(warehouse);
        org.mockito.Mockito.lenient().when(warehouseService.requireWarehouseForUpdate(1L))
                .thenReturn(warehouse);
        org.mockito.Mockito.lenient().when(cargoService.getCargoForTransport(10L))
                .thenReturn(cargo);
        org.mockito.Mockito.lenient().when(cargoService.getCargoForTransportForUpdate(10L))
                .thenReturn(cargo);
        org.mockito.Mockito.lenient().when(vehicleService.getVehicleForTransport(20L))
                .thenReturn(vehicle);
        org.mockito.Mockito.lenient().when(vehicleService.getVehicleForTransportForUpdate(20L))
                .thenReturn(vehicle);
        org.mockito.Mockito.lenient().when(vehicleService.requireTransportSimCode(any()))
                .thenReturn("sim_008");
        org.mockito.Mockito.lenient().when(etaPlannedRouteService.planRoute(
                106.735012, 29.610634, 106.80, 29.70)).thenReturn(plannedRoute);
        org.mockito.Mockito.lenient().when(transactionOperations.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
        org.mockito.Mockito.lenient().when(
                transportTaskService.persistTransportTaskWithInitialRoute(
                        any(TransportTaskService.CreateValues.class), any(Cargo.class),
                        any(EtaPlannedRoute.class))).thenReturn(expectedResponse);

        service = new WarehouseTransportTaskCreateService(
                transportTaskService, cargoTypeService, warehouseService, cargoService,
                vehicleService, availabilityService, etaPlannedRouteService,
                transactionOperations);
    }

    @Test
    void createsFromWarehouseSnapshotAndReplansFinalActiveRoute() {
        TransportTaskResponse response = service.createTransportTask(request());

        assertSame(expectedResponse, response);
        verify(etaPlannedRouteService).planRoute(
                106.735012, 29.610634, 106.80, 29.70);
        ArgumentCaptor<TransportTaskService.CreateValues> values =
                ArgumentCaptor.forClass(TransportTaskService.CreateValues.class);
        verify(transportTaskService).persistTransportTaskWithInitialRoute(
                values.capture(), eq(cargo), eq(plannedRoute));
        assertEquals(1L, values.getValue().originWarehouseId());
        assertEquals("Central Warehouse", values.getValue().startLocation());
        assertEquals(106.735012, values.getValue().startLongitude());
        assertEquals(29.610634, values.getValue().startLatitude());
        assertEquals(10L, values.getValue().cargoId());
        assertEquals(20L, values.getValue().vehicleId());
        verify(availabilityService, org.mockito.Mockito.times(2))
                .ensureCargoAvailable(10L);
        verify(availabilityService, org.mockito.Mockito.times(2))
                .ensureVehicleAvailable(20L);
        InOrder locks = inOrder(cargoService, vehicleService, warehouseService);
        locks.verify(cargoService).getCargoForTransportForUpdate(10L);
        locks.verify(vehicleService).getVehicleForTransportForUpdate(20L);
        locks.verify(warehouseService).requireWarehouseForUpdate(1L);
    }

    @Test
    void rejectsCargoTypeMismatchBeforePlanning() {
        cargo.setCargoTypeId(41L);

        assertConflict(ErrorCode.DATA_CONFLICT);

        verify(etaPlannedRouteService, never()).planRoute(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void rejectsMissingWarehouse() {
        when(warehouseService.requireActiveWarehouse(1L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "warehouse not found"));
        assertConflict(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsMissingCargo() {
        when(cargoService.getCargoForTransport(10L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found"));
        assertConflict(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsNonWaitingCargo() {
        cargo.setStatus(CargoStatus.TRANSPORTING.name());
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsCargoWithoutRequiredWarehouseOrType() {
        cargo.setCargoTypeId(null);
        assertConflict(ErrorCode.STATE_CONFLICT);

        cargo.setCargoTypeId(40L);
        cargo.setWarehouseId(null);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsCargoWarehouseMismatch() {
        cargo.setWarehouseId(2L);
        assertConflict(ErrorCode.DATA_CONFLICT);
    }

    @Test
    void rejectsCargoOwnedByAnotherOwner() {
        cargo.setOwnerId(31L);
        assertConflict(ErrorCode.DATA_CONFLICT);
    }

    @Test
    void rejectsVehicleWarehouseMismatch() {
        vehicle.setWarehouseId(2L);
        assertConflict(ErrorCode.DATA_CONFLICT);
    }

    @Test
    void rejectsMissingOrNonIdleVehicle() {
        when(vehicleService.getVehicleForTransport(20L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found"));
        assertConflict(ErrorCode.RESOURCE_NOT_FOUND);

        org.mockito.Mockito.doReturn(vehicle)
                .when(vehicleService).getVehicleForTransport(20L);
        vehicle.setStatus(VehicleStatus.TRANSPORTING.name());
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsVehicleWithoutWarehouse() {
        vehicle.setWarehouseId(null);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsVehicleWithoutDriver() {
        vehicle.setDriverId(null);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsVehicleWithoutValidSimCode() {
        doThrow(new BusinessException(ErrorCode.STATE_CONFLICT, "vehicle has no simCode"))
                .when(vehicleService).requireTransportSimCode(vehicle);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsInactiveWarehouse() {
        warehouse.setStatus(WarehouseStatus.INACTIVE.name());
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsWarehouseWithInvalidBusinessSnapshot() {
        warehouse.setAddress(" ");
        assertConflict(ErrorCode.STATE_CONFLICT);

        warehouse.setAddress("Central Warehouse");
        warehouse.setLongitude(181.0);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void rejectsWarehouseWithIncompleteCoordinates() {
        warehouse.setLongitude(null);
        assertConflict(ErrorCode.STATE_CONFLICT);

        warehouse.setLongitude(106.735012);
        warehouse.setLatitude(null);
        assertConflict(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void providerFailureLeavesWriteTransactionUntouched() {
        when(etaPlannedRouteService.planRoute(
                106.735012, 29.610634, 106.80, 29.70))
                .thenThrow(new BusinessException(
                        ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, "planned route unavailable"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(request()));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
        verify(transactionOperations, never()).execute(any());
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsCargoOccupiedAfterPlanningDuringLockedRecheck() {
        doNothing().doThrow(new BusinessException(
                        ErrorCode.DATA_CONFLICT, "cargo already has an active transport task"))
                .when(availabilityService).ensureCargoAvailable(10L);

        assertConflict(ErrorCode.DATA_CONFLICT);

        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsVehicleOccupiedAfterPlanningDuringLockedRecheck() {
        doNothing().doThrow(new BusinessException(
                        ErrorCode.DATA_CONFLICT, "vehicle already has an active transport task"))
                .when(availabilityService).ensureVehicleAvailable(20L);

        assertConflict(ErrorCode.DATA_CONFLICT);

        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsWarehouseSnapshotChangedAfterPlanning() {
        Warehouse changed = warehouse(WarehouseStatus.ACTIVE);
        changed.setLongitude(106.74);
        when(warehouseService.requireWarehouseForUpdate(1L)).thenReturn(changed);

        assertConflict(ErrorCode.STATE_CONFLICT);

        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void routePersistenceFailureEscapesTheSameWriteTransaction() {
        BusinessException failure = new BusinessException(
                ErrorCode.INTERNAL_ERROR, "failed to persist planned route");
        when(transportTaskService.persistTransportTaskWithInitialRoute(
                any(), any(), any())).thenThrow(failure);

        BusinessException actual = assertThrows(BusinessException.class,
                () -> service.createTransportTask(request()));

        assertSame(failure, actual);
        verify(transactionOperations).execute(any());
    }

    private void assertConflict(ErrorCode expected) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createTransportTask(request()));
        assertEquals(expected, exception.getErrorCode());
    }

    private WarehouseTransportTaskCreateRequest request() {
        WarehouseTransportTaskCreateRequest request =
                new WarehouseTransportTaskCreateRequest();
        request.setOwnerId(30L);
        request.setCargoTypeId(40L);
        request.setOriginWarehouseId(1L);
        request.setCargoId(10L);
        request.setVehicleId(20L);
        request.setEndLocation("Destination");
        request.setEndLongitude(106.80);
        request.setEndLatitude(29.70);
        request.setPlanStartTime(OffsetDateTime.parse("2026-08-31T08:00:00+08:00"));
        request.setPlanEndTime(OffsetDateTime.parse("2026-08-31T10:00:00+08:00"));
        return request;
    }

    private Warehouse warehouse(WarehouseStatus status) {
        Warehouse entity = new Warehouse();
        entity.setId(1L);
        entity.setAddress("Central Warehouse");
        entity.setLongitude(106.735012);
        entity.setLatitude(29.610634);
        entity.setStatus(status.name());
        return entity;
    }

    private Cargo cargo() {
        Cargo entity = new Cargo();
        entity.setId(10L);
        entity.setCargoTypeId(40L);
        entity.setWarehouseId(1L);
        entity.setStatus(CargoStatus.WAITING.name());
        return entity;
    }

    private Vehicle vehicle() {
        Vehicle entity = new Vehicle();
        entity.setId(20L);
        entity.setWarehouseId(1L);
        entity.setDriverId(9L);
        entity.setSimCode("sim_008");
        entity.setStatus(VehicleStatus.IDLE.name());
        return entity;
    }

    private TransportTaskResponse response() {
        return new TransportTaskResponse(
                100L, "T202608300001", 10L, 20L,
                "Central Warehouse", 106.735012, 29.610634,
                "Destination", 106.80, 29.70,
                null, null, null, null, TransportTaskStatus.WAITING,
                null, null, null, null, 9L, "Driver", "渝A10001",
                "route_v1", 1, null, 1L);
    }
}
