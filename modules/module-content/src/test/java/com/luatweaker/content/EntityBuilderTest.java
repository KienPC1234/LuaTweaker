package com.luatweaker.content;

import com.luatweaker.api.content.EntityDrop;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EntityBuilderTest {

    private ILuaEngine engine;
    private ContentServiceImpl contentService;

    @BeforeEach
    public void setUp() {
        engine = LuaEngineFactory.create(false);
        contentService = new ContentServiceImpl();
        ContentLuaBinding.registerBindings(engine, contentService,
                new StorageServiceImpl(new File("build/tmp/test_storage.json")), new DatapackServiceImpl());
    }

    private IEntityBuilder entity(String id) {
        return contentService.getRegisteredEntities().stream()
                .filter(b -> id.equals(b.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entity must be registered: " + id));
    }

    private void createBlank(String id) {
        contentService.createEntity(id, builder -> { });
    }

    private static IEntity mockEntity(String type) {
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

    @Test
    public void rig_DefaultsToParentAndRejectsUnknown() {
        createBlank("test:rig_default");
        IEntityBuilder builder = entity("test:rig_default");
        assertEquals("parent", builder.getRig());

        RuntimeException error = assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:rig_bad', function(e) e:rig('mech') end)\n", "RigBadTest"));
        assertTrue(error.getMessage().contains("rig"), "error was: " + error.getMessage());
    }

    @Test
    public void defaultGoals_NormalizesAndRejectsUnknown() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:goals_ok', function(e) e:rig('humanoid'):defaultGoals('monster') end)\n",
                "GoalsOkTest");
        IEntityBuilder builder = entity("test:goals_ok");
        assertEquals("humanoid", builder.getRig());
        assertEquals("monster", builder.getDefaultGoals());

        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:goals_bad', function(e) e:defaultGoals('sentient') end)\n",
                "GoalsBadTest"));
    }

    @Test
    public void modelAndBbmodel_ThrowWithMigrationPointer() {
        RuntimeException modelError = assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:model_dead', function(e) e:model('some/path') end)\n", "ModelDeadTest"));
        assertTrue(modelError.getMessage().contains("rig"), "model() error must point at rig: " + modelError.getMessage());

        RuntimeException bbError = assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:bbmodel_dead', function(e) e:bbmodel('some/path') end)\n",
                "BbmodelDeadTest"));
        assertTrue(bbError.getMessage().contains("rig"), "bbmodel() error must point at rig: " + bbError.getMessage());
    }

    @Test
    public void drops_AppendInsteadOfOverwrite() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:drops', function(e)\n"
                        + "  e:drop('luatweaker:crystal_shard', 10, 20)\n"
                        + "  e:drop('luatweaker:crystal_staff', 1, 1)\n"
                        + "end)\n",
                "DropsAppendTest");
        List<EntityDrop> drops = entity("test:drops").getDrops();
        assertEquals(2, drops.size(), "each :drop call must add an entry (second call used to wipe the first)");
        assertEquals("luatweaker:crystal_shard", drops.get(0).itemId());
        assertEquals(10, drops.get(0).minCount());
        assertEquals(20, drops.get(0).maxCount());
        assertEquals("luatweaker:crystal_staff", drops.get(1).itemId());
    }

    @Test
    public void renderScaleAndTickRate_ValidateRanges() {
        createBlank("test:tuning");
        IEntityBuilder builder = entity("test:tuning");
        assertEquals(1.0, builder.getRenderScale());
        assertEquals(5, builder.getTickRate());

        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:scale_bad', function(e) e:renderScale(0) end)\n", "ScaleBadTest"));

        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:tick_bad', function(e) e:tickRate(0) end)\n", "TickBadTest"));
    }

    @Test
    public void experience_IsExplicitOnlyWhenSet() {
        createBlank("test:xp_default");
        IEntityBuilder builder = entity("test:xp_default");
        assertFalse(builder.hasExplicitExperience(), "default experience must not override vanilla XP");

        engine.executeStringOrThrow(
                "startup:createEntity('test:xp_set', function(e) e:experience(200) end)\n", "XpSetTest");
        IEntityBuilder withXp = entity("test:xp_set");
        assertTrue(withXp.hasExplicitExperience());
        assertEquals(200, withXp.getExperience());
    }

    @Test
    public void hooks_StoredAndInvokedThroughLua() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:hooked', function(e)\n"
                        + "  e:rig('humanoid')\n"
                        + "  e:tickRate(2)\n"
                        + "  e:onTick(function(ent) ticked = (ticked or 0) + 1 end)\n"
                        + "  e:onHurt(function(ent, amount, attacker) return amount * 0.5 end)\n"
                        + "  e:onDeath(function(ent, killer) return true end)\n"
                        + "  e:onInteract(function(ent, player) return true end)\n"
                        + "end)\n",
                "HooksTest");
        IEntityBuilder builder = entity("test:hooked");
        assertNotNull(builder.getOnTickHandler());
        assertNotNull(builder.getOnHurtHandler());
        assertNotNull(builder.getOnDeathHandler());
        assertNotNull(builder.getOnInteractHandler());
        assertEquals(2, builder.getTickRate());

        IEntity mock = mockEntity("luatweaker:test_hooked");
        builder.getOnTickHandler().handle(mock);
        engine.executeStringOrThrow("assert(ticked == 1, 'onTick must run the Lua callback')", "TickFiredTest");

        Float reduced = builder.getOnHurtHandler().handle(mock, 10.0f, null);
        assertNotNull(reduced, "a numeric Lua return must replace the damage");
        assertEquals(5.0f, reduced, 0.001f);

        assertTrue(builder.getOnDeathHandler().handle(mock, null), "death hook true must cancel");
    }

    @Test
    public void onHurt_NilReturnKeepsDamage() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:hurt_nil', function(e)\n"
                        + "  e:onHurt(function(ent, amount, attacker) return nil end)\n"
                        + "end)\n",
                "HurtNilTest");
        assertNull(entity("test:hurt_nil").getOnHurtHandler().handle(mockEntity("x"), 8.0f, null),
                "nil Lua return must keep the original damage");
    }
}
