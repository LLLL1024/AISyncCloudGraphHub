package com.xiyan.xipicture.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 */
@Target(ElementType.METHOD)  // 针对方法的注解
@Retention(RetentionPolicy.RUNTIME)  // 在运行时生效
public @interface AuthCheck {

    /**
     * 必须具有某个角色
     **/
    String mustRole() default "";
}