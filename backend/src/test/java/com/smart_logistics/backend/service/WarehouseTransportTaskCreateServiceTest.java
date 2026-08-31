package com.smart_logistics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.InitialRouteLocationSnapshot;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.request.WarehouseTransportTaskCreateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.CargoType;
import com.smart_logistics.backend.entity.InitialRouteCandidate;
import com.smart_logistics.backend.entity.InitialRouteDecision;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.InitialRouteCandidateMapper;
import com.smart_logistics.backend.mapper.InitialRouteDecisionMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private InitialRouteDecisionMapper decisionMapper;
    @Mock private InitialRouteCandidateMapper candidateMapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private TransactionOperations transactionOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private WarehouseTransportTaskCreateService service;
    private InitialRouteDecision decision;
    private InitialRouteCandidate candidate;
    private Warehouse warehouse;
    private Cargo cargo;
    private Vehicle vehicle;
    private TransportTaskResponse expectedResponse;

    @BeforeEach
    void setUp() throws Exception {
        warehouse = warehouse();
        cargo = cargo();
        vehicle = vehicle();
        decision = decision();
        candidate = candidate();
        expectedResponse = response();

        org.mockito.Mockito.lenient().when(currentUserService.getCurrentUser())
                .thenReturn(new UserIdentityResponse(
                        99L, "warehouse_manager", "Warehouse Manager", null,
                        UserRole.WAREHOUSE_MANAGER, UserStatus.ACTIVE, null, null));
        org.mockito.Mockito.lenient().when(decisionMapper.selectOne(any()))
                .thenReturn(decision);
        org.mockito.Mockito.lenient().when(candidateMapper.selectOne(any()))
                .thenReturn(candidate);
        org.mockito.Mockito.lenient().when(decisionMapper.updateById(
                        any(InitialRouteDecision.class)))
                .thenReturn(1);
        org.mockito.Mockito.lenient().when(cargoTypeService.requireCargoType(40L))
                .thenReturn(new CargoType());
        org.mockito.Mockito.lenient().when(warehouseService.requireWarehouseForUpdate(1L))
                .thenReturn(warehouse);
        org.mockito.Mockito.lenient().when(cargoService.getCargoForTransportForUpdate(10L))
                .thenReturn(cargo);
        org.mockito.Mockito.lenient().when(vehicleService.getVehicleForTransportForUpdate(20L))
                .thenReturn(vehicle);
        org.mockito.Mockito.lenient().when(vehicleService.requireTransportSimCode(vehicle))
                .thenReturn("sim_008");
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
                vehicleService, availabilityService, decisionMapper, candidateMapper,
                currentUserService, objectMapper, transactionOperations);
    }

    @Test
    void confirmsSingleCandidateDecisionAndCreatesUniqueInitialRoute() {
        TransportTaskResponse actual = service.createTransportTask(request(), "confirm-key");

        assertSame(expectedResponse, actual);
        ArgumentCaptor<TransportTaskService.CreateValues> values =
                ArgumentCaptor.forClass(TransportTaskService.CreateValues.class);
        ArgumentCaptor<EtaPlannedRoute> route =
                ArgumentCaptor.forClass(EtaPlannedRoute.class);
        verify(transportTaskService).persistTransportTaskWithInitialRoute(
                values.capture(), eq(cargo), route.capture());
        assertEquals(1L, values.getValue().originWarehouseId());
        assertEquals("Central Warehouse", values.getValue().startLocation());
        assertEquals("Destination", values.getValue().endLocation());
        assertEquals(9_000L, route.getValue().distanceMeters());
        assertEquals(1_000L, route.getValue().referenceDuration().toSeconds());
        assertEquals(2, route.getValue().polyline().size());
        assertEquals("AMAP_DRIVING_V3", route.getValue().trafficSnapshot().source());

        assertEquals(InitialRouteDecisionStatus.CONFIRMED.name(), decision.getStatus());
        assertEquals("preview-route-1", decision.getSelectedRouteId());
        assertEquals("confirm-key", decision.getConfirmationIdempotencyKey());
        assertEquals(100L, decision.getTaskId());
        verify(decisionMapper).updateById(decision);

        InOrder locks = inOrder(cargoService, vehicleService, warehouseService);
        locks.verify(cargoService).getCargoForTransportForUpdate(10L);
        locks.verify(vehicleService).getVehicleForTransportForUpdate(20L);
        locks.verify(warehouseService).requireWarehouseForUpdate(1L);
    }

    @Test
    void sameConfirmationKeyAndRouteIsIdempotent() {
        decision.setStatus(InitialRouteDecisionStatus.CONFIRMED.name());
        decision.setSelectedRouteId("preview-route-1");
        decision.setConfirmationIdempotencyKey("confirm-key");
        decision.setTaskId(100L);
        when(transportTaskService.getTransportTask(100L)).thenReturn(expectedResponse);

        TransportTaskResponse actual = service.createTransportTask(request(), "confirm-key");

        assertSame(expectedResponse, actual);
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
        verify(candidateMapper, never()).selectOne(any());
    }

    @Test
    void confirmedDecisionRejectsDifferentKey() {
        decision.setStatus(InitialRouteDecisionStatus.CONFIRMED.name());
        decision.setSelectedRouteId("preview-route-1");
        decision.setConfirmationIdempotencyKey("old-key");
        decision.setTaskId(100L);

        assertBusinessError(ErrorCode.DATA_CONFLICT,
                () -> service.createTransportTask(request(), "new-key"));
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void expiredDecisionIsMarkedAndRejectedWithoutTaskCreation() {
        decision.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        assertBusinessError(ErrorCode.DECISION_EXPIRED,
                () -> service.createTransportTask(request(), "confirm-key"));

        assertEquals(InitialRouteDecisionStatus.EXPIRED.name(), decision.getStatus());
        verify(decisionMapper).updateById(decision);
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsDecisionOwnedByAnotherWarehouseManager() {
        decision.setCreatedBy(101L);

        assertBusinessError(ErrorCode.FORBIDDEN,
                () -> service.createTransportTask(request(), "confirm-key"));
        verify(candidateMapper, never()).selectOne(any());
    }

    @Test
    void rejectsSelectedRouteOutsideDecision() {
        when(candidateMapper.selectOne(any())).thenReturn(null);

        assertBusinessError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> service.createTransportTask(request(), "confirm-key"));
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsTaskInputThatDoesNotMatchDecisionSnapshot() {
        WarehouseTransportTaskCreateRequest request = request();
        request.setEndLongitude(106.81);

        assertBusinessError(ErrorCode.DATA_CONFLICT,
                () -> service.createTransportTask(request, "confirm-key"));
        verify(cargoService, never()).getCargoForTransportForUpdate(any());
    }

    @Test
    void rejectsWarehouseChangedAfterPreview() {
        warehouse.setLongitude(106.74);

        assertBusinessError(ErrorCode.STATE_CONFLICT,
                () -> service.createTransportTask(request(), "confirm-key"));
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    @Test
    void rejectsCargoThatBecameUnavailable() {
        org.mockito.Mockito.doThrow(new BusinessException(
                        ErrorCode.DATA_CONFLICT, "cargo already occupied"))
                .when(availabilityService).ensureCargoAvailable(10L);

        assertBusinessError(ErrorCode.DATA_CONFLICT,
                () -> service.createTransportTask(request(), "confirm-key"));
        verify(transportTaskService, never()).persistTransportTaskWithInitialRoute(
                any(), any(), any());
    }

    private void assertBusinessError(ErrorCode errorCode,
                                     org.junit.jupiter.api.function.Executable action) {
        BusinessException exception = assertThrows(BusinessException.class, action);
        assertEquals(errorCode, exception.getErrorCode());
    }

    private WarehouseTransportTaskCreateRequest request() {
        WarehouseTransportTaskCreateRequest request =
                new WarehouseTransportTaskCreateRequest();
        request.setRouteDecisionId("decision-1");
        request.setSelectedRouteId("preview-route-1");
        request.setRouteSelectionRemark("仓库管理员选择推荐路线");
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

    private InitialRouteDecision decision() throws Exception {
        InitialRouteDecision value = new InitialRouteDecision();
        value.setId(1L);
        value.setDecisionId("decision-1");
        value.setCreatedBy(99L);
        value.setOriginWarehouseId(1L);
        value.setStatus(InitialRouteDecisionStatus.PENDING.name());
        value.setStartSnapshot(objectMapper.writeValueAsString(
                new InitialRouteLocationSnapshot(
                        "Central Warehouse", 106.735012, 29.610634, "GCJ02")));
        value.setDestinationSnapshot(objectMapper.writeValueAsString(
                new InitialRouteLocationSnapshot(
                        "Destination", 106.80, 29.70, "GCJ02")));
        value.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return value;
    }

    private InitialRouteCandidate candidate() throws Exception {
        InitialRouteCandidate value = new InitialRouteCandidate();
        value.setDecisionId("decision-1");
        value.setPreviewRouteId("preview-route-1");
        value.setDistanceMeters(9_000L);
        value.setDurationSeconds(1_000L);
        value.setPoints(objectMapper.writeValueAsString(List.of(
                List.of(106.735012, 29.610634), List.of(106.80, 29.70))));
        value.setTrafficSnapshot(objectMapper.writeValueAsString(
                new TrafficSnapshot("AMAP_DRIVING_V3", "32", false,
                        8, 0, 8_000, 500, 300, 200)));
        return value;
    }

    private Warehouse warehouse() {
        Warehouse value = new Warehouse();
        value.setId(1L);
        value.setAddress("Central Warehouse");
        value.setLongitude(106.735012);
        value.setLatitude(29.610634);
        value.setStatus(WarehouseStatus.ACTIVE.name());
        return value;
    }

    private Cargo cargo() {
        Cargo value = new Cargo();
        value.setId(10L);
        value.setCargoTypeId(40L);
        value.setWarehouseId(1L);
        value.setStatus(CargoStatus.WAITING.name());
        return value;
    }

    private Vehicle vehicle() {
        Vehicle value = new Vehicle();
        value.setId(20L);
        value.setWarehouseId(1L);
        value.setDriverId(9L);
        value.setSimCode("sim_008");
        value.setStatus(VehicleStatus.IDLE.name());
        return value;
    }

    private TransportTaskResponse response() {
        return new TransportTaskResponse(
                100L, "T202608300001", 10L, 20L,
                "Central Warehouse", 106.735012, 29.610634,
                "Destination", 106.80, 29.70,
                null, null, null, null, TransportTaskStatus.WAITING,
                null, null, null, null, 9L, "Driver", "渝A10001",
                "route_v1", 1, TransportTaskRouteStatus.ACTIVE, 1L);
    }
}
