package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Outbound RemoteEvent delivery with rate limiting. */
final class RemoteEventSender {

    /** Budget namespace for server-to-client traffic, one bucket per player. */
    private static final String SERVER_TO_CLIENT_PREFIX = "srv->";

    private final RateLimiter rateLimiter;
    private final double eventBurst;
    private final double eventRefillPerSecond;
    private final double channelBurst;
    private final double channelRefillPerSecond;
    private final String localClientBudgetKey;
    private final java.util.function.Predicate<String> channelKnown;

    RemoteEventSender(RateLimiter rateLimiter, double eventBurst, double eventRefillPerSecond,
                      double channelBurst, double channelRefillPerSecond,
                      String localClientBudgetKey, java.util.function.Predicate<String> channelKnown) {
        this.rateLimiter = rateLimiter;
        this.eventBurst = eventBurst;
        this.eventRefillPerSecond = eventRefillPerSecond;
        this.channelBurst = channelBurst;
        this.channelRefillPerSecond = channelRefillPerSecond;
        this.localClientBudgetKey = localClientBudgetKey;
        this.channelKnown = channelKnown;
    }

    void fireClient(String channelName, String playerUuid, ILuaValue... args) {
        if (playerUuid == null || playerUuid.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteEvent '" + channelName + "': no player identity");
            return;
        }
        if (!tryConsumeChannelBudget(SERVER_TO_CLIENT_PREFIX + playerUuid, channelName, channelBurst, channelRefillPerSecond)) {
            return;
        }
        if (!rateLimiter.tryConsumeEvent(SERVER_TO_CLIENT_PREFIX + playerUuid, eventBurst, eventRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited server->client RemoteEvent '" + channelName
                            + "' to player " + playerUuid);
            return;
        }
        Platform.getNetwork().sendPayloadBytes(playerUuid, channelName, encode(args));
    }

    void fireClients(String channelName, List<String> playerUuids, ILuaValue... args) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return;
        }
        List<String> targets = new ArrayList<>(playerUuids.size());
        for (String uuid : playerUuids) {
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            if (!tryConsumeChannelBudget(SERVER_TO_CLIENT_PREFIX + uuid, channelName, channelBurst, channelRefillPerSecond)) {
                continue;
            }
            if (!rateLimiter.tryConsumeEvent(SERVER_TO_CLIENT_PREFIX + uuid, eventBurst, eventRefillPerSecond)) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Network] Rate-limited server->client RemoteEvent '" + channelName
                                + "' to player " + uuid);
                continue;
            }
            targets.add(uuid);
        }
        if (targets.isEmpty()) {
            return;
        }
        Platform.getNetwork().sendPayloadBytesToPlayers(targets, channelName, encode(args));
    }

    void fireAllClientsExcept(String channelName, String excludedPlayerUuid, ILuaValue... args) {
        List<String> targets = collectTargets(excludedPlayerUuid);
        if (targets.isEmpty()) {
            return;
        }
        fireClients(channelName, targets, args);
    }

    private static List<String> collectTargets(@Nullable String excludedPlayerUuid) {
        List<String> targets = new ArrayList<>();
        if (!Platform.isInitialized()) {
            return targets;
        }
        try {
            for (com.luatweaker.api.entity.IPlayer player : Platform.getEntity().getAllPlayers()) {
                addTarget(targets, player, excludedPlayerUuid);
            }
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] FireAllClientsExcept listed players failed: " + e.getMessage());
        }
        return targets;
    }

    private static void addTarget(List<String> targets, @Nullable com.luatweaker.api.entity.IPlayer player,
                                  @Nullable String excludedPlayerUuid) {
        if (player == null) {
            return;
        }
        String uuid = player.getUuid();
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        if (excludedPlayerUuid != null && excludedPlayerUuid.equalsIgnoreCase(uuid)) {
            return;
        }
        targets.add(uuid);
    }

    void fireAllClients(String channelName, ILuaValue... args) {
        if (!tryConsumeChannelBudget("srv-broadcast", channelName, channelBurst, channelRefillPerSecond)) {
            return;
        }
        if (!rateLimiter.tryConsumeEvent("srv-broadcast", eventBurst * 4, eventRefillPerSecond * 4)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited broadcast RemoteEvent '" + channelName + "'");
            return;
        }
        Platform.getNetwork().broadcastPayloadBytes(channelName, encode(args));
    }

    void fireServer(String channelName, ILuaValue... args) {
        if (!tryConsumeChannelBudget(localClientBudgetKey, channelName, channelBurst, channelRefillPerSecond)) {
            return;
        }
        if (!rateLimiter.tryConsumeEvent(localClientBudgetKey, eventBurst, eventRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited outbound RemoteEvent '" + channelName + "'");
            return;
        }
        Platform.getNetwork().sendPayloadBytesToServer(channelName, encode(args));
    }

    /**
     * Per-channel budget for one direction, checked before the shared budget
     * so a runaway loop on one channel cannot silence the others. Unknown
     * channel names skip this (they must not mint unlimited buckets).
     */
    private boolean tryConsumeChannelBudget(String budgetKey, String channelName,
                                            double burst, double refillPerSecond) {
        if (channelName == null || !channelKnown.test(channelName)) {
            return true;
        }
        if (rateLimiter.tryConsumeEvent(budgetKey + '\0' + channelName, burst, refillPerSecond)) {
            return true;
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "[Network] Rate-limited RemoteEvent '" + channelName
                        + "' on its own channel budget; other channels are unaffected");
        return false;
    }

    private static byte[] encode(ILuaValue... args) {
        return LuaPayloadCodec.encodeArgs(args == null ? new ILuaValue[0] : args);
    }
}
