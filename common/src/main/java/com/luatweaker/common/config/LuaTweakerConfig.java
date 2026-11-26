package com.luatweaker.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.core.vm.LuaEngineLimits;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class LuaTweakerConfig {

    private static final String CONFIG_FILE_PATH = "config/luatweaker.json";
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ConfigData {
        public boolean debug;
        public boolean autoGenerateStubs = true;
        public boolean suppressExperimentalWarning = true;
        public String modSyncMode = "kick";
        public int modSyncTimeoutSeconds = 10;
        public String serverModBundleUrl = "";
        public String serverModBundleSha256 = "";
        public String serverModBundleVersion = "";
        public String serverModBundleName = "";
        public String serverModBundleFile = "";
        public transient EngineLimitsData limits = new EngineLimitsData();
    }

    /** Flat-serialized subset of {@link ConfigData} holding VM resource limits. */
    public static class EngineLimitsData {
        public long maxMemoryBytes;
        public int maxCallDepth;
        public int maxCoroutines;
        public long instructionLimit;
        public long timeoutMillis;
        public long maxAllocationBytes;
        public boolean cancellable;
    }

    private static final AtomicReference<ConfigData> INSTANCE = new AtomicReference<>(new ConfigData());

    static {
        load();
    }

    private LuaTweakerConfig() {
    }

    private static Path resolveConfigPath() {
        try {
            return dev.architectury.platform.Platform.getGameFolder().resolve(CONFIG_FILE_PATH);
        } catch (IllegalStateException | LinkageError | AssertionError e) {
            return Path.of("build/tmp/luatweaker-config/luatweaker.json");
        }
    }

    public static void load() {
        LOCK.lock();
        try {
            Path path = resolveConfigPath();
            if (!Files.exists(path)) {
                save();
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    ConfigData loaded = GSON.fromJson(root, ConfigData.class);
                    if (loaded != null) {
                        EngineLimitsData loadedLimits = GSON.fromJson(root, EngineLimitsData.class);
                        loaded.limits = loadedLimits != null ? loadedLimits : new EngineLimitsData();
                        INSTANCE.set(loaded);
                    }
                }
            } catch (IOException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to load config: " + e.getMessage());
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static void save() {
        LOCK.lock();
        try {
            Path path = resolveConfigPath();
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to create config dir: " + e.getMessage());
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                ConfigData current = INSTANCE.get();
                JsonObject root = GSON.toJsonTree(current).getAsJsonObject();
                JsonObject limitsJson = GSON.toJsonTree(current.limits).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : limitsJson.entrySet()) {
                    root.add(entry.getKey(), entry.getValue());
                }
                GSON.toJson(root, writer);
            } catch (IOException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to save config: " + e.getMessage());
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean isDebug() {
        return INSTANCE.get().debug;
    }

    public static void setDebug(boolean debug) {
        LOCK.lock();
        try {
            INSTANCE.get().debug = debug;
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean isAutoGenerateStubs() {
        return INSTANCE.get().autoGenerateStubs;
    }

    public static boolean isSuppressExperimentalWarning() {
        return INSTANCE.get().suppressExperimentalWarning;
    }

    public static String getModSyncMode() {
        return INSTANCE.get().modSyncMode;
    }

    public static int getModSyncTimeoutSeconds() {
        return INSTANCE.get().modSyncTimeoutSeconds;
    }

    public static String getServerModBundleUrl() {
        return INSTANCE.get().serverModBundleUrl;
    }

    public static String getServerModBundleSha256() {
        return INSTANCE.get().serverModBundleSha256;
    }

    public static String getServerModBundleVersion() {
        return INSTANCE.get().serverModBundleVersion;
    }

    public static String getServerModBundleName() {
        return INSTANCE.get().serverModBundleName;
    }

    public static String getServerModBundleFile() {
        return INSTANCE.get().serverModBundleFile;
    }

    public static long getMaxMemoryBytes() {
        return INSTANCE.get().limits.maxMemoryBytes;
    }

    public static int getMaxCallDepth() {
        return INSTANCE.get().limits.maxCallDepth;
    }

    public static int getMaxCoroutines() {
        return INSTANCE.get().limits.maxCoroutines;
    }

    public static long getInstructionLimit() {
        return INSTANCE.get().limits.instructionLimit;
    }

    public static long getTimeoutMillis() {
        return INSTANCE.get().limits.timeoutMillis;
    }

    public static long getMaxAllocationBytes() {
        return INSTANCE.get().limits.maxAllocationBytes;
    }

    public static boolean isCancellable() {
        return INSTANCE.get().limits.cancellable;
    }

    public static @NotNull LuaEngineLimits toEngineLimits() {
        EngineLimitsData limits = INSTANCE.get().limits;
        return new LuaEngineLimits(
                Math.max(0L, limits.maxMemoryBytes),
                Math.max(0, limits.maxCallDepth),
                Math.max(0, limits.maxCoroutines),
                Math.max(0L, limits.instructionLimit),
                Math.max(0L, limits.timeoutMillis),
                Math.max(0L, limits.maxAllocationBytes),
                limits.cancellable);
    }
}
