package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模型上传结果视图
 * <p>上传设备3D模型后返回：设备入场景后的坐标ID、设备ID、模型访问地址与MD5。对应设计文档 3.1.6 响应。</p>
 */
@Data
@Schema(description = "模型上传结果视图")
public class ModelUploadResultVO {

    @Schema(description = "坐标映射ID（设备入场景后的坐标行ID）")
    private Long coordinateId;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备模型文件访问地址（/device-models/...）")
    private String modelFileUrl;

    @Schema(description = "文件MD5校验码")
    private String modelFileMd5;
}
