package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the WorldGen build-limit, sea level, coordinate-scale and identifier
 * validation that {@link DimensionServiceImpl#create} enforces.
 */
class DimensionBuildRangeValidationTest extends DimensionServiceTestBase {

    private static final String MIN_HEIGHT = "minHeight";
    private static final String MAX_HEIGHT = "maxHeight";

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void createRejectsOutOfRangeCoordinateScales() {
        for (double scale : List.of(0.0, -2.0, 1.0E-9, 4.0E7)) {
            ILuaTable config = engine.createTable();
            config.rawset("coordinateScale", scale);
            assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                    "coordinate scale must be rejected: " + scale);
        }
    }

    @Test
    void createRejectsSeaLevelOutsideHeightRange() {
        for (int seaLevel : List.of(96, -1)) {
            ILuaTable config = engine.createTable();
            config.rawset(MIN_HEIGHT, 0);
            config.rawset(MAX_HEIGHT, 96);
            config.rawset("seaLevel", seaLevel);
            assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                    "sea level outside the build range must be rejected: " + seaLevel);
        }
    }

    @Test
    void createRejectsUnalignedBuildHeights() {
        ILuaTable unalignedMin = engine.createTable();
        unalignedMin.rawset(MIN_HEIGHT, -60);
        unalignedMin.rawset(MAX_HEIGHT, 320);
        ILuaTable unalignedMax = engine.createTable();
        unalignedMax.rawset(MIN_HEIGHT, -64);
        unalignedMax.rawset(MAX_HEIGHT, 300);

        for (ILuaTable config : List.of(unalignedMin, unalignedMax)) {
            assertTrue(DimensionExceptions.illegalArgumentMessage(() -> service.create(DIMENSION_ID, config))
                            .contains("multiple of 16"),
                    "build bounds must be a multiple of 16");
        }
    }

    @Test
    void createRejectsBuildTopAboveVanillaLimit() {
        ILuaTable config = engine.createTable();
        config.rawset(MIN_HEIGHT, 0);
        config.rawset(MAX_HEIGHT, 2032 + 16);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "a build top above the vanilla limit must be rejected");
    }

    @Test
    void createRejectsLogicalHeightAboveHeight() {
        ILuaTable config = engine.createTable();
        config.rawset(MIN_HEIGHT, -64);
        config.rawset(MAX_HEIGHT, 320);
        config.rawset("logicalHeight", 400);

        assertTrue(DimensionExceptions.illegalArgumentMessage(() -> service.create(DIMENSION_ID, config))
                        .contains("logicalHeight"),
                "logicalHeight above maxHeight - minHeight must be rejected");
    }

    @Test
    void createRejectsInfiniburnWithoutTagPrefix() {
        ILuaTable config = engine.createTable();
        config.rawset("infiniburn", "minecraft:infiniburn_overworld");

        assertTrue(DimensionExceptions.illegalArgumentMessage(() -> service.create(DIMENSION_ID, config))
                        .contains("block tag"),
                "infiniburn must be a block tag reference");
    }

    @Test
    void createRejectsBareDimensionId() {
        assertTrue(DimensionExceptions.illegalArgumentMessage(
                        () -> service.create("skylands", engine.createTable()))
                        .contains("namespaced"),
                "a dimension id without a namespace must be rejected");
    }
}
