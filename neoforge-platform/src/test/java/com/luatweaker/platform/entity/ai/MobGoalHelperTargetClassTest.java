package com.luatweaker.platform.entity.ai;

import com.luatweaker.api.entity.IEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MobGoalHelperTargetClassTest {

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
    void resolveTargetClass_MapsKnownNames() {
        IEntity entity = mockEntity();
        assertEquals(net.minecraft.world.entity.player.Player.class,
                MobGoalHelper.resolveTargetClass("player", entity));
        assertEquals(net.minecraft.world.entity.monster.Monster.class,
                MobGoalHelper.resolveTargetClass("MONSTERS", entity));
        assertEquals(net.minecraft.world.entity.animal.Animal.class,
                MobGoalHelper.resolveTargetClass("animal", entity));
        assertEquals(net.minecraft.world.entity.npc.AbstractVillager.class,
                MobGoalHelper.resolveTargetClass("villager", entity));
        assertEquals(net.minecraft.world.entity.animal.IronGolem.class,
                MobGoalHelper.resolveTargetClass("golem", entity));
        assertEquals(net.minecraft.world.entity.LivingEntity.class,
                MobGoalHelper.resolveTargetClass("living", entity));
    }
}
