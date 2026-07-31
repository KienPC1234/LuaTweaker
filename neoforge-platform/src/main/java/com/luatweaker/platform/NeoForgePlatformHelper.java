package com.luatweaker.platform;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformHelper;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.interaction.IInteractableEntity;
import com.luatweaker.platform.interaction.*;
import com.luatweaker.platform.entity.ai.NeoForgeLuaGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public boolean isClient() {
        return net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT;
    }

    @Override
    public boolean isDedicatedServer() {
        return net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER;
    }

    @Override
    public IItem createItem(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        return new NeoForgeItem(new ItemStack(item, count));
    }

    @Override
    public boolean itemExists(String itemId) {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemId));
    }

    @Override
    public boolean blockExists(String blockId) {
        return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockId));
    }

    @Override
    public boolean fluidExists(String fluidId) {
        return BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(fluidId));
    }

    @Override
    public boolean tagExists(String tagId) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
        return BuiltInRegistries.ITEM.getTag(tagKey).isPresent();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public java.util.Set<String> getSupportedMobParents() {
        return com.luatweaker.platform.content.MobParentRegistry.getSupportedMobs();
    }

    @Override
    public List<IRecipe> getAllRecipes() {
        List<IRecipe> list = new ArrayList<>();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                list.add(new IRecipe() {
                    @Override
                    public String getId() {
                        return holder.id().toString();
                    }

                    @Override
                    public String getType() {
                        return holder.value().getType().toString();
                    }

                    @Override
                    public List<com.luatweaker.api.wrapper.IngredientWrapper> getIngredients() {
                        return List.of();
                    }

                    @Override
                    public IItem getResult() {
                        var registries = server.registryAccess();
                        return new NeoForgeItem(holder.value().getResultItem(registries));
                    }

                    @Override
                    public Object getRawRecipe() {
                        return holder.value();
                    }
                });
            }
        }
        return list;
    }

    @Override
    public void addCustomGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine, boolean isTargetSelector) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addCustomGoal(entity, priority, goalTable, engine, isTargetSelector);
    }

    @Override
    public void removeCustomGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.removeCustomGoal(entity, goalTable);
    }

    @Override
    public void clearCustomGoals(@NotNull IEntity entity) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.clearCustomGoals(entity);
    }

    @Override
    public void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addMeleeAttackGoal(entity, priority, speed, pauseWhenMobIdle);
    }

    @Override
    public void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addHurtByTargetGoal(entity, priority);
    }

    @Override
    public void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addNearestAttackableTargetGoal(entity, priority, targetType);
    }

    @Override
    public void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectile(shooter, projectileType, speed, inaccuracy);
    }

    @Override
    public void shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectileAt(shooter, projectileType, target, speed);
    }

    @Override
    public void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.playAnimation(entity, animName, speed, transition);
    }

    @Override
    public boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean performItemUse(@NotNull IEntity actor, int slot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.getInventoryItems(entity);
    }

    @Override
    @Nullable
    public IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        return new NeoForgeInteractableBlock(dimension, x, y, z);
    }

    @Override
    @Nullable
    public IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) {
        return new NeoForgeInteractableItem(entityOrBlock, slot);
    }

    @Override
    @Nullable
    public IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.getInteractableEntity(uuid);
    }

    @Override
    @Nullable
    public IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) {
        if (rawEntity instanceof Entity mcEntity) {
            return new NeoForgeInteractableEntity(mcEntity);
        }
        return null;
    }

    @Override
    public java.io.File getStorageDirectory() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            java.io.File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            return new java.io.File(worldDir, "luatweaker/storage");
        }
        java.io.File gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile();
        return new java.io.File(gameDir, "luatweaker/storage");
    }

    @Override
    @Nullable
    public com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                java.util.UUID id = java.util.UUID.fromString(uuid);
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    return new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(playerUuid);
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void broadcastPayloadPacket(String channelName, String dataJson) {
        com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public void sendPayloadPacketToServer(String channelName, String dataJson) {
        com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }
}
