package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import com.smart_logistics.backend.enums.DispatchCommandType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCommandValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void textRequestNeedsOnlyTaskTypeAndContent() {
        DispatchCommandCreateRequest request = validRequest();
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        DispatchCommandCreateRequest request = new DispatchCommandCreateRequest();
        request.setContent(" ");
        Set<ConstraintViolation<DispatchCommandCreateRequest>> violations =
                validator.validate(request);
        assertTrue(hasMessage(violations, "taskId must not be null"));
        assertTrue(hasMessage(violations, "commandType must not be null"));
        assertTrue(hasMessage(violations, "content must not be blank"));
    }

    @Test
    void malformedRouteIdIsRejectedAtRequestBoundary() {
        DispatchCommandCreateRequest request = validRequest();
        request.setCommandType(DispatchCommandType.ROUTE_CHANGE);
        request.setRouteId("not-a-route");
        assertTrue(hasMessage(validator.validate(request), "routeId has invalid format"));
    }

    private DispatchCommandCreateRequest validRequest() {
        DispatchCommandCreateRequest request = new DispatchCommandCreateRequest();
        request.setTaskId(15L);
        request.setCommandType(DispatchCommandType.TEXT);
        request.setContent("Slow down near the congestion");
        return request;
    }

    private boolean hasMessage(
            Set<ConstraintViolation<DispatchCommandCreateRequest>> violations,
            String message) {
        return violations.stream().anyMatch(v -> message.equals(v.getMessage()));
    }
}
