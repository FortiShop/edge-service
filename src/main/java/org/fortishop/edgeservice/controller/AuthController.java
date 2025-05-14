package org.fortishop.edgeservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.fortishop.edgeservice.auth.jwt.JwtTokenProvider;
import org.fortishop.edgeservice.global.Responder;
import org.fortishop.edgeservice.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Repository
@RequestMapping("/api/auths")
@RequiredArgsConstructor
public class AuthController {
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @PatchMapping("/reissue")
    public ResponseEntity<String> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.resolveRefreshToken(request);
        refreshTokenService.reissueAccessToken(request, response);
        return Responder.success("토큰 재발급이 완료되었습니다.");
    }

    @PatchMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        String refreshToken = jwtTokenProvider.resolveRefreshToken(request);
        String accessToken = jwtTokenProvider.resolveAccessToken(request);
        refreshTokenService.logout(refreshToken, accessToken);
        return Responder.success("로그아웃이 완료되었습니다.");
    }
}
