package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 3D场景配置视图
 * <p>对应设计文档 3.1.1 / 3.1.3 响应。</p>
 */
@Data
@Schema(description = "3D场景配置视图")
public class SceneConfigVO {

    @Schema(description = "场景ID")
    private Long id;

    @Schema(description = "关联项目ID")
    private Long projectId;

    @Schema(description = "场景名称")
    private String sceneName;

    @Schema(description = "场景中心X坐标")
    private BigDecimal centerX;

    @Schema(description = "场景中心Y坐标")
    private BigDecimal centerY;

    @Schema(description = "场景中心Z坐标")
    private BigDecimal centerZ;

    @Schema(description = "简易几何体模式：true-是，false-否")
    private Boolean isSimpleMode;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "更新时间")
    private String updatedAt;
}
