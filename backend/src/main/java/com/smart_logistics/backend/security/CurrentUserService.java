package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public UserIdentityResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserIdentityResponse identity)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "unauthorized");
        }
        return identity;
    }
}
