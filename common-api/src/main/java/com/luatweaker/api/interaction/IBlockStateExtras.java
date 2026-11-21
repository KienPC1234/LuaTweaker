package com.luatweaker.api.interaction;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block state/world read-write primitives kept separate from {@link IInteractableBlock} so the
 * core block contract stays focused. Lua bindings scan inherited methods, so every primitive here
 * is still visible on the same block table.
 */
public interface IBlockStateExtras {

    @LuaDoc(description = "Redstone power level received by this block (0-15).",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getRedstoneSignal() { return 0; }

    @LuaDoc(description = "Direct (strong) redstone signal emitted through the given side.",
            params = {"side: string"}, returnType = LuaDocFragments.Types.INTEGER)
    default int getDirectSignal(@NotNull String side) { return 0; }

    @LuaDoc(description = "Value of one block-state property (e.g. 'facing'), or nil.",
            params = {"name: string"}, returnType = LuaDocFragments.Types.STRING)
    default @Nullable String getProperty(@NotNull String name) { return null; }

    /**
     * Applies one block-state property. Named {@code applyProperty} rather than {@code setProperty}
     * because PMD's LinguisticNaming rejects non-void setters and a failed lookup must be visible.
     */
    @LuaDoc(description = "Applies one block-state property; returns false when the property or value is invalid.",
            params = {"name: string", "value: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean applyProperty(@NotNull String name, @NotNull String value) { return false; }

    @LuaDoc(description = "Every block-state property as a name-to-value table.",
            returnType = LuaDocFragments.Types.TABLE)
    default @NotNull java.util.Map<String, String> getProperties() { return java.util.Map.of(); }

    @LuaDoc(description = "Biome id at this block position.", returnType = LuaDocFragments.Types.STRING)
    default @NotNull String getBiome() { return "minecraft:plains"; }

    @LuaDoc(description = "Creates an explosion at this block position.",
            params = {"power: number", "[causesFire: boolean]", "[breakBlocks: boolean]"})
    default void explode(@LuaDefault("4.0") float power,
                         @LuaDefault("false") boolean causesFire,
                         @LuaDefault("true") boolean breakBlocks) {}

    @LuaDoc(description = "Creates a block-breaking explosion at this block position.",
            params = {"power: number"})
    default void explode(@LuaDefault("4.0") float power) { explode(power, false, true); }
}
