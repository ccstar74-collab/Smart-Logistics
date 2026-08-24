package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DispatchCommandService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final DispatchCommandMapper dispatchCommandMapper;

    public DispatchCommandService(DispatchCommandMapper dispatchCommandMapper) {
        this.dispatchCommandMapper = dispatchCommandMapper;
    }

    public PageResult<DispatchCommandResponse> listCommands(
            long page, long pageSize, String keyword, DispatchCommandStatus status,
            Long taskId, Long vehicleId, String commandType) {
        LambdaQueryWrapper<DispatchCommand> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(DispatchCommand::getContent, keyword.trim());
        }
        if (status != null) {
            query.eq(DispatchCommand::getStatus, status.name());
        }
        if (taskId != null) {
            query.eq(DispatchCommand::getTaskId, taskId);
        }
        if (vehicleId != null) {
            query.eq(DispatchCommand::getVehicleId, vehicleId);
        }
        if (StringUtils.hasText(commandType)) {
            query.eq(DispatchCommand::getCommandType, commandType.trim());
        }
        query.orderByDesc(DispatchCommand::getCreatedAt)
                .orderByDesc(DispatchCommand::getId);

        Page<DispatchCommand> entityPage = dispatchCommandMapper.selectPage(
                new Page<>(page, pageSize), query
        );
        List<DispatchCommandResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    public DispatchCommandResponse getCommand(Long id) {
        DispatchCommand command = dispatchCommandMapper.selectById(id);
        if (command == null) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "dispatch command not found"
            );
        }
        return toResponse(command);
    }

    private DispatchCommandResponse toResponse(DispatchCommand command) {
        return new DispatchCommandResponse(
                command.getId(),
                command.getTaskId(),
                command.getVehicleId(),
                command.getFromUserId(),
                command.getToUserId(),
                command.getCommandType(),
                command.getContent(),
                parseStatus(command.getStatus()),
                toOffsetDateTime(command.getSentAt()),
                toOffsetDateTime(command.getExecutedAt()),
                toOffsetDateTime(command.getCreatedAt())
        );
    }

    private DispatchCommandStatus parseStatus(String status) {
        try {
            return DispatchCommandStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "invalid dispatch command status in database"
            );
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
