package org.fortishop.edgeservice.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.auth.jwt.JwtProperties;
import org.fortishop.edgeservice.auth.jwt.JwtTokenProvider;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.RefreshToken;
import org.fortishop.edgeservice.exception.Member.MemberException;
import org.fortishop.edgeservice.exception.Member.MemberExceptionType;
import org.fortishop.edgeservice.exception.Token.TokenException;
import org.fortishop.edgeservice.exception.Token.TokenExceptionType;
import org.fortishop.edgeservice.global.redis.RedisService;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final RedisService redisService;

    @Override
    @Transactional
    public void reissueAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.resolveRefreshToken(request);
        if (refreshToken == null) {
            throw new TokenException(TokenExceptionType.REFRESH_TOKEN_NOT_FOUND);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        }

        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        String email = claims.getSubject();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));

        RefreshToken stored = refreshTokenRepository.findByMember(member)
                .orElseThrow(() -> new TokenException(TokenExceptionType.REFRESH_TOKEN_NOT_FOUND));

        if (!stored.getToken().equals(refreshToken)) {
            throw new TokenException(TokenExceptionType.TOKEN_MISMATCH);
        }

        var principalDetails = PrincipalDetails.of(member.getId(), member.getEmail(), member.getRole().name());
        var newToken = jwtTokenProvider.generateTokenDto(principalDetails);

        stored.updateToken(
                newToken.getRefreshToken(),
                jwtTokenProvider.getRefreshExpirationDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );

        refreshTokenRepository.save(stored);

        jwtTokenProvider.accessTokenSetHeader(newToken.getAccessToken(), response);
        jwtTokenProvider.setTokenCookie("refreshToken", newToken.getRefreshToken(), response);

        log.info("[RefreshTokenService] access token 재발급 완료: {}", member.getEmail());
    }

    @Override
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            throw new TokenException(TokenExceptionType.TOKEN_INVALID);
        }

        Claims claims = jwtTokenProvider.parseClaims(accessToken);
        String email = claims.getSubject();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));

        RefreshToken stored = refreshTokenRepository.findByMember(member)
                .orElseThrow(() -> new TokenException(TokenExceptionType.REFRESH_TOKEN_NOT_FOUND));

        refreshTokenRepository.delete(stored);

        long accessTokenExpirationMillis = jwtProperties.getAccessTokenValidity();
        redisService.setValues(accessToken, "logout", Duration.ofMillis(accessTokenExpirationMillis));

        log.info("[Logout] {} 로그아웃 성공. RefreshToken 삭제, AccessToken 블랙리스트 등록", email);
    }

    @Override
    @Transactional
    public void save(String email, String refreshToken, LocalDateTime expiresAt) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MemberExceptionType.MEMBER_NOT_FOUND));
        refreshTokenRepository.findByMember(member)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateToken(refreshToken, expiresAt);
                            refreshTokenRepository.save(existing);
                        },
                        () -> {
                            RefreshToken token = RefreshToken.builder()
                                    .member(member)
                                    .token(refreshToken)
                                    .expiresAt(expiresAt)
                                    .build();
                            refreshTokenRepository.save(token);
                        }
                );
        
        log.info("[RefreshTokenService] refresh token 저장 완료: {}", member.getEmail());
    }
}
