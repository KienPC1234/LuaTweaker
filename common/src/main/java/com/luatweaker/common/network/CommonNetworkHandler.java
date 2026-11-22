package com.luatweaker.common.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.network.ChunkAssembler;
import com.luatweaker.network.NetworkServiceImpl;
import com.luatweaker.network.RpcEnvelope;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CommonNetworkHandler {
    private static final ChunkAssembler INBOUND_CHUNKS = new ChunkAssembler();
    private static final AtomicReference<NetworkServiceImpl> SERVER_NETWORK_SERVICE = new AtomicReference<>();
    private static final AtomicReference<NetworkServiceImpl> CLIENT_NETWORK_SERVICE = new AtomicReference<>();

    private static final String SYNC_DATA_CHANNEL = "LuaTweaker:SyncData";
    private static final String CLOSE_SCREEN_CHANNEL = "LuaTweaker:CloseScreen";

    private CommonNetworkHandler() {
    }

    public static void setServerNetworkService(NetworkServiceImpl service) {
        SERVER_NETWORK_SERVICE.set(service);
    }

    public static void setClientNetworkService(NetworkServiceImpl service) {
        CLIENT_NETWORK_SERVICE.set(service);
    }

    public static NetworkServiceImpl getServerNetworkService() {
        return SERVER_NETWORK_SERVICE.get();
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ContainerButtonPayload.TYPE,
                ContainerButtonPayload.STREAM_CODEC, CommonNetworkHandler::handleContainerButton);

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, LuaTweakerC2SPayload.TYPE,
                LuaTweakerC2SPayload.STREAM_CODEC, CommonNetworkHandler::handleLuaPayloadServer);

        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            initClientReceivers();
        } else {
            NetworkManager.registerS2CPayloadType(LuaTweakerS2CPayload.TYPE, LuaTweakerS2CPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(ItemAnimationPayload.TYPE, ItemAnimationPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(OpenGuiEditorPayload.TYPE, OpenGuiEditorPayload.STREAM_CODEC);
        }
    }

    private static void initClientReceivers() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LuaTweakerS2CPayload.TYPE,
                LuaTweakerS2CPayload.STREAM_CODEC, CommonNetworkHandler::handleLuaPayloadClient);

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ItemAnimationPayload.TYPE,
                ItemAnimationPayload.STREAM_CODEC, (payload, context) -> context.queue(() ->
                        com.luatweaker.common.client.ClientItemAnimationHandler.trigger(
                                payload.entityId(), payload.animationName(), payload.durationTicks(), payload.speed())));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, OpenGuiEditorPayload.TYPE,
                OpenGuiEditorPayload.STREAM_CODEC, (payload, context) -> context.queue(() ->
                        com.luatweaker.common.client.ClientGuiOpener.openGuiEditor(
                                payload.title(), payload.guiWidth(), payload.guiHeight(), payload.isJeiMode())));
    }

    private static void handleContainerButton(ContainerButtonPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                Level level = serverPlayer.level();
                BlockPos pos = payload.pos();
                if (level.hasChunkAt(pos)) {
                    BlockState state = level.getBlockState(pos);
                    var handler = CustomContainerRegistry.BUTTON_HANDLERS.get(state.getBlock());
                    if (handler != null) {
                        try {
                            Map<String, Object> posMap = Map.of(
                                    "X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ());
                            handler.accept(posMap, payload.buttonId());
                        } catch (IllegalArgumentException | IllegalStateException e) {
                            LuaTweakerLog.get().error(LogStage.SYSTEM,
                                    "Failed container button handler for " + payload.buttonId() + ": " + e.getMessage());
                        }
                    }
                }
            }
        });
    }

    private static void handleLuaPayloadServer(LuaTweakerC2SPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            String playerUuid = player != null ? player.getUUID().toString() : "";
            String connectionKey = "S:" + playerUuid;
            String channel = payload.channelName();

            byte[] data;
            try {
                data = INBOUND_CHUNKS.accept(connectionKey, channel, payload.index(), payload.total(), payload.data());
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "[Network] Dropped frame '" + channel + "': " + e.getMessage());
                INBOUND_CHUNKS.clear(connectionKey, channel);
                return;
            }
            if (data == null) {
                return;
            }

            if (NetworkServiceImpl.RPC_CHANNEL.equals(channel)) {
                handleRpcFrame(data, true, playerUuid);
                return;
            }

            NetworkServiceImpl ns = SERVER_NETWORK_SERVICE.get();
            if (ns != null) {
                ns.OnClientFired(channel, playerUuid, data);
            }
        });
    }

    private static void handleLuaPayloadClient(LuaTweakerS2CPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            String connectionKey = "C:";
            String channel = payload.channelName();

            byte[] data;
            try {
                data = INBOUND_CHUNKS.accept(connectionKey, channel, payload.index(), payload.total(), payload.data());
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "[Network] Dropped client frame '" + channel + "': " + e.getMessage());
                INBOUND_CHUNKS.clear(connectionKey, channel);
                return;
            }
            if (data == null) {
                return;
            }

            if (dispatchClientboundControlChannel(channel, data)) {
                return;
            }

            if (NetworkServiceImpl.RPC_CHANNEL.equals(channel)) {
                handleRpcFrame(data, false, "");
                return;
            }

            Object ns = com.luatweaker.core.service.LuaServiceRegistry.get("NetworkServiceImpl");
            if (ns instanceof NetworkServiceImpl networkService) {
                networkService.OnServerFired(channel, "", data);
            }
        });
    }

    private static boolean dispatchClientboundControlChannel(String channel, byte[] data) {
        if (LuaTweakerCommon.modSyncChannel().equals(channel)) {
            com.luatweaker.common.modsync.ClientModSync.handleVerdict(new String(data, StandardCharsets.UTF_8));
            return true;
        }
        if (SYNC_DATA_CHANNEL.equals(channel)) {
            Platform.getClient().handleClientSyncData(new String(data, StandardCharsets.UTF_8));
            return true;
        }
        if (CLOSE_SCREEN_CHANNEL.equals(channel)) {
            Platform.getClient().closeScreen();
            return true;
        }
        return false;
    }

    private static void handleRpcFrame(byte[] data, boolean serverbound, String playerUuid) {
        NetworkServiceImpl ns = serverbound ? SERVER_NETWORK_SERVICE.get() : resolveClientNetworkService();
        if (ns == null) {
            return;
        }
        RpcEnvelope envelope;
        try {
            envelope = RpcEnvelope.decode(data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "[Network] Dropped malformed RPC envelope: " + e.getMessage());
            return;
        }
        if (envelope.phase() == RpcEnvelope.PHASE_INVOKE) {
            ns.OnRpcInvoke(envelope.requestId(), envelope.functionName(), playerUuid, envelope.payload());
        } else {
            ns.OnRpcResult(envelope.requestId(), envelope.payload(), envelope.error());
        }
    }

    private static NetworkServiceImpl resolveClientNetworkService() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("NetworkServiceImpl");
        return service instanceof NetworkServiceImpl ns ? ns : null;
    }
}
