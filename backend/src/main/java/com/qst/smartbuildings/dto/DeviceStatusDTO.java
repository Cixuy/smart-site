package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改设备启用状态请求体。
 */
@Data
public class DeviceStatusDTO {

    /** 启用状态：0-禁用，1-启用 */
    @NotNull(message = "启用状态不能为空")
    @Min(value = 0, message = "启用状态只能为0或1")
    @Max(value = 1, message = "启用状态只能为0或1")
    private Integer enableStatus;
}
