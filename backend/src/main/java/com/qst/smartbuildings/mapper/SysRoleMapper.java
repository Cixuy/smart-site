package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.entity.SysRole;
import com.qst.smartbuildings.vo.UserRoleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 批量查询用户在指定用户集合下的角色（已过滤被软删除的角色）。
     *
     * @param userIds 用户ID集合
     * @return 用户-角色关联行
     */
    @Select("<script>" +
            "SELECT ur.user_id AS userId, r.id AS roleId, r.role_name AS roleName, r.role_code AS roleCode " +
            "FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE r.is_deleted = 0 " +
            "AND ur.user_id IN " +
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>#{userId}</foreach>" +
            "</script>")
    List<UserRoleRow> selectRolesByUserIds(@Param("userIds") Collection<Long> userIds);
}

