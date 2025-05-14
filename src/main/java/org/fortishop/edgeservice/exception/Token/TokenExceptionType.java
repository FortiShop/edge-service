package org.fortishop.edgeservice.exception.Token;


import org.fortishop.edgeservice.global.exception.BaseExceptionType;
import org.springframework.http.HttpStatus;

public enum TokenExceptionType implements BaseExceptionType {
    TOKEN_INVALID("T001", "올바르지 않은 토큰입니다.", HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED("T002", "토큰이 만료되었습니다.", HttpStatus.BAD_REQUEST),
    TOKEN_UNSUPPORTED("T003", "지원하지 않는 형식의 토큰입니다.", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_NOT_FOUND("T004", "RefreshToken이 존재하지 않습니다.", HttpStatus.BAD_REQUEST),
    TOKEN_MISMATCH("T005", "올바르지 않은 RefreshToken 입니다.", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus httpStatus;

    TokenExceptionType(String errorCode, String errorMessage, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String getErrorMessage() {
        return this.errorMessage;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
