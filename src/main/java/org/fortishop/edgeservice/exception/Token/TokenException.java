package org.fortishop.edgeservice.exception.Token;

import org.fortishop.edgeservice.global.exception.BaseException;
import org.fortishop.edgeservice.global.exception.BaseExceptionType;

public class TokenException extends BaseException {
    private final BaseExceptionType exceptionType;

    public TokenException(BaseExceptionType exceptionType) {
        this.exceptionType = exceptionType;
    }

    @Override
    public BaseExceptionType getExceptionType() {
        return exceptionType;
    }
}
