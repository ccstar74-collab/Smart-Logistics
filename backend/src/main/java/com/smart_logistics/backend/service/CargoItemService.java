package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.request.CargoItemCreateRequest;
import com.smart_logistics.backend.dto.response.CargoItemResponse;
import com.smart_logistics.backend.entity.CargoItem;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class CargoItemService {

    private final CargoItemMapper cargoItemMapper;
    private final CargoService cargoService;

    public CargoItemService(CargoItemMapper cargoItemMapper, CargoService cargoService) {
        this.cargoItemMapper = cargoItemMapper;
        this.cargoService = cargoService;
    }

    @Transactional
    public CargoItemResponse createCargoItem(Long cargoId, CargoItemCreateRequest request) {
        cargoService.getCargo(cargoId);

        CargoItem cargoItem = new CargoItem();
        cargoItem.setCargoId(cargoId);
        cargoItem.setItemName(request.getItemName().trim());
        cargoItem.setQuantity(request.getQuantity());
        cargoItem.setUnit(trimToNull(request.getUnit()));
        cargoItem.setWeight(request.getWeight());
        cargoItem.setVolume(request.getVolume());

        if (cargoItemMapper.insert(cargoItem) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to create cargo item");
        }
        return toResponse(getRequiredCargoItem(cargoItem.getId()));
    }

    public List<CargoItemResponse> getCargoItemsByCargoId(Long cargoId) {
        cargoService.getCargo(cargoId);

        LambdaQueryWrapper<CargoItem> query = new LambdaQueryWrapper<CargoItem>()
                .eq(CargoItem::getCargoId, cargoId)
                .orderByAsc(CargoItem::getId);
        return cargoItemMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
    }

    public CargoItemResponse getCargoItemById(Long id) {
        return toResponse(getRequiredCargoItem(id));
    }

    public CargoItemResponse getCargoItemByCargoIdAndId(Long cargoId, Long itemId) {
        cargoService.getCargo(cargoId);

        CargoItem cargoItem = getRequiredCargoItem(itemId);
        if (!Objects.equals(cargoItem.getCargoId(), cargoId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo item not found");
        }
        return toResponse(cargoItem);
    }

    private CargoItem getRequiredCargoItem(Long id) {
        CargoItem cargoItem = cargoItemMapper.selectById(id);
        if (cargoItem == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo item not found");
        }
        return cargoItem;
    }

    private CargoItemResponse toResponse(CargoItem cargoItem) {
        return new CargoItemResponse(
                cargoItem.getId(),
                cargoItem.getCargoId(),
                cargoItem.getItemName(),
                cargoItem.getQuantity(),
                cargoItem.getUnit(),
                cargoItem.getWeight(),
                cargoItem.getVolume()
        );
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
