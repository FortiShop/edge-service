package org.fortishop.edgeservice.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.fortishop.edgeservice.global.ErrorResponse;
import org.fortishop.edgeservice.global.Responder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handleEx(Exception e) {
        log.error(e.getMessage(), e);
        return Responder.error("S001", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
