package com.qst.smartbuildings.exception;

import com.qst.smartbuildings.common.SystemCodeEnum;
import lombok.Getter;

/**
 * 喷淋降尘模块统一异常。
 * 复用 SystemCodeEnum 标准 HTTP 错误码，具体 message 在构造时区分。
 */
@Getter
public class SprayException extends RuntimeException {

    private final int code;
    private final String msg;

    public SprayException(SystemCodeEnum e) {
        this.code = e.getCode();
        this.msg = e.getMsg();
    }

    public SprayException(SystemCodeEnum e, String msg) {
        this.code = e.getCode();
        this.msg = msg;
    }
}
