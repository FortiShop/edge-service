package org.fortishop.edgeservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.fortishop.edgeservice.auth.TokenDto;
import org.fortishop.edgeservice.auth.jwt.JwtProperties;
import org.fortishop.edgeservice.auth.jwt.JwtTokenProvider;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.RefreshToken;
import org.fortishop.edgeservice.exception.Member.MemberExceptionType;
import org.fortishop.edgeservice.exception.Token.TokenException;
import org.fortishop.edgeservice.exception.Token.TokenExceptionType;
import org.fortishop.edgeservice.global.redis.RedisService;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RefreshTokenServiceImplTest {

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private RedisService redisService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void reissueAccessToken_success() {
        String refreshToken = "valid-refresh-token";
        String email = "user@a.com";

        Member member = Member.builder().id(1L).email(email).role(org.fortishop.edgeservice.domain.Role.ROLE_USER)
                .build();
        RefreshToken stored = RefreshToken.builder().id(1L).member(member).token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7)).build();

        given(jwtTokenProvider.resolveRefreshToken(request)).willReturn(refreshToken);
        given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
        Claims claims = mock(Claims.class);
        given(jwtTokenProvider.parseClaims(refreshToken)).willReturn(claims);
        given(claims.getSubject()).willReturn(email);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));
        given(refreshTokenRepository.findByMember(member)).willReturn(Optional.of(stored));

        TokenDto newToken = TokenDto.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .build();
        given(jwtTokenProvider.generateTokenDto(any())).willReturn(newToken);
        given(jwtTokenProvider.getRefreshExpirationDate()).willReturn(
                java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(7)));

        refreshTokenService.reissueAccessToken(request, response);

        verify(jwtTokenProvider).accessTokenSetHeader("new-access-token", response);
        verify(jwtTokenProvider).setTokenCookie("refreshToken", "new-refresh-token", response);
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void reissueAccessToken_fail_no_cookie() {
        given(jwtTokenProvider.resolveRefreshToken(request)).willReturn(null);

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken(request, response))
                .isInstanceOf(TokenException.class)
                .extracting("exceptionType")
                .isEqualTo(TokenExceptionType.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void reissueAccessToken_fail_invalid_token() {
        String token = "invalid-token";
        given(jwtTokenProvider.resolveRefreshToken(request)).willReturn(token);
        given(jwtTokenProvider.validateToken(token)).willReturn(false);

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken(request, response))
                .isInstanceOf(TokenException.class)
                .extracting("exceptionType")
                .isEqualTo(TokenExceptionType.TOKEN_INVALID);
    }

    @Test
    void reissueAccessToken_fail_member_not_found() {
        String token = "valid-token";
        Claims claims = mock(Claims.class);
        given(jwtTokenProvider.resolveRefreshToken(request)).willReturn(token);
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.parseClaims(token)).willReturn(claims);
        given(claims.getSubject()).willReturn("notfound@a.com");
        given(memberRepository.findByEmail("notfound@a.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken(request, response))
                .isInstanceOf(org.fortishop.edgeservice.exception.Member.MemberException.class)
                .extracting("exceptionType")
                .isEqualTo(MemberExceptionType.MEMBER_NOT_FOUND);
    }

    @Test
    void reissueAccessToken_fail_token_mismatch() {
        String token = "old-token";
        Member member = Member.builder().id(1L).email("user@a.com")
                .role(org.fortishop.edgeservice.domain.Role.ROLE_USER).build();
        RefreshToken stored = RefreshToken.builder().token("different-token").member(member).build();

        Claims claims = mock(Claims.class);
        given(jwtTokenProvider.resolveRefreshToken(request)).willReturn(token);
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.parseClaims(token)).willReturn(claims);
        given(claims.getSubject()).willReturn("user@a.com");
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(member));
        given(refreshTokenRepository.findByMember(member)).willReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken(request, response))
                .isInstanceOf(TokenException.class)
                .extracting("exceptionType")
                .isEqualTo(TokenExceptionType.TOKEN_MISMATCH);
    }

    @Test
    void logout_success() {
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        Member member = Member.builder().id(1L).email("user@a.com")
                .role(org.fortishop.edgeservice.domain.Role.ROLE_USER).build();
        RefreshToken stored = RefreshToken.builder().member(member).token(refreshToken).build();
        Claims claims = mock(Claims.class);

        given(jwtTokenProvider.parseClaims(accessToken)).willReturn(claims);
        given(claims.getSubject()).willReturn("user@a.com");
        given(memberRepository.findByEmail("user@a.com")).willReturn(Optional.of(member));
        given(refreshTokenRepository.findByMember(member)).willReturn(Optional.of(stored));
        given(jwtProperties.getAccessTokenValidity()).willReturn(1000L * 60 * 30); // 30분

        refreshTokenService.logout(refreshToken, accessToken);

        verify(refreshTokenRepository).delete(stored);
        verify(redisService).setValues(eq(accessToken), eq("logout"), any(Duration.class));
    }

    @Test
    void logout_fail_missing_token() {
        assertThatThrownBy(() -> refreshTokenService.logout(null, "access"))
                .isInstanceOf(TokenException.class);

        assertThatThrownBy(() -> refreshTokenService.logout("refresh", ""))
                .isInstanceOf(TokenException.class);
    }
}

