package com.qst.smartbuildings.exception;

/**
 * 升降机监控业务异常
 */
public class HoistException extends RuntimeException {
    public HoistException(String message) {
        super(message);
    }
}