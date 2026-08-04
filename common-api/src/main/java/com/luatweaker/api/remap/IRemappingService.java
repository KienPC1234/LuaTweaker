package com.luatweaker.api.remap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public interface IRemappingService {
    @Nullable
    Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName);

    @Nullable
    Method resolveMethodBySignature(
            @NotNull Class<?> clazz,
            @NotNull String baseName,
            @Nullable Class<?>[] paramTypes,
            @Nullable Class<?> returnType
    );

    @Nullable
    String remapMethodName(@NotNull String methodName);

    boolean isObfuscatedEnvironment();
}
