package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_device_offline_log")
public class OfflineLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备 ID */
    private Long deviceId;

    /** 设备名称（入库时未冗余存储，按 device_id 关联 asset_device 获取） */
    @TableField(exist = false)
    private String deviceName;

    /** 设备编码（同上，关联 asset_device 获取） */
    @TableField(exist = false)
    private String deviceCode;

    /** 离线开始时间 */
    private LocalDateTime offlineTime;

    /** 恢复上线时间（NULL 表示当前仍处于离线状态） */
    private LocalDateTime onlineTime;

    /** 离线持续时长（秒，BIGINT） */
    private Long durationSeconds;

    /** 触发原因：主动断开 / 网络异常 / 设备故障 / 未知 */
    private String reason;

    /** 处理人（手动断开时记录） */
    private String handler;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}
