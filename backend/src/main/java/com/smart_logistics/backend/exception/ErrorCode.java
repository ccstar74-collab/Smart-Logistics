package com.smart_logistics.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_PARAMETER(40001, HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN),
    REGISTRATION_ROLE_NOT_ALLOWED(40302, HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    DATA_CONFLICT(40901, HttpStatus.CONFLICT),
    STATE_CONFLICT(40902, HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS(40903, HttpStatus.CONFLICT),
    REALTIME_PROVIDER_UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE),
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
