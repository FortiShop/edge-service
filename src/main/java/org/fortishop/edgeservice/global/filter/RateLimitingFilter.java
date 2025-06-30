package org.fortishop.edgeservice.global.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.fortishop.edgeservice.auth.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    private static final int CAPACITY = 5;          // 최대 버킷 크기
    private static final int REFILL_RATE = 5;      // 초당 토큰 생성 수

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = resolveRateLimitKey(request);
        String redisKey = "tokenbucket:" + key;
        long now = System.currentTimeMillis();

        String luaScript = """
                    local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
                    local tokens = tonumber(bucket[1])
                    local last_refill = tonumber(bucket[2])
                    local now = tonumber(ARGV[1])
                    local capacity = tonumber(ARGV[2])
                    local refill_rate = tonumber(ARGV[3])

                    if tokens == nil then
                        tokens = capacity
                        last_refill = now
                    end

                    local elapsed = math.max(0, now - last_refill)
                    local refill = math.floor(elapsed / 1000 * refill_rate)
                    tokens = math.min(capacity, tokens + refill)

                    if tokens < 1 then
                        return 0
                    else
                        tokens = tokens - 1
                        redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill', now)
                        return 1
                    end
                """;

        Long allowed = redisTemplate.execute(
                RedisScript.of(luaScript, Long.class),
                Collections.singletonList(redisKey),
                String.valueOf(now), String.valueOf(CAPACITY), String.valueOf(REFILL_RATE)
        );

        if (allowed == null || allowed == 0L) {
            response.setStatus(429);
            response.getWriter().write("Too Many Requests");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            String accessToken = token.substring(7);
            String memberId = extractMemberId(accessToken);
            if (memberId != null) {
                return "member:" + memberId;
            }
        }

        String ip = request.getRemoteAddr();
        return "guest:" + (ip != null ? ip : "unknown");
    }

    private String extractMemberId(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("memberId", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
