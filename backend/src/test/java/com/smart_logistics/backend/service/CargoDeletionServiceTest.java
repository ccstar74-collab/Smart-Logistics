package com.smart_logistics.backend.service;

import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoDeletionServiceTest {
    @Mock private CargoMapper cargoMapper;
    @Mock private OwnerMapper ownerMapper;
    @Mock private UserDisplayNameService displayNameService;
    @Mock private TransportTaskAvailabilityService availabilityService;
    @Mock private BusinessDataScopeService dataScopeService;
    private CargoService cargoService;

    @BeforeEach
    void setUp() {
        cargoService = new CargoService(cargoMapper, ownerMapper, displayNameService,
                availabilityService, dataScopeService,
                mock(CargoTypeService.class), mock(WarehouseService.class));
    }

    @Test
    void deletesWaitingCargoWithoutActiveTask() {
        Cargo cargo = cargo(CargoStatus.WAITING);
        when(cargoMapper.selectById(1L)).thenReturn(cargo);
        when(cargoMapper.deleteById(1L)).thenReturn(1);

        cargoService.deleteCargo(1L);

        verify(dataScopeService).requireCargoAccess(cargo);
        verify(availabilityService).ensureCargoAvailable(1L);
        verify(cargoMapper).deleteById(1L);
    }

    @Test
    void activeWaitingTaskProducesConflictAndDoesNotDelete() {
        assertActiveTaskConflict("cargo already has an active WAITING transport task");
    }

    @Test
    void activeTransportingTaskProducesConflictAndDoesNotDelete() {
        assertActiveTaskConflict("cargo already has an active TRANSPORTING transport task");
    }

    @Test
    void rejectsNonWaitingCargo() {
        when(cargoMapper.selectById(1L)).thenReturn(cargo(CargoStatus.TRANSPORTING));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoService.deleteCargo(1L));
        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(availabilityService, never()).ensureCargoAvailable(1L);
        verify(cargoMapper, never()).deleteById(1L);
    }

    private void assertActiveTaskConflict(String message) {
        when(cargoMapper.selectById(1L)).thenReturn(cargo(CargoStatus.WAITING));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.DATA_CONFLICT, message))
                .when(availabilityService).ensureCargoAvailable(1L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoService.deleteCargo(1L));
        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        verify(cargoMapper, never()).deleteById(1L);
    }

    private Cargo cargo(CargoStatus status) {
        Cargo cargo = new Cargo();
        cargo.setId(1L);
        cargo.setStatus(status.name());
        return cargo;
    }
}
