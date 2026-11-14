package com.qst.smartbuildings.exception;

import com.qst.smartbuildings.common.SystemCodeEnum;
import lombok.Getter;

/**
 * 3D可视化场景模块统一异常。
 * 复用 SystemCodeEnum 标准 HTTP 错误码，具体 message 在构造时区分。
 */
@Getter
public class SceneException extends RuntimeException {

    private final int code;
    private final String msg;

    public SceneException(SystemCodeEnum e) {
        this.code = e.getCode();
        this.msg = e.getMsg();
    }

    public SceneException(SystemCodeEnum e, String msg) {
        this.code = e.getCode();
        this.msg = msg;
    }
}
