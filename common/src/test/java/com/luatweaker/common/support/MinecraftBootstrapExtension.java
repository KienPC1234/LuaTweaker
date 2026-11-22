package com.luatweaker.common.support;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Boots vanilla Minecraft registries once before any test class executes.
 * Ensures tests touching ItemStack, BuiltInRegistries, or BlockPos run without Not bootstrapped errors.
 */
public class MinecraftBootstrapExtension implements BeforeAllCallback {

    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    @Override
    public void beforeAll(ExtensionContext context) {
        if (BOOTSTRAPPED.compareAndSet(false, true)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }
}
