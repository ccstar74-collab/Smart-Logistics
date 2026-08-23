package com.smart_logistics.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart_logistics.backend.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {
}
