package com.luatweaker.platform.integration.jei;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiCategoryTest {

    @BeforeEach
    void setup() {
        CustomJeiRegistry.clear();
    }

    private static Map<String, Object> categorySpec() {
        return Map.of(
                "id", "crystal_infusion",
                "title", "Crystal Infusion",
                "width", 150,
                "height", 65,
                "catalysts", List.of("minecraft:furnace"),
                "slots", List.of(
                        Map.of("x", 20, "y", 20, "isInput", true),
                        Map.of("x", 100, "y", 20, "isInput", false)
                ),
                "arrowX", 55,
                "arrowY", 20
        );
    }

    private static CustomJeiRegistry.CustomCategorySpec registeredCategory() {
        CustomJeiRegistry.registerCategoryFromSpec(categorySpec());
        return CustomJeiRegistry.getCategories().iterator().next();
    }

    @Test
    void registeredCategoryCountIsOne() {
        CustomJeiRegistry.registerCategoryFromSpec(categorySpec());

        assertEquals(1, CustomJeiRegistry.getCategories().size(),
                "registering one category spec must populate exactly one category");
    }

    @Test
    void registeredCategoryKeepsUid() {
        assertEquals("luatweaker:crystal_infusion", registeredCategory().uid().toString(),
                "the category id must default to the luatweaker namespace");
    }

    @Test
    void registeredCategoryKeepsTitle() {
        assertEquals("Crystal Infusion", registeredCategory().title().getString(),
                "the category title must round-trip through the spec");
    }

    @Test
    void registeredCategoryKeepsWidth() {
        assertEquals(150, registeredCategory().width(),
                "the category width must round-trip through the spec");
    }

    @Test
    void registeredCategoryKeepsHeight() {
        assertEquals(65, registeredCategory().height(),
                "the category height must round-trip through the spec");
    }

    @Test
    void registeredCategoryKeepsBothSlots() {
        assertEquals(2, registeredCategory().slots().size(),
                "both the input and the output slot must be registered");
    }

    @Test
    void registeredCategoryKeepsArrowX() {
        assertEquals(55, registeredCategory().arrowX(),
                "the arrow position must round-trip through the spec");
    }
}
