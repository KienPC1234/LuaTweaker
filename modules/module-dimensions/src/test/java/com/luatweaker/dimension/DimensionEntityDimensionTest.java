package com.luatweaker.dimension;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformDimension;
import com.luatweaker.api.pal.Platform;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies entity dimension lookup delegates to the platform with the raw entity. */
class DimensionEntityDimensionTest extends DimensionServiceTestBase {

    @Test
    void entityDimensionComesFromPlatform() {
        AtomicReference<Object> rawSeen = new AtomicReference<>();
        Platform.setDimension(new IPlatformDimension() {
            @Override public boolean teleportToDimension(Object rawPlayerEntity, String dimensionId) {
                return true;
            }
            @Override public Object getLevel(String dimensionId) { return null; }
            @Override public String getEntityDimensionId(Object rawEntity) {
                rawSeen.set(rawEntity);
                return "minecraft:overworld";
            }
        });

        String dimension = service.getEntityDimension(playerWithRaw("mock-raw"));

        assertEquals(List.of("minecraft:overworld", "mock-raw"), List.of(dimension, rawSeen.get()),
                "entity dimension must come from the platform with the raw entity");
    }

    @Test
    void entityDimensionRejectsInvalidEntities() {
        for (IEntity entity : Arrays.asList(null, playerWithRaw(null))) {
            assertThrows(IllegalArgumentException.class, () -> service.getEntityDimension(entity),
                    "entity dimension must reject a null entity or raw handle");
        }
    }
}
