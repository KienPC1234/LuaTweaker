package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Entity rig and default-goal validation with migration pointers. */
class EntityRigGoalsTest extends ContentFixture {

    private static final String RIG_BAD_SCRIPT =
            "startup:createEntity('test:rig_bad', function(e) e:rig('mech') end)\n";
    private static final String RIG_BAD_CHUNK = "RigBadTest";

    @Test
    void rigDefaultsToParent() {
        contentService.createEntity("test:rig_default", builder -> { });
        IEntityBuilder builder = entity("test:rig_default");

        assertEquals("parent", builder.getRig(), "the default rig must be the parent");
    }

    @Test
    void rigRejectsUnknown() {
        assertThrows(RuntimeException.class,
                () -> engine.executeStringOrThrow(RIG_BAD_SCRIPT, RIG_BAD_CHUNK),
                "an unknown rig must be rejected");
    }

    @Test
    void rigErrorMentionsRig() {
        assertTrue(ScriptFailures.message(engine, RIG_BAD_SCRIPT, RIG_BAD_CHUNK).contains("rig"),
                "the error must point at the rig system");
    }

    @Test
    void goalsNormalizeRig() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:goals_ok', function(e) e:rig('humanoid'):defaultGoals('monster') end)\n",
                "GoalsOkTest");

        assertEquals("humanoid", entity("test:goals_ok").getRig(),
                "the rig must normalize to humanoid");
    }

    @Test
    void goalsNormalizeGoals() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:goals_ok', function(e) e:rig('humanoid'):defaultGoals('monster') end)\n",
                "GoalsOkTest");

        assertEquals("monster", entity("test:goals_ok").getDefaultGoals(),
                "the default goals must normalize to monster");
    }

    @Test
    void goalsRejectUnknown() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:goals_bad', function(e) e:defaultGoals('sentient') end)\n",
                "GoalsBadTest"),
                "unknown default goals must be rejected");
    }
}
