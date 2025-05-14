package org.fortishop.edgeservice.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Calendar;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.auth.TokenDto;
import org.fortishop.edgeservice.exception.Token.TokenException;
import org.fortishop.edgeservice.exception.Token.TokenExceptionType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    private static final String BEARER = "Bearer";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private Key key;

    @PostConstruct
    public void init() {
        String base64EncodedSecretKey = encodeBase64SecretKey(jwtProperties.getSecret());
        this.key = getKeyFromBase64EncodeKey(base64EncodedSecretKey);
    }

    public String encodeBase64SecretKey(String secretKey) {
        return Encoders.BASE64.encode(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    private Key getKeyFromBase64EncodeKey(String base64EncodedSecretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(base64EncodedSecretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenDto generateTokenDto(PrincipalDetails principalDetails) {
        log.info("GenerateTokenDto execute principalDetails : {} {}",
                principalDetails.getUsername(), principalDetails.getAuthorities());
        log.info("expiration period : {}", jwtProperties.getAccessTokenValidity());
        Date accessTokenExpirationDate = getTokenExpiration(jwtProperties.getAccessTokenValidity());
        Date refreshTokenExpirationDate = getTokenExpiration(jwtProperties.getRefreshTokenValidity());
        String role = principalDetails.getAuthorities().iterator().next().getAuthority();
        Long memberId = principalDetails.getId();

        String accessToken = Jwts.builder()
                .claim("memberId", memberId)
                .claim("role", role)
                .setSubject(principalDetails.getUsername())
                .setExpiration(accessTokenExpirationDate)
                .setIssuedAt(Calendar.getInstance().getTime())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(principalDetails.getUsername())
                .setExpiration(refreshTokenExpirationDate)
                .setIssuedAt(Calendar.getInstance().getTime())
                .signWith(key)
                .compact();

        return TokenDto.builder()
                .grantType(BEARER)
                .authorizationType(AUTHORIZATION_HEADER)
                .accessToken(accessToken)
                .accessTokenExpiresIn(accessTokenExpirationDate.getTime())
                .refreshToken(refreshToken)
                .build();
    }

    public Authentication getAuthentication(String accessToken) {
        log.info("getAuthentication execute accessToken: {}", accessToken);
        Claims claims = parseClaims(accessToken);

        if (claims.get("role") == null || claims.get("memberId") == null) {
            log.debug("getAuthentication exception execute : no role or id in accessToken : {}", accessToken);
            throw new BadCredentialsException("Invalid access token");
        }

        Integer memberIdNumber = (Integer) claims.get("memberId");
        Long memberId = memberIdNumber.longValue();
        String authority = claims.get("role").toString();
        PrincipalDetails principalDetails = PrincipalDetails.of(
                memberId, claims.getSubject(), authority);
        log.info("getAuthentication Role check : {}",
                principalDetails.getAuthorities().iterator().next().getAuthority());
        return new UsernamePasswordAuthenticationToken(principalDetails, null, principalDetails.getAuthorities());
    }

    public boolean validateToken(String token) {
        log.info("ValidateToken execute, token = {}", token);
        try {
            Jwts.parserBuilder()
                    .setSigningKey(this.key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            log.info("Invalid JWT token");
            log.trace("Invalid JWT token trace = { }", e);
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token");
            log.info("Expired JWT token trace = { }", e);
            throw new TokenException(TokenExceptionType.TOKEN_EXPIRED);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token");
            log.trace("Unsupported JWT token trace = { }", e);
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty.");
            log.trace("JWT claims string is empty trace = { }", e);
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        } catch (SignatureException e) {
            log.info("Invalid JWT signature");
            log.trace("JWT claims string is empty trace = { }", e);
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        }
    }

    private Date getTokenExpiration(long expirationPeriod) {
        Date date = new Date();
        return new Date(date.getTime() + expirationPeriod);
    }

    public Date getRefreshExpirationDate() {
        return getTokenExpiration(jwtProperties.getRefreshTokenValidity());
    }

    public Date getAccessExpirationDate() {
        return getTokenExpiration(jwtProperties.getAccessTokenValidity());
    }


    public Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public void accessTokenSetHeader(String accessToken, HttpServletResponse response) {
        String headerValue = BEARER_PREFIX + accessToken;
        response.setHeader(AUTHORIZATION_HEADER, headerValue);
    }

    public String resolveAccessToken(HttpServletRequest request) {
        log.info("ResolveAccessToken execute request = {}", request.toString());
        String accessToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(accessToken) && accessToken.startsWith(BEARER_PREFIX)) {
            return accessToken.substring(7);
        }
        return null;
    }

    public String resolveRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public void setTokenCookie(String tokenName, String tokenValue, HttpServletResponse response) {
        Cookie cookie = new Cookie(tokenName, tokenValue);
        cookie.setHttpOnly(true);
//        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(14 * 24 * 60 * 60);

        response.addCookie(cookie);

        String cookieHeader = String.format("%s=%s; Max-Age=%d; Path=%s; HttpOnly; SameSite=None",
                cookie.getName(),
                cookie.getValue(),
                cookie.getMaxAge(),
                cookie.getPath());
        response.addHeader("Set-Cookie", cookieHeader);
    }
}
