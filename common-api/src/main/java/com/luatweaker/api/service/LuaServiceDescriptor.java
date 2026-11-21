package com.luatweaker.api.service;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Declarative descriptor for a LuaTweaker service.
 * Standardizes registration across VM bindings, require() resolution, and EmmyLua stubs.
 */
public record LuaServiceDescriptor(
        @NotNull String name,
        @NotNull Class<?> apiInterface,
        @NotNull List<String> aliases,
        @NotNull String requireNamespace,
        boolean registerGlobal
) {
    public LuaServiceDescriptor(
            @NotNull String name,
            @NotNull Class<?> apiInterface,
            @NotNull List<String> aliases
    ) {
        this(name, apiInterface, aliases, "LuaTweaker." + name, true);
    }

    public LuaServiceDescriptor(
            @NotNull String name,
            @NotNull Class<?> apiInterface
    ) {
        this(name, apiInterface, Collections.emptyList(), "LuaTweaker." + name, true);
    }
}
