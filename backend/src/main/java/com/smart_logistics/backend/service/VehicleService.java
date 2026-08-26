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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class VehicleService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final VehicleMapper vehicleMapper;

    public VehicleService(VehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    public PageResult<VehicleResponse> listVehicles(long page, long pageSize,
                                                    String keyword, VehicleStatus status) {
        LambdaQueryWrapper<Vehicle> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(Vehicle::getPlateNumber, keyword.trim());
        }
        if (status != null) {
            query.eq(Vehicle::getStatus, status.name());
        }
        query.orderByDesc(Vehicle::getId);

        Page<Vehicle> entityPage = vehicleMapper.selectPage(new Page<>(page, pageSize), query);
        List<VehicleResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public VehicleResponse getVehicle(Long id) {
        return toResponse(getRequiredVehicle(id));
    }

    public Vehicle getVehicleForTransport(Long id) {
        return getRequiredVehicle(id);
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
        ensurePlateNumberAvailable(plateNumber, null);

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plateNumber);
        vehicle.setType(trimToNull(request.getType()));
        vehicle.setCapacity(request.getCapacity());
        vehicle.setDriverId(request.getDriverId());
        vehicle.setSimCode(trimToNull(request.getSimCode()));
        vehicle.setStatus(VehicleStatus.IDLE.name());
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);

        try {
            if (vehicleMapper.insert(vehicle) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to create vehicle");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicatePlateNumber(exception);
        }
        return toResponse(getRequiredVehicle(vehicle.getId()));
    }

    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleUpdateRequest request) {
        getRequiredVehicle(id);
        String plateNumber = request.getPlateNumber().trim();
        ensurePlateNumberAvailable(plateNumber, id);

        LambdaUpdateWrapper<Vehicle> update = new LambdaUpdateWrapper<>();
        update.eq(Vehicle::getId, id)
                .set(Vehicle::getPlateNumber, plateNumber)
                .set(Vehicle::getType, trimToNull(request.getType()))
                .set(Vehicle::getCapacity, request.getCapacity())
                .set(Vehicle::getDriverId, request.getDriverId())
                .set(Vehicle::getSimCode, trimToNull(request.getSimCode()))
                .set(Vehicle::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));

        try {
            if (vehicleMapper.update(null, update) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to update vehicle");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicatePlateNumber(exception);
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
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found");
        }
        return vehicle;
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

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlateNumber(),
                vehicle.getType(),
                vehicle.getCapacity(),
                parseStatus(vehicle.getStatus()),
                vehicle.getDriverId(),
                toOffsetDateTime(vehicle.getCreatedAt()),
                toOffsetDateTime(vehicle.getUpdatedAt()),
                vehicle.getLastLongitude(),
                vehicle.getLastLatitude(),
                toOffsetDateTime(vehicle.getLastUpdatedAt()),
                vehicle.getSimCode()
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
}