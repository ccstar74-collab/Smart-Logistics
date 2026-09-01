package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.OriginRecommendationRequest;
import com.smart_logistics.backend.dto.response.OriginRecommendationResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OriginRecommendationServiceTest {

    @Mock private OwnerMapper ownerMapper;
    @Mock private CargoService cargoService;
    @Mock private VehicleService vehicleService;
    @Mock private WarehouseService warehouseService;
    @Mock private EtaPlannedRouteService etaPlannedRouteService;

    private OriginRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new OriginRecommendationService(ownerMapper, cargoService,
                vehicleService, warehouseService, etaPlannedRouteService);
    }

    @Test
    void usesFrozenAvailableQueriesCountsEligibleResourcesAndPlansOnlyIntersection() {
        OriginRecommendationRequest request = request();
        when(ownerMapper.selectById(30L)).thenReturn(owner());
        when(cargoService.findAvailableCargos(10L, null, 30L)).thenReturn(List.of(
                cargo(1L, 1L), cargo(2L, 1L), cargo(3L, 1L), cargo(4L, 2L)));
        when(vehicleService.findAvailableVehicles(null)).thenReturn(List.of(
                vehicle(1L, 1L), vehicle(2L, 1L), vehicle(3L, 3L)));
        Warehouse warehouse = warehouse(1L, 106.71, 29.61);
        when(warehouseService.listActiveWarehousesByIds(anyCollection()))
                .thenReturn(List.of(warehouse));
        when(etaPlannedRouteService.planRoute(
                106.71, 29.61, 106.80, 29.70))
                .thenReturn(route(9_000, 1_000));

        List<OriginRecommendationResponse> result = service.recommend(request);

        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().getAvailableCargoCount());
        assertEquals(2, result.getFirst().getAvailableVehicleCount());
        assertTrue(result.getFirst().isRecommended());
        verify(cargoService).findAvailableCargos(10L, null, 30L);
        verify(vehicleService).findAvailableVehicles(null);
        verify(etaPlannedRouteService).planRoute(106.71, 29.61, 106.80, 29.70);
    }

    @Test
    void cargoOnlyVehicleOnlyAndInactiveWarehousesNeverReachPlanner() {
        when(ownerMapper.selectById(30L)).thenReturn(owner());
        when(cargoService.findAvailableCargos(10L, null, 30L)).thenReturn(List.of(
                cargo(1L, 1L), cargo(2L, 2L)));
        when(vehicleService.findAvailableVehicles(null)).thenReturn(List.of(
                vehicle(1L, 2L), vehicle(2L, 3L)));
        when(warehouseService.listActiveWarehousesByIds(anyCollection()))
                .thenReturn(List.of());

        List<OriginRecommendationResponse> result = service.recommend(request());

        assertTrue(result.isEmpty());
        verify(warehouseService).listActiveWarehousesByIds(
                org.mockito.ArgumentMatchers.argThat(ids -> ids.equals(java.util.Set.of(2L))));
        verifyNoInteractions(etaPlannedRouteService);
    }

    @Test
    void noResourceIntersectionReturnsEmptyWithoutWarehouseOrProviderCalls() {
        when(ownerMapper.selectById(30L)).thenReturn(owner());
        when(cargoService.findAvailableCargos(10L, null, 30L))
                .thenReturn(List.of(cargo(1L, 1L)));
        when(vehicleService.findAvailableVehicles(null))
                .thenReturn(List.of(vehicle(1L, 2L)));

        List<OriginRecommendationResponse> result = service.recommend(request());

        assertTrue(result.isEmpty());
        verifyNoInteractions(warehouseService, etaPlannedRouteService);
    }

    @Test
    void sortsByDurationThenDistanceThenWarehouseIdAndMarksOnlyFirst() {
        when(ownerMapper.selectById(30L)).thenReturn(owner());
        when(cargoService.findAvailableCargos(10L, null, 30L)).thenReturn(List.of(
                cargo(1L, 1L), cargo(2L, 2L), cargo(3L, 3L), cargo(4L, 4L)));
        when(vehicleService.findAvailableVehicles(null)).thenReturn(List.of(
                vehicle(1L, 1L), vehicle(2L, 2L),
                vehicle(3L, 3L), vehicle(4L, 4L)));
        when(warehouseService.listActiveWarehousesByIds(anyCollection())).thenReturn(List.of(
                warehouse(4L, 104.0, 24.0), warehouse(3L, 103.0, 23.0),
                warehouse(2L, 102.0, 22.0), warehouse(1L, 101.0, 21.0)));
        when(etaPlannedRouteService.planRoute(101.0, 21.0, 106.80, 29.70))
                .thenReturn(route(9_000, 1_000));
        when(etaPlannedRouteService.planRoute(102.0, 22.0, 106.80, 29.70))
                .thenReturn(route(20_000, 900));
        when(etaPlannedRouteService.planRoute(103.0, 23.0, 106.80, 29.70))
                .thenReturn(route(8_000, 1_000));
        when(etaPlannedRouteService.planRoute(104.0, 24.0, 106.80, 29.70))
                .thenReturn(route(8_000, 1_000));

        List<OriginRecommendationResponse> result = service.recommend(request());

        assertEquals(List.of(2L, 3L, 4L, 1L),
                result.stream().map(OriginRecommendationResponse::getWarehouseId).toList());
        assertTrue(result.getFirst().isRecommended());
        assertTrue(result.stream().skip(1)
                .noneMatch(OriginRecommendationResponse::isRecommended));
    }

    @Test
    void providerFailureFailsWholeRecommendation() {
        when(ownerMapper.selectById(30L)).thenReturn(owner());
        when(cargoService.findAvailableCargos(10L, null, 30L))
                .thenReturn(List.of(cargo(1L, 1L)));
        when(vehicleService.findAvailableVehicles(null))
                .thenReturn(List.of(vehicle(1L, 1L)));
        when(warehouseService.listActiveWarehousesByIds(anyCollection()))
                .thenReturn(List.of(warehouse(1L, 106.71, 29.61)));
        when(etaPlannedRouteService.planRoute(106.71, 29.61, 106.80, 29.70))
                .thenThrow(new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                        "planned route is unavailable"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.recommend(request()));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void missingOwnerStopsBeforeResourceQueries() {
        when(ownerMapper.selectById(30L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.recommend(request()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cargoService, vehicleService, warehouseService,
                etaPlannedRouteService);
    }

    private OriginRecommendationRequest request() {
        OriginRecommendationRequest request = new OriginRecommendationRequest();
        request.setOwnerId(30L);
        request.setCargoTypeId(10L);
        request.setEndLocation("Chongqing");
        request.setEndLongitude(106.80);
        request.setEndLatitude(29.70);
        return request;
    }

    private Owner owner() {
        Owner owner = new Owner();
        owner.setId(30L);
        return owner;
    }

    private Cargo cargo(Long id, Long warehouseId) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setWarehouseId(warehouseId);
        return cargo;
    }

    private Vehicle vehicle(Long id, Long warehouseId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setWarehouseId(warehouseId);
        return vehicle;
    }

    private Warehouse warehouse(Long id, double longitude, double latitude) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setWarehouseNo("WH-" + id);
        warehouse.setName("Warehouse " + id);
        warehouse.setAddress("Address " + id);
        warehouse.setLongitude(longitude);
        warehouse.setLatitude(latitude);
        warehouse.setStatus(WarehouseStatus.ACTIVE.name());
        return warehouse;
    }

    private EtaPlannedRoute route(long distanceMeters, long durationSeconds) {
        return new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.70, 29.60),
                new EtaCoordinate(106.80, 29.70)),
                distanceMeters, Duration.ofSeconds(durationSeconds));
    }
}
