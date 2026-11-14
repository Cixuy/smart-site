package com.qst.smartbuildings.exception;

public class RoleException extends RuntimeException {
    private final int status;

    public RoleException(String message) {
        this(message, 400);
    }

    public RoleException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
