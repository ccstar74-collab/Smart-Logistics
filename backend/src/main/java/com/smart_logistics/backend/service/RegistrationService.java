package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.request.RegisterRequest;
import com.smart_logistics.backend.dto.response.RegisterResponse;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

@Service
public class RegistrationService {

    private static final Set<UserRole> SELF_REGISTRATION_ROLES =
            EnumSet.of(UserRole.OWNER, UserRole.DRIVER, UserRole.WAREHOUSE_MANAGER);

    private final UserMapper userMapper;
    private final OwnerMapper ownerMapper;
    private final DriverMapper driverMapper;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserMapper userMapper, OwnerMapper ownerMapper,
                               DriverMapper driverMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.ownerMapper = ownerMapper;
        this.driverMapper = driverMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        UserRole role = request.getRole();
        if (!SELF_REGISTRATION_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.REGISTRATION_ROLE_NOT_ALLOWED,
                    "role is not allowed for self-registration");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "password must not exceed 72 UTF-8 bytes");
        }

        String username = request.getUsername().trim();
        ensureUsernameAvailable(username);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName().trim());
        user.setPhone(trimToNull(request.getPhone()));
        user.setRole(role.name());
        user.setStatus(UserStatus.ACTIVE.name());
        insertUser(user);

        if (role == UserRole.OWNER) {
            createOwnerIdentity(user.getId(), request);
        } else if (role == UserRole.DRIVER) {
            createDriverIdentity(user.getId(), request);
        }
        return new RegisterResponse(user.getId(), user.getUsername(), role);
    }

    private void ensureUsernameAvailable(String username) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0) {
            throw usernameAlreadyExists(null);
        }
    }

    private void insertUser(User user) {
        try {
            if (userMapper.insert(user) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "failed to create user");
            }
        } catch (DuplicateKeyException exception) {
            throw usernameAlreadyExists(exception);
        }
    }

    private void createOwnerIdentity(Long userId, RegisterRequest request) {
        Owner owner = new Owner();
        owner.setUserId(userId);
        owner.setCompanyName(trimToNull(request.getCompanyName()));
        String contactPerson = trimToNull(request.getContactPerson());
        owner.setContactPerson(contactPerson == null ? request.getName().trim() : contactPerson);
        if (ownerMapper.insert(owner) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to create owner identity");
        }
    }

    private void createDriverIdentity(Long userId, RegisterRequest request) {
        Driver driver = new Driver();
        driver.setUserId(userId);
        driver.setLicenseNo(trimToNull(request.getLicenseNo()));
        driver.setStatus("OFFLINE");
        if (driverMapper.insert(driver) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to create driver identity");
        }
    }

    private BusinessException usernameAlreadyExists(Throwable cause) {
        BusinessException exception = new BusinessException(
                ErrorCode.USERNAME_ALREADY_EXISTS, "username already exists");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
