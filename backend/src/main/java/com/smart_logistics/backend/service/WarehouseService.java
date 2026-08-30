package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.WarehouseResponse;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class WarehouseService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final WarehouseMapper warehouseMapper;

    public WarehouseService(WarehouseMapper warehouseMapper) {
        this.warehouseMapper = warehouseMapper;
    }

    public PageResult<WarehouseResponse> listWarehouses(long page, long pageSize,
                                                        String keyword) {
        LambdaQueryWrapper<Warehouse> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Warehouse::getWarehouseNo, normalizedKeyword)
                    .or()
                    .like(Warehouse::getName, normalizedKeyword));
        }
        query.orderByDesc(Warehouse::getId);
        Page<Warehouse> entityPage = warehouseMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<WarehouseResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public WarehouseResponse getWarehouse(Long id) {
        return toResponse(requireWarehouse(id));
    }

    public Warehouse requireWarehouse(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "warehouse not found");
        }
        return warehouse;
    }

    public Warehouse requireActiveWarehouse(Long id) {
        Warehouse warehouse = requireWarehouse(id);
        if (parseStatus(warehouse.getStatus()) != WarehouseStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse must be active");
        }
        return warehouse;
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(), warehouse.getWarehouseNo(), warehouse.getName(),
                warehouse.getAddress(), warehouse.getLongitude(), warehouse.getLatitude(),
                warehouse.getContactName(), warehouse.getContactPhone(),
                parseStatus(warehouse.getStatus()),
                toOffsetDateTime(warehouse.getCreatedAt()),
                toOffsetDateTime(warehouse.getUpdatedAt()));
    }

    private WarehouseStatus parseStatus(String status) {
        try {
            return WarehouseStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid warehouse status in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
