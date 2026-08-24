package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCommandValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validCommandRequestPassesValidation() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void missingIdsAndBlankFieldsAreRejected() {
        DispatchCommandCreateRequest request = new DispatchCommandCreateRequest();
        request.setCommandType(" ");
        request.setContent(" ");

        Set<ConstraintViolation<DispatchCommandCreateRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(violations, "taskId must not be null"));
        assertTrue(hasMessage(violations, "vehicleId must not be null"));
        assertTrue(hasMessage(violations, "toUserId must not be null"));
        assertTrue(hasMessage(violations, "commandType must not be blank"));
        assertTrue(hasMessage(violations, "content must not be blank"));
    }

    @Test
    void invalidIdsAreRejected() {
        DispatchCommandCreateRequest request = validRequest();
        request.setTaskId(0L);
        request.setVehicleId(-1L);
        request.setToUserId(0L);

        Set<ConstraintViolation<DispatchCommandCreateRequest>> violations =
                validator.validate(request);

        assertEquals(3, violations.size());
        assertTrue(hasMessage(violations, "taskId must be greater than 0"));
        assertTrue(hasMessage(violations, "vehicleId must be greater than 0"));
        assertTrue(hasMessage(violations, "toUserId must be greater than 0"));
    }

    @Test
    void commandTypeMustUseFrozenWireFormat() {
        DispatchCommandCreateRequest request = validRequest();
        request.setCommandType("route-change");

        Set<ConstraintViolation<DispatchCommandCreateRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(
                violations,
                "commandType must use uppercase letters, numbers, and underscores"
        ));
    }

    @Test
    void overlongContentIsRejected() {
        DispatchCommandCreateRequest request = validRequest();
        request.setContent("x".repeat(501));

        Set<ConstraintViolation<DispatchCommandCreateRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(violations, "content must not exceed 500 characters"));
    }

    private DispatchCommandCreateRequest validRequest() {
        DispatchCommandCreateRequest request = new DispatchCommandCreateRequest();
        request.setTaskId(15L);
        request.setVehicleId(1L);
        request.setToUserId(8L);
        request.setCommandType("ROUTE_CHANGE");
        request.setContent("Switch to backup route B");
        return request;
    }

    private boolean hasMessage(
            Set<ConstraintViolation<DispatchCommandCreateRequest>> violations,
            String message) {
        return violations.stream().anyMatch(violation -> message.equals(violation.getMessage()));
    }
}
