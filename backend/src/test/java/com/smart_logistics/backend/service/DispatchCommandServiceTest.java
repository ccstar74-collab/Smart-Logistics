package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchCommandServiceTest {

    @Mock
    private DispatchCommandMapper dispatchCommandMapper;

    private DispatchCommandService dispatchCommandService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "dispatch-command-test"),
                DispatchCommand.class
        );
        dispatchCommandService = new DispatchCommandService(dispatchCommandMapper);
    }

    @Test
    void getCommandReturnsExistingCommandWithFrozenStatusAndOffsetTime() {
        when(dispatchCommandMapper.selectById(1L)).thenReturn(command());

        DispatchCommandResponse response = dispatchCommandService.getCommand(1L);

        assertEquals(1L, response.getId());
        assertEquals(15L, response.getTaskId());
        assertEquals(1L, response.getVehicleId());
        assertEquals("ROUTE_CHANGE", response.getCommandType());
        assertEquals(DispatchCommandStatus.PENDING, response.getStatus());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getCommandThrowsNotFoundForMissingCommand() {
        when(dispatchCommandMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dispatchCommandService.getCommand(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("dispatch command not found", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCommandsReturnsPageAndAppliesAllIndependentFilters() {
        DispatchCommand command = command();
        when(dispatchCommandMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<DispatchCommand> page = invocation.getArgument(0);
                    page.setRecords(List.of(command));
                    page.setTotal(1);
                    return page;
                });

        PageResult<DispatchCommandResponse> result = dispatchCommandService.listCommands(
                2, 5, " backup ", DispatchCommandStatus.PENDING,
                15L, 1L, " ROUTE_CHANGE "
        );

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getPageSize());

        ArgumentCaptor<LambdaQueryWrapper<DispatchCommand>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(dispatchCommandMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        LambdaQueryWrapper<DispatchCommand> query = wrapperCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("content"));
        assertTrue(query.getSqlSegment().contains("status"));
        assertTrue(query.getSqlSegment().contains("task_id"));
        assertTrue(query.getSqlSegment().contains("vehicle_id"));
        assertTrue(query.getSqlSegment().contains("command_type"));
        assertTrue(query.getParamNameValuePairs().containsValue("%backup%"));
        assertTrue(query.getParamNameValuePairs().containsValue(DispatchCommandStatus.PENDING.name()));
        assertTrue(query.getParamNameValuePairs().containsValue(15L));
        assertTrue(query.getParamNameValuePairs().containsValue(1L));
        assertTrue(query.getParamNameValuePairs().containsValue("ROUTE_CHANGE"));
    }

    @Test
    void getCommandRejectsUnknownDatabaseStatus() {
        DispatchCommand command = command();
        command.setStatus("SENT");
        when(dispatchCommandMapper.selectById(1L)).thenReturn(command);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dispatchCommandService.getCommand(1L)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("invalid dispatch command status in database", exception.getMessage());
    }

    private DispatchCommand command() {
        DispatchCommand command = new DispatchCommand();
        command.setId(1L);
        command.setTaskId(15L);
        command.setVehicleId(1L);
        command.setFromUserId(7L);
        command.setToUserId(8L);
        command.setCommandType("ROUTE_CHANGE");
        command.setContent("Switch to backup route B");
        command.setStatus(DispatchCommandStatus.PENDING.name());
        command.setSentAt(LocalDateTime.of(2026, 8, 23, 10, 31));
        command.setCreatedAt(LocalDateTime.of(2026, 8, 23, 10, 30));
        return command;
    }
}
