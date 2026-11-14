package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoleMenuRequest(@NotNull List<@NotNull Long> menuIds) {
}
