package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skyland islands must float high with thin slabs over void to the world bottom. */
class SkylandsSlabTest {

    private static final int SCAN_COLUMNS = 300;
    private static final int ISLAND_MIN_HEIGHT = 180;
    private static final int ISLAND_MAX_HEIGHT = 269;
    private static final int VOID_MARGIN = 1;
    private static final int SLAB_THICKNESS = 20;
    private static final int WORLD_BOTTOM_MARGIN = 2;
    private static final String AIR_BLOCK = "minecraft:air";

    private record IslandColumn(int height, boolean voidColumn, Integer topAir, Integer lowestAir) {
    }

    private record IslandScan(boolean voidColumn, int height, Integer topAir, Integer lowestAir) {
    }

    private static IslandScan scanColumn(DimensionServiceImpl service,
            DimensionConfig skylands, int index) {
        DimensionServiceImpl.SurfaceResult surface = service.computeSurface(
                DimensionShowcaseFixture.SKYLANDS_ID, index * 5, index * 9, 0);
        if (surface.height() <= skylands.minHeight() + VOID_MARGIN) {
            return new IslandScan(true, surface.height(), null, null);
        }
        // Island slab thickness comes from the Lua picker (air below the slab).
        Map<Integer, String> overrides = service.computeBlockOverrides(
                DimensionShowcaseFixture.SKYLANDS_ID, index * 5, index * 9,
                surface.height(), skylands.minHeight(), 320);
        Integer lowestAir = null;
        Integer topAir = null;
        for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
            if (AIR_BLOCK.equals(entry.getValue())) {
                if (lowestAir == null || entry.getKey() < lowestAir) {
                    lowestAir = entry.getKey();
                }
                if (topAir == null || entry.getKey() > topAir) {
                    topAir = entry.getKey();
                }
            }
        }
        return new IslandScan(false, surface.height(), topAir, lowestAir);
    }

    private static List<IslandColumn> scanIslands() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig skylands = DimensionShowcaseFixture.getSkylands();
        List<IslandColumn> columns = new ArrayList<>();
        for (int index = 0; index < SCAN_COLUMNS; index++) {
            IslandScan scan = scanColumn(service, skylands, index);
            columns.add(new IslandColumn(scan.height(), scan.voidColumn(), scan.topAir(),
                    scan.lowestAir()));
        }
        return columns;
    }

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @Test
    void islandsExist() {
        assertTrue(scanIslands().stream().anyMatch(column -> !column.voidColumn()),
                "skylands must produce islands");
    }

    @Test
    void voidExistsBetweenIslands() {
        assertTrue(scanIslands().stream().anyMatch(IslandColumn::voidColumn),
                "skylands must produce void between islands");
    }

    @Test
    void thinIslandsExist() {
        assertTrue(scanIslands().stream().anyMatch(column -> column.topAir() != null),
                "island slabs must float high above the world bottom");
    }

    @Test
    void islandHeightsStayInRange() {
        for (IslandColumn column : scanIslands()) {
            if (!column.voidColumn()) {
                assertTrue(column.height() >= ISLAND_MIN_HEIGHT
                                && column.height() <= ISLAND_MAX_HEIGHT,
                        "island top out of range: " + column.height());
            }
        }
    }

    @Test
    void islandSlabsStayThin() {
        for (IslandColumn column : scanIslands()) {
            if (column.topAir() != null) {
                assertTrue(column.topAir() >= column.height() - SLAB_THICKNESS,
                        "island slab must be thin: top=" + column.height()
                                + " airUpTo=" + column.topAir());
            }
        }
    }

    @Test
    void voidExtendsToTheWorldBottom() {
        DimensionConfig skylands = DimensionShowcaseFixture.getSkylands();
        for (IslandColumn column : scanIslands()) {
            if (column.topAir() != null) {
                assertTrue(column.lowestAir() <= skylands.minHeight() + WORLD_BOTTOM_MARGIN,
                        "void must extend to the world bottom");
            }
        }
    }
}
