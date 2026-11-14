package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties({"name", "code", "description", "createTime"})
public class UpdateDeviceTypeRequest {
    @NotNull(message = "类型ID不能为空")
    private Long id;

    @Size(max = 50, message = "类型名称不能超过50个字符")
    private String typeName;

    @Size(max = 50, message = "类型编码不能超过50个字符")
    private String typeCode;

    @Min(value = 0, message = "父节点ID不能小于0")
    private Long parentId;

    @Min(value = 1, message = "层级必须在1到4之间")
    @Max(value = 4, message = "层级必须在1到4之间")
    private Integer level;

    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

    @Min(value = 0, message = "状态必须为0或1")
    @Max(value = 1, message = "状态必须为0或1")
    private Integer status;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    private List<?> children;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("不支持的字段：" + fieldName);
    }
}
