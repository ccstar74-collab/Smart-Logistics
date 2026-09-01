package com.smart_logistics.backend.enums;

public enum InitialRouteDegradationReason {
    SHORT_DISTANCE_SINGLE_ROUTE(
            "起终点距离较近，未找到明显不同的备选路线，当前返回唯一可行路线。"),
    NO_DISTINCT_ALTERNATIVE(
            "道路结构较为单一，未找到明显不同的备选路线，当前返回唯一可行路线。"),
    ROUTE_PROVIDER_SINGLE_RESULT(
            "地图服务当前仅返回一条可行路线。"),
    ALTERNATIVES_FILTERED_AS_DUPLICATES(
            "其他候选路线与当前路线高度重合，已过滤重复路线。"),
    PARTIAL_ROUTE_PROVIDER_FAILURE(
            "部分候选路线规划暂时不可用，当前返回已成功生成的路线。"),
    ;

    private final String message;

    InitialRouteDegradationReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
