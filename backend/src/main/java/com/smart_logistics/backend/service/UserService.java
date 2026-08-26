package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final DriverMapper driverMapper;
    private final OwnerMapper ownerMapper;

    public UserService(UserMapper userMapper, DriverMapper driverMapper, OwnerMapper ownerMapper) {
        this.userMapper = userMapper;
        this.driverMapper = driverMapper;
        this.ownerMapper = ownerMapper;
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    public UserIdentityResponse getActiveIdentity(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "user no longer exists");
        }
        requireActive(user);
        return toIdentity(user);
    }

    public void requireActive(User user) {
        if (parseStatus(user.getStatus()) != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "account is disabled");
        }
    }

    public UserIdentityResponse toIdentity(User user) {
        UserRole role = parseRole(user.getRole());
        UserStatus status = parseStatus(user.getStatus());
        Long driverId = null;
        Long ownerId = null;
        if (role == UserRole.DRIVER) {
            Driver driver = driverMapper.selectOne(new LambdaQueryWrapper<Driver>()
                    .eq(Driver::getUserId, user.getId()));
            driverId = driver == null ? null : driver.getId();
        } else if (role == UserRole.OWNER) {
            Owner owner = ownerMapper.selectOne(new LambdaQueryWrapper<Owner>()
                    .eq(Owner::getUserId, user.getId()));
            ownerId = owner == null ? null : owner.getId();
        }
        return new UserIdentityResponse(
                user.getId(), user.getUsername(), user.getName(), user.getPhone(),
                role, status, driverId, ownerId);
    }

    public UserRole parseRole(String role) {
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid user role in database");
        }
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid user status in database");
        }
    }
}
