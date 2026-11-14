package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank @Size(max = 30) String roleCode,
        @NotBlank @Size(max = 30) String roleName,
        @Min(1) @Max(3) Integer dataScope,
        Integer sort,
        @Size(max = 200) String remark
) {
}
