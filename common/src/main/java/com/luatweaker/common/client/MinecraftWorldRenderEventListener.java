package com.luatweaker.common.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.common.LuaTweakerCommon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side world-space rendering primitives.
 *
 * <p>Java only exposes minimal building blocks — {@code DrawLine} / {@code DrawBox}
 * in world coordinates and {@code GetEntity} for reading client-side entity state.
 * All higher-level visuals (target outlines, custom shapes, animations) are computed
 * in Lua during the {@code Client.OnRenderWorld} signal. Pending primitives are
 * rendered AFTER the signal runs, then cleared every frame.</p>
 */
public final class MinecraftWorldRenderEventListener {
    private static final String DRAW_BOX_METHOD = "DrawBox";
    private static final int DRAW_ARG_COUNT = 7;
    private static final int HIGHLIGHT_TABLE_ARG_COUNT = 2;
    private static final int HIGHLIGHT_POSITION_ARG_COUNT = 4;
    private static final int GET_ENTITY_ARG_COUNT = 1;
    private static final int WORLD_TO_SCREEN_ARG_COUNT = 3;
    private static final float CLIP_W_EPSILON = 0.0001f;
    private static final float NORMALIZE_EPSILON = 1.0E-5F;
    /** On-screen margin (in NDC) beyond which a projected point is treated as off-screen. */
    private static final float NDC_MARGIN = 1.5f;

    private static final ThreadLocal<List<Object>> PENDING_OPS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Camera> CURRENT_CAMERA = new ThreadLocal<>();
    private static final ThreadLocal<float[]> CURRENT_PROJECTION = new ThreadLocal<>();

    private MinecraftWorldRenderEventListener() {
    }

    private record Line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {}
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {}

    public static void registerRenderService(@NotNull ILuaEngine engine, @NotNull ILuaTable clientTable) {
        ILuaTable renderService = engine.createTable();

        renderService.rawset("DrawLine", args -> pushLine(engine, args));
        renderService.rawset("drawLine", renderService.rawget("DrawLine"));

        renderService.rawset(DRAW_BOX_METHOD, args -> pushBox(engine, args));
        renderService.rawset("drawBox", renderService.rawget(DRAW_BOX_METHOD));

        renderService.rawset("HighlightBlock", args -> pushHighlight(engine, args));
        renderService.rawset("highlightBlock", renderService.rawget("HighlightBlock"));

        renderService.rawset("HighlightBox", renderService.rawget(DRAW_BOX_METHOD));
        renderService.rawset("highlightBox", renderService.rawget(DRAW_BOX_METHOD));

        // Client-side entity lookup: position + bounding box size, so Lua can
        // compute its own outlines/shapes from real world data.
        renderService.rawset("GetEntity", args -> describeEntity(engine, args));
        renderService.rawset("getEntity", renderService.rawget("GetEntity"));

        // World -> screen projection for HUD labels. Only valid inside OnRenderWorld.
        renderService.rawset("WorldToScreen", args -> worldToScreen(engine, args));
        renderService.rawset("worldToScreen", renderService.rawget("WorldToScreen"));

        engine.registerService("RenderService", renderService);
        engine.getGlobalEnvironment().rawset("RenderService", renderService);

        // Client.OnRenderWorld signal (merged Client table carries OnKeyBindPressed).
        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        if (signalClass != null && signalClass.isTable()) {
            ILuaValue newSignalFn = signalClass.asTable().rawget("new");
            if (newSignalFn != null && !newSignalFn.isNil()) {
                clientTable.rawset("OnRenderWorld", engine.callFunction(newSignalFn, signalClass));
            }
        }
    }

    private static @NotNull ILuaValue pushLine(@NotNull ILuaEngine engine, ILuaValue @NotNull ... args) {
        int offset = LuaBinder.getOffset(args);
        if (args.length - offset >= DRAW_ARG_COUNT) {
            PENDING_OPS.get().add(new Line(
                    args[offset].asDouble(), args[offset + 1].asDouble(), args[offset + 2].asDouble(),
                    args[offset + 3].asDouble(), args[offset + 4].asDouble(), args[offset + 5].asDouble(),
                    colorOf(args[offset + 6])));
        }
        return engine.nilValue();
    }

    private static @NotNull ILuaValue pushBox(@NotNull ILuaEngine engine, ILuaValue @NotNull ... args) {
        int offset = LuaBinder.getOffset(args);
        if (args.length - offset >= DRAW_ARG_COUNT) {
            PENDING_OPS.get().add(new Box(
                    args[offset].asDouble(), args[offset + 1].asDouble(), args[offset + 2].asDouble(),
                    args[offset + 3].asDouble(), args[offset + 4].asDouble(), args[offset + 5].asDouble(),
                    colorOf(args[offset + 6])));
        }
        return engine.nilValue();
    }

