package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block methods, capabilities, properties and dynamic fallbacks. */
final class BlockTableBinding {

    private BlockTableBinding() {
    }

    static void bindBlockCore(ILuaTable methods, ILuaEngine engine, IInteractableBlock block,
            ILuaValue attrSignal) {
        methods.rawset("Destroy", args -> engine.wrapBoolean(block.Destroy()));
        methods.rawset("destroy", methods.rawget("Destroy"));
        methods.rawset(InteractionBindingSupport.METHOD_GET_ATTRIBUTE, args -> {
            int off = LuaBinder.getOffset(args);
            if (InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                String val = block.getAttribute(args[off].asString());
                return val != null ? engine.wrapString(val) : engine.nilValue();
            }
            return engine.nilValue();
        });
        methods.rawset("getAttribute", methods.rawget(InteractionBindingSupport.METHOD_GET_ATTRIBUTE));

        methods.rawset(InteractionBindingSupport.METHOD_SET_ATTRIBUTE, args -> {
            int off = LuaBinder.getOffset(args);
            if (InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                String name = args[off].asString();
                String val = args[off + 1].asString();
                block.setAttribute(name, val);
                InteractionBindingSupport.fireAttributeChanged(engine, attrSignal, name, val);
            }
            return engine.nilValue();
        });
        methods.rawset("setAttribute", methods.rawget(InteractionBindingSupport.METHOD_SET_ATTRIBUTE));

        methods.rawset("UseBlock", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Block:UseBlock requires (actor)");
            }
            Object actor = resolveBlockActor(args[off]);
            return engine.wrapBoolean(actor != null && block.useBlock(actor));
        });
        methods.rawset("useBlock", methods.rawget("UseBlock"));
    }

    private static Object resolveBlockActor(ILuaValue actorVal) {
        Object actor = actorVal.toJavaObject();
        if (actorVal.isTable()) {
            ILuaValue inner = actorVal.asTable().rawget(InteractionBindingSupport.ENTITY_MARKER);
            if (inner != null && !inner.isNil()) {
                actor = inner.toJavaObject();
            }
        }
        return actor;
    }

    static void bindBlockGeo(ILuaTable methods, ILuaEngine engine, IInteractableBlock block) {
        methods.rawset(InteractionBindingSupport.METHOD_PLAY_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapBoolean(false);
            }
            String animName = args[off].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .triggerGeoAnimation(block, ctrl, animName, 1.0, false));
        });
        methods.rawset("playAnimation", methods.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));
        methods.rawset("TriggerAnimation", methods.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));
        methods.rawset("triggerAnimation", methods.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));

        methods.rawset(InteractionBindingSupport.METHOD_STOP_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapBoolean(false);
            }
            String animName = args[off].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            return engine.wrapBoolean(block.stopAnimation(animName, ctrl));
        });
        methods.rawset("stopAnimation", methods.rawget(InteractionBindingSupport.METHOD_STOP_ANIMATION));
    }

    static void bindBlockIndex(ILuaEngine engine, ILuaTable meta, ILuaTable methods,
            IInteractableBlock block) {
        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key.toLowerCase(java.util.Locale.ROOT)) {
                case "id":
                    return engine.wrapString(block.getId());
                case "nbt":
                    return engine.wrapString(block.getNbt());
                case "position":
                    return readBlockPosition(engine, block);
                case "dimension":
                    return engine.wrapString(block.getDimension());
                case "hardness":
                    return engine.wrapNumber(block.getHardness());
                case "lightlevel":
                    return engine.wrapNumber(block.getLightLevel());
                case "isair":
                    return engine.wrapBoolean(block.isAir());
                case "issolid":
                    return engine.wrapBoolean(block.isSolid());
                case "isliquid":
                    return engine.wrapBoolean(block.isLiquid());
                case "redstonesignal":
                    return engine.wrapNumber(block.getRedstoneSignal());
                case "biome":
                    return engine.wrapString(block.getBiome());
                case "properties":
                    return InteractionLuaBinding.wrapValue(engine, block.getProperties());
                default:
                    return resolveBlockMember(engine, block, methods, key);
            }
        });
    }

    private static ILuaValue readBlockPosition(ILuaEngine engine, IInteractableBlock block) {
        ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
        if (v3Class == null || v3Class.isNil()) {
            return engine.nilValue();
        }
        ILuaValue v3New = v3Class.asTable().rawget(InteractionBindingSupport.NEW_METHOD);
        return engine.callFunction(v3New, v3Class,
            engine.wrapNumber(block.getX()),
            engine.wrapNumber(block.getY()),
            engine.wrapNumber(block.getZ()));
    }

    static void bindBlockNewIndex(ILuaEngine engine, ILuaTable meta, IInteractableBlock block) {
        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            if ("Id".equals(key) || "id".equals(key)) {
                block.setId(val.asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                block.setNbt(val.asString());
            } else {
                Object rawBlock = block.getRawBlockState();
                if (rawBlock != null) {
                    InteractionBindingSupport.setDynamicFallback(engine, rawBlock, key, val);
                }
            }
            return engine.nilValue();
        });
    }

    private static ILuaValue resolveBlockMember(ILuaEngine engine, IInteractableBlock block,
            ILuaTable methods, String key) {
        ILuaValue method = methods.rawget(key);
        if (method != null && !method.isNil()) {
            return method;
        }
        Object rawBlock = block.getRawBlockState();
        if (rawBlock != null) {
            ILuaValue fallback = InteractionBindingSupport.getDynamicFallback(engine, rawBlock, key);
            if (fallback != null) {
                return fallback;
            }
        }
        return engine.nilValue();
    }
}


