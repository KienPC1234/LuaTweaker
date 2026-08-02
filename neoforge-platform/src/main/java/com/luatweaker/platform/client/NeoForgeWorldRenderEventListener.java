package com.luatweaker.platform.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side world-space rendering primitives.
 *
 * <p>Java only exposes minimal building blocks — {@code DrawLine} / {@code DrawBox}
 * in world coordinates and {@code GetEntity} for reading client-side entity state.
 * All higher-level visuals (target outlines, custom shapes, animations) are computed
 * in Lua during the {@code Client.OnRenderWorld} signal. Pending primitives are
 * rendered AFTER the signal runs, then cleared every frame.</p>
 */
public class NeoForgeWorldRenderEventListener {

    private record Line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {}
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {}

    private static final ThreadLocal<List<Object>> PENDING_OPS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<net.minecraft.client.Camera> CURRENT_CAMERA = new ThreadLocal<>();
    private static final ThreadLocal<float[]> CURRENT_PROJECTION = new ThreadLocal<>();

    public static void registerRenderService(ILuaEngine engine, ILuaTable clientTable) {
        ILuaTable renderService = engine.createTable();

        renderService.rawset("DrawLine", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 7) {
                PENDING_OPS.get().add(new Line(
                        args[off].asDouble(), args[off + 1].asDouble(), args[off + 2].asDouble(),
                        args[off + 3].asDouble(), args[off + 4].asDouble(), args[off + 5].asDouble(),
                        (int) (long) args[off + 6].asDouble()));
            }
            return engine.nilValue();
        });
        renderService.rawset("drawLine", renderService.rawget("DrawLine"));

        renderService.rawset("DrawBox", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 7) {
                PENDING_OPS.get().add(new Box(
                        args[off].asDouble(), args[off + 1].asDouble(), args[off + 2].asDouble(),
                        args[off + 3].asDouble(), args[off + 4].asDouble(), args[off + 5].asDouble(),
                        (int) (long) args[off + 6].asDouble()));
            }
            return engine.nilValue();
        });
        renderService.rawset("drawBox", renderService.rawget("DrawBox"));

        // Client-side entity lookup: position + bounding box size, so Lua can
        // compute its own outlines/shapes from real world data.
        renderService.rawset("GetEntity", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 1) return engine.nilValue();
            String uuid = args[off].asString();
            Entity entity = findEntityByUuid(uuid);
            if (entity == null) return engine.nilValue();
            ILuaTable result = engine.createTable();
            result.rawset("X", engine.wrapNumber(entity.getX()));
            result.rawset("Y", engine.wrapNumber(entity.getY()));
            result.rawset("Z", engine.wrapNumber(entity.getZ()));
            result.rawset("Width", engine.wrapNumber(entity.getBbWidth()));
            result.rawset("Height", engine.wrapNumber(entity.getBbHeight()));
            result.rawset("Name", engine.wrapString(entity.getName().getString()));
            result.rawset("Type", engine.wrapString(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
            return result;
        });
        renderService.rawset("getEntity", renderService.rawget("GetEntity"));

        // World -> screen projection for HUD labels. Only valid inside OnRenderWorld.
        renderService.rawset("WorldToScreen", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 3) return engine.nilValue();
            double wx = args[off].asDouble();
            double wy = args[off + 1].asDouble();
            double wz = args[off + 2].asDouble();
            ILuaTable result = engine.createTable();
            result.rawset("Visible", engine.wrapBoolean(false));
            result.rawset("X", engine.wrapNumber(0));
            result.rawset("Y", engine.wrapNumber(0));
            net.minecraft.client.Camera camera = CURRENT_CAMERA.get();
            float[] projArray = CURRENT_PROJECTION.get();
            if (camera != null && projArray != null) {
                org.joml.Vector3f camPos = camera.getPosition().toVector3f();
                org.joml.Vector3f rel = new org.joml.Vector3f((float) wx - camPos.x, (float) wy - camPos.y, (float) wz - camPos.z);
                org.joml.Vector3f view = camera.rotation().conjugate().transform(rel);
                org.joml.Vector4f clip = new org.joml.Vector4f(view.x, view.y, view.z, 1.0f);
                new org.joml.Matrix4f().set(projArray).transform(clip);
                if (clip.w > 0.0001f) {
                    float ndcX = clip.x / clip.w;
                    float ndcY = clip.y / clip.w;
                    if (ndcX >= -1.5f && ndcX <= 1.5f && ndcY >= -1.5f && ndcY <= 1.5f) {
                        var window = Minecraft.getInstance().getWindow();
                        int w = window.getGuiScaledWidth();
                        int h = window.getGuiScaledHeight();
                        result.rawset("Visible", engine.wrapBoolean(true));
                        result.rawset("X", engine.wrapNumber((ndcX * 0.5f + 0.5f) * w));
                        result.rawset("Y", engine.wrapNumber((1.0f - (ndcY * 0.5f + 0.5f)) * h));
                    }
                }
            }
            return result;
        });
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

    private static Entity findEntityByUuid(String uuid) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null || uuid == null || uuid.isBlank()) return null;
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ILuaEngine engine = com.luatweaker.platform.LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            PENDING_OPS.get().clear();
            return;
        }

        List<Object> ops = PENDING_OPS.get();
        CURRENT_CAMERA.set(event.getCamera());
        CURRENT_PROJECTION.set(event.getProjectionMatrix().get(new float[16]));

        // Fire the Lua signal first (scripts queue DrawLine/DrawBox during it).
        try {
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue clientVal = globals.rawget("Client");
            if (clientVal != null && clientVal.isTable()) {
                ILuaValue signal = clientVal.asTable().rawget("OnRenderWorld");
                if (signal != null && signal.isTable()) {
                    // Skip the per-frame VM round trip when nothing listens.
                    ILuaValue listeners = signal.asTable().rawget("_listeners");
                    if (listeners != null && listeners.isTable() && listeners.asTable().length() > 0) {
                        ILuaValue signalClass = globals.rawget("Signal");
                        if (signalClass != null && signalClass.isTable()) {
                            ILuaValue fireFn = signalClass.asTable().rawget("FireSync");
                            if (fireFn != null && fireFn.isFunction()) {
                                engine.callFunction(fireFn, signal, engine.wrapNumber(event.getPartialTick().getGameTimeDeltaTicks()));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Never crash the render loop
        }

        // Render queued primitives. The stage pose stack only carries the camera
        // rotation (LevelRenderer renders everything camera-relative), so world
        // coordinates must be shifted by -cameraPosition before drawing.
        if (!ops.isEmpty()) {
            try {
                com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
                net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
                poseStack.pushPose();
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                com.mojang.blaze3d.vertex.VertexConsumer consumer = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.lines());
                for (Object op : ops) {
                    if (op instanceof Line line) {
                        float[] c = toColor(line.color());
                        net.minecraft.client.renderer.LevelRenderer.renderLineBox(poseStack, consumer,
                                new net.minecraft.world.phys.AABB(line.x1(), line.y1(), line.z1(), line.x2(), line.y2(), line.z2()),
                                c[0], c[1], c[2], c[3]);
                    } else if (op instanceof Box box) {
                        float[] c = toColor(box.color());
                        net.minecraft.client.renderer.LevelRenderer.renderLineBox(poseStack, consumer,
                                new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                                c[0], c[1], c[2], c[3]);
                    }
                }
                poseStack.popPose();
                bufferSource.endBatch(net.minecraft.client.renderer.RenderType.lines());
            } catch (Throwable t) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[RenderService] Render failed: " + t.getMessage());
            } finally {
                ops.clear();
            }
        } else {
            PENDING_OPS.get().clear();
        }
    }

    private static float[] toColor(int color) {
        float a = (float) ((color >> 24) & 0xFF) / 255.0f;
        float r = (float) ((color >> 16) & 0xFF) / 255.0f;
        float g = (float) ((color >> 8) & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        return new float[] { r, g, b, a };
    }
}
