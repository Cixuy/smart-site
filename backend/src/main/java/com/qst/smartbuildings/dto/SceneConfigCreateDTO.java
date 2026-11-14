package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 3D场景配置新增/修改请求
 * <p>对应设计文档 3.1.2 / 3.1.4。新增时 projectId + sceneName 必填；修改时除 projectId 外均可改。</p>
 */
@Data
@Schema(description = "3D场景配置新增/修改请求")
public class SceneConfigCreateDTO {

    @Schema(description = "关联项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景名称不能为空")
    private String sceneName;

    @Schema(description = "场景中心X坐标")
    private BigDecimal centerX;

    @Schema(description = "场景中心Y坐标")
    private BigDecimal centerY;

    @Schema(description = "场景中心Z坐标")
    private BigDecimal centerZ;

    @Schema(description = "简易几何体模式：true-是，false-否")
    private Boolean isSimpleMode;
}
