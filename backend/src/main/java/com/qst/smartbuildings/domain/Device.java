package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账实体，对应表 asset_device。
 * 记录每台设备从生产到报废的全生命周期信息。
 */
@Data
@TableName("asset_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编码（系统生成，唯一） */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型ID -> asset_device_type(id) */
    private Long typeId;

    /** 安装位置ID -> asset_device_location(id) */
    private Long locationId;

    /** 品牌 */
    private String brand;

    /** 型号 */
    private String model;

    /** 生产日期 */
    private LocalDate productionDate;

    /** 供货日期 */
    private LocalDate supplyDate;

    /** 验收日期 */
    private LocalDate acceptanceDate;

    /** 安装日期 */
    private LocalDate installDate;

    /** 启用日期 */
    private LocalDate enableDate;

    /** 预计报废日期 */
    private LocalDate expectedScrapDate;

    /** 实际报废日期 */
    private LocalDate actualScrapDate;

    /** 在线状态：0-离线，1-在线 */
    private Integer onlineStatus;

    /** 启用状态：0-禁用，1-启用 */
    private Integer enableStatus;

    /** 二维码图片地址 */
    private String qrCodeUrl;

    /** 合格证附件地址 */
    private String certificateUrl;

    /** 最后在线时间 */
    private LocalDateTime lastOnlineTime;

    /** 逻辑删除：0-未删，1-删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
