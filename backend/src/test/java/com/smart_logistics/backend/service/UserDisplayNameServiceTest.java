package com.smart_logistics.backend.service;

import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDisplayNameServiceTest {

    @Mock private DriverMapper driverMapper;
    @Mock private OwnerMapper ownerMapper;
    @Mock private UserMapper userMapper;

    private UserDisplayNameService service;

    @BeforeEach
    void setUp() {
        service = new UserDisplayNameService(driverMapper, ownerMapper, userMapper);
    }

    @Test
    void driverNameUsesDriverToUserForeignKeyInsteadOfEqualIds() {
        Driver driver = new Driver();
        driver.setId(37L);
        driver.setUserId(8L);
        User user = user(8L, "Driver Name", "driver001");
        when(driverMapper.selectBatchIds(List.of(37L))).thenReturn(List.of(driver));
        when(userMapper.selectBatchIds(List.of(8L))).thenReturn(List.of(user));
        assertEquals(Map.of(37L, "Driver Name"), service.getDriverNames(List.of(37L)));
    }

    @Test
    void ownerNameUsesOwnerToUserForeignKeyAndFallbackOrder() {
        Owner owner = new Owner();
        owner.setId(42L);
        owner.setUserId(5L);
        owner.setContactPerson("Contact Person");
        User user = user(5L, null, "owner001");
        when(ownerMapper.selectBatchIds(List.of(42L))).thenReturn(List.of(owner));
        when(userMapper.selectBatchIds(List.of(5L))).thenReturn(List.of(user));
        assertEquals(Map.of(42L, "Contact Person"), service.getOwnerNames(List.of(42L)));
    }

    private User user(Long id, String name, String username) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setUsername(username);
        return user;
    }
}
