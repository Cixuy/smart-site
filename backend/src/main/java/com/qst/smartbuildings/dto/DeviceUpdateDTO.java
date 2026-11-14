package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 修改设备请求体。
 * 仅 id 必填，其余字段为空时不修改。
 */
@Data
public class DeviceUpdateDTO {

    /** 设备ID（必填） */
    @NotNull(message = "设备ID不能为空")
    private Long id;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型ID */
    private Long typeId;

    /** 安装位置ID */
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

    /** 合格证附件地址 */
    private String certificateUrl;
}
