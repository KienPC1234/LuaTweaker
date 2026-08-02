package com.luatweaker.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the default value for an interface method parameter when the Lua
 * caller omits the argument. Used by {@code LuaBinder} to preserve optional
 * arguments without hand-written glue code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface LuaDefault {
    String value();
}
