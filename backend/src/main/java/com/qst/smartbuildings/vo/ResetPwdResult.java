package com.qst.smartbuildings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResetPwdResult {
    private String newPassword;
    private boolean forceChange;
}
