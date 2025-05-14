package org.fortishop.edgeservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

public interface RefreshTokenService {
    void reissueAccessToken(HttpServletRequest request, HttpServletResponse response);

    void logout(String refreshToken, String accessToken);

    void save(String email, String refreshToken, LocalDateTime expiresAt);
}
