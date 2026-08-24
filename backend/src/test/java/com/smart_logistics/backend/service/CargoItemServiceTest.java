package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.request.CargoItemCreateRequest;
import com.smart_logistics.backend.dto.response.CargoItemResponse;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.entity.CargoItem;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoItemServiceTest {

    @Mock
    private CargoItemMapper cargoItemMapper;

    @Mock
    private CargoService cargoService;

    private CargoItemService cargoItemService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "cargo-item-test"),
                CargoItem.class
        );
        cargoItemService = new CargoItemService(cargoItemMapper, cargoService);
    }

    @Test
    void createCargoItemValidatesCargoAndUsesOnlyItemFields() {
        CargoResponse cargo = cargoResponse();
        CargoItemCreateRequest request = createRequest();
        request.setItemName(" Laptop computer ");
        request.setUnit(" piece ");
        stubSuccessfulCreate(cargo);

        CargoItemResponse response = cargoItemService.createCargoItem(1L, request);

        ArgumentCaptor<CargoItem> captor = ArgumentCaptor.forClass(CargoItem.class);
        verify(cargoService).getCargo(1L);
        verify(cargoItemMapper).insert(captor.capture());
        CargoItem inserted = captor.getValue();
        assertEquals(1L, inserted.getCargoId());
        assertEquals("Laptop computer", inserted.getItemName());
        assertEquals(20, inserted.getQuantity());
        assertEquals("piece", inserted.getUnit());
        assertEquals(new BigDecimal("25.50"), inserted.getWeight());
        assertEquals(new BigDecimal("0.80"), inserted.getVolume());
        assertEquals(10L, response.getId());
        assertEquals("Laptop computer", response.getItemName());
    }

    @Test
    void createCargoItemConvertsBlankOptionalUnitToNull() {
        CargoItemCreateRequest request = createRequest();
        request.setUnit("   ");
        stubSuccessfulCreate(cargoResponse());

        CargoItemResponse response = cargoItemService.createCargoItem(1L, request);

        assertNull(response.getUnit());
    }

    @Test
    void createCargoItemFailsWhenCargoDoesNotExist() {
        BusinessException notFound = new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "cargo not found"
        );
        when(cargoService.getCargo(99999L)).thenThrow(notFound);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.createCargoItem(99999L, createRequest())
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo not found", exception.getMessage());
        verifyNoInteractions(cargoItemMapper);
    }

    @Test
    void createCargoItemReportsInsertFailure() {
        when(cargoService.getCargo(1L)).thenReturn(cargoResponse());
        when(cargoItemMapper.insert(any(CargoItem.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.createCargoItem(1L, createRequest())
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("failed to create cargo item", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCargoItemsByCargoIdValidatesCargoAndReturnsMappedItems() {
        when(cargoService.getCargo(1L)).thenReturn(cargoResponse());
        when(cargoItemMapper.selectList(any())).thenReturn(List.of(
                cargoItem(10L, "Laptop computer"),
                cargoItem(11L, "Display")
        ));

        List<CargoItemResponse> responses = cargoItemService.getCargoItemsByCargoId(1L);

        assertEquals(2, responses.size());
        assertEquals(10L, responses.get(0).getId());
        assertEquals("Display", responses.get(1).getItemName());
        verify(cargoService).getCargo(1L);
        verifyNoMoreInteractions(cargoService);
        ArgumentCaptor<LambdaQueryWrapper<CargoItem>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cargoItemMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<CargoItem> query = wrapperCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("cargo_id"));
        assertTrue(query.getSqlSegment().contains("ORDER BY id ASC"));
        assertTrue(query.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void getCargoItemsByCargoIdReturnsEmptyListForExistingCargoWithoutItems() {
        when(cargoService.getCargo(1L)).thenReturn(cargoResponse());
        when(cargoItemMapper.selectList(any())).thenReturn(List.of());

        List<CargoItemResponse> responses = cargoItemService.getCargoItemsByCargoId(1L);

        assertTrue(responses.isEmpty());
    }

    @Test
    void getCargoItemsByCargoIdFailsWhenCargoDoesNotExist() {
        when(cargoService.getCargo(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.getCargoItemsByCargoId(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo not found", exception.getMessage());
        verifyNoInteractions(cargoItemMapper);
    }

    @Test
    void getCargoItemByIdReturnsAllEntityFieldsInResponse() {
        CargoItem cargoItem = cargoItem(10L, "Laptop computer");
        when(cargoItemMapper.selectById(10L)).thenReturn(cargoItem);

        CargoItemResponse response = cargoItemService.getCargoItemById(10L);

        assertEquals(10L, response.getId());
        assertEquals(1L, response.getCargoId());
        assertEquals("Laptop computer", response.getItemName());
        assertEquals(20, response.getQuantity());
        assertEquals("piece", response.getUnit());
        assertEquals(new BigDecimal("25.50"), response.getWeight());
        assertEquals(new BigDecimal("0.80"), response.getVolume());
        verifyNoInteractions(cargoService);
    }

    @Test
    void getCargoItemByIdThrowsNotFoundForMissingItem() {
        when(cargoItemMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.getCargoItemById(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo item not found", exception.getMessage());
    }

    @Test
    void getCargoItemByCargoIdAndIdReturnsItemBelongingToCargo() {
        when(cargoService.getCargo(1L)).thenReturn(cargoResponse());
        when(cargoItemMapper.selectById(10L))
                .thenReturn(cargoItem(10L, "Laptop computer"));

        CargoItemResponse response = cargoItemService.getCargoItemByCargoIdAndId(1L, 10L);

        assertEquals(10L, response.getId());
        assertEquals(1L, response.getCargoId());
        verify(cargoService).getCargo(1L);
        verifyNoMoreInteractions(cargoService);
    }

    @Test
    void getCargoItemByCargoIdAndIdFailsWhenCargoDoesNotExist() {
        when(cargoService.getCargo(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.getCargoItemByCargoIdAndId(99999L, 10L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo not found", exception.getMessage());
        verifyNoInteractions(cargoItemMapper);
    }

    @Test
    void getCargoItemByCargoIdAndIdFailsWhenItemDoesNotExist() {
        when(cargoService.getCargo(1L)).thenReturn(cargoResponse());
        when(cargoItemMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.getCargoItemByCargoIdAndId(1L, 99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo item not found", exception.getMessage());
    }

    @Test
    void getCargoItemByCargoIdAndIdHidesItemBelongingToAnotherCargo() {
        when(cargoService.getCargo(2L)).thenReturn(cargoResponse());
        when(cargoItemMapper.selectById(10L))
                .thenReturn(cargoItem(10L, "Laptop computer"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoItemService.getCargoItemByCargoIdAndId(2L, 10L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo item not found", exception.getMessage());
    }

    @Test
    void createCargoItemDoesNotModifyCargoWeight() {
        CargoResponse cargo = cargoResponse();
        stubSuccessfulCreate(cargo);

        cargoItemService.createCargoItem(1L, createRequest());

        assertEquals(new BigDecimal("120.50"), cargo.getWeight());
        verify(cargoService).getCargo(1L);
        verifyNoMoreInteractions(cargoService);
    }

    @Test
    void createCargoItemDoesNotModifyCargoVolume() {
        CargoResponse cargo = cargoResponse();
        stubSuccessfulCreate(cargo);

        cargoItemService.createCargoItem(1L, createRequest());

        assertEquals(new BigDecimal("1.80"), cargo.getVolume());
        verify(cargoService).getCargo(1L);
        verifyNoMoreInteractions(cargoService);
    }

    @Test
    void createCargoItemDoesNotModifyCargoStatus() {
        CargoResponse cargo = cargoResponse();
        stubSuccessfulCreate(cargo);

        cargoItemService.createCargoItem(1L, createRequest());

        assertEquals(CargoStatus.WAITING, cargo.getStatus());
        verify(cargoService).getCargo(1L);
        verifyNoMoreInteractions(cargoService);
    }

    private void stubSuccessfulCreate(CargoResponse cargo) {
        CargoItem[] insertedHolder = new CargoItem[1];
        when(cargoService.getCargo(1L)).thenReturn(cargo);
        when(cargoItemMapper.insert(any(CargoItem.class))).thenAnswer(invocation -> {
            CargoItem inserted = invocation.getArgument(0);
            inserted.setId(10L);
            insertedHolder[0] = inserted;
            return 1;
        });
        when(cargoItemMapper.selectById(10L)).thenAnswer(invocation -> insertedHolder[0]);
    }

    private CargoItem cargoItem(Long id, String itemName) {
        CargoItem cargoItem = new CargoItem();
        cargoItem.setId(id);
        cargoItem.setCargoId(1L);
        cargoItem.setItemName(itemName);
        cargoItem.setQuantity(20);
        cargoItem.setUnit("piece");
        cargoItem.setWeight(new BigDecimal("25.50"));
        cargoItem.setVolume(new BigDecimal("0.80"));
        return cargoItem;
    }

    private CargoItemCreateRequest createRequest() {
        CargoItemCreateRequest request = new CargoItemCreateRequest();
        request.setItemName("Laptop computer");
        request.setQuantity(20);
        request.setUnit("piece");
        request.setWeight(new BigDecimal("25.50"));
        request.setVolume(new BigDecimal("0.80"));
        return request;
    }

    private CargoResponse cargoResponse() {
        return new CargoResponse(
                1L,
                "CGO-001",
                "Electronic equipment",
                "Fragile",
                new BigDecimal("120.50"),
                new BigDecimal("1.80"),
                100L,
                CargoStatus.WAITING,
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00")
        );
    }
}
