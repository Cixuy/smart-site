package com.qst.smartbuildings.exception;

import com.qst.smartbuildings.common.SystemCodeEnum;
import lombok.Getter;

/**
 * 设备在线/离线统计模块业务异常
 */
@Getter
public class DeviceStatsException extends RuntimeException {

    private final int code;
    private final String msg;

    public DeviceStatsException(SystemCodeEnum e) {
        super(e.getMsg());
        this.code = e.getCode();
        this.msg = e.getMsg();
    }

    public DeviceStatsException(SystemCodeEnum e, String msg) {
        super(msg);
        this.code = e.getCode();
        this.msg = msg;
    }

    public DeviceStatsException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}
