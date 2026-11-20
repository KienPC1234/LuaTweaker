package com.luatweaker.platform.entity;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.platform.interaction.NeoForgeSpatialHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeoForgeEntityWrapper implements IEntity {

    protected final Entity entity;

    public NeoForgeEntityWrapper(Entity entity) {
        this.entity = entity;
    }

    @Override
    public String getType() {
        return entity != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : "";
    }

    @Override
    public String getName() {
        return entity != null ? entity.getName().getString() : "Unknown";
    }

    @Override
    public String getUuid() {
        return entity != null ? entity.getUUID().toString() : "";
    }

    @Override
    public float getHealth() {
        return (entity instanceof LivingEntity living) ? living.getHealth() : 0.0f;
    }

    @Override
    public void setHealth(float health) {
        if (entity instanceof LivingEntity living) {
            living.setHealth(health);
        }
    }

    @Override
    public float getMaxHealth() {
        return (entity instanceof LivingEntity living) ? living.getMaxHealth() : 0.0f;
    }

    @Override
    public void setMaxHealth(float maxHealth) {
        if (entity instanceof LivingEntity living) {
            var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(maxHealth);
                if (living.getHealth() > maxHealth) {
                    living.setHealth(maxHealth);
                }
            }
        }
    }

    @Override
    public void damage(float amount) {
        if (entity instanceof LivingEntity living) {
            living.hurt(living.damageSources().generic(), amount);
        }
    }

    @Override
    public void applyDamage(float amount, String damageTypeId, @Nullable IEntity attacker) {
        if (entity instanceof LivingEntity living) {
            Entity attackerEntity = (attacker != null && attacker.getRawEntity() instanceof Entity mcAttacker) ? mcAttacker : null;
            if (damageTypeId != null && !damageTypeId.isBlank()) {
                ResourceLocation rl = damageTypeId.contains(":")
                        ? ResourceLocation.tryParse(damageTypeId)
                        : ResourceLocation.fromNamespaceAndPath("minecraft", damageTypeId);
                var registry = rl == null ? java.util.Optional.<net.minecraft.core.Registry<net.minecraft.world.damagesource.DamageType>>empty()
                        : living.level().registryAccess().registry(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
                var holder = registry.flatMap(reg -> reg.getHolder(
                        ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, rl)));
                if (holder.isPresent()) {
                    living.hurt(new net.minecraft.world.damagesource.DamageSource(holder.get(), attackerEntity, attackerEntity), amount);
                    return;
                }
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "ApplyDamage: unknown damage type '" + damageTypeId
                                + "'; falling back to a generic " + (attackerEntity != null ? "mob attack" : "hit"));
            }
            if (attackerEntity != null) {
                living.hurt(living.damageSources().mobAttack(attackerEntity instanceof LivingEntity la ? la : null), amount);
            } else {
                living.hurt(living.damageSources().generic(), amount);
            }
        }
    }

    @Override
    public void applyImpulse(double vx, double vy, double vz) {
        if (entity != null) {
            entity.push(vx, vy, vz);
            entity.hasImpulse = true;
            if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    @Override
    public void setEquipment(String slot, String itemId, int count) {
        if (!(entity instanceof LivingEntity living) || slot == null) {
            return;
        }
        net.minecraft.world.entity.EquipmentSlot eqSlot = parseEquipmentSlot(slot);
        if (eqSlot == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "SetEquipment ignored: unknown slot '" + slot
                            + "' (expected mainhand/offhand/head/chest/legs/feet)");
            return;
        }
        if (itemId == null || itemId.isBlank() || itemId.equalsIgnoreCase("minecraft:air")) {
            living.setItemSlot(eqSlot, net.minecraft.world.item.ItemStack.EMPTY);
            return;
        }
        ResourceLocation rl = itemId.contains(":")
                ? ResourceLocation.tryParse(itemId)
                : ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
        var itemOpt = rl == null ? java.util.Optional.<net.minecraft.world.item.Item>empty()
                : BuiltInRegistries.ITEM.getOptional(rl);
        if (itemOpt.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "SetEquipment ignored: unknown item '" + itemId + "' for slot '" + slot + "'");
            return;
        }
        living.setItemSlot(eqSlot, new net.minecraft.world.item.ItemStack(itemOpt.get(), Math.max(1, count)));
    }

    @Override
    public String getEquipment(String slot) {
        if (entity instanceof LivingEntity living && slot != null) {
            net.minecraft.world.entity.EquipmentSlot eqSlot = parseEquipmentSlot(slot);
            if (eqSlot != null) {
                var stack = living.getItemBySlot(eqSlot);
                return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
        }
        return "minecraft:air";
    }

    @Override
    public @Nullable java.util.Map<String, Object> getEquipmentData(String slot) {
        if (!(entity instanceof LivingEntity living) || slot == null) {
            return null;
        }
        net.minecraft.world.entity.EquipmentSlot eqSlot = parseEquipmentSlot(slot);
        if (eqSlot == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "GetEquipmentData ignored: unknown slot '" + slot
                            + "' (expected mainhand/offhand/head/chest/legs/feet)");
            return null;
        }
        net.minecraft.world.item.ItemStack stack = living.getItemBySlot(eqSlot);
        return stack.isEmpty() ? null : describeStack(stack);
    }

    /** Shared shape for inventory and equipment reads. */
    static java.util.Map<String, Object> describeStack(net.minecraft.world.item.ItemStack stack) {
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        entry.put("Count", stack.getCount());
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            entry.put("Name", stack.getHoverName().getString());
        }
        if (stack.isDamaged()) {
            entry.put("Damage", stack.getDamageValue());
        }
        return entry;
    }

    private static net.minecraft.world.entity.EquipmentSlot parseEquipmentSlot(String slot) {
        return switch (slot.toLowerCase().trim()) {
            case "mainhand", "main_hand", "hand" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case "offhand", "off_hand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "head", "helmet" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest", "chestplate" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs", "leggings" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet", "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }

    @Override
    public com.luatweaker.api.spatial.RaycastResult raycast(double maxDistance, boolean ignoreLiquids) {
        if (entity != null) {
            String dim = entity.level().dimension().location().toString();
            double eyeX = entity.getX();
            double eyeY = entity.getEyeY();
            double eyeZ = entity.getZ();
            net.minecraft.world.phys.Vec3 look = entity.getLookAngle();
            var params = new com.luatweaker.api.spatial.RaycastParams(
                    maxDistance > 0 ? maxDistance : 64.0,
                    ignoreLiquids,
                    false,
                    java.util.List.of(this)
            );
            return NeoForgeSpatialHelper.raycast(dim, eyeX, eyeY, eyeZ, look.x, look.y, look.z, params);
        }
        return com.luatweaker.api.spatial.RaycastResult.miss(getX(), getY(), getZ());
    }

    @Override
    public void heal(float amount) {
        if (entity instanceof LivingEntity living) {
            living.heal(amount);
        }
    }

    @Override
    public void addEffect(String effectId, int durationTicks, int amplifier) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
            if (effectHolder != null) {
                living.addEffect(new MobEffectInstance(effectHolder, durationTicks, amplifier));
            }
        }
    }

    @Override
    public void removeEffect(String effectId) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            BuiltInRegistries.MOB_EFFECT.getHolder(rl).ifPresent(living::removeEffect);
        }
    }

    @Override
    public void removeAllEffects() {
        if (entity instanceof LivingEntity living) {
            living.removeAllEffects();
        }
    }

    @Override
    public boolean hasEffect(String effectId) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            return BuiltInRegistries.MOB_EFFECT.getHolder(rl).map(living::hasEffect).orElse(false);
        }
        return false;
    }

    @Override
    public boolean isAlive() {
        return entity != null && entity.isAlive();
    }

    @Override
    public void setIgniteSeconds(int seconds) {
        if (entity != null) {
            entity.igniteForSeconds(seconds);
        }
    }

    @Override
    public void extinguish() {
        if (entity != null) {
            entity.clearFire();
        }
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        playSoundFrom(soundId, volume, pitch, "NEUTRAL");
    }

    @Override
    public void playSoundFrom(String soundId, float volume, float pitch, String source) {
        if (entity == null) {
            return;
        }
        ResourceLocation location = com.luatweaker.platform.sound.SoundHelper.parseId(soundId);
        SoundEvent sound = com.luatweaker.platform.sound.SoundHelper.registered(location, "entity " + getType());
        if (sound == null) {
            return;
        }
        SoundSource category = com.luatweaker.platform.sound.SoundHelper.parseSource(source, SoundSource.NEUTRAL);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, category,
                com.luatweaker.platform.sound.SoundHelper.volume(volume),
                com.luatweaker.platform.sound.SoundHelper.pitch(pitch));
    }

    @Override
    public void spawnParticle(String particleId, int count, double speed) {
        if (entity != null && entity.level() instanceof ServerLevel serverLevel && particleId != null) {
            ResourceLocation rl = particleId.contains(":") ? ResourceLocation.parse(particleId) : ResourceLocation.fromNamespaceAndPath("minecraft", particleId);
            ParticleType<?> pType = BuiltInRegistries.PARTICLE_TYPE.get(rl);
            if (pType instanceof net.minecraft.core.particles.SimpleParticleType simple) {
                serverLevel.sendParticles(simple, entity.getX(), entity.getY() + 1.0, entity.getZ(), Math.max(1, count), 0.2, 0.2, 0.2, speed);
            }
        }
    }

    @Override
    public IEntity spawnEntity(String entityId, double offsetX, double offsetY, double offsetZ) {
        if (entity != null && entity.level() instanceof ServerLevel serverLevel && entityId != null) {
            java.util.Optional<net.minecraft.world.entity.EntityType<?>> optType =
                    com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(entityId);
            if (optType.isPresent()) {
                Entity newEntity = optType.get().create(serverLevel);
                if (newEntity != null) {
                    newEntity.moveTo(entity.getX() + offsetX, entity.getY() + offsetY, entity.getZ() + offsetZ, entity.getYRot(), entity.getXRot());
                    serverLevel.addFreshEntity(newEntity);
                    return new NeoForgeEntityWrapper(newEntity);
                }
            }
        }
        return null;
    }

    @Override
    public void teleport(double x, double y, double z) {
        if (entity != null) {
            entity.teleportTo(x, y, z);
        }
    }

    @Override
    public void setMotion(double vx, double vy, double vz) {
        if (entity != null) {
            entity.setDeltaMovement(vx, vy, vz);
            entity.hasImpulse = true;
        }
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        if (entity instanceof net.minecraft.world.entity.PathfinderMob mob) {
            return mob.getNavigation().moveTo(x, y, z, speed);
        }
        return false;
    }

    @Override
    public void addVelocity(double vx, double vy, double vz) {
        if (entity != null) {
            entity.push(vx, vy, vz);
            entity.hasImpulse = true;
        }
    }

    @Override public double getX() { return entity != null ? entity.getX() : 0; }
    @Override public double getY() { return entity != null ? entity.getY() : 0; }
    @Override public double getZ() { return entity != null ? entity.getZ() : 0; }
    @Override public float getYaw() { return entity != null ? entity.getYRot() : 0; }
    @Override public float getPitch() { return entity != null ? entity.getXRot() : 0; }

    @Override
    public void setYaw(float yaw) {
        if (entity != null) entity.setYRot(yaw);
    }

    @Override
    public void setPitch(float pitch) {
        if (entity != null) entity.setXRot(pitch);
    }

    @Override public boolean isSneaking() { return entity != null && entity.isShiftKeyDown(); }
    @Override public void setSneaking(boolean value) { if (entity != null) entity.setShiftKeyDown(value); }
    @Override public boolean isSprinting() { return entity != null && entity.isSprinting(); }
    @Override public void setSprinting(boolean value) { if (entity != null) entity.setSprinting(value); }
    @Override public boolean isGlowing() { return entity != null && entity.isCurrentlyGlowing(); }
    @Override public void setGlowing(boolean value) { if (entity != null) entity.setGlowingTag(value); }
    @Override public boolean isInvulnerable() { return entity != null && entity.isInvulnerable(); }
    @Override public void setInvulnerable(boolean value) { if (entity != null) entity.setInvulnerable(value); }
    @Override public boolean isInWater() { return entity != null && entity.isInWater(); }
    @Override public boolean isInLava() { return entity != null && entity.isInLava(); }
    @Override public boolean isOnGround() { return entity != null && entity.onGround(); }

    @Override public String getCustomName() { return (entity != null && entity.hasCustomName()) ? entity.getCustomName().getString() : ""; }
    @Override public void setCustomName(String name) { if (entity != null && name != null) entity.setCustomName(Component.literal(name)); }
    @Override public boolean isCustomNameVisible() { return entity != null && entity.isCustomNameVisible(); }
    @Override public void setCustomNameVisible(boolean visible) { if (entity != null) entity.setCustomNameVisible(visible); }

    @Override public void addTag(String tag) { if (entity != null && tag != null) entity.addTag(tag); }
    @Override public void removeTag(String tag) { if (entity != null && tag != null) entity.removeTag(tag); }
    @Override public boolean hasTag(String tag) { return entity != null && tag != null && entity.getTags().contains(tag); }

    @Override public void swingArm() {
        if (entity instanceof LivingEntity living) living.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void setPose(String pose) {
        if (entity == null || pose == null || pose.isBlank()) {
            return;
        }
        String clean = pose.strip().toUpperCase();
        try {
            entity.setPose(net.minecraft.world.entity.Pose.valueOf(clean));
        } catch (IllegalArgumentException e) {
            StringBuilder known = new StringBuilder();
            for (net.minecraft.world.entity.Pose p : net.minecraft.world.entity.Pose.values()) {
                if (known.length() > 0) {
                    known.append(", ");
                }
                known.append(p.name());
            }
            throw new IllegalArgumentException("unknown pose '" + pose + "' (use one of: " + known + ")");
        }
    }

    @Override
    public String getPose() {
        if (entity == null) {
            return "STANDING";
        }
        try {
            net.minecraft.world.entity.Pose pose = entity.getPose();
            return pose == null ? "STANDING" : pose.name();
        } catch (Exception e) {
            return "STANDING";
        }
    }

    @Override
    public boolean canSee(IEntity target) {
        if (entity instanceof LivingEntity living && target != null
                && target.getRawEntity() instanceof Entity targetEntity) {
            try {
                return living.hasLineOfSight(targetEntity);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void stopMoving() {
        if (entity instanceof Mob mob) {
            try {
                mob.getNavigation().stop();
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "stopMoving failed: " + e.getMessage());
            }
        } else if (entity != null) {
            entity.setDeltaMovement(0, 0, 0);
        }
    }

    @Override
    public void lookAt(double x, double y, double z) {
        if (entity != null) {
            com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(this, x, y, z);
        }
    }

    @Override
    public void lookAtEntity(@NotNull IEntity target) {
        if (entity != null && target != null) {
            com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(this, target);
        }
    }

    @Override
    public void jump() {
        if (entity instanceof Mob mob) {
            try {
                mob.getJumpControl().jump();
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "jump failed: " + e.getMessage());
            }
        }
    }

    @Override
    public long getGameTime() {
        if (entity != null && entity.level() != null) {
            try {
                return entity.level().getGameTime();
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "getGameTime failed: " + e.getMessage());
            }
        }
        return 0;
    }

    @Override
    public boolean isNavigating() {
        if (entity instanceof Mob mob) {
            try {
                return mob.getNavigation().isInProgress();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override public void setTarget(IEntity target) {
        if (entity instanceof Mob mob) {
            if (target != null && target.getRawEntity() instanceof LivingEntity livingTarget) {
                mob.setTarget(livingTarget);
            } else {
                mob.setTarget(null);
            }
        }
    }

    @Override public IEntity getTarget() {
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            return new NeoForgeEntityWrapper(mob.getTarget());
        }
        return null;
    }

    @Override public boolean isPlayer() { return entity instanceof Player; }
    @Override public boolean isLiving() { return entity instanceof LivingEntity; }

    @Override
    public void shootProjectile(String projectileType, double speed, double inaccuracy) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectile(this, projectileType, speed, inaccuracy);
    }

    @Override
    public IEntity shootProjectileAt(String projectileType, IEntity target, double speed) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectileAt(this, projectileType, target, speed);
    }

    @Override
    public void playAnimation(String animName, double speed, double transition) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.playAnimation(this, animName, speed, transition);
    }

    // ==== Minecraft attributes (generic RPG stats) ====

    @Override
    public @Nullable Double getAttributeValue(String attributeId) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = attributeInstance(attributeId);
        return instance == null ? null : instance.getValue();
    }

    @Override
    public @Nullable Double getAttributeBaseValue(String attributeId) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = attributeInstance(attributeId);
        return instance == null ? null : instance.getBaseValue();
    }

    @Override
    public boolean setAttributeBaseValue(String attributeId, double value) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = attributeInstance(attributeId);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(value);
        return true;
    }

    @Override
    public boolean addAttributeModifier(String attributeId, String modifierId, double amount, String operation) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = attributeInstance(attributeId);
        if (instance == null || modifierId == null || modifierId.isBlank()) {
            return false;
        }
        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op = parseModifierOperation(operation);
        if (op == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "AddAttributeModifier ignored: unknown operation '" + operation
                            + "' (expected ADD_VALUE/ADD_MULTIPLIED_BASE/ADD_MULTIPLIED_TOTAL)");
            return false;
        }
        ResourceLocation rl = modifierId.contains(":")
                ? ResourceLocation.tryParse(modifierId)
                : ResourceLocation.fromNamespaceAndPath("luatweaker", modifierId);
        if (rl == null) {
            return false;
        }
        // addOrReplacePermanentModifier would survive a reload of the entity from NBT twice
        // over; the transient variant is the script-owned layer and is replaced on re-apply.
        instance.addOrUpdateTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(rl, amount, op));
        return true;
    }

    @Override
    public boolean removeAttributeModifier(String attributeId, String modifierId) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = attributeInstance(attributeId);
        if (instance == null || modifierId == null || modifierId.isBlank()) {
            return false;
        }
        ResourceLocation rl = modifierId.contains(":")
                ? ResourceLocation.tryParse(modifierId)
                : ResourceLocation.fromNamespaceAndPath("luatweaker", modifierId);
        return rl != null && instance.removeModifier(rl);
    }

    /** @return the attribute instance, or {@code null} when the entity lacks that attribute */
    private @Nullable net.minecraft.world.entity.ai.attributes.AttributeInstance attributeInstance(String attributeId) {
        if (!(entity instanceof LivingEntity living) || attributeId == null || attributeId.isBlank()) {
            return null;
        }
        ResourceLocation rl = attributeId.contains(":")
                ? ResourceLocation.tryParse(attributeId)
                : ResourceLocation.fromNamespaceAndPath("minecraft", attributeId);
        if (rl == null) {
            return null;
        }
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(rl);
        if (holder.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(com.luatweaker.api.log.LogStage.SYSTEM,
                    "Unknown attribute '" + attributeId + "' on " + getType());
            return null;
        }
        return living.getAttribute(holder.get());
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation parseModifierOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
        }
        try {
            return net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                    .valueOf(operation.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void setAttribute(String key, String value) {
        if (entity == null || key == null) {
            return;
        }
        if (value == null) {
            entity.getPersistentData().remove(key);
        } else {
            entity.getPersistentData().putString(key, value);
        }
    }

    @Override
    public String getAttribute(String key) {
        if (entity != null && key != null && entity.getPersistentData().contains(key)) {
            if (entity.getPersistentData().contains(key, net.minecraft.nbt.Tag.TAG_STRING)) {
                return entity.getPersistentData().getString(key);
            }
            return entity.getPersistentData().get(key).getAsString();
        }
        return null;
    }

    @Override
    @Nullable
    public Object getCustomData(@NotNull String key) {
        if (entity != null && key != null && entity.getPersistentData().contains(key)) {
            net.minecraft.nbt.Tag valTag = entity.getPersistentData().get(key);
            if (valTag instanceof net.minecraft.nbt.NumericTag num) {
                if (valTag instanceof net.minecraft.nbt.ByteTag bt && (bt.getAsByte() == 0 || bt.getAsByte() == 1)) {
                    return bt.getAsByte() == 1;
                }
                return num.getAsDouble();
            } else if (valTag instanceof net.minecraft.nbt.StringTag str) {
                return str.getAsString();
            } else if (valTag != null) {
                return valTag.getAsString();
            }
        }
        return null;
    }

    @Override
    public void setCustomData(@NotNull String key, @Nullable Object value) {
        if (entity == null || key == null) {
            return;
        }
        net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
        if (value == null) {
            tag.remove(key);
            return;
        }
        if (value instanceof com.luatweaker.api.vm.ILuaValue lv) {
            if (lv.isNil()) {
                tag.remove(key);
            } else if (lv.isBoolean()) {
                tag.putBoolean(key, lv.asBoolean());
            } else if (lv.isNumber()) {
                tag.putDouble(key, lv.asDouble());
            } else if (lv.isString()) {
                tag.putString(key, lv.asString());
            } else {
                // Storing "table: 0x..." silently loses the value; make the script see the error.
                throw new IllegalArgumentException("SetCustomData('" + key
                        + "') only supports strings, numbers and booleans; got a Lua "
                        + (lv.isTable() ? "table" : lv.isFunction() ? "function" : "value"));
            }
            return;
        }
        if (value instanceof Boolean flag) {
            tag.putBoolean(key, flag);
        } else if (value instanceof Number num) {
            tag.putDouble(key, num.doubleValue());
        } else if (value instanceof CharSequence text) {
            tag.putString(key, text.toString());
        } else {
            throw new IllegalArgumentException("SetCustomData('" + key
                    + "') only supports strings, numbers and booleans; got "
                    + value.getClass().getSimpleName());
        }
    }

    @Override
    public double getMotionX() {
        return entity != null ? entity.getDeltaMovement().x : 0;
    }

    @Override
    public double getMotionY() {
        return entity != null ? entity.getDeltaMovement().y : 0;
    }

    @Override
    public double getMotionZ() {
        return entity != null ? entity.getDeltaMovement().z : 0;
    }

    @Override
    public void remove() {
        if (entity != null) {
            entity.discard();
        }
    }

    @Override
    public Object getRawEntity() {
        return entity;
    }
}
