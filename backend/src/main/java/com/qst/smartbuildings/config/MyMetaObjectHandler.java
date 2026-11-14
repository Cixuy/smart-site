package com.qst.smartbuildings.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>为所有声明了 {@code @TableField(fill = FieldFill.INSERT)} 或
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 的实体字段提供统一自动填充，保证
 * {@code create_time} 与 {@code update_time} 在插入、更新及逻辑删除时始终不为空。</p>
 *
 * <p>注意：MyBatis-Plus 的逻辑删除（{@code deleteById}）也会生成 {@code UPDATE} 语句并触发
 * {@code updateFill}，因此在此统一填充可避免逻辑删除时 {@code update_time} 为空导致的数据库
 * 约束错误。</p>
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    /** 创建时间字段名 */
    private static final String CREATE_TIME = "createTime";

    /** 更新时间字段名 */
    private static final String UPDATE_TIME = "updateTime";

    /**
     * 插入时填充：仅为尚未赋值的 {@code createTime} / {@code updateTime} 填充当前时间。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 仅在字段当前为空时才填充，避免覆盖业务层已显式设置的值
        this.strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
    }

    /**
     * 更新（含逻辑删除）时填充：仅为尚未赋值的 {@code updateTime} 填充当前时间。
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 仅在字段当前为空时才填充，避免覆盖业务层已显式设置的值
        this.strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
