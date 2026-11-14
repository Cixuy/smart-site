package com.qst.smartbuildings.util;

import java.util.UUID;

/**
 * Token 工具类 —— 生成/校验 UUID 格式的登录 Token
 */
public class TokenUtil {

    /**
     * 生成一个唯一 Token（UUID 去除短横线）
     */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
