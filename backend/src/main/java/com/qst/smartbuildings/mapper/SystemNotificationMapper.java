package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.SystemNotification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统通知数据访问层
 */
@Mapper
public interface SystemNotificationMapper extends BaseMapper<SystemNotification> {
}
