package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Portal mapping must resolve the crystal portal to the crystal realm. */
class RealmPortalTest {

    private static DimensionServiceImpl service;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        service = DimensionShowcaseFixture.getService();
    }

    @Test
    void portalMapLinksCrystalPortal() {
        assertEquals(DimensionShowcaseFixture.REALM_ID,
                service.getPortals().get("luatweaker:crystal_portal"),
                "the crystal portal must map to the crystal realm");
    }

    @Test
    void portalTargetResolvesCrystalPortal() {
        assertEquals(DimensionShowcaseFixture.REALM_ID,
                service.getPortalTarget("luatweaker:crystal_portal"),
                "the crystal portal target must resolve to the crystal realm");
    }
}
