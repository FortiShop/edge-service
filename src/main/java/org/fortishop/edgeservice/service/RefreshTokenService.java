package org.fortishop.edgeservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface RefreshTokenService {
    void reissueAccessToken(HttpServletRequest request, HttpServletResponse response);

    void logout(String refreshToken, String accessToken);
}
