package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.VehicleCreateRequest;
import com.smart_logistics.backend.dto.request.VehicleUpdateRequest;
import com.smart_logistics.backend.dto.response.VehicleResponse;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class VehicleService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SIM_CODE_PATTERN = Pattern.compile("^sim_\\d{3}$");

    private final VehicleMapper vehicleMapper;
    private final UserDisplayNameService userDisplayNameService;
    private final TransportTaskAvailabilityService availabilityService;
    private final BusinessDataScopeService dataScopeService;
    private final DriverService driverService;

    public VehicleService(VehicleMapper vehicleMapper,
                          UserDisplayNameService userDisplayNameService,
                          TransportTaskAvailabilityService availabilityService,
                          BusinessDataScopeService dataScopeService,
                          DriverService driverService) {
        this.vehicleMapper = vehicleMapper;
        this.userDisplayNameService = userDisplayNameService;
        this.availabilityService = availabilityService;
        this.dataScopeService = dataScopeService;
        this.driverService = driverService;
    }

    public PageResult<VehicleResponse> listVehicles(long page, long pageSize,
                                                     String keyword, VehicleStatus status) {
        return listVehicles(page, pageSize, keyword, status, null);
    }

    public PageResult<VehicleResponse> listVehicles(long page, long pageSize,
                                                     String keyword, VehicleStatus status,
                                                     Long driverId) {
        LambdaQueryWrapper<Vehicle> query = new LambdaQueryWrapper<>();
        dataScopeService.applyVehicleScope(query, driverId);
        if (StringUtils.hasText(keyword)) {
            query.like(Vehicle::getPlateNumber, keyword.trim());
        }
        if (status != null) {
            query.eq(Vehicle::getStatus, status.name());
        }
        if (driverId != null) {
            query.eq(Vehicle::getDriverId, driverId);
        }
        query.orderByDesc(Vehicle::getId);

        Page<Vehicle> entityPage = vehicleMapper.selectPage(new Page<>(page, pageSize), query);
        List<VehicleResponse> records = toResponses(entityPage.getRecords());
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public VehicleResponse getVehicle(Long id) {
        return toResponse(getRequiredVehicle(id));
    }

    public List<VehicleResponse> listAvailableVehicles() {
        List<Vehicle> idleVehicles = vehicleMapper.selectList(
                new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getStatus, VehicleStatus.IDLE.name())
                        .orderByAsc(Vehicle::getId));
        Set<Long> occupiedIds = availabilityService.findActiveVehicleIds(
                idleVehicles.stream().map(Vehicle::getId).toList());
        return toResponses(idleVehicles.stream()
                .filter(vehicle -> !occupiedIds.contains(vehicle.getId()))
                .toList());
    }

    public List<String> listAvailableSimCodes(String keyword) {
        List<Vehicle> assignedVehicles = vehicleMapper.selectList(
                new LambdaQueryWrapper<Vehicle>()
                        .select(Vehicle::getSimCode)
                        .isNotNull(Vehicle::getSimCode));
        Set<String> assignedCodes = assignedVehicles.stream()
                .map(Vehicle::getSimCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        return java.util.stream.IntStream.rangeClosed(0, 999)
                .mapToObj(number -> String.format(Locale.ROOT, "sim_%03d", number))
                .filter(code -> !assignedCodes.contains(code))
                .filter(code -> normalizedKeyword == null || code.contains(normalizedKeyword))
                .toList();
    }

    public Vehicle getVehicleForTransport(Long id) {
        return getRequiredVehicleRaw(id);
    }

    @Transactional
    public void updateStatusForTransport(Long id, VehicleStatus expectedStatus,
                                         VehicleStatus targetStatus) {
        LambdaUpdateWrapper<Vehicle> update = new LambdaUpdateWrapper<Vehicle>()
                .eq(Vehicle::getId, id)
                .eq(Vehicle::getStatus, expectedStatus.name())
                .set(Vehicle::getStatus, targetStatus.name())
                .set(Vehicle::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));
        if (vehicleMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "vehicle status conflict");
        }
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleCreateRequest request) {
        String plateNumber = request.getPlateNumber().trim();
        String simCode = normalizeSimCode(request.getSimCode());
        ensurePlateNumberAvailable(plateNumber, null);
        ensureSimCodeAvailable(simCode, null);

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plateNumber);
        vehicle.setType(trimToNull(request.getType()));
        vehicle.setCapacity(request.getCapacity());
        vehicle.setDriverId(request.getDriverId());
        vehicle.setSimCode(simCode);
        requireActiveDriverIfPresent(request.getDriverId());
        vehicle.setStatus(VehicleStatus.IDLE.name());
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);

        try {
            if (vehicleMapper.insert(vehicle) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to create vehicle");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateVehicleKey(exception);
        }
        return toResponse(getRequiredVehicle(vehicle.getId()));
    }

    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleUpdateRequest request) {
        Vehicle current = getRequiredVehicle(id);
        validateDriverChange(current, request.getDriverId());
        String plateNumber = request.getPlateNumber().trim();
        ensurePlateNumberAvailable(plateNumber, id);
        String simCode = request.getSimCode() == null
                ? current.getSimCode() : normalizeSimCode(request.getSimCode());
        if (!Objects.equals(current.getSimCode(), simCode)) {
            ensureSimCodeAvailable(simCode, id);
        }

        LambdaUpdateWrapper<Vehicle> update = new LambdaUpdateWrapper<>();
        update.eq(Vehicle::getId, id)
                .set(Vehicle::getPlateNumber, plateNumber)
                .set(Vehicle::getType, trimToNull(request.getType()))
                .set(Vehicle::getCapacity, request.getCapacity())
                .set(Vehicle::getDriverId, request.getDriverId())
                .set(Vehicle::getSimCode, simCode)
                .set(Vehicle::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));

        try {
            if (vehicleMapper.update(null, update) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to update vehicle");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateVehicleKey(exception);
        }
        return toResponse(getRequiredVehicle(id));
    }

    @Transactional
    public VehicleResponse updateDriverBinding(Long id, Long driverId) {
        Vehicle vehicle = getRequiredVehicle(id);
        if (Objects.equals(vehicle.getDriverId(), driverId)) {
            return toResponse(vehicle);
        }
        validateDriverChange(vehicle, driverId);
        LambdaUpdateWrapper<Vehicle> update = new LambdaUpdateWrapper<Vehicle>()
                .eq(Vehicle::getId, id)
                .set(Vehicle::getDriverId, driverId)
                .set(Vehicle::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));
        if (vehicleMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to update vehicle driver");
        }
        return toResponse(getRequiredVehicle(id));
    }

    @Transactional
    public void disableVehicle(Long id) {
        Vehicle vehicle = getRequiredVehicle(id);
        VehicleStatus currentStatus = parseStatus(vehicle.getStatus());
        if (currentStatus == VehicleStatus.TRANSPORTING) {
            throw new BusinessException(
                    ErrorCode.STATE_CONFLICT,
                    "transporting vehicle cannot be disabled"
            );
        }
        if (currentStatus == VehicleStatus.DISABLED) {
            return;
        }

        Vehicle disabledVehicle = new Vehicle();
        disabledVehicle.setId(id);
        disabledVehicle.setStatus(VehicleStatus.DISABLED.name());
        disabledVehicle.setUpdatedAt(LocalDateTime.now(API_TIME_ZONE));
        if (vehicleMapper.updateById(disabledVehicle) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to disable vehicle");
        }
    }

    private Vehicle getRequiredVehicle(Long id) {
        Vehicle vehicle = getRequiredVehicleRaw(id);
        dataScopeService.requireVehicleAccess(vehicle);
        return vehicle;
    }

    private Vehicle getRequiredVehicleRaw(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found");
        }
        return vehicle;
    }

    private void validateDriverChange(Vehicle vehicle, Long driverId) {
        if (!Objects.equals(vehicle.getDriverId(), driverId)
                && parseStatus(vehicle.getStatus()) == VehicleStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transporting vehicle cannot change driver");
        }
        requireActiveDriverIfPresent(driverId);
    }

    private void requireActiveDriverIfPresent(Long driverId) {
        if (driverId != null) {
            driverService.requireActiveDriver(driverId);
        }
    }

    private void ensurePlateNumberAvailable(String plateNumber, Long excludedId) {
        LambdaQueryWrapper<Vehicle> query = new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNumber, plateNumber);
        if (excludedId != null) {
            query.ne(Vehicle::getId, excludedId);
        }
        if (vehicleMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "plate number already exists");
        }
    }

    private BusinessException duplicatePlateNumber(DuplicateKeyException cause) {
        BusinessException exception = new BusinessException(
                ErrorCode.DATA_CONFLICT,
                "plate number already exists"
        );
        exception.initCause(cause);
        return exception;
    }

    private void ensureSimCodeAvailable(String simCode, Long excludedId) {
        LambdaQueryWrapper<Vehicle> query = new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getSimCode, simCode);
        if (excludedId != null) {
            query.ne(Vehicle::getId, excludedId);
        }
        if (vehicleMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "simCode is already assigned to another vehicle");
        }
    }

    private BusinessException duplicateVehicleKey(DuplicateKeyException cause) {
        Throwable current = cause;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("sim_code")
                        || normalized.contains("uk_vehicle_sim_code")) {
                    BusinessException exception = new BusinessException(
                            ErrorCode.DATA_CONFLICT,
                            "simCode is already assigned to another vehicle"
                    );
                    exception.initCause(cause);
                    return exception;
                }
            }
            current = current.getCause();
        }
        return duplicatePlateNumber(cause);
    }

    private List<VehicleResponse> toResponses(List<Vehicle> vehicles) {
        Map<Long, String> driverNames = userDisplayNameService.getDriverNames(
                vehicles.stream().map(Vehicle::getDriverId).toList());
        return vehicles.stream()
                .map(vehicle -> toResponse(vehicle, vehicle.getDriverId() == null
                        ? null : driverNames.get(vehicle.getDriverId())))
                .toList();
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        String driverName = null;
        if (vehicle.getDriverId() != null) {
            driverName = userDisplayNameService.getDriverNames(List.of(vehicle.getDriverId()))
                    .get(vehicle.getDriverId());
        }
        return toResponse(vehicle, driverName);
    }

    private VehicleResponse toResponse(Vehicle vehicle, String driverName) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlateNumber(),
                vehicle.getType(),
                vehicle.getCapacity(),
                parseStatus(vehicle.getStatus()),
                vehicle.getDriverId(),
                driverName,
                vehicle.getSimCode(),
                toOffsetDateTime(vehicle.getCreatedAt()),
                toOffsetDateTime(vehicle.getUpdatedAt()),
                vehicle.getLastLongitude(),
                vehicle.getLastLatitude(),
                toOffsetDateTime(vehicle.getLastUpdatedAt())
        );
    }

    private VehicleStatus parseStatus(String status) {
        try {
            return VehicleStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid vehicle status in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSimCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "simCode must not be blank");
        }
        if (!SIM_CODE_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "simCode must match ^sim_\\d{3}$");
        }
        return value;
    }
}
