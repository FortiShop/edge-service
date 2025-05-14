package org.fortishop.edgeservice.global.util;

import jakarta.servlet.http.Cookie;

public class CookieUtil {
    
    public static Cookie createRefreshTokenCookie(String token, int maxAgeInSeconds) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS 환경만 허용할 경우
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeInSeconds);
        return cookie;
    }

    public static Cookie deleteRefreshTokenCookie() {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        return cookie;
    }
}
