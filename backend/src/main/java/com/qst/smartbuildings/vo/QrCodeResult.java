package com.qst.smartbuildings.vo;

import lombok.Data;

/**
 * 设备二维码信息。
 */
@Data
public class QrCodeResult {

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 二维码图片地址 */
    private String qrCodeUrl;
}
