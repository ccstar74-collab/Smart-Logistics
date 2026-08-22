package com.smart_logistics.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_PARAMETER(40001, HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    DATA_CONFLICT(40901, HttpStatus.CONFLICT),
    STATE_CONFLICT(40902, HttpStatus.CONFLICT),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    ErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
