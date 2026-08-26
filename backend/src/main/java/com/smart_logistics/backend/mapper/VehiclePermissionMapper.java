package com.smart_logistics.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * WebSocket推送权限范围专用Mapper
 * 只负责计算"某用户允许看到哪些车辆的GPS"，不参与业务读写
 */
@Mapper
public interface VehiclePermissionMapper {

    /** 登录用户id -> driver表业务id（vehicle.driver_id引用的是driver.id，不是user.id） */
    @Select("SELECT id FROM driver WHERE user_id = #{userId}")
    Long selectDriverIdByUserId(@Param("userId") Long userId);

    /** 登录用户id -> owner表业务id（cargo.owner_id引用的是owner.id，不是user.id） */
    @Select("SELECT id FROM owner WHERE user_id = #{userId}")
    Long selectOwnerIdByUserId(@Param("userId") Long userId);

    /** 调度员：所有待运输、运输中任务关联的车辆 */
    @Select("SELECT DISTINCT vehicle_id FROM transport_task " +
            "WHERE status IN ('WAITING','TRANSPORTING') AND vehicle_id IS NOT NULL")
    List<Long> selectActiveTaskVehicleIds();

    /** 司机：本人驾驶车辆上的待运输、运输中任务车辆 */
    @Select("SELECT DISTINCT t.vehicle_id FROM transport_task t " +
            "JOIN vehicle v ON v.id = t.vehicle_id " +
            "WHERE v.driver_id = #{driverId} AND t.status IN ('WAITING','TRANSPORTING')")
    List<Long> selectActiveTaskVehicleIdsByDriverId(@Param("driverId") Long driverId);

    /** 货主：本人订单货物对应的待运输、运输中任务车辆 */
    @Select("SELECT DISTINCT t.vehicle_id FROM transport_task t " +
            "JOIN cargo c ON c.id = t.cargo_id " +
            "WHERE c.owner_id = #{ownerId} AND t.status IN ('WAITING','TRANSPORTING')")
    List<Long> selectActiveTaskVehicleIdsByOwnerId(@Param("ownerId") Long ownerId);
}
