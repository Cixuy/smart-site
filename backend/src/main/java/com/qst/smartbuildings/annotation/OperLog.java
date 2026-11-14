package com.qst.smartbuildings.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * <p>标注在 Controller 方法上，由 {@code OperLogAspect} 拦截并写入操作日志
 * （sys_oper_log），记录操作人、请求参数、执行状态与错误信息。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 操作模块（如：用户管理） */
    String title() default "";

    /** 操作类型（新增/修改/删除） */
    String operType() default "";
}