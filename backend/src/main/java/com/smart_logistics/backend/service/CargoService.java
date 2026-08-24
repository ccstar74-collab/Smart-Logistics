package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CargoService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final CargoMapper cargoMapper;

    public CargoService(CargoMapper cargoMapper) {
        this.cargoMapper = cargoMapper;
    }

    public PageResult<CargoResponse> listCargos(long page, long pageSize,
                                                String keyword, CargoStatus status) {
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Cargo::getCargoNo, normalizedKeyword)
                    .or()
                    .like(Cargo::getName, normalizedKeyword));
        }
        if (status != null) {
            query.eq(Cargo::getStatus, status.name());
        }
        query.orderByDesc(Cargo::getId);

        Page<Cargo> entityPage = cargoMapper.selectPage(new Page<>(page, pageSize), query);
        List<CargoResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public CargoResponse getCargo(Long id) {
        return toResponse(getRequiredCargo(id));
    }

    public Cargo getCargoForTransport(Long id) {
        return getRequiredCargo(id);
    }

    @Transactional
    public void updateStatusForTransport(Long id, CargoStatus expectedStatus,
                                         CargoStatus targetStatus) {
        LambdaUpdateWrapper<Cargo> update = new LambdaUpdateWrapper<Cargo>()
                .eq(Cargo::getId, id)
                .eq(Cargo::getStatus, expectedStatus.name())
                .set(Cargo::getStatus, targetStatus.name())
                .set(Cargo::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));
        if (cargoMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "cargo status conflict");
        }
    }

    @Transactional
    public CargoResponse createCargo(CargoCreateRequest request) {
        String cargoNo = request.getCargoNo().trim();
        ensureCargoNoAvailable(cargoNo);

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        Cargo cargo = new Cargo();
        cargo.setCargoNo(cargoNo);
        cargo.setName(request.getName().trim());
        cargo.setDescription(trimToNull(request.getDescription()));
        cargo.setWeight(request.getWeight());
        cargo.setVolume(request.getVolume());
        cargo.setOwnerId(request.getOwnerId());
        cargo.setStatus(CargoStatus.WAITING.name());
        cargo.setCreatedAt(now);
        cargo.setUpdatedAt(now);

        try {
            if (cargoMapper.insert(cargo) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to create cargo");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateCargoNo(exception);
        }
        return toResponse(getRequiredCargo(cargo.getId()));
    }

    private Cargo getRequiredCargo(Long id) {
        Cargo cargo = cargoMapper.selectById(id);
        if (cargo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found");
        }
        return cargo;
    }

    private void ensureCargoNoAvailable(String cargoNo) {
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<Cargo>()
                .eq(Cargo::getCargoNo, cargoNo);
        if (cargoMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "cargo number already exists");
        }
    }

    private BusinessException duplicateCargoNo(DuplicateKeyException cause) {
        BusinessException exception = new BusinessException(
                ErrorCode.DATA_CONFLICT,
                "cargo number already exists"
        );
        exception.initCause(cause);
        return exception;
    }

    private CargoResponse toResponse(Cargo cargo) {
        return new CargoResponse(
                cargo.getId(),
                cargo.getCargoNo(),
                cargo.getName(),
                cargo.getDescription(),
                cargo.getWeight(),
                cargo.getVolume(),
                cargo.getOwnerId(),
                parseStatus(cargo.getStatus()),
                toOffsetDateTime(cargo.getCreatedAt()),
                toOffsetDateTime(cargo.getUpdatedAt())
        );
    }

    private CargoStatus parseStatus(String status) {
        try {
            return CargoStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid cargo status in database");
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
