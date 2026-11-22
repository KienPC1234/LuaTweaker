package com.luatweaker.common.entity;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the one-call movement/state snapshot Lua scripts read from {@code PlayerTick.event}
 * and from {@code Entity:GetMovementData()}. One VM crossing per tick instead of dozens.
 *
 * <p>Also owns the full NBT snapshot/restore pair, because both are "capture the live entity"
 * concerns and keeping them here leaves the entity wrapper free of NBT plumbing.</p>
 */
final class EntitySnapshot {

    private static final String NBT_ID = "id";

    private EntitySnapshot() {
    }

    static Map<String, Object> capture(@Nullable Entity entity, @NotNull IEntity wrapper) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        if (entity == null) {
            return data;
        }
        putPosition(data, wrapper);
        putRotation(data, entity, wrapper);
        putEnvironment(data, entity, wrapper);
        putCombat(data, wrapper);
        if (entity instanceof ServerPlayer serverPlayer) {
            data.put("GameMode", serverPlayer.gameMode.getGameModeForPlayer().getName());
            data.put("Ping", serverPlayer.connection.latency());
        }
        return data;
    }

    private static void putPosition(Map<String, Object> data, IEntity wrapper) {
        data.put("X", wrapper.getX());
        data.put("Y", wrapper.getY());
        data.put("Z", wrapper.getZ());
        data.put("DeltaX", wrapper.getDeltaX());
        data.put("DeltaY", wrapper.getDeltaY());
        data.put("DeltaZ", wrapper.getDeltaZ());
        data.put("HorizontalSpeed", wrapper.getHorizontalSpeed());
        data.put("VerticalSpeed", wrapper.getVerticalSpeed());
        data.put("Speed", wrapper.getSpeed());
    }

    private static void putRotation(Map<String, Object> data, Entity entity, IEntity wrapper) {
        data.put("Yaw", entity.getYRot());
        data.put("Pitch", entity.getXRot());
        data.put("PreviousYaw", wrapper.getPreviousYaw());
        data.put("PreviousPitch", wrapper.getPreviousPitch());
        data.put("YawDelta", wrapper.getYawDelta());
        data.put("PitchDelta", wrapper.getPitchDelta());
        data.put("HeadYaw", wrapper.getHeadYaw());
    }

    private static void putEnvironment(Map<String, Object> data, Entity entity, IEntity wrapper) {
        data.put("OnGround", entity.onGround());
        data.put("InWater", entity.isInWater());
        data.put("InLava", entity.isInLava());
        data.put("FallFlying", wrapper.isFallFlying());
        data.put("Flying", wrapper.isFlying());
        data.put("FallDistance", wrapper.getFallDistance());
        data.put("EyeHeight", wrapper.getEyeHeight());
        data.put("Sprinting", entity.isSprinting());
        data.put("Sneaking", entity.isShiftKeyDown());
        data.put("Swimming", wrapper.isSwimming());
        data.put("Pose", wrapper.getPose());
        data.put("Invulnerable", wrapper.isInvulnerable());
        data.put("NoAi", wrapper.isNoAi());
        data.put("Invisible", wrapper.isInvisible());
        data.put("NoGravity", wrapper.isNoGravity());
    }

    private static void putCombat(Map<String, Object> data, IEntity wrapper) {        data.put("UsingItem", wrapper.isUsingItem());
        data.put("Swinging", wrapper.isSwinging());
        data.put("SwingTime", wrapper.getSwingTime());
        data.put("EntityReach", wrapper.getEntityInteractionRange());
        data.put("BlockReach", wrapper.getBlockInteractionRange());
        data.put("UseItemTicks", wrapper.getUseItemRemainingTicks());
        data.put("Armor", wrapper.getArmorValue());
        data.put("AttackStrength", wrapper.getAttackStrengthScale(1.0F));
        data.put("LastHurtByMobAt", wrapper.getLastHurtByMobTimestamp());
        data.put("LastHurtMobAt", wrapper.getLastHurtMobTimestamp());
        data.put("LastDamageType", wrapper.getLastDamageType());
    }

    /**
     * Serialises the entity exactly as the world save would, plus the registry id so the snapshot can
     * be re-spawned later with {@code Entities:SpawnFromNbt}.
     */
    static String toNbt(@Nullable Entity entity) {
        if (entity == null) {
            return "{}";
        }
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null) {
            tag.putString(NBT_ID, id.toString());
        }
        return tag.toString();
    }

    /**
     * Restores a live entity from a {@link #toNbt(Entity)} snapshot.
     *
     * @return false for a missing/removed entity, a player (the server owns player state) or
     *         malformed JSON; failures are logged, never swallowed
     */
    static boolean loadNbt(@Nullable Entity entity, @NotNull String nbtJson) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Player) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Entity:SetNbt ignored: player NBT is restored by the server, not by scripts");
            return false;
        }
        try {
            entity.load(TagParser.parseTag(nbtJson));
            return true;
        } catch (CommandSyntaxException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Entity:SetNbt rejected malformed NBT: " + e.getMessage());
            return false;
        }
    }
}
