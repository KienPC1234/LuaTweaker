package com.luatweaker.common.support;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableEntity;
import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.api.pal.IPlatformEntity;
import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.api.pal.IPlatformStorage;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared fake platform for integration tests that load the real showcase LuaMods.
 *
 * <p>Extracted so the dimension/spawn tests and the script integration test do not
 * each carry a 150-line anonymous-class copy of the same stubs. The two content
 * modes reflect what those tests need: dimension tests only query registries,
 * while script tests exercise item/NBT bindings.</p>
 */
public final class FakePlatform {

    private FakePlatform() {
    }

    /** Read-only platform: no items/blocks exist, block-entity writes are rejected. */
    public static void installMinimal(String platformName) {
        install(platformName, false, null);
    }

    /**
     * Permissive platform: every id exists and block-entity data is recorded.
     *
     * @param blockEntitySink receives the map passed to {@code setBlockEntityData}
     */
    public static void installPermissive(String platformName,
                                         @Nullable Consumer<Map<String, Object>> blockEntitySink) {
        install(platformName, true, blockEntitySink);
    }

    private static void install(String platformName, boolean permissive,
                                @Nullable Consumer<Map<String, Object>> blockEntitySink) {
        Platform.setContent(new IPlatformContent() {
            @Override
            public IItem createItem(String itemId, int count) {
                if (!permissive) {
                    return null;
                }
                return new IItem() {
                    @Override public String getId() { return itemId; }
                    @Override public int getCount() { return count; }
                    @Override public boolean hasTag(String tagId) { return false; }
                    @Override public Object getRawItemStack() { return null; }
                };
            }

            @Override public boolean itemExists(String itemId) { return permissive; }
            @Override public boolean blockExists(String blockId) { return permissive; }
            @Override public boolean fluidExists(String fluidId) { return permissive; }
            @Override public boolean tagExists(String tagId) { return permissive; }
            @Override public boolean isModLoaded(String modId) { return permissive; }
            @Override public boolean isClient() { return false; }
            @Override public boolean isDedicatedServer() { return true; }
            @Override public String getPlatformName() { return platformName; }
            @Override public Set<String> getSupportedMobParents() { return Set.of(); }
            @Override public List<IRecipe> getAllRecipes() { return List.of(); }
        });

        Platform.setNetwork(new IPlatformNetwork() {
            @Override public void sendPayloadBytes(String uuid, String channel, byte[] data) {}
            @Override public void broadcastPayloadBytes(String channel, byte[] data) {}
            @Override public void sendPayloadBytesToServer(String channel, byte[] data) {}
        });

        Platform.setStorage(new IPlatformStorage() {
            @Override public File getStorageDirectory() { return new File("build/tmp/fake-platform/storage"); }
        });

        Platform.setInteraction(new IPlatformInteraction() {
            @Override public void shootProjectile(IEntity shooter, String projectileId, double speed, double inaccuracy) {}
            @Override public IEntity shootProjectileAt(IEntity shooter, String projectileId, IEntity target, double speed) { return null; }
            @Override public void playAnimation(IEntity entity, String animation, double speed, double transition) {}
            @Override public boolean performBlockBreak(IEntity actor, int x, int y, int z) { return false; }
            @Override public boolean performBlockPlace(IEntity actor, int x, int y, int z, String blockId) { return false; }
            @Override public boolean performBlockUse(IEntity actor, int x, int y, int z) { return false; }
            @Override public boolean performItemUse(IEntity actor, int slot) { return false; }
            @Override public void lookAt(IEntity actor, double x, double y, double z) {}
            @Override public void lookAt(IEntity actor, IEntity target) {}
            @Override public boolean moveInventoryItem(IEntity actor, int from, int to) { return false; }
            @Override public boolean dropInventoryItem(IEntity actor, int slot, int count) { return false; }
            @Override public List<IWorldBlock> getNearbyBlocks(IEntity entity, int radius) { return List.of(); }
            @Override public List<ILocatedItem> getInventoryItems(IEntity entity) { return List.of(); }
            @Override public List<IEntity> getNearbyEntities(IEntity center, double radius) { return List.of(); }
            @Override public IInteractableBlock getInteractableBlock(String dimension, int x, int y, int z) { return null; }
            @Override public IInteractableItem getInteractableItem(Object entity, int slot) { return null; }
            @Override public IInteractableEntity getInteractableEntity(String uuid) { return null; }
            @Override public IInteractableEntity getInteractableEntity(Object entity) { return null; }
            @Override public @Nullable Map<String, Object> getBlockState(String dimension, int x, int y, int z) { return null; }
            @Override public boolean setBlockState(String dimension, int x, int y, int z, String blockId,
                                                   Map<String, Object> properties) { return false; }
            @Override public @Nullable Map<String, Object> getBlockEntityData(String dimension, int x, int y, int z) { return null; }

            @Override
            public boolean setBlockEntityData(String dimension, int x, int y, int z,
                                              Map<String, Object> data) {
                if (blockEntitySink == null) {
                    return false;
                }
                blockEntitySink.accept(data);
                return true;
            }

            @Override public boolean ejectContainerItem(String dimension, int x, int y, int z,
                                                        int slot, int count) { return false; }
            @Override public boolean placeStructure(String dimension, String template,
                                                    int x, int y, int z, int rotation) { return false; }
            @Override public long fillBlocks(String dimension, int x1, int y1, int z1,
                                             int x2, int y2, int z2, String blockId,
                                             Map<String, Object> properties) { return 0; }
            @Override public long replaceBlocks(String dimension, int x1, int y1, int z1,
                                                int x2, int y2, int z2, String from, String to) { return 0; }
            @Override public boolean executeCommand(String command) { return false; }
        });

        Platform.setEntity(new IPlatformEntity() {
            @Override public void addCustomGoal(IEntity entity, int priority, ILuaTable goal,
                                                ILuaEngine engine, boolean interrupt) {}
            @Override public void removeCustomGoal(IEntity entity, ILuaTable goal) {}
            @Override public void clearCustomGoals(IEntity entity) {}
            @Override public void addMeleeAttackGoal(IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {}
            @Override public void addHurtByTargetGoal(IEntity entity, int priority) {}
            @Override public void addNearestAttackableTargetGoal(IEntity entity, int priority, String targetType) {}
            @Override public void addFloatGoal(IEntity entity, int priority) {}
            @Override public void addWanderGoal(IEntity entity, int priority, double speed) {}
            @Override public void addLookAtGoal(IEntity entity, int priority, double distance, String targetType) {}
            @Override public void addLookAroundGoal(IEntity entity, int priority) {}
            @Override public void addPanicGoal(IEntity entity, int priority, double speed) {}
            @Override public void addAvoidGoal(IEntity entity, int priority, String avoidType,
                                               double distance, double walkSpeed, double sprintSpeed) {}
            @Override public void addLeapGoal(IEntity entity, int priority, double speed) {}
            @Override public void addBreakDoorGoal(IEntity entity, int priority, boolean difficulty) {}
            @Override public void addTemptGoal(IEntity entity, int priority, double speed, String itemId) {}
            @Override public void addMoveToTargetGoal(IEntity entity, int priority, double speed, double maxDistance) {}
            @Override public List<Map<String, Object>> listGoals(IEntity entity) { return List.of(); }
            @Override public IPlayer getPlayer(String uuid) { return null; }
            @Override public List<IPlayer> getAllPlayers() { return List.of(); }
            @Override public Object spawnEntity(String entityId, double x, double y, double z) { return null; }

            @Override public Object spawnEntityFromNbt(String dimension, double x, double y, double z,
                                                       String nbtJson) { return null; }
        });
    }
}
