package com.luatweaker.common.support;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.IdentityHashMap;
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
            unlockItemRegistry();
        }
    }

    @SuppressWarnings("unchecked")
    private static void unlockItemRegistry() {
        if (BuiltInRegistries.ITEM instanceof MappedRegistry<?> mapped) {
            MappedRegistry<Object> raw = (MappedRegistry<Object>) mapped;
            if (raw.unregisteredIntrusiveHolders == null) {
                raw.unregisteredIntrusiveHolders = new IdentityHashMap<>();
            }
            raw.frozen = false;
        }
    }
}
