package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_env_monitor")
public class EnvMonitorPoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String location;
}
