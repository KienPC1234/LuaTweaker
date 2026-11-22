package com.luatweaker.common.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Sound and redstone operations behind the Lua {@code World} service. */
final class WorldEffectsOps {

    private WorldEffectsOps() {
    }

    static void playSound(@NotNull String dimension, @NotNull String soundId,
                          double x, double y, double z,
                          float volume, float pitch, @NotNull String source) {
        ServerLevels.run(ServerLevels.resolve(dimension),
                level -> playIn(level, dimension, soundId, x, y, z, volume, pitch, source));
    }

    private static void playIn(ServerLevel level, String dimension, String soundId,
                               double x, double y, double z, float volume, float pitch,
                               String source) {
        var rl = com.luatweaker.common.sound.SoundHelper.parseId(soundId);
        SoundEvent event = com.luatweaker.common.sound.SoundHelper.registered(rl,
                "dimension " + dimension);
        if (event == null) {
            return;
        }
        SoundSource category =
                com.luatweaker.common.sound.SoundHelper.parseSource(source, SoundSource.PLAYERS);
        level.playSound(null, x, y, z, event, category,
                com.luatweaker.common.sound.SoundHelper.volume(volume),
                com.luatweaker.common.sound.SoundHelper.pitch(pitch));
    }

    static boolean stopSound(@NotNull String dimension, @NotNull String soundId,
                             @NotNull String source) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> stopIn(level, soundId, source));
    }

    private static boolean stopIn(ServerLevel level, String soundId, String source) {
        var rl = com.luatweaker.common.sound.SoundHelper.parseId(soundId);
        if (rl == null) {
            return false;
        }
        SoundSource category =
                com.luatweaker.common.sound.SoundHelper.parseSource(source, SoundSource.MASTER);
        if (level.players().isEmpty()) {
            return false;
        }
        var packet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl, category);
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
        return true;
    }

    static int getRedstoneSignal(@NotNull String dimension, int x, int y, int z,
                                 @Nullable String direction) {
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> signalAt(level, new BlockPos(x, y, z), direction));
    }

    private static int signalAt(ServerLevel level, BlockPos pos, @Nullable String direction) {
        if (direction != null && !direction.isBlank()) {
            Direction dir = Direction.byName(direction.toLowerCase(Locale.ROOT));
            if (dir != null) {
                return level.getSignal(pos, dir);
            }
        }
        return level.getBestNeighborSignal(pos);
    }
}
