package com.smart_logistics.backend.service;

import com.smart_logistics.backend.config.JwtProperties;
import com.smart_logistics.backend.dto.request.LoginRequest;
import com.smart_logistics.backend.dto.request.RegisterRequest;
import com.smart_logistics.backend.dto.response.RegisterResponse;
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
import com.smart_logistics.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private OwnerMapper ownerMapper;
    @Mock private DriverMapper driverMapper;

    private BCryptPasswordEncoder passwordEncoder;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        registrationService = new RegistrationService(
                userMapper, ownerMapper, driverMapper, passwordEncoder);
        lenient().when(userMapper.selectCount(any())).thenReturn(0L);
        lenient().when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });
    }

    @Test
    void ownerRegistrationCreatesActiveUserAndOwnerWithBcryptPassword() {
        when(ownerMapper.insert(any(Owner.class))).thenReturn(1);

        RegisterResponse response = registrationService.register(
                request(UserRole.OWNER, "owner001"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(userMapper).insert(userCaptor.capture());
        verify(ownerMapper).insert(ownerCaptor.capture());
        User user = userCaptor.getValue();
        Owner owner = ownerCaptor.getValue();
        assertEquals(UserRole.OWNER.name(), user.getRole());
        assertEquals(UserStatus.ACTIVE.name(), user.getStatus());
        assertFalse("correct-password".equals(user.getPassword()));
        assertTrue(passwordEncoder.matches("correct-password", user.getPassword()));
        assertEquals(10L, owner.getUserId());
        assertEquals("Test User", owner.getContactPerson());
        assertEquals(10L, response.getUserId());
        assertEquals(UserRole.OWNER, response.getRole());
    }

    @Test
    void driverRegistrationCreatesDriverAndLeavesNullableLicenseNull() {
        when(driverMapper.insert(any(Driver.class))).thenReturn(1);

        registrationService.register(request(UserRole.DRIVER, "driver001"));

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertNull(captor.getValue().getLicenseNo());
        assertEquals("OFFLINE", captor.getValue().getStatus());
    }

    @Test
    void warehouseManagerRegistrationCreatesOnlyUserIdentity() {
        RegisterResponse response = registrationService.register(
                request(UserRole.WAREHOUSE_MANAGER, "warehouse001"));

        assertEquals(UserRole.WAREHOUSE_MANAGER, response.getRole());
        verify(ownerMapper, never()).insert(any(Owner.class));
        verify(driverMapper, never()).insert(any(Driver.class));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "DISPATCHER"})
    void internalRolesCannotSelfRegister(UserRole role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> registrationService.register(request(role, "internal001")));
        assertEquals(ErrorCode.REGISTRATION_ROLE_NOT_ALLOWED, exception.getErrorCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void existingUsernameReturnsSpecificError() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registrationService.register(request(UserRole.WAREHOUSE_MANAGER, "used")));

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals("username already exists", exception.getMessage());
    }

    @Test
    void concurrentDuplicateUsernameReturnsSpecificError() {
        when(userMapper.insert(any(User.class)))
                .thenThrow(new DuplicateKeyException("duplicate username"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registrationService.register(request(UserRole.WAREHOUSE_MANAGER, "raced")));

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void ownerIdentityFailurePropagatesWithinTransaction() {
        when(ownerMapper.insert(any(Owner.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registrationService.register(request(UserRole.OWNER, "owner001")));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void driverIdentityFailurePropagatesWithinTransaction() {
        when(driverMapper.insert(any(Driver.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registrationService.register(request(UserRole.DRIVER, "driver001")));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void registrationUsesSpringTransactionBoundaryForIdentityRollback() throws Exception {
        Transactional annotation = RegistrationService.class
                .getMethod("register", RegisterRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "DRIVER", "WAREHOUSE_MANAGER"})
    void newlyRegisteredPublicRoleCanSubsequentlyLogin(UserRole role) {
        lenient().when(ownerMapper.insert(any(Owner.class))).thenReturn(1);
        lenient().when(driverMapper.insert(any(Driver.class))).thenReturn(1);
        registrationService.register(request(role, role.name().toLowerCase()));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User stored = captor.getValue();
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        when(userService.findByUsername(stored.getUsername())).thenReturn(stored);
        when(userService.toIdentity(stored)).thenReturn(new UserIdentityResponse(
                stored.getId(), stored.getUsername(), stored.getName(), stored.getPhone(),
                role, UserStatus.ACTIVE,
                role == UserRole.DRIVER ? 20L : null,
                role == UserRole.OWNER ? 30L : null));
        JwtProperties properties = new JwtProperties();
        properties.setSecret("registration-test-secret-at-least-32-bytes-long");
        properties.setExpiresSeconds(3600);
        AuthService authService = new AuthService(userService, passwordEncoder,
                new JwtService(properties));
        LoginRequest login = new LoginRequest();
        login.setUsername(stored.getUsername());
        login.setPassword("correct-password");

        assertNotNull(authService.login(login).getAccessToken());
    }

    private RegisterRequest request(UserRole role, String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("correct-password");
        request.setName("Test User");
        request.setPhone("13800000000");
        request.setRole(role);
        return request;
    }
}
