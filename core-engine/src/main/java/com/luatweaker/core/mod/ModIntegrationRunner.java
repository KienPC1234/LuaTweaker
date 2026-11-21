package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads a mod's declared {@code integrations} entries whose platform-mod
 * conditions hold. Each entry runs in the mod's Lua context (the
 * {@code mod} global already points at the owner). Outcomes land in the
 * integration status map; a skipped or crashing entry never fails the
 * owning mod - the failure is loud in the logs instead.
 */
final class ModIntegrationRunner {
    private ModIntegrationRunner() {}

    private static final String LOG_MOD = "[LuaMod][";
    private static final String INTEGRATION_TAG = "] Integration '";

    static void loadIntegrations(@NotNull ILuaEngine engine, @NotNull DiscoveredMod dm,
            @NotNull Map<String, Map<String, String>> integrationStatus,
            @NotNull Map<String, String> installedMods) {
        LuaModManifest manifest = dm.manifest();
        if (manifest.integrations().isEmpty()) {
            return;
        }
        String modId = manifest.id();
        Map<String, String> statuses = Collections.synchronizedMap(new LinkedHashMap<>());
        integrationStatus.put(modId, statuses);

        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MOD + modId + "] Checking " + manifest.integrations().size() + " integration(s)...");
        for (Map.Entry<String, ModIntegration> declared : manifest.integrations().entrySet()) {
            runIntegration(engine, dm, statuses, installedMods, declared.getKey(), declared.getValue());
        }
    }

    private static void runIntegration(@NotNull ILuaEngine engine, @NotNull DiscoveredMod dm,
            @NotNull Map<String, String> statuses, @NotNull Map<String, String> installedMods,
            @NotNull String targetModId, @NotNull ModIntegration integration) {
        String modId = dm.manifest().id();
        String installed = installedMods.get(targetModId);
        if (installed == null) {
            skipIntegration(statuses, modId, targetModId,
                    "platform mod '" + targetModId + "' is not installed");
            return;
        }
        if (!integration.satisfiedBy(installed)) {
            skipIntegration(statuses, modId, targetModId,
                    "platform mod '" + integration.describeRequirement(targetModId)
                            + "' not satisfied (installed " + installed + ")");
            return;
        }
        String entry = integration.entryOrDefault(targetModId);
        String code;
        try {
            code = ModLoader.readScriptContent(dm.file(), entry);
        } catch (IllegalArgumentException e) {
            statuses.put(targetModId, "skipped: cannot resolve entry '" + entry + "': " + errorMessage(e));
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MOD + modId + INTEGRATION_TAG + targetModId + "' skipped: " + e.getMessage());
            return;
        }
        if (code == null) {
            skipIntegration(statuses, modId, targetModId, "entry script not found: " + entry);
            return;
        }
        executeIntegration(engine, statuses, modId, targetModId, entry, code, installed);
    }

    private static void skipIntegration(@NotNull Map<String, String> statuses,
            @NotNull String modId, @NotNull String targetModId, @NotNull String reason) {
        statuses.put(targetModId, "skipped: " + reason);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MOD + modId + INTEGRATION_TAG + targetModId + "' skipped: " + reason + ".");
    }

    private static void executeIntegration(@NotNull ILuaEngine engine,
            @NotNull Map<String, String> statuses, @NotNull String modId,
            @NotNull String targetModId, @NotNull String entry, @NotNull String code,
            @NotNull String installed) {
        try {
            ModLoader.clearEngineError(engine);
            engine.executeString(code, modId + "/" + entry);
            String integrationError = ModLoader.clearEngineError(engine);
            if (integrationError != null) {
                statuses.put(targetModId, "error: " + integrationError);
                LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                        LOG_MOD + modId + INTEGRATION_TAG + targetModId + "' failed: " + integrationError);
                return;
            }
            statuses.put(targetModId, "loaded (host v" + installed + ")");
            LuaTweakerLog.get().info(LogStage.SCRIPT_LOAD,
                    LOG_MOD + modId + INTEGRATION_TAG + targetModId + "' loaded (host v" + installed + ").");
        } catch (org.luava.runtime.LuaException | IllegalStateException e) {
            statuses.put(targetModId, "error: " + errorMessage(e));
            LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                    LOG_MOD + modId + INTEGRATION_TAG + targetModId + "' failed: " + errorMessage(e));
        }
    }

    private static @NotNull String errorMessage(@NotNull Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
