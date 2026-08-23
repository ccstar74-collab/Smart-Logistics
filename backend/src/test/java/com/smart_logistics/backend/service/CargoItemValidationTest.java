package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.request.CargoItemCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoItemValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidCargoItemRequest() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void rejectsBlankItemName() {
        CargoItemCreateRequest request = validRequest();
        request.setItemName("   ");

        assertViolation(request, "itemName must not be blank");
    }

    @Test
    void rejectsItemNameLongerThanDatabaseColumn() {
        CargoItemCreateRequest request = validRequest();
        request.setItemName("A".repeat(101));

        assertViolation(request, "itemName must not exceed 100 characters");
    }

    @Test
    void rejectsNullQuantity() {
        CargoItemCreateRequest request = validRequest();
        request.setQuantity(null);

        assertViolation(request, "quantity must not be null");
    }

    @Test
    void rejectsZeroQuantity() {
        CargoItemCreateRequest request = validRequest();
        request.setQuantity(0);

        assertViolation(request, "quantity must be greater than 0");
    }

    @Test
    void rejectsUnitLongerThanDatabaseColumn() {
        CargoItemCreateRequest request = validRequest();
        request.setUnit("U".repeat(21));

        assertViolation(request, "unit must not exceed 20 characters");
    }

    @Test
    void rejectsNegativeWeight() {
        CargoItemCreateRequest request = validRequest();
        request.setWeight(new BigDecimal("-0.01"));

        assertViolation(request, "weight must be greater than or equal to 0");
    }

    @Test
    void rejectsWeightOutsideDecimalColumnPrecision() {
        CargoItemCreateRequest request = validRequest();
        request.setWeight(new BigDecimal("123456789.00"));

        assertViolation(
                request,
                "weight must have at most 8 integer digits and 2 fraction digits"
        );
    }

    @Test
    void rejectsNegativeVolume() {
        CargoItemCreateRequest request = validRequest();
        request.setVolume(new BigDecimal("-0.01"));

        assertViolation(request, "volume must be greater than or equal to 0");
    }

    @Test
    void rejectsVolumeOutsideDecimalColumnScale() {
        CargoItemCreateRequest request = validRequest();
        request.setVolume(new BigDecimal("1.123"));

        assertViolation(
                request,
                "volume must have at most 8 integer digits and 2 fraction digits"
        );
    }

    private void assertViolation(CargoItemCreateRequest request, String expectedMessage) {
        Set<ConstraintViolation<CargoItemCreateRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals(expectedMessage, violations.iterator().next().getMessage());
    }

    private CargoItemCreateRequest validRequest() {
        CargoItemCreateRequest request = new CargoItemCreateRequest();
        request.setItemName("Laptop computer");
        request.setQuantity(20);
        request.setUnit("piece");
        request.setWeight(new BigDecimal("25.50"));
        request.setVolume(new BigDecimal("0.80"));
        return request;
    }
}
