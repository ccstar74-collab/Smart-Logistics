package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.request.CargoUpdateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CargoService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final CargoMapper cargoMapper;
    private final OwnerMapper ownerMapper;
    private final UserDisplayNameService userDisplayNameService;
    private final TransportTaskAvailabilityService availabilityService;
    private final BusinessDataScopeService dataScopeService;
    private final CargoTypeService cargoTypeService;
    private final WarehouseService warehouseService;

    public CargoService(CargoMapper cargoMapper,
                        OwnerMapper ownerMapper,
                        UserDisplayNameService userDisplayNameService,
                        TransportTaskAvailabilityService availabilityService,
                        BusinessDataScopeService dataScopeService,
                        CargoTypeService cargoTypeService,
                        WarehouseService warehouseService) {
        this.cargoMapper = cargoMapper;
        this.ownerMapper = ownerMapper;
        this.userDisplayNameService = userDisplayNameService;
        this.availabilityService = availabilityService;
        this.dataScopeService = dataScopeService;
        this.cargoTypeService = cargoTypeService;
        this.warehouseService = warehouseService;
    }

    public PageResult<CargoResponse> listCargos(long page, long pageSize,
                                                String keyword, CargoStatus status) {
        return listCargos(page, pageSize, keyword, status, null);
    }

    public PageResult<CargoResponse> listCargos(long page, long pageSize,
                                                String keyword, CargoStatus status,
                                                Long ownerId) {
        return listCargos(page, pageSize, keyword, status, ownerId, null, null);
    }

    public PageResult<CargoResponse> listCargos(long page, long pageSize,
                                                String keyword, CargoStatus status,
                                                Long ownerId, Long cargoTypeId,
                                                Long warehouseId) {
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<>();
        dataScopeService.applyCargoScope(query, ownerId);
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
        if (ownerId != null) {
            query.eq(Cargo::getOwnerId, ownerId);
        }
        if (cargoTypeId != null) {
            query.eq(Cargo::getCargoTypeId, cargoTypeId);
        }
        if (warehouseId != null) {
            query.eq(Cargo::getWarehouseId, warehouseId);
        }
        query.orderByDesc(Cargo::getId);

        Page<Cargo> entityPage = cargoMapper.selectPage(new Page<>(page, pageSize), query);
        List<CargoResponse> records = toResponses(entityPage.getRecords());
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public CargoResponse getCargo(Long id) {
        return toResponse(getRequiredCargo(id));
    }

    public List<CargoResponse> listAvailableCargos() {
        return listAvailableCargos(null, null, null);
    }

    public List<CargoResponse> listAvailableCargos(Long cargoTypeId, Long warehouseId,
                                                   Long ownerId) {
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<Cargo>()
                .eq(Cargo::getStatus, CargoStatus.WAITING.name());
        if (cargoTypeId != null) {
            query.eq(Cargo::getCargoTypeId, cargoTypeId);
        }
        if (warehouseId != null) {
            query.eq(Cargo::getWarehouseId, warehouseId);
        }
        if (ownerId != null) {
            query.and(wrapper -> wrapper.isNull(Cargo::getOwnerId)
                    .or()
                    .eq(Cargo::getOwnerId, ownerId));
        }
        query.orderByAsc(Cargo::getId);
        List<Cargo> waitingCargos = cargoMapper.selectList(
                query);
        Set<Long> occupiedIds = availabilityService.findActiveCargoIds(
                waitingCargos.stream().map(Cargo::getId).toList());
        return toResponses(waitingCargos.stream()
                .filter(cargo -> !occupiedIds.contains(cargo.getId()))
                .toList());
    }

    public Cargo getCargoForTransport(Long id) {
        return getRequiredCargoRaw(id);
    }

    public Cargo getCargoForTransportForUpdate(Long id) {
        Cargo cargo = cargoMapper.selectOne(new LambdaQueryWrapper<Cargo>()
                .eq(Cargo::getId, id)
                .last("FOR UPDATE"));
        if (cargo == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found");
        }
        return cargo;
    }

    @Transactional
    public void bindOwnerForTransport(Cargo cargo, Long ownerId) {
        if (cargo.getOwnerId() != null) {
            if (!cargo.getOwnerId().equals(ownerId)) {
                throw new BusinessException(ErrorCode.DATA_CONFLICT,
                        "cargo is already assigned to another owner");
            }
            return;
        }
        LambdaUpdateWrapper<Cargo> update = new LambdaUpdateWrapper<Cargo>()
                .eq(Cargo::getId, cargo.getId())
                .eq(Cargo::getStatus, CargoStatus.WAITING.name())
                .isNull(Cargo::getOwnerId)
                .set(Cargo::getOwnerId, ownerId)
                .set(Cargo::getUpdatedAt, LocalDateTime.now(API_TIME_ZONE));
        if (cargoMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "cargo owner binding conflict");
        }
        cargo.setOwnerId(ownerId);
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
        ensureOwnerExists(request.getOwnerId());
        requireCargoTypeIfPresent(request.getCargoTypeId());
        requireActiveWarehouseIfPresent(request.getWarehouseId());

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        Cargo cargo = new Cargo();
        cargo.setCargoNo(cargoNo);
        cargo.setName(request.getName().trim());
        cargo.setDescription(trimToNull(request.getDescription()));
        cargo.setWeight(request.getWeight());
        cargo.setVolume(request.getVolume());
        cargo.setCargoTypeId(request.getCargoTypeId());
        cargo.setWarehouseId(request.getWarehouseId());
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

    @Transactional
    public CargoResponse updateCargo(Long id, CargoUpdateRequest request) {
        Cargo cargo = requireCargoMutable(id);
        requireCargoTypeIfPresent(request.getCargoTypeId());
        requireActiveWarehouseIfPresent(request.getWarehouseId());
        cargo.setName(request.getName().trim());
        cargo.setDescription(trimToNull(request.getDescription()));
        cargo.setWeight(request.getWeight());
        cargo.setVolume(request.getVolume());
        if (request.getCargoTypeId() != null) {
            cargo.setCargoTypeId(request.getCargoTypeId());
        }
        if (request.getWarehouseId() != null) {
            cargo.setWarehouseId(request.getWarehouseId());
        }
        cargo.setUpdatedAt(LocalDateTime.now(API_TIME_ZONE));
        if (cargoMapper.updateById(cargo) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to update cargo");
        }
        return toResponse(getRequiredCargo(id));
    }

    public Cargo requireCargoMutable(Long id) {
        Cargo cargo = getRequiredCargo(id);
        if (parseStatus(cargo.getStatus()) != CargoStatus.WAITING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "only waiting cargo can be modified");
        }
        availabilityService.ensureCargoAvailable(id);
        return cargo;
    }

    @Transactional
    public void deleteCargo(Long id) {
        requireCargoMutable(id);
        if (cargoMapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to delete cargo");
        }
    }

    private Cargo getRequiredCargo(Long id) {
        Cargo cargo = getRequiredCargoRaw(id);
        dataScopeService.requireCargoAccess(cargo);
        return cargo;
    }

    private Cargo getRequiredCargoRaw(Long id) {
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

    private List<CargoResponse> toResponses(List<Cargo> cargos) {
        Map<Long, String> ownerNames = userDisplayNameService.getOwnerNames(
                cargos.stream().map(Cargo::getOwnerId).toList());
        return cargos.stream()
                .map(cargo -> toResponse(cargo, cargo.getOwnerId() == null
                        ? null : ownerNames.get(cargo.getOwnerId())))
                .toList();
    }

    private void ensureOwnerExists(Long ownerId) {
        if (ownerId == null) {
            return;
        }
        Owner owner = ownerMapper.selectById(ownerId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "owner not found");
        }
    }

    private CargoResponse toResponse(Cargo cargo) {
        Long ownerId = cargo.getOwnerId();
        String ownerName = ownerId == null ? null : userDisplayNameService
                .getOwnerNames(List.of(ownerId)).get(ownerId);
        return toResponse(cargo, ownerName);
    }

    private CargoResponse toResponse(Cargo cargo, String ownerName) {
        return new CargoResponse(
                cargo.getId(),
                cargo.getCargoNo(),
                cargo.getName(),
                cargo.getDescription(),
                cargo.getWeight(),
                cargo.getVolume(),
                cargo.getCargoTypeId(),
                cargo.getWarehouseId(),
                cargo.getOwnerId(),
                ownerName,
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

    private void requireCargoTypeIfPresent(Long cargoTypeId) {
        if (cargoTypeId != null) {
            cargoTypeService.requireCargoType(cargoTypeId);
        }
    }

    private void requireActiveWarehouseIfPresent(Long warehouseId) {
        if (warehouseId != null) {
            warehouseService.requireActiveWarehouse(warehouseId);
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
