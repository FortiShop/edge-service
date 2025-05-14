package org.fortishop.edgeservice.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.auth.TokenDto;
import org.fortishop.edgeservice.exception.Token.TokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("my-super-secure-very-long-jwt-secret-key-which-is-at-least-32-bytes");
        jwtProperties.setAccessTokenValidity(1000L * 60 * 30);  // 30분
        jwtProperties.setRefreshTokenValidity(1000L * 60 * 60 * 24 * 7);  // 7일

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init(); // @PostConstruct 수동 호출
    }


    @Test
    @DisplayName("AccessToken, RefreshToken 생성 및 파싱 성공")
    void generateAndParseToken_success() {
        // given
        PrincipalDetails principal = PrincipalDetails.of(1L, "test@example.com", "ROLE_USER");

        // when
        TokenDto token = jwtTokenProvider.generateTokenDto(principal);
        Claims claims = jwtTokenProvider.parseClaims(token.getAccessToken());

        // then
        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());
        assertTrue(jwtTokenProvider.validateToken(token.getAccessToken()));
        assertEquals("test@example.com", claims.getSubject());
    }

    @Test
    @DisplayName("만료된 토큰 검증 실패")
    void validateExpiredToken_fail() {
        // given: 만료된 토큰 직접 생성
        String expiredToken = Jwts.builder()
                .setSubject("expired@example.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60))
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60)) // 이미 만료
                .signWith(Keys.hmacShaKeyFor(jwtTokenProvider.encodeBase64SecretKey(
                                "my-super-secure-very-long-jwt-secret-key-which-is-at-least-32-bytes24").getBytes()),
                        SignatureAlgorithm.HS256)
                .compact();

        // then
        assertThrows(TokenException.class, () -> jwtTokenProvider.validateToken(expiredToken));
    }
}

