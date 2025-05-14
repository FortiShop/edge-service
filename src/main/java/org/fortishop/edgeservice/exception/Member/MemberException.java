package org.fortishop.edgeservice.exception.Member;


import org.fortishop.edgeservice.global.exception.BaseException;
import org.fortishop.edgeservice.global.exception.BaseExceptionType;

public class MemberException extends BaseException {
    private final BaseExceptionType exceptionType;

    public MemberException(BaseExceptionType exceptionType) {
        this.exceptionType = exceptionType;
    }

    @Override
    public BaseExceptionType getExceptionType() {
        return exceptionType;
    }
}
