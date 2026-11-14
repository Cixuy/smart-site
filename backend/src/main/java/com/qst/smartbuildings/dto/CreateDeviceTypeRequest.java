package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties({"name", "code", "description", "createTime"})
public class CreateDeviceTypeRequest {
    @Null(message = "新增设备类型时ID必须为空")
    private Long id;

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 50, message = "类型名称不能超过50个字符")
    private String typeName;

    @NotBlank(message = "类型编码不能为空")
    @Size(max = 50, message = "类型编码不能超过50个字符")
    private String typeCode;

    @Min(value = 0, message = "父节点ID不能小于0")
    private Long parentId = 0L;

    @Min(value = 1, message = "层级必须在1到4之间")
    @Max(value = 4, message = "层级必须在1到4之间")
    private Integer level;

    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort = 0;

    @Min(value = 1, message = "新增设备类型时状态必须为1")
    @Max(value = 1, message = "新增设备类型时状态必须为1")
    private Integer status = 1;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    private List<?> children;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("不支持的字段：" + fieldName);
    }
}
