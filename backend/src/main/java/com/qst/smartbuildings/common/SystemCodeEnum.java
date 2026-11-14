package com.qst.smartbuildings.common;

import com.qst.smartbuildings.entity.ResultCode;

public enum SystemCodeEnum implements ResultCode {
    //系统响应成功
    SYSTEM_OK(0, "success"),
    //未捕获的错误
    SYSTEM_ERROR(500, "网络错误，请稍候再试"),

    //拒绝访问
    SYSTEM_BAD_REQUEST(403, "请求频率过快,请稍后再试"),
    //无权访问
    SYSTEM_NO_AUTH(401, "无权操作"),

    // ---------- 认证相关 ----------
    SYSTEM_NOT_LOGIN(302, "请先登录！"),
    SYSTEM_LOGIN_ERROR(305, "登录异常"),

    // ---------- 设备在线/离线统计模块（复用标准 HTTP 错误码，具体 message 在异常构造函数中区分） ----------
    NOT_FOUND(404, "资源不存在"),
    BAD_REQUEST(400, "请求参数错误"),
    CONFLICT(409, "时间冲突"),
    ;
    SystemCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    private int code;
    private String msg;

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    public static SystemCodeEnum parse(Integer status) {
        for (SystemCodeEnum value : values()) {
            if (value.getCode() == status) {
                return value;
            }
        }
        return SystemCodeEnum.SYSTEM_ERROR;
    }
}
