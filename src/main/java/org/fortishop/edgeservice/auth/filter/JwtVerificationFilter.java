package org.fortishop.edgeservice.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.auth.jwt.JwtTokenProvider;
import org.fortishop.edgeservice.global.ErrorResponse;
import org.fortishop.edgeservice.global.exception.BaseException;
import org.fortishop.edgeservice.global.redis.RedisService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtVerificationFilter extends OncePerRequestFilter {
    private static final List<String> EXCLUDED_PATHS =
            List.of("/api/members/signup", "/api/members/check-nickname",
                    "/api/members/check-email", "/api/auths/reissue", "/actuator", "/actuator/",
                    "/actuator/prometheus");
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        log.info("doFilterInternal executed");

        try {
            String accessToken = jwtTokenProvider.resolveAccessToken(request);

            if (StringUtils.hasText(accessToken) && isLogoutAccount(accessToken)) {
                throw new RuntimeException("Token is in logout state.");
            }

            if (StringUtils.hasText(accessToken) && jwtTokenProvider.validateToken(accessToken)) {
                setAuthentication(accessToken);
            }
        } catch (RuntimeException e) {
            log.info("doFilterInternal failed");
            handleException(response, e);
            return;
        }

        chain.doFilter(request, response);
    }

    private void handleException(HttpServletResponse response, Exception e) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (e instanceof BaseException) {
            ErrorResponse errorResponse = new ErrorResponse(
                    ((BaseException) e).getExceptionType().getErrorCode(),
                    ((BaseException) e).getExceptionType().getErrorMessage()
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            log.error("Unexpected error occurred in JwtVerificationFilter: ", e);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ErrorResponse errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred.");
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            log.error("Unexpected error occurred in JwtVerificationFilter: ", e);
        }
    }

    private boolean isLogoutAccount(String accessToken) {
        String isLogout = redisService.getValues(accessToken);
        return "true".equals(isLogout);
    }

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        log.info("Request URI excluded from JwtVerificationFilter");
        String path = request.getServletPath();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private void setAuthentication(String accessToken) {
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("Set Authentication Success");
    }
}
