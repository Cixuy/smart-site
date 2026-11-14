package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "AiScreenshotCreateDTO", description = "AI 告警截图关联请求")
public class AiScreenshotCreateDTO {
    @NotBlank(message = "截图地址不能为空")
    @Size(max = 500, message = "截图地址长度不能超过 500")
    @Schema(description = "截图存储路径/OSS Key", example = "/ai_image/2026/08/15/abc.png")
    private String screenshotUrl;

    @Min(value = 1, message = "截图类型最小值为 1")
    @Max(value = 2, message = "截图类型最大值为 2")
    @Schema(description = "截图类型：1-原始截图，2-标注截图（缺省为 1）", example = "1")
    private Integer screenshotType;

    @Min(value = 0, message = "文件大小不能为负")
    @Schema(description = "文件大小（Bytes），可选", example = "204800")
    private Long fileSize;

    @Min(value = 1, message = "图片宽度最小值为 1")
    @Schema(description = "图片宽度（像素），可选", example = "1920")
    private Integer width;

    @Min(value = 1, message = "图片高度最小值为 1")
    @Schema(description = "图片高度（像素），可选", example = "1080")
    private Integer height;

    @Size(max = 50, message = "AI 识别标签长度不能超过 50")
    @Schema(description = "AI 识别标签（中文描述），可选", example = "未佩戴安全帽")
    private String aiLabel;

    @DecimalMin(value = "0.0", message = "置信度不能小于 0")
    @DecimalMax(value = "1.0", message = "置信度不能大于 1")
    @Schema(description = "AI 识别置信度（0~1），可选", example = "0.87")
    private Double aiConfidence;
}
