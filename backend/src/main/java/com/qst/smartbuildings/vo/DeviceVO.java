package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账视图对象，对应设备列表页。字段与前端 DeviceVO 数据模型一致。
 */
@Data
public class DeviceVO {

    private Long id;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型ID */
    private Long typeId;

    /** 设备类型名称 */
    private String typeName;

    /** 安装位置ID */
    private Long locationId;

    /** 安装位置名称 */
    private String locationName;

    /** 品牌 */
    private String brand;

    /** 型号 */
    private String model;

    /** 生产日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    /** 供货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate supplyDate;

    /** 验收日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate acceptanceDate;

    /** 安装日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installDate;

    /** 启用日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate enableDate;

    /** 预计报废日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedScrapDate;

    /** 实际报废日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualScrapDate;

    /** 在线状态: 0-离线,1-在线 */
    private Integer onlineStatus;

    /** 启用状态: 0-禁用,1-启用 */
    private Integer enableStatus;

    /** 二维码图片地址 */
    private String qrCodeUrl;

    /** 合格证附件地址 */
    private String certificateUrl;

    /** 最后在线时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOnlineTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 关联的监测点列表 */
    private List<DeviceSensorVO> sensors;
}
