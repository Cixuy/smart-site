package com.qst.smartbuildings.exception;

/**
 * 菜单管理异常
 */
public class MenuException extends RuntimeException {
    private final int status;

    public MenuException(String message) {
        this(message, 400);
    }

    public MenuException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
