package com.luatweaker.math;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure geometry checks for the {@code Region3} box math. */
class Region3MathTest {

    private static Region3Math.Box box(double x1, double y1, double z1,
                                       double x2, double y2, double z2) {
        return Region3Math.create(x1, y1, z1, x2, y2, z2);
    }

    @Test
    void cornersAreNormalisedRegardlessOfArgumentOrder() {
        Region3Math.Box region = box(10, 20, 30, 0, 0, 0);

        assertAll("corners must be normalised",
                () -> assertEquals(0, region.minX(), "minX comes from the smaller corner"),
                () -> assertEquals(10, region.maxX(), "maxX comes from the larger corner"),
                () -> assertEquals(20, region.maxY(), "maxY comes from the larger corner"),
                () -> assertEquals(30, region.maxZ(), "maxZ comes from the larger corner"));
    }

    @Test
    void containsIsInclusiveOnEveryFace() {
        Region3Math.Box region = box(0, 0, 0, 10, 10, 10);

        assertAll("containment must include the surface",
                () -> Assertions.assertTrue(region.contains(0, 0, 0), "the minimum corner is inside"),
                () -> Assertions.assertTrue(region.contains(10, 10, 10), "the maximum corner is inside"),
                () -> Assertions.assertTrue(region.contains(5, 5, 5), "the middle is inside"),
                () -> Assertions.assertFalse(region.contains(10.0001, 5, 5), "a point just outside is rejected"),
                () -> Assertions.assertFalse(region.contains(-0.0001, 5, 5), "a point just below is rejected"));
    }

    @Test
    void intersectsRequiresOverlapOnEveryAxis() {
        Region3Math.Box first = box(0, 0, 0, 10, 10, 10);

        assertAll("intersection must require overlap on every axis",
                () -> Assertions.assertTrue(first.intersects(box(5, 5, 5, 15, 15, 15)),
                        "overlapping boxes intersect"),
                () -> Assertions.assertTrue(first.intersects(box(10, 10, 10, 20, 20, 20)),
                        "touching boxes intersect"),
                () -> Assertions.assertFalse(first.intersects(box(11, 0, 0, 20, 10, 10)),
                        "a gap on X means no intersection"),
                () -> Assertions.assertFalse(first.intersects(box(0, 11, 0, 10, 20, 10)),
                        "a gap on Y means no intersection"));
    }

    @Test
    void volumeExpansionUnionAndCenter() {
        Region3Math.Box region = box(0, 0, 0, 2, 3, 4);
        Region3Math.Box expanded = region.expand(1);
        Region3Math.Box union = region.union(box(5, 5, 5, 6, 6, 6));
        Region3Math.Point center = region.center();
        Region3Math.Point size = region.size();

        assertAll("box metrics must match the geometry",
                () -> assertEquals(24.0, region.volume(), 1.0E-9, "volume is width * height * depth"),
                () -> assertEquals(-1, expanded.minX(), "expansion moves the minimum outward"),
                () -> assertEquals(3, expanded.maxX(), "expansion moves the maximum outward"),
                () -> assertEquals(0, union.minX(), "union keeps the lower bound"),
                () -> assertEquals(6, union.maxX(), "union takes the higher bound"),
                () -> assertEquals(1, center.x(), 1.0E-9, "center X"),
                () -> assertEquals(1.5, center.y(), 1.0E-9, "center Y"),
                () -> assertEquals(2, center.z(), 1.0E-9, "center Z"),
                () -> assertEquals(2, size.x(), 1.0E-9, "size X"));
    }

    @Test
    void nonFiniteCoordinatesFailLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> Region3Math.create(0, 0, 0, Double.NaN, 1, 1),
                "NaN coordinates must be rejected instead of silently producing a broken box");
    }
}
