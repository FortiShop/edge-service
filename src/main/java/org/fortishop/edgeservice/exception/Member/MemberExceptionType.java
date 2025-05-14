package org.fortishop.edgeservice.exception.Member;

import org.fortishop.edgeservice.global.exception.BaseExceptionType;
import org.springframework.http.HttpStatus;

public enum MemberExceptionType implements BaseExceptionType {
    ALREADY_EXIST_EMAIL("M001", "이미 존재하는 이메일입니다.", HttpStatus.CONFLICT),
    ALREADY_EXIST_NICKNAME("M002", "이미 존재하는 닉네임입니다.", HttpStatus.CONFLICT),
    MEMBER_NOT_FOUND("M003", "일치하는 회원이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    WRONG_ROLE("M004", "잘못된 역할 권한입니다.", HttpStatus.BAD_REQUEST),
    NOT_MATCH_NICKNAME("M005", "기존 닉네임과 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD("M006", "기존 패스워드가 일치하지 않습니다", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus httpStatus;

    MemberExceptionType(String errorCode, String errorMessage, HttpStatus httpStatus) {
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
