package com.luatweaker.common.client;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Per-thread render-frame state for {@link MinecraftGuiService} so scissor and
 * pose stacks unwind cleanly even when a Lua callback throws mid-frame.
 */
final class GuiFrameState {
    private static final int ALPHA_MASK = 0xFF000000;
    private static final ThreadLocal<GuiFrameState> CURRENT = new ThreadLocal<>();

    private final GuiGraphics activeGraphics;
    private int clipDepth;
    private int poseDepth;

    private GuiFrameState(GuiGraphics activeGraphics) {
        this.activeGraphics = activeGraphics;
    }

    static void bind(@Nullable GuiGraphics graphics) {
        if (graphics == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(new GuiFrameState(graphics));
        }
    }

    static void clear() {
        GuiFrameState state = CURRENT.get();
        if (state != null && state.activeGraphics != null) {
            while (state.clipDepth > 0) {
                state.activeGraphics.disableScissor();
                state.clipDepth--;
            }
            while (state.poseDepth > 0) {
                state.activeGraphics.pose().popPose();
                state.poseDepth--;
            }
        }
        CURRENT.remove();
    }

    @Nullable
    static GuiGraphics currentGraphics() {
        GuiFrameState state = CURRENT.get();
        return state != null ? state.activeGraphics : null;
    }

    static int opaqueColor(int color) {
        return (color & ALPHA_MASK) == 0 ? (color | ALPHA_MASK) : color;
    }

    static void pushClip(int x, int y, int width, int height) {
        GuiFrameState state = CURRENT.get();
        if (state == null || state.activeGraphics == null) {
            return;
        }
        state.activeGraphics.enableScissor(x, y, x + Math.max(0, width), y + Math.max(0, height));
        state.clipDepth++;
    }

    static void popClip() {
        GuiFrameState state = CURRENT.get();
        if (state != null && state.activeGraphics != null && state.clipDepth > 0) {
            state.activeGraphics.disableScissor();
            state.clipDepth--;
        }
    }

    static void pushPose() {
        GuiFrameState state = CURRENT.get();
        if (state != null && state.activeGraphics != null) {
            state.activeGraphics.pose().pushPose();
            state.poseDepth++;
        }
    }

    static void popPose() {
        GuiFrameState state = CURRENT.get();
        if (state != null && state.activeGraphics != null && state.poseDepth > 0) {
            state.activeGraphics.pose().popPose();
            state.poseDepth--;
        }
    }
}
