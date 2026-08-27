package com.smart_logistics.backend.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.service.UserService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    /**
     * 手动从queryString解析token，应对@ServerEndpoint握手时 getParameter()拿不到参数的tomcat坑
     */
    private String extractTokenFromQueryString(HttpServletRequest request) {
        String query = request.getQueryString();
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = null;
        String authorization = request.getHeader("Authorization");

        // 1. 优先 Header Bearer
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            token = authorization.substring(BEARER_PREFIX.length()).trim();
        }

        // 2. Header为空，手动解析queryString拿token（适配WebSocket握手）
        if (!StringUtils.hasText(token)) {
            token = extractTokenFromQueryString(request);
        }

        logger.info("JwtFilter: headerAuth=[{}], queryToken=[{}]", authorization, token);

        if (StringUtils.hasText(token)) {
            try {
                Long userId = jwtService.extractUserId(token);
                UserIdentityResponse identity = userService.getActiveIdentity(userId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(identity, token, List.of(
                                new SimpleGrantedAuthority("ROLE_" + identity.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.info("JwtFilter鉴权成功 userId={}, role={}", userId, identity.getRole());
            } catch (JwtException | IllegalArgumentException | BusinessException exception) {
                SecurityContextHolder.clearContext();
                logger.warn("JwtFilter鉴权失败", exception);
            }
        }

        filterChain.doFilter(request, response);
    }
}