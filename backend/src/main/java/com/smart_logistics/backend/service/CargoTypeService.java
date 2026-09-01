package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoTypeCreateRequest;
import com.smart_logistics.backend.dto.response.CargoTypeResponse;
import com.smart_logistics.backend.entity.CargoType;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoTypeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CargoTypeService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final CargoTypeMapper cargoTypeMapper;

    public CargoTypeService(CargoTypeMapper cargoTypeMapper) {
        this.cargoTypeMapper = cargoTypeMapper;
    }

    public PageResult<CargoTypeResponse> listCargoTypes(long page, long pageSize,
                                                        String keyword) {
        LambdaQueryWrapper<CargoType> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(CargoType::getName, keyword.trim());
        }
        query.orderByDesc(CargoType::getId);
        Page<CargoType> entityPage = cargoTypeMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<CargoTypeResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    @Transactional
    public CargoTypeResponse createCargoType(CargoTypeCreateRequest request) {
        String name = normalizeName(request.getName());
        if (cargoTypeMapper.selectCount(new LambdaQueryWrapper<CargoType>()
                .eq(CargoType::getName, name)) > 0) {
            throw duplicateName(null);
        }

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        CargoType cargoType = new CargoType();
        cargoType.setName(name);
        cargoType.setUnit(trimToNull(request.getUnit()));
        cargoType.setUnitWeight(request.getUnitWeight());
        cargoType.setUnitVolume(request.getUnitVolume());
        cargoType.setDescription(trimToNull(request.getDescription()));
        cargoType.setCreatedAt(now);
        cargoType.setUpdatedAt(now);
        try {
            if (cargoTypeMapper.insert(cargoType) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "failed to create cargo type");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateName(exception);
        }
        return toResponse(requireCargoType(cargoType.getId()));
    }

    public CargoType requireCargoType(Long id) {
        CargoType cargoType = cargoTypeMapper.selectById(id);
        if (cargoType == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "cargo type not found");
        }
        return cargoType;
    }

    private CargoTypeResponse toResponse(CargoType cargoType) {
        return new CargoTypeResponse(
                cargoType.getId(), cargoType.getName(), cargoType.getUnit(),
                cargoType.getUnitWeight(), cargoType.getUnitVolume(),
                cargoType.getDescription(), toOffsetDateTime(cargoType.getCreatedAt()),
                toOffsetDateTime(cargoType.getUpdatedAt()));
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "name must not be blank");
        }
        return value.trim();
    }

    private BusinessException duplicateName(DuplicateKeyException cause) {
        BusinessException exception = new BusinessException(
                ErrorCode.DATA_CONFLICT, "cargo type name already exists");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
