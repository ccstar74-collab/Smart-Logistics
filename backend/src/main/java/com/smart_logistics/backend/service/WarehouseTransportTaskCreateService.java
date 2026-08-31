package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.InitialRouteLocationSnapshot;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.request.WarehouseTransportTaskCreateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.InitialRouteCandidate;
import com.smart_logistics.backend.entity.InitialRouteDecision;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.InitialRouteCandidateMapper;
import com.smart_logistics.backend.mapper.InitialRouteDecisionMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class WarehouseTransportTaskCreateService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TransportTaskService transportTaskService;
    private final CargoTypeService cargoTypeService;
    private final WarehouseService warehouseService;
    private final CargoService cargoService;
    private final VehicleService vehicleService;
    private final TransportTaskAvailabilityService availabilityService;
    private final InitialRouteDecisionMapper decisionMapper;
    private final InitialRouteCandidateMapper candidateMapper;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;

    @Autowired
    public WarehouseTransportTaskCreateService(
            TransportTaskService transportTaskService,
            CargoTypeService cargoTypeService,
            WarehouseService warehouseService,
            CargoService cargoService,
            VehicleService vehicleService,
            TransportTaskAvailabilityService availabilityService,
            InitialRouteDecisionMapper decisionMapper,
            InitialRouteCandidateMapper candidateMapper,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this(transportTaskService, cargoTypeService, warehouseService, cargoService,
                vehicleService, availabilityService, decisionMapper, candidateMapper,
                currentUserService, objectMapper,
                new TransactionTemplate(transactionManager));
    }

    WarehouseTransportTaskCreateService(
            TransportTaskService transportTaskService,
            CargoTypeService cargoTypeService,
            WarehouseService warehouseService,
            CargoService cargoService,
            VehicleService vehicleService,
            TransportTaskAvailabilityService availabilityService,
            InitialRouteDecisionMapper decisionMapper,
            InitialRouteCandidateMapper candidateMapper,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations) {
        this.transportTaskService = transportTaskService;
        this.cargoTypeService = cargoTypeService;
        this.warehouseService = warehouseService;
        this.cargoService = cargoService;
        this.vehicleService = vehicleService;
        this.availabilityService = availabilityService;
        this.decisionMapper = decisionMapper;
        this.candidateMapper = candidateMapper;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
    }

    @Deprecated
    public TransportTaskResponse createTransportTask(
            WarehouseTransportTaskCreateRequest request) {
        return createTransportTask(request, request.getRouteDecisionId());
    }

    public TransportTaskResponse createTransportTask(
            WarehouseTransportTaskCreateRequest request,
            String idempotencyKey) {
        validateRequest(request);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        ConfirmationOutcome outcome;
        try {
            outcome = transactionOperations.execute(status ->
                    confirmAndCreate(request, normalizedKey));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "confirmation idempotency key is already in use");
        }
        if (outcome == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "transport task transaction returned no result");
        }
        if (outcome.expired()) {
            throw new BusinessException(ErrorCode.DECISION_EXPIRED,
                    "initial route decision has expired");
        }
        return outcome.response();
    }

    private ConfirmationOutcome confirmAndCreate(
            WarehouseTransportTaskCreateRequest request,
            String idempotencyKey) {
        InitialRouteDecision decision = getDecisionForUpdate(request.getRouteDecisionId());
        requireDecisionOwner(decision);
        InitialRouteDecisionStatus decisionStatus = parseDecisionStatus(decision.getStatus());
        if (decisionStatus == InitialRouteDecisionStatus.CONFIRMED) {
            return confirmedOutcome(decision, request, idempotencyKey);
        }
        if (decisionStatus == InitialRouteDecisionStatus.EXPIRED
                || !decision.getExpiresAt().isAfter(LocalDateTime.now(API_TIME_ZONE))) {
            markExpired(decision);
            return ConfirmationOutcome.expiredOutcome();
        }
        if (decisionStatus != InitialRouteDecisionStatus.PENDING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "initial route decision is not pending");
        }

        InitialRouteCandidate candidate = getSelectedCandidate(
                decision.getDecisionId(), request.getSelectedRouteId());
        InitialRouteLocationSnapshot plannedWarehouse = readJson(
                decision.getStartSnapshot(), InitialRouteLocationSnapshot.class);
        InitialRouteLocationSnapshot destination = readJson(
                decision.getDestinationSnapshot(), InitialRouteLocationSnapshot.class);
        validateDecisionContext(decision, request, plannedWarehouse, destination);

        transportTaskService.requireOwner(request.getOwnerId());
        cargoTypeService.requireCargoType(request.getCargoTypeId());
        // Preserve the established resource lock order after locking the decision.
        Cargo cargo = cargoService.getCargoForTransportForUpdate(request.getCargoId());
        Vehicle vehicle = vehicleService.getVehicleForTransportForUpdate(request.getVehicleId());
        Warehouse warehouse = warehouseService.requireWarehouseForUpdate(
                request.getOriginWarehouseId());

        validateCargo(cargo, request);
        validateVehicle(vehicle, request);
        availabilityService.ensureCargoAvailable(request.getCargoId());
        availabilityService.ensureVehicleAvailable(request.getVehicleId());
        requireUnchangedWarehouse(plannedWarehouse, warehouse);

        EtaPlannedRoute plannedRoute = toPlannedRoute(candidate);

        TransportTaskResponse response =
                transportTaskService.persistTransportTaskWithInitialRoute(
                new TransportTaskService.CreateValues(
                        request.getOwnerId(), request.getCargoId(), request.getVehicleId(),
                        request.getOriginWarehouseId(), plannedWarehouse.location(),
                        plannedWarehouse.longitude(), plannedWarehouse.latitude(),
                        destination.location(), destination.longitude(),
                        destination.latitude(), request.getPlanStartTime(),
                        request.getPlanEndTime()), cargo, plannedRoute);

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        decision.setStatus(InitialRouteDecisionStatus.CONFIRMED.name());
        decision.setSelectedRouteId(candidate.getPreviewRouteId());
        decision.setRouteSelectionRemark(trimToNull(request.getRouteSelectionRemark()));
        decision.setConfirmationIdempotencyKey(idempotencyKey);
        decision.setConfirmedAt(now);
        decision.setTaskId(response.getId());
        decision.setUpdatedAt(now);
        if (decisionMapper.updateById(decision) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "initial route decision confirmation conflict");
        }
        return ConfirmationOutcome.success(response);
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
        if (!StringUtils.hasText(request.getRouteDecisionId())
                || !StringUtils.hasText(request.getSelectedRouteId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "routeDecisionId and selectedRouteId are required");
        }
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

    private void requireUnchangedWarehouse(
            InitialRouteLocationSnapshot planned, Warehouse current) {
        WarehouseSnapshot locked = validatedSnapshot(current);
        if (!Objects.equals(planned.location(), locked.address())
                || Double.compare(planned.longitude(), locked.longitude()) != 0
                || Double.compare(planned.latitude(), locked.latitude()) != 0
                || !"GCJ02".equals(planned.coordinateSystem())) {
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

    private InitialRouteDecision getDecisionForUpdate(String decisionId) {
        InitialRouteDecision decision = decisionMapper.selectOne(
                new LambdaQueryWrapper<InitialRouteDecision>()
                        .eq(InitialRouteDecision::getDecisionId, decisionId)
                        .last("FOR UPDATE"));
        if (decision == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "initial route decision not found");
        }
        return decision;
    }

    private InitialRouteCandidate getSelectedCandidate(
            String decisionId, String selectedRouteId) {
        InitialRouteCandidate candidate = candidateMapper.selectOne(
                new LambdaQueryWrapper<InitialRouteCandidate>()
                        .eq(InitialRouteCandidate::getDecisionId, decisionId)
                        .eq(InitialRouteCandidate::getPreviewRouteId, selectedRouteId));
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "selected initial route candidate not found");
        }
        return candidate;
    }

    private void requireDecisionOwner(InitialRouteDecision decision) {
        if (!Objects.equals(decision.getCreatedBy(),
                currentUserService.getCurrentUser().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "initial route decision belongs to another user");
        }
    }

    private ConfirmationOutcome confirmedOutcome(
            InitialRouteDecision decision,
            WarehouseTransportTaskCreateRequest request,
            String idempotencyKey) {
        if (Objects.equals(decision.getConfirmationIdempotencyKey(), idempotencyKey)
                && Objects.equals(decision.getSelectedRouteId(),
                request.getSelectedRouteId())
                && decision.getTaskId() != null) {
            return ConfirmationOutcome.success(
                    transportTaskService.getTransportTask(decision.getTaskId()));
        }
        throw new BusinessException(ErrorCode.DATA_CONFLICT,
                "initial route decision has already been confirmed");
    }

    private void validateDecisionContext(
            InitialRouteDecision decision,
            WarehouseTransportTaskCreateRequest request,
            InitialRouteLocationSnapshot start,
            InitialRouteLocationSnapshot destination) {
        if (!Objects.equals(decision.getOriginWarehouseId(),
                request.getOriginWarehouseId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "origin warehouse does not match route decision");
        }
        if (!Objects.equals(destination.location(), request.getEndLocation().trim())
                || Double.compare(destination.longitude(), request.getEndLongitude()) != 0
                || Double.compare(destination.latitude(), request.getEndLatitude()) != 0
                || !"GCJ02".equals(start.coordinateSystem())
                || !"GCJ02".equals(destination.coordinateSystem())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "task route context does not match route decision");
        }
    }

    private EtaPlannedRoute toPlannedRoute(InitialRouteCandidate candidate) {
        List<List<Double>> points = readJson(
                candidate.getPoints(), new TypeReference<>() {});
        List<EtaCoordinate> polyline = points.stream()
                .map(point -> {
                    if (point == null || point.size() != 2) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                "invalid candidate route geometry in database");
                    }
                    return new EtaCoordinate(point.get(0), point.get(1));
                }).toList();
        TrafficSnapshot traffic = readJson(
                candidate.getTrafficSnapshot(), TrafficSnapshot.class);
        return new EtaPlannedRoute(polyline, candidate.getDistanceMeters(),
                Duration.ofSeconds(candidate.getDurationSeconds()), traffic);
    }

    private void markExpired(InitialRouteDecision decision) {
        decision.setStatus(InitialRouteDecisionStatus.EXPIRED.name());
        decision.setUpdatedAt(LocalDateTime.now(API_TIME_ZONE));
        if (decisionMapper.updateById(decision) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "initial route decision expiration conflict");
        }
    }

    private InitialRouteDecisionStatus parseDecisionStatus(String value) {
        try {
            return InitialRouteDecisionStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route decision status in database");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Idempotency-Key must contain 1 to 128 characters");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route decision snapshot in database");
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route candidate snapshot in database");
        }
    }

    private record ConfirmationOutcome(TransportTaskResponse response,
                                       boolean expired) {
        static ConfirmationOutcome success(TransportTaskResponse response) {
            return new ConfirmationOutcome(response, false);
        }

        static ConfirmationOutcome expiredOutcome() {
            return new ConfirmationOutcome(null, true);
        }
    }
}
