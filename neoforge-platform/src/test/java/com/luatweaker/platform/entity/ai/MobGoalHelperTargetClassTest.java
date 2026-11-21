package com.luatweaker.platform.entity.ai;

import com.luatweaker.api.entity.IEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Target-class names must resolve to the right vanilla entity classes. */
class MobGoalHelperTargetClassTest {

    static IEntity mockEntity() {
        return new IEntity() {
            @Override public String getType() { return "test:mob"; }
            @Override public String getName() { return "mob"; }
            @Override public float getHealth() { return 10.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 10.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return null; }
        };
    }

    @Test
    void playerNameResolvesToPlayer() {
        assertEquals(net.minecraft.world.entity.player.Player.class,
                MobGoalHelper.resolveTargetClass("player", mockEntity()),
                "the player target name must resolve to the vanilla Player class");
    }

    @Test
    void monstersNameResolvesToMonster() {
        assertEquals(net.minecraft.world.entity.monster.Monster.class,
                MobGoalHelper.resolveTargetClass("MONSTERS", mockEntity()),
                "the case-insensitive monsters name must resolve to the vanilla Monster class");
    }

    @Test
    void animalNameResolvesToAnimal() {
        assertEquals(net.minecraft.world.entity.animal.Animal.class,
                MobGoalHelper.resolveTargetClass("animal", mockEntity()),
                "the animal target name must resolve to the vanilla Animal class");
    }

    @Test
    void villagerNameResolvesToVillager() {
        assertEquals(net.minecraft.world.entity.npc.AbstractVillager.class,
                MobGoalHelper.resolveTargetClass("villager", mockEntity()),
                "the villager target name must resolve to the vanilla villager class");
    }

    @Test
    void golemNameResolvesToIronGolem() {
        assertEquals(net.minecraft.world.entity.animal.IronGolem.class,
                MobGoalHelper.resolveTargetClass("golem", mockEntity()),
                "the golem target name must resolve to the vanilla IronGolem class");
    }

    @Test
    void livingNameResolvesToLivingEntity() {
        assertEquals(net.minecraft.world.entity.LivingEntity.class,
                MobGoalHelper.resolveTargetClass("living", mockEntity()),
                "the living target name must resolve to the vanilla LivingEntity class");
    }
}
