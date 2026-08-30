package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.OriginRecommendationRequest;
import com.smart_logistics.backend.dto.response.OriginRecommendationResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OriginRecommendationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OriginRecommendationService.class);

    private final OwnerMapper ownerMapper;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final WarehouseService warehouseService;
    private final EtaPlannedRouteService etaPlannedRouteService;

    public OriginRecommendationService(OwnerMapper ownerMapper,
                                       CargoService cargoService,
                                       VehicleService vehicleService,
                                       WarehouseService warehouseService,
                                       EtaPlannedRouteService etaPlannedRouteService) {
        this.ownerMapper = ownerMapper;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.warehouseService = warehouseService;
        this.etaPlannedRouteService = etaPlannedRouteService;
    }

    public List<OriginRecommendationResponse> recommend(
            OriginRecommendationRequest request) {
        requireOwner(request.getOwnerId());

        Map<Long, Long> cargoCounts = countCargosByWarehouse(
                cargoService.findAvailableCargos(
                        request.getCargoTypeId(), null, request.getOwnerId()));
        Map<Long, Long> vehicleCounts = countVehiclesByWarehouse(
                vehicleService.findAvailableVehicles(null));

        Set<Long> candidateIds = new HashSet<>(cargoCounts.keySet());
        candidateIds.retainAll(vehicleCounts.keySet());
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<Warehouse> warehouses =
                warehouseService.listActiveWarehousesByIds(candidateIds);
        LOGGER.info("Planning origin recommendation routes for {} candidate warehouses",
                warehouses.size());

        List<OriginRecommendationResponse> planned = new ArrayList<>(warehouses.size());
        for (Warehouse warehouse : warehouses) {
            EtaPlannedRoute route = etaPlannedRouteService.planRoute(
                    warehouse.getLongitude(), warehouse.getLatitude(),
                    request.getEndLongitude(), request.getEndLatitude());
            planned.add(new OriginRecommendationResponse(
                    warehouse.getId(), warehouse.getWarehouseNo(), warehouse.getName(),
                    warehouse.getAddress(), warehouse.getLongitude(), warehouse.getLatitude(),
                    route.distanceMeters(), route.referenceDuration().getSeconds(),
                    cargoCounts.get(warehouse.getId()), vehicleCounts.get(warehouse.getId()),
                    false));
        }

        planned.sort(Comparator
                .comparingLong(OriginRecommendationResponse::getDurationSeconds)
                .thenComparingLong(OriginRecommendationResponse::getDistanceMeters)
                .thenComparing(OriginRecommendationResponse::getWarehouseId));
        return java.util.stream.IntStream.range(0, planned.size())
                .mapToObj(index -> planned.get(index).withRecommended(index == 0))
                .toList();
    }

    private void requireOwner(Long ownerId) {
        if (ownerMapper.selectById(ownerId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "owner not found");
        }
    }

    private Map<Long, Long> countCargosByWarehouse(List<Cargo> cargos) {
        return cargos.stream()
                .map(Cargo::getWarehouseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private Map<Long, Long> countVehiclesByWarehouse(List<Vehicle> vehicles) {
        return vehicles.stream()
                .map(Vehicle::getWarehouseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