    /**
     * {@code HighlightBlock(posTable, color)} or {@code HighlightBlock(x, y, z, color)}: queues a
     * unit box on the block. Accepts both {@code X}/{@code Y}/{@code Z} and lowercase spellings.
     */
    private static @NotNull ILuaValue pushHighlight(@NotNull ILuaEngine engine, ILuaValue @NotNull ... args) {
        int offset = LuaBinder.getOffset(args);
        int remaining = args.length - offset;
        if (remaining >= HIGHLIGHT_TABLE_ARG_COUNT && args[offset].isTable()) {
            ILuaTable position = args[offset].asTable();
            double x = coordinate(position, "X", "x");
            double y = coordinate(position, "Y", "y");
            double z = coordinate(position, "Z", "z");
            PENDING_OPS.get().add(new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0, colorOf(args[offset + 1])));
        } else if (remaining >= HIGHLIGHT_POSITION_ARG_COUNT) {
            double x = args[offset].asDouble();
            double y = args[offset + 1].asDouble();
            double z = args[offset + 2].asDouble();
            PENDING_OPS.get().add(new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0, colorOf(args[offset + 3])));
        }
        return engine.nilValue();
    }

    private static double coordinate(@NotNull ILuaTable position, @NotNull String upperKey,
                                    @NotNull String lowerKey) {
        ILuaValue value = position.rawget(upperKey);
        if (value == null || value.isNil()) {
            value = position.rawget(lowerKey);
        }
        return value != null && !value.isNil() ? value.asDouble() : 0.0;
    }

    private static @NotNull ILuaValue describeEntity(@NotNull ILuaEngine engine, ILuaValue @NotNull ... args) {
        int offset = LuaBinder.getOffset(args);
        if (args.length - offset < GET_ENTITY_ARG_COUNT) {
            return engine.nilValue();
        }
        Entity entity = findEntityByUuid(args[offset].asString());
        if (entity == null) {
            return engine.nilValue();
        }
        ILuaTable result = engine.createTable();
        result.rawset("X", engine.wrapNumber(entity.getX()));
        result.rawset("Y", engine.wrapNumber(entity.getY()));
        result.rawset("Z", engine.wrapNumber(entity.getZ()));
        result.rawset("Width", engine.wrapNumber(entity.getBbWidth()));
        result.rawset("Height", engine.wrapNumber(entity.getBbHeight()));
        result.rawset("Name", engine.wrapString(entity.getName().getString()));
        result.rawset("Type", engine.wrapString(
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
        return result;
    }

    private static @NotNull ILuaValue worldToScreen(@NotNull ILuaEngine engine, ILuaValue @NotNull ... args) {
        int offset = LuaBinder.getOffset(args);
        ILuaTable result = engine.createTable();
        result.rawset("Visible", engine.wrapBoolean(false));
        result.rawset("X", engine.wrapNumber(0));
        result.rawset("Y", engine.wrapNumber(0));
        if (args.length - offset < WORLD_TO_SCREEN_ARG_COUNT) {
            return result;
        }
        Camera camera = CURRENT_CAMERA.get();
        float[] projection = CURRENT_PROJECTION.get();
        if (camera == null || projection == null) {
            return result;
        }
        org.joml.Vector4f clip = projectToClip(camera, projection,
                args[offset].asDouble(), args[offset + 1].asDouble(), args[offset + 2].asDouble());
        if (clip.w <= CLIP_W_EPSILON) {
            return result;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -NDC_MARGIN || ndcX > NDC_MARGIN || ndcY < -NDC_MARGIN || ndcY > NDC_MARGIN) {
            return result;
        }
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        result.rawset("Visible", engine.wrapBoolean(true));
        result.rawset("X", engine.wrapNumber((ndcX * 0.5f + 0.5f) * width));
        result.rawset("Y", engine.wrapNumber((1.0f - (ndcY * 0.5f + 0.5f)) * height));
        return result;
    }

    private static @NotNull org.joml.Vector4f projectToClip(@NotNull Camera camera,
                                                            float @NotNull [] projection,
                                                            double worldX, double worldY, double worldZ) {
        org.joml.Vector3f camPos = camera.getPosition().toVector3f();
        org.joml.Vector3f relative = new org.joml.Vector3f(
                (float) worldX - camPos.x, (float) worldY - camPos.y, (float) worldZ - camPos.z);
        org.joml.Vector3f view = camera.rotation().conjugate().transform(relative);
        org.joml.Vector4f clip = new org.joml.Vector4f(view.x, view.y, view.z, 1.0f);
        new org.joml.Matrix4f().set(projection).transform(clip);
        return clip;
    }

    private static int colorOf(@NotNull ILuaValue value) {
        return (int) (long) value.asDouble();
    }

    private static @Nullable Entity findEntityByUuid(@Nullable String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return findInLevel(Minecraft.getInstance().level, id);
    }

    private static @Nullable Entity findInLevel(@Nullable ClientLevel level, @NotNull UUID id) {
        if (level == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    public static void onRenderLevel(@NotNull PoseStack poseStack, @NotNull Camera camera,
                                     @NotNull org.joml.Matrix4f projectionMatrix, float deltaTicks) {
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        if (engine == null) {
            PENDING_OPS.get().clear();
            return;
        }
        List<Object> ops = PENDING_OPS.get();
        CURRENT_CAMERA.set(camera);
        CURRENT_PROJECTION.set(projectionMatrix.get(new float[16]));

        // Fire the Lua signal first (scripts queue DrawLine/DrawBox during it).
        RenderFirewall.run(() -> fireOnRenderWorld(engine, deltaTicks),
                () -> "Client.OnRenderWorld dispatch failed");

        // Render queued primitives. The stage pose stack only carries the camera
        // rotation (LevelRenderer renders everything camera-relative), so world
        // coordinates must be shifted by -cameraPosition before drawing.
        if (!ops.isEmpty()) {
            RenderFirewall.runLoud(() -> drawPendingOps(ops, poseStack, camera), () -> "[RenderService] Render failed");
        }
        PENDING_OPS.get().clear();
    }

    private static void fireOnRenderWorld(@NotNull ILuaEngine engine, float deltaTicks) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable signal = renderSignal(globals);
        if (signal == null || !hasListeners(signal)) {
            return;
        }
        ILuaValue fireFn = fireSync(globals);
        if (fireFn == null) {
            return;
        }
        engine.callFunction(fireFn, signal,
                engine.wrapNumber(deltaTicks));
    }

    /** The {@code Client.OnRenderWorld} signal table, or {@code null} before Lua built it. */
    private static @Nullable ILuaTable renderSignal(@NotNull ILuaTable globals) {
        ILuaValue client = globals.rawget("Client");
        if (client == null || !client.isTable()) {
            return null;
        }
        ILuaValue signal = client.asTable().rawget("OnRenderWorld");
        return signal != null && signal.isTable() ? signal.asTable() : null;
    }

    /** Skip the per-frame VM round trip when nothing is connected to the signal. */
    private static boolean hasListeners(@NotNull ILuaTable signal) {
        ILuaValue listeners = signal.rawget("_listeners");
        return listeners != null && listeners.isTable() && listeners.asTable().length() > 0;
    }

    private static @Nullable ILuaValue fireSync(@NotNull ILuaTable globals) {
        ILuaValue signalClass = globals.rawget("Signal");
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaValue fireFn = signalClass.asTable().rawget("FireSync");
        return fireFn != null && fireFn.isFunction() ? fireFn : null;
    }

    private static void drawPendingOps(@NotNull List<Object> ops, @NotNull PoseStack poseStack,
                                       @NotNull Camera cam) {
        net.minecraft.world.phys.Vec3 camera = cam.getPosition();
        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
            for (Object op : ops) {
                if (op instanceof Line line) {
                    drawLine(poseStack, consumer, line);
                } else if (op instanceof Box box) {
                    drawBox(poseStack, consumer, box);
                }
            }
            bufferSource.endBatch(RenderType.lines());
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawLine(@NotNull PoseStack poseStack, @NotNull VertexConsumer consumer,
                                 @NotNull Line line) {
        float[] color = toColor(line.color());
        float dx = (float) (line.x2() - line.x1());
        float dy = (float) (line.y2() - line.y1());
        float dz = (float) (line.z2() - line.z1());
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > NORMALIZE_EPSILON) {
            dx /= length;
            dy /= length;
            dz /= length;
        } else {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
        }
        consumer.addVertex(poseStack.last().pose(),
                        (float) line.x1(), (float) line.y1(), (float) line.z1())
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(poseStack.last(), dx, dy, dz);
        consumer.addVertex(poseStack.last().pose(),
                        (float) line.x2(), (float) line.y2(), (float) line.z2())
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(poseStack.last(), dx, dy, dz);
    }

    private static void drawBox(@NotNull PoseStack poseStack, @NotNull VertexConsumer consumer,
                                @NotNull Box box) {
        float[] color = toColor(box.color());
        LevelRenderer.renderLineBox(poseStack, consumer,
                new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                color[0], color[1], color[2], color[3]);
    }

    private static float @NotNull [] toColor(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[] { r, g, b, a };
    }
}
