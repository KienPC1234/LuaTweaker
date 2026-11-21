package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Comparison half of {@link ModSync}, kept in its own collaborator (composition, no inheritance). */
final class ModSyncCompare {

    private ModSyncCompare() {
    }

    static @NotNull ModSync.Report compare(@NotNull Map<String, String> serverMods,
                                           @Nullable Map<String, String> clientMods) {
        Map<String, String> client = lowerCopy(clientMods);
        Map<String, String> serverLower = lowerCopy(serverMods);
        List<ModSync.RequiredMod> missingOnClient = new ArrayList<>();
        List<ModSync.RequiredMod> missingOnServer = new ArrayList<>();
        List<String> mismatch = new ArrayList<>();
        List<String> detail = new ArrayList<>();
        compareServerEntries(serverMods, client, missingOnClient, mismatch, detail);
        compareClientEntries(clientMods, serverLower, missingOnServer);
        return new ModSync.Report(List.copyOf(missingOnClient), List.copyOf(missingOnServer),
                List.copyOf(mismatch), List.copyOf(detail));
    }

    private static void compareServerEntries(Map<String, String> serverMods,
                                             Map<String, String> client,
                                             List<ModSync.RequiredMod> missingOnClient,
                                             List<String> mismatch, List<String> detail) {
        for (Map.Entry<String, String> entry : serverMods.entrySet()) {
            String clientVersion = client.get(lower(entry.getKey()));
            if (clientVersion == null) {
                missingOnClient.add(new ModSync.RequiredMod(entry.getKey(), entry.getValue()));
            } else if (ModVersion.compare(clientVersion, entry.getValue()) != 0) {
                mismatch.add(entry.getKey());
                detail.add(entry.getKey() + ": server v" + entry.getValue()
                        + " vs client v" + clientVersion);
            }
        }
    }

    private static void compareClientEntries(@Nullable Map<String, String> clientMods,
                                             Map<String, String> serverLower,
                                             List<ModSync.RequiredMod> missingOnServer) {
        if (clientMods == null) {
            return;
        }
        for (Map.Entry<String, String> entry : clientMods.entrySet()) {
            if (!serverLower.containsKey(lower(entry.getKey()))) {
                missingOnServer.add(new ModSync.RequiredMod(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static Map<String, String> lowerCopy(@Nullable Map<String, String> source) {
        Map<String, String> out = new ConcurrentHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> out.put(lower(key), value));
        }
        return out;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
