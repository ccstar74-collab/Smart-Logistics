package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.WarehouseTransportTaskCreateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class WarehouseTransportTaskCreateService {

    private final TransportTaskService transportTaskService;
    private final CargoTypeService cargoTypeService;
    private final WarehouseService warehouseService;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final TransportTaskAvailabilityService availabilityService;
    private final EtaPlannedRouteService etaPlannedRouteService;
    private final TransactionOperations transactionOperations;

    @Autowired
    public WarehouseTransportTaskCreateService(
            TransportTaskService transportTaskService,
            CargoTypeService cargoTypeService,
            WarehouseService warehouseService,
            CargoService cargoService,
            VehicleService vehicleService,
            TransportTaskAvailabilityService availabilityService,
            EtaPlannedRouteService etaPlannedRouteService,
            PlatformTransactionManager transactionManager) {
        this(transportTaskService, cargoTypeService, warehouseService, cargoService,
                vehicleService, availabilityService, etaPlannedRouteService,
                new TransactionTemplate(transactionManager));
    }

    WarehouseTransportTaskCreateService(
            TransportTaskService transportTaskService,
            CargoTypeService cargoTypeService,
            WarehouseService warehouseService,
            CargoService cargoService,
            VehicleService vehicleService,
            TransportTaskAvailabilityService availabilityService,
            EtaPlannedRouteService etaPlannedRouteService,
            TransactionOperations transactionOperations) {
        this.transportTaskService = transportTaskService;
        this.cargoTypeService = cargoTypeService;
        this.warehouseService = warehouseService;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.availabilityService = availabilityService;
        this.etaPlannedRouteService = etaPlannedRouteService;
        this.transactionOperations = transactionOperations;
    }

    public TransportTaskResponse createTransportTask(
            WarehouseTransportTaskCreateRequest request) {
        validateRequest(request);
        transportTaskService.requireOwner(request.getOwnerId());
        cargoTypeService.requireCargoType(request.getCargoTypeId());

        WarehouseSnapshot warehouseSnapshot = validatedSnapshot(
                warehouseService.requireActiveWarehouse(request.getOriginWarehouseId()));
        Cargo cargo = cargoService.getCargoForTransport(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransport(request.getVehicleId());
        validateCargo(cargo, request);
        validateVehicle(vehicle, request);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());

        EtaPlannedRoute plannedRoute = etaPlannedRouteService.planRoute(
                warehouseSnapshot.longitude(), warehouseSnapshot.latitude(),
                request.getEndLongitude(), request.getEndLatitude());

        TransportTaskResponse response = transactionOperations.execute(status ->
                persistWithLockedResources(request, warehouseSnapshot, plannedRoute));
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "transport task transaction returned no result");
        }
        return response;
    }

    private TransportTaskResponse persistWithLockedResources(
            WarehouseTransportTaskCreateRequest request,
            WarehouseSnapshot plannedWarehouse,
            EtaPlannedRoute plannedRoute) {
        // Preserve the legacy lock order: Cargo -> Vehicle.
        Cargo cargo = cargoService.getCargoForTransportForUpdate(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransportForUpdate(request.getVehicleId());
        Warehouse warehouse = warehouseService.requireWarehouseForUpdate(
                request.getOriginWarehouseId());

        validateCargo(cargo, request);
        validateVehicle(vehicle, request);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());
        requireUnchangedWarehouse(plannedWarehouse, warehouse);

        return transportTaskService.persistTransportTaskWithInitialRoute(
                new TransportTaskService.CreateValues(
                        request.getOwnerId(), request.getCargoId(), request.getVehicleId(),
                        request.getOriginWarehouseId(), plannedWarehouse.address(),
                        plannedWarehouse.longitude(), plannedWarehouse.latitude(),
                        request.getEndLocation().trim(), request.getEndLongitude(),
                        request.getEndLatitude(), request.getPlanStartTime(),
                        request.getPlanEndTime()), cargo, plannedRoute);
    }

    private void validateRequest(WarehouseTransportTaskCreateRequest request) {
        transportTaskService.validatePlanTimes(
                request.getPlanStartTime(), request.getPlanEndTime());
        if (!StringUtils.hasText(request.getEndLocation())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "endLocation must not be blank");
        }
        if (request.getEndLocation().length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "endLocation must not exceed 255 characters");
        }
        if (request.getEndLongitude() == null || request.getEndLatitude() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "end coordinates must be complete");
        }
        transportTaskService.validateCoordinateRange(
                "endLongitude", request.getEndLongitude(), -180, 180);
        transportTaskService.validateCoordinateRange(
                "endLatitude", request.getEndLatitude(), -90, 90);
    }

    private WarehouseSnapshot validatedSnapshot(Warehouse warehouse) {
        if (!WarehouseStatus.ACTIVE.name().equals(warehouse.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse must be active");
        }
        if (!StringUtils.hasText(warehouse.getAddress())
                || warehouse.getAddress().length() > 255) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse address is invalid");
        }
        if (warehouse.getLongitude() == null || warehouse.getLatitude() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse coordinates are incomplete");
        }
        TransportTaskService.validateCoordinateRange(
                "warehouse longitude", warehouse.getLongitude(), -180, 180,
                ErrorCode.STATE_CONFLICT);
        TransportTaskService.validateCoordinateRange(
                "warehouse latitude", warehouse.getLatitude(), -90, 90,
                ErrorCode.STATE_CONFLICT);
        return new WarehouseSnapshot(warehouse.getId(), warehouse.getAddress(),
                warehouse.getLongitude(), warehouse.getLatitude(), warehouse.getStatus());
    }

    private void requireUnchangedWarehouse(WarehouseSnapshot planned, Warehouse current) {
        WarehouseSnapshot locked = validatedSnapshot(current);
        if (!planned.equals(locked)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse changed during route planning");
        }
    }

    private void validateCargo(Cargo cargo, WarehouseTransportTaskCreateRequest request) {
        if (!CargoStatus.WAITING.name().equals(cargo.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "cargo status does not allow this operation");
        }
        if (cargo.getCargoTypeId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "cargo has no cargo type");
        }
        if (!Objects.equals(cargo.getCargoTypeId(), request.getCargoTypeId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo type does not match requested cargoTypeId");
        }
        if (cargo.getWarehouseId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "cargo has no warehouse");
        }
        if (!Objects.equals(cargo.getWarehouseId(), request.getOriginWarehouseId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo warehouse does not match originWarehouseId");
        }
        if (cargo.getOwnerId() != null
                && !Objects.equals(cargo.getOwnerId(), request.getOwnerId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "cargo is already assigned to another owner");
        }
    }

    private void validateVehicle(Vehicle vehicle,
                                 WarehouseTransportTaskCreateRequest request) {
        if (!VehicleStatus.IDLE.name().equals(vehicle.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "vehicle status does not allow this operation");
        }
        if (vehicle.getWarehouseId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "vehicle has no warehouse");
        }
        if (!Objects.equals(vehicle.getWarehouseId(), request.getOriginWarehouseId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "vehicle warehouse does not match originWarehouseId");
        }
        if (vehicle.getDriverId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "vehicle has no driver");
        }
        vehicleService.requireTransportSimCode(vehicle);
    }

    private record WarehouseSnapshot(Long id, String address, Double longitude,
                                     Double latitude, String status) {
    }
}
