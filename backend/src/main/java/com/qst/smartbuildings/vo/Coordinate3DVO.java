package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 三维坐标（x, y, z），用于 position / rotation / scale 三类嵌套字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "三维坐标（x,y,z）")
public class Coordinate3DVO {

    @Schema(description = "X 坐标")
    private BigDecimal x;

    @Schema(description = "Y 坐标（高度）")
    private BigDecimal y;

    @Schema(description = "Z 坐标")
    private BigDecimal z;
}
