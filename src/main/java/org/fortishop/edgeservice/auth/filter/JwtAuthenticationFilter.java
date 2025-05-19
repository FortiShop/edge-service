package org.fortishop.edgeservice.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.auth.TokenDto;
import org.fortishop.edgeservice.auth.jwt.JwtTokenProvider;
import org.fortishop.edgeservice.dto.request.LoginRequest;
import org.fortishop.edgeservice.service.RefreshTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        try {
            LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);

            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
            );

            log.info("[JwtAuthenticationFilter] Attempting login for email: {}", loginRequest.getEmail());

            return authenticationManager.authenticate(authRequest);
        } catch (IOException e) {
            throw new RuntimeException("로그인 요청 JSON 파싱 실패", e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult)
            throws IOException, ServletException {

        PrincipalDetails principal = (PrincipalDetails) authResult.getPrincipal();
        TokenDto tokenDto = jwtTokenProvider.generateTokenDto(principal);

        jwtTokenProvider.accessTokenSetHeader(tokenDto.getAccessToken(), response);
        jwtTokenProvider.setTokenCookie("refreshToken", tokenDto.getRefreshToken(), response);

        Claims claims = jwtTokenProvider.parseClaims(tokenDto.getRefreshToken());
        LocalDateTime expiresAt = claims.getExpiration().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        refreshTokenService.save(principal.getUsername(), tokenDto.getRefreshToken(), expiresAt);

        response.setStatus(HttpServletResponse.SC_OK);
        log.info("[JwtAuthenticationFilter] 로그인 성공: {}", principal.getUsername());
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException failed)
            throws IOException, ServletException {
        log.warn("[JwtAuthenticationFilter] 로그인 실패: {}", failed.getMessage());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그인 실패: " + failed.getMessage());
    }
}
