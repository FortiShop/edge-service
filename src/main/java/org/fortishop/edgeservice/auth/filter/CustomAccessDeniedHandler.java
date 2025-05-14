package org.fortishop.edgeservice.auth.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.global.Responder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                       AccessDeniedException e) {
        Responder.error(String.valueOf(httpServletResponse.getStatus()), e.getMessage(), HttpStatus.FORBIDDEN);
        log.warn("Forbidden Error : {}", e.getMessage());
    }
}
