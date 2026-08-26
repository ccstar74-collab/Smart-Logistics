package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private DriverMapper driverMapper;
    @Mock private OwnerMapper ownerMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        initTable(Driver.class, "driver-test");
        initTable(Owner.class, "owner-test");
        userService = new UserService(userMapper, driverMapper, ownerMapper);
    }

    @Test
    void driverIdentityUsesDriverUserIdRelationship() {
        User user = user(UserRole.DRIVER, UserStatus.ACTIVE);
        Driver driver = new Driver();
        driver.setId(37L);
        driver.setUserId(8L);
        when(driverMapper.selectOne(any())).thenReturn(driver);
        UserIdentityResponse response = userService.toIdentity(user);
        assertEquals(37L, response.getDriverId());
        assertNull(response.getOwnerId());
    }

    @Test
    void ownerIdentityUsesOwnerUserIdRelationship() {
        User user = user(UserRole.OWNER, UserStatus.ACTIVE);
        Owner owner = new Owner();
        owner.setId(42L);
        owner.setUserId(8L);
        when(ownerMapper.selectOne(any())).thenReturn(owner);
        UserIdentityResponse response = userService.toIdentity(user);
        assertEquals(42L, response.getOwnerId());
        assertNull(response.getDriverId());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"WAREHOUSE_MANAGER", "DISPATCHER", "ADMIN"})
    void nonDriverOwnerRolesHaveNoSecondaryIdentity(UserRole role) {
        UserIdentityResponse response = userService.toIdentity(user(role, UserStatus.ACTIVE));
        assertNull(response.getDriverId());
        assertNull(response.getOwnerId());
    }

    @Test
    void meRejectsMissingUserAsUnauthorized() {
        when(userMapper.selectById(8L)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getActiveIdentity(8L));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void meRejectsDisabledUser() {
        when(userMapper.selectById(8L)).thenReturn(user(UserRole.ADMIN, UserStatus.DISABLED));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getActiveIdentity(8L));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    private User user(UserRole role, UserStatus status) {
        User user = new User();
        user.setId(8L);
        user.setUsername("identity-user");
        user.setName("Identity User");
        user.setRole(role.name());
        user.setStatus(status.name());
        return user;
    }

    private void initTable(Class<?> type, String namespace) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), namespace), type);
    }
}
