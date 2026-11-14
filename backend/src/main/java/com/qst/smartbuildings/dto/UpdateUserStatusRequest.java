package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotNull(message = "用户状态不能为空")
    @Pattern(regexp = "^[012]$", message = "用户状态必须为0、1或2")
    private String status;

    public int statusValue() {
        return Integer.parseInt(status);
    }
}
