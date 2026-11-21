package com.luatweaker.content;

import java.util.List;

import com.luatweaker.api.content.EntityDrop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Dead model APIs point at rig; drops append instead of overwriting. */
class EntityModelDropsTest extends ContentFixture {

    private static final String MODEL_CHUNK = "ModelDeadTest";
    private static final String BBMODEL_CHUNK = "BbmodelDeadTest";
    private static final String DROPS_CHUNK = "DropsAppendTest";
    private static final String DROPS_SCRIPT =
            "startup:createEntity('test:drops', function(e)\n"
                    + "  e:drop('luatweaker:crystal_shard', 10, 20)\n"
                    + "  e:drop('luatweaker:crystal_staff', 1, 1)\n"
                    + "end)\n";
    private static final String SHARD = "luatweaker:crystal_shard";
    private static final String MODEL_SCRIPT =
            "startup:createEntity('test:model_dead', function(e) e:model('some/path') end)\n";
    private static final String BBMODEL_SCRIPT =
            "startup:createEntity('test:bbmodel_dead', function(e) e:bbmodel('some/path') end)\n";

    private List<EntityDrop> drops() {
        engine.executeStringOrThrow(DROPS_SCRIPT, DROPS_CHUNK);
        return entity("test:drops").getDrops();
    }

    @Test
    void modelThrows() {
        assertThrows(RuntimeException.class,
                () -> engine.executeStringOrThrow(MODEL_SCRIPT, MODEL_CHUNK),
                "model() must throw");
    }

    @Test
    void modelErrorMentionsRig() {
        assertTrue(ScriptFailures.message(engine, MODEL_SCRIPT, MODEL_CHUNK).contains("rig"),
                "model() error must point at rig");
    }

    @Test
    void bbmodelThrows() {
        assertThrows(RuntimeException.class,
                () -> engine.executeStringOrThrow(BBMODEL_SCRIPT, BBMODEL_CHUNK),
                "bbmodel() must throw");
    }

    @Test
    void bbmodelErrorMentionsRig() {
        assertTrue(ScriptFailures.message(engine, BBMODEL_SCRIPT, BBMODEL_CHUNK).contains("rig"),
                "bbmodel() error must point at rig");
    }

    @Test
    void dropsAppend() {
        assertEquals(2, drops().size(),
                "each :drop call must add an entry (second call used to wipe the first)");
    }

    @Test
    void firstDropItem() {
        assertEquals(SHARD, drops().get(0).itemId(), "the first drop must be the shard");
    }

    @Test
    void firstDropMin() {
        assertEquals(10, drops().get(0).minCount(), "the first drop min must be 10");
    }

    @Test
    void firstDropMax() {
        assertEquals(20, drops().get(0).maxCount(), "the first drop max must be 20");
    }

    @Test
    void secondDropItem() {
        assertEquals("luatweaker:crystal_staff", drops().get(1).itemId(),
                "the second drop must be the staff");
    }
}
