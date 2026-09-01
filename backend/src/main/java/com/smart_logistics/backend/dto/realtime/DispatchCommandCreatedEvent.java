package com.smart_logistics.backend.dto.realtime;

/**
 * 调度指令创建事件（进程内Spring事件）。
 * 指令入库事务提交后由通知模块消费，生成DISPATCH_COMMAND_CREATED通知，
 * 只推给指令目标司机对应的用户账号，绝不广播给所有DRIVER。
 */
public record DispatchCommandCreatedEvent(Long commandId) {
}
