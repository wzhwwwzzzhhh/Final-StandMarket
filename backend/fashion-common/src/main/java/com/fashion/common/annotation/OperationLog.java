package com.fashion.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在管理端 Controller 的写操作方法上，
 * 由 {@code OperationLogAspect} 切面拦截并记录操作审计日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块，如：商品管理 */
    String module() default "";

    /** 操作描述，如：新增商品 */
    String operation() default "";
}