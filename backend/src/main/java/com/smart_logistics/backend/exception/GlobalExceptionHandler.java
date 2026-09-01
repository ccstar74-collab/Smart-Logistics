package com.smart_logistics.backend.exception;

import com.smart_logistics.backend.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return failure(errorCode, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return failure(ErrorCode.FORBIDDEN, "forbidden");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = fieldError == null ? "invalid request body" : fieldError.getDefaultMessage();
        return failure(ErrorCode.INVALID_PARAMETER, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(Exception exception) {
        return failure(ErrorCode.INVALID_PARAMETER, "invalid request parameter");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return failure(ErrorCode.INVALID_PARAMETER, "invalid request parameter or body");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return failure(ErrorCode.RESOURCE_NOT_FOUND, "resource not found");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        return failure(ErrorCode.DATA_CONFLICT, "data already exists");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        return failure(ErrorCode.DATA_CONFLICT, "data conflicts with an existing record");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return failure(ErrorCode.INTERNAL_ERROR, "database operation failed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected server error", exception);
        return failure(ErrorCode.INTERNAL_ERROR, "internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode.getCode(), message));
    }
}
