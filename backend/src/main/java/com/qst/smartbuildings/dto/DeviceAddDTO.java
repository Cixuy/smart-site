package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增设备请求体。
 * 字段与 {@link com.qst.smartbuildings.domain.Device} 对应，device_code 由系统自动生成，无需前端传入。
 */
@Data
@Schema(description = "新增设备请求")
public class DeviceAddDTO {

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50, message = "设备名称长度不能超过50")
    private String deviceName;

    @Schema(description = "设备类型ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备类型不能为空")
    private Long typeId;

    @Schema(description = "安装位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "安装位置不能为空")
    private Long locationId;

    @Schema(description = "品牌", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "品牌不能为空")
    @Size(max = 50, message = "品牌长度不能超过50")
    private String brand;

    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "型号不能为空")
    @Size(max = 50, message = "型号长度不能超过50")
    private String model;

    @Schema(description = "生产日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生产日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    @Schema(description = "供货日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate supplyDate;

    @Schema(description = "验收日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate acceptanceDate;

    @Schema(description = "安装日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installDate;

    @Schema(description = "启用日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate enableDate;

    @Schema(description = "预计报废日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedScrapDate;

    @Schema(description = "合格证附件地址")
    @Size(max = 255, message = "合格证附件地址长度不能超过255")
    private String certificateUrl;
}
