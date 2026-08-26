package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.LoginRequest;
import com.smart_logistics.backend.dto.response.LoginResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.getUsername().trim());
        if (user == null || !passwordMatches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "invalid username or password");
        }
        userService.requireActive(user);
        UserIdentityResponse identity = userService.toIdentity(user);
        UserRole role = identity.getRole();
        String token = jwtService.generateToken(user.getId(), user.getUsername(), role);
        return new LoginResponse(token, jwtService.getExpiresSeconds(), identity);
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
