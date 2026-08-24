package com.smart_logistics.backend.contract;

import com.smart_logistics.backend.controller.AlarmController;
import com.smart_logistics.backend.controller.CargoController;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase0ContractTest {

    @Test
    void userStatusIsFrozenToActiveAndDisabled() {
        assertArrayEquals(new UserStatus[]{UserStatus.ACTIVE, UserStatus.DISABLED},
                UserStatus.values());
    }

    @Test
    void userRoleIncludesWarehouseAndOnlyFrozenRoles() {
        assertArrayEquals(new UserRole[]{UserRole.OWNER, UserRole.DRIVER, UserRole.WAREHOUSE_MANAGER,
                UserRole.DISPATCHER, UserRole.ADMIN}, UserRole.values());
    }

    @Test
    void alarmListParameterRemainsAlarmType() {
        Method listMethod = Arrays.stream(AlarmController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("listAlarms"))
                .findFirst().orElseThrow();
        assertTrue(Arrays.stream(listMethod.getParameters())
                .anyMatch(parameter -> parameter.getName().equals("alarmType")));
        assertFalse(Arrays.stream(listMethod.getParameters())
                .anyMatch(parameter -> parameter.getName().equals("type")));
    }

    @Test
    void dispatchStatusIsNotExpanded() {
        assertArrayEquals(new DispatchCommandStatus[]{DispatchCommandStatus.PENDING,
                DispatchCommandStatus.EXECUTED, DispatchCommandStatus.CANCELLED,
                DispatchCommandStatus.FAILED}, DispatchCommandStatus.values());
    }

    @Test
    void cargoControllerHasNoDirectStatusPost() {
        List<String> postPaths = Arrays.stream(CargoController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .toList();
        assertFalse(postPaths.stream().anyMatch(path -> path.contains("status")));
    }

    @Test
    void bindingsModuleIsAbsent() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.smart_logistics.backend.controller.BindingController"));
    }
}
