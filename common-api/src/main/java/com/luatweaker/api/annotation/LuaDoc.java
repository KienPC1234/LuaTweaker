package com.luatweaker.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LuaDoc {
    String description() default "";
    String[] params() default {}; // E.g. {"id: string", "count: number"}
    String returnType() default "void";
}
