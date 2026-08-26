package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.response.DriverOptionResponse;
import com.smart_logistics.backend.dto.response.OwnerOptionResponse;
import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionServiceTest {

    @Mock private DriverMapper driverMapper;
    @Mock private OwnerMapper ownerMapper;
    @Mock private UserMapper userMapper;

    private DriverService driverService;
    private OwnerService ownerService;

    @BeforeEach
    void setUp() {
        initTable(Driver.class, "driver-options-test");
        initTable(Owner.class, "owner-options-test");
        driverService = new DriverService(driverMapper, userMapper);
        ownerService = new OwnerService(ownerMapper, userMapper);
    }

    @Test
    void driverOptionsReturnOnlyActiveDriverUsersAndFallbackToUsername() {
        Driver active = driver(3L, 8L);
        Driver disabled = driver(4L, 9L);
        Driver wrongRole = driver(5L, 10L);
        Driver missingUser = driver(6L, 11L);
        when(driverMapper.selectList(any())).thenReturn(
                List.of(active, disabled, wrongRole, missingUser));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(8L, UserRole.DRIVER, UserStatus.ACTIVE, null, "driver001"),
                user(9L, UserRole.DRIVER, UserStatus.DISABLED, "Disabled", "driver002"),
                user(10L, UserRole.ADMIN, UserStatus.ACTIVE, "Admin", "admin001")));

        List<DriverOptionResponse> options = driverService.listOptions();

        assertEquals(1, options.size());
        assertEquals(3L, options.getFirst().getDriverId());
        assertEquals(8L, options.getFirst().getUserId());
        assertEquals("driver001", options.getFirst().getName());
    }

    @Test
    void ownerOptionsReturnOnlyActiveOwnersWithNameAndCompany() {
        Owner active = owner(2L, 5L, "Acme Logistics", "Fallback Contact");
        Owner disabled = owner(3L, 6L, "Disabled Co", "Disabled Contact");
        Owner wrongRole = owner(4L, 7L, "Wrong Co", "Wrong Contact");
        when(ownerMapper.selectList(any())).thenReturn(List.of(active, disabled, wrongRole));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(5L, UserRole.OWNER, UserStatus.ACTIVE, "Owner Name", "owner001"),
                user(6L, UserRole.OWNER, UserStatus.DISABLED, "Disabled", "owner002"),
                user(7L, UserRole.DISPATCHER, UserStatus.ACTIVE, "Dispatch", "dispatch001")));

        List<OwnerOptionResponse> options = ownerService.listOptions();

        assertEquals(1, options.size());
        assertEquals(2L, options.getFirst().getOwnerId());
        assertEquals(5L, options.getFirst().getUserId());
        assertEquals("Owner Name", options.getFirst().getName());
        assertEquals("Acme Logistics", options.getFirst().getCompanyName());
    }

    @Test
    void ownerNameFallsBackToContactThenUsername() {
        Owner contactOwner = owner(2L, 5L, "A", "Contact Person");
        Owner usernameOwner = owner(3L, 6L, "B", " ");
        when(ownerMapper.selectList(any())).thenReturn(List.of(contactOwner, usernameOwner));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(5L, UserRole.OWNER, UserStatus.ACTIVE, null, "owner001"),
                user(6L, UserRole.OWNER, UserStatus.ACTIVE, null, "owner002")));
        List<OwnerOptionResponse> options = ownerService.listOptions();
        assertEquals("Contact Person", options.get(0).getName());
        assertEquals("owner002", options.get(1).getName());
    }

    @Test
    void activeDriverBindingRequiresDriverRoleAndActiveUser() {
        Driver driver = driver(3L, 8L);
        when(driverMapper.selectById(3L)).thenReturn(driver);
        when(userMapper.selectById(8L)).thenReturn(
                user(8L, UserRole.DRIVER, UserStatus.ACTIVE, "Driver", "driver001"));

        assertEquals(driver, driverService.requireActiveDriver(3L));
    }

    @Test
    void disabledDriverCannotBeBound() {
        when(driverMapper.selectById(3L)).thenReturn(driver(3L, 8L));
        when(userMapper.selectById(8L)).thenReturn(
                user(8L, UserRole.DRIVER, UserStatus.DISABLED, "Driver", "driver001"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> driverService.requireActiveDriver(3L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    private Driver driver(Long id, Long userId) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setUserId(userId);
        return driver;
    }

    private Owner owner(Long id, Long userId, String company, String contact) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setUserId(userId);
        owner.setCompanyName(company);
        owner.setContactPerson(contact);
        return owner;
    }

    private User user(Long id, UserRole role, UserStatus status,
                      String name, String username) {
        User user = new User();
        user.setId(id);
        user.setRole(role.name());
        user.setStatus(status.name());
        user.setName(name);
        user.setUsername(username);
        return user;
    }

    private void initTable(Class<?> type, String namespace) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), namespace), type);
    }
}
