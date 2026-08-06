package com.luatweaker.api.content;

/**
 * A boolean block-state variant pair for Lua-configured blocks.
 *
 * <p>When set, the engine automatically registers a {@code BooleanProperty} with
 * the given name on the block and generates the blockstate JSON plus the two
 * cube-all models (no hand-written JSON needed). The state is toggled at runtime
 * from Lua via {@code World:SetBlockState(x, y, z, "ns:id", { running = true })}.
 *
 * @param property     property name, lowercase letters/digits/underscores (e.g. "running")
 * @param offTexture   block texture for {@code property=false} (e.g. "luatweaker:block/crusher")
 * @param onTexture    block texture for {@code property=true} (e.g. "luatweaker:block/crusher_running")
 */
public record BooleanStateSpec(String property, String offTexture, String onTexture) {
}
