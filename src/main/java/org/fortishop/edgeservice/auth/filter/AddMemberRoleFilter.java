package org.fortishop.edgeservice.auth.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.auth.PrincipalDetails;
import org.fortishop.edgeservice.global.MutableHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class AddMemberRoleFilter implements Filter {

    private static final List<String> EXCLUDED_PATHS =
            List.of("/api/members/signup", "/api/members/check-nickname",
                    "/api/members/check-email", "/api/auths/reissue", "/actuator", "/actuator/",
                    "/actuator/prometheus");

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();
        if (shouldNotFilter(requestURI)) {
            log.info("Request URI excluded from AddMemberIdHeaderFilter: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            PrincipalDetails principalDetails = (PrincipalDetails) authentication.getPrincipal();
            String role = principalDetails.getRoleName();
            MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(httpRequest);
            mutableRequest.addHeader("x-member-role", role);
            mutableRequest.addHeader("x-member-id", principalDetails.getId().toString());
            log.info("Member Role Set Complete :{}", role);
            log.info("Member Id Set Complete :{}", principalDetails.getId().toString());
            chain.doFilter(mutableRequest, httpResponse);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean shouldNotFilter(String requestURI) {
        return EXCLUDED_PATHS.stream().anyMatch(requestURI::startsWith);
    }
}
