package org.fortishop.edgeservice.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.global.ErrorResponse;
import org.fortishop.edgeservice.global.Responder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseEx(BaseException exception) {
        String errorCode = exception.getExceptionType().getErrorCode();
        String errorMessage = exception.getExceptionType().getErrorMessage();
        log.error("BaseException errorCode() : {}", errorCode);
        log.error("BaseException errorMessage() : {}", errorMessage);
        return Responder.error(errorCode, errorMessage, exception.getExceptionType().getHttpStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        return Responder.error("403", "접근이 거부되었습니다.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException e) {
        log.warn("AuthorizationDeniedException: {}", e.getMessage());
        return Responder.error("403", "접근이 거부되었습니다.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return Responder.error("400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEx(Exception e) {
        log.error("Unhandled Exception: {}", e.getMessage(), e);
        return Responder.error("S001", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
