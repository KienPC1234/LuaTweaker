package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/** One manifest-bearing mod found by scanning a directory or zip. */
record DiscoveredMod(@NotNull LuaModManifest manifest, @NotNull File file, @Nullable String defaultConfig) {
}
