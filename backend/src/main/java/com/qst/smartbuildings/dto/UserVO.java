package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.qst.smartbuildings.vo.RoleOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息响应")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "角色列表")
    private List<RoleOption> roles;

    @Schema(description = "登录Token")
    private String token;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 从 User 实体构建 UserVO（不含 token）
     */
    public static UserVO fromUser(com.qst.smartbuildings.domain.User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .createTime(user.getCreateTime())
                .build();
    }
}
