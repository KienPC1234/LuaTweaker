package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;

/** Shared hooked-entity arrange plus a minimal entity fake. */
class EntityHookFixture extends ContentFixture {

    private static final String HOOKS_CHUNK = "HooksTest";
    private static final String HOOKS_SCRIPT =
            "startup:createEntity('test:hooked', function(e)\n"
                    + "  e:rig('humanoid')\n"
                    + "  e:tickRate(2)\n"
                    + "  e:onTick(function(ent) ticked = (ticked or 0) + 1 end)\n"
                    + "  e:onHurt(function(ent, amount, attacker) return amount * 0.5 end)\n"
                    + "  e:onDeath(function(ent, killer) return true end)\n"
                    + "  e:onInteract(function(ent, player) return true end)\n"
                    + "end)\n";

    IEntityBuilder hooked() {
        engine.executeStringOrThrow(HOOKS_SCRIPT, HOOKS_CHUNK);
        return entity("test:hooked");
    }

    static IEntity mockEntity(String type) {
        return new IEntity() {
            @Override public String getType() { return type; }
            @Override public String getName() { return type; }
            @Override public float getHealth() { return 20.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 20.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return null; }
        };
    }
}
