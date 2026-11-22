package com.luatweaker.content;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/** Entity builder validation: loud failures instead of silent corruption. */
class EntityBuilderValidationTest {

    private static final String ID = "test:mob";
    private static final String BOSS_TITLE = "Boss";

    private record Case(String label, Consumer<EntityBuilderImpl> arrange) {
        void run() {
            arrange.accept(new EntityBuilderImpl(ID));
        }
    }

    private static EntityBuilderImpl fresh(Consumer<EntityBuilderImpl> arrange) {
        EntityBuilderImpl builder = new EntityBuilderImpl(ID);
        arrange.accept(builder);
        return builder;
    }

    @Test
    void invalidCategoriesThrow() {
        List<Case> cases = List.of(
                new Case("typo category", builder -> builder.category("MOSTER")),
                new Case("blank-ish category", builder -> builder.category(" monster ")),
                new Case("unknown category", builder -> builder.category("BOSS")));

        for (Case current : cases) {
            assertThrows(IllegalArgumentException.class, current::run, current.label());
        }
    }

    @Test
    void validCategoriesStick() {
        EntityBuilderImpl builder = fresh(mob -> mob.category("monster"));

        assertEquals("MONSTER", builder.getCategory(), "valid category must be stored upper-cased");
    }

    @Test
    void nonFiniteStatsThrow() {
        List<Case> cases = List.of(
                new Case("NaN dimensions", builder -> builder.dimensions(Float.NaN, 1.8f)),
                new Case("infinite dimensions", builder -> builder.dimensions(0.6f, Float.POSITIVE_INFINITY)),
                new Case("NaN health", builder -> builder.maxHealth(Double.NaN)),
                new Case("infinite speed", builder -> builder.movementSpeed(Double.POSITIVE_INFINITY)),
                new Case("NaN damage", builder -> builder.attackDamage(Double.NaN)),
                new Case("NaN range", builder -> builder.followRange(Double.NaN)),
                new Case("NaN armor", builder -> builder.armor(Double.NaN)),
                new Case("infinite knockback", builder -> builder.knockbackResistance(Double.POSITIVE_INFINITY)));

        for (Case current : cases) {
            assertThrows(IllegalArgumentException.class, current::run, current.label());
        }
    }

    @Test
    void blankDropItemIdThrows() {
        EntityBuilderImpl builder = new EntityBuilderImpl(ID);

        assertThrows(IllegalArgumentException.class, () -> builder.drop("  ", 1, 1),
                "blank drop item ids must be rejected at startup");
    }

    @Test
    void unknownBossBarValuesThrow() {
        assertAll("unknown boss bar values must be rejected",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EntityBuilderImpl(ID).bossBar(BOSS_TITLE, "GOLD", "PROGRESS"),
                        "unknown boss bar color must be rejected"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EntityBuilderImpl(ID).bossBar(BOSS_TITLE, "RED", "NOTCHED_5"),
                        "unknown boss bar overlay must be rejected"));
    }

    @Test
    void validBossBarValuesStick() {
        EntityBuilderImpl builder = fresh(mob -> mob.bossBar(BOSS_TITLE, "blue", "notched_10"));

        assertAll("boss bar values must be normalized upper-case",
                () -> assertEquals("BLUE", builder.getBossBarColor(), "color"),
                () -> assertEquals("NOTCHED_10", builder.getBossBarOverlay(), "overlay"));
    }

    @Test
    void attributeFlagsTrackExplicitSets() {
        EntityBuilderImpl builder = fresh(mob -> mob.maxHealth(40).movementSpeed(0.4)
                .attackDamage(7).followRange(48).armor(4).knockbackResistance(0.5));

        assertTrue(builder.hasExplicitMaxHealth() && builder.hasExplicitMovementSpeed()
                && builder.hasExplicitAttackDamage() && builder.hasExplicitFollowRange()
                && builder.hasExplicitArmor() && builder.hasExplicitKnockbackResistance(),
                "all six stats must be flagged explicit after being set");
    }

    @Test
    void attributeFlagsDefaultToUnset() {
        EntityBuilderImpl builder = new EntityBuilderImpl(ID);

        assertTrue(!builder.hasExplicitMaxHealth()
                && !builder.hasExplicitMovementSpeed()
                && !builder.hasExplicitAttackDamage()
                && !builder.hasExplicitFollowRange()
                && !builder.hasExplicitArmor()
                && !builder.hasExplicitKnockbackResistance(),
                "fresh builder must not flag any attribute as explicit");
    }
}
