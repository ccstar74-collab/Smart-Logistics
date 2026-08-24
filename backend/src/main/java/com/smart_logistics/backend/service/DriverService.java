package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.response.DriverOptionResponse;
import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DriverService {

    private final DriverMapper driverMapper;
    private final UserMapper userMapper;

    public DriverService(DriverMapper driverMapper, UserMapper userMapper) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
    }

    public List<DriverOptionResponse> listOptions() {
        List<Driver> drivers = driverMapper.selectList(
                new LambdaQueryWrapper<Driver>().orderByAsc(Driver::getId));
        if (drivers.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(drivers.stream()
                        .map(Driver::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return drivers.stream()
                .filter(driver -> isActiveDriver(users.get(driver.getUserId())))
                .map(driver -> {
                    User user = users.get(driver.getUserId());
                    return new DriverOptionResponse(driver.getId(), user.getId(),
                            displayName(user));
                })
                .toList();
    }

    private boolean isActiveDriver(User user) {
        return user != null
                && UserRole.DRIVER.name().equals(user.getRole())
                && UserStatus.ACTIVE.name().equals(user.getStatus());
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername();
    }
}
