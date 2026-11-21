package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds item methods, properties and dynamic fallbacks. */
final class ItemTableBinding {

    private ItemTableBinding() {
    }

    static void bindItemCore(ILuaTable methods, ILuaEngine engine, IInteractableItem item,
            ILuaValue attrSignal) {
        methods.rawset("UseItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Item:UseItem requires (actor)");
            }
            Object actor = resolveActor(args[off]);
            return engine.wrapBoolean(actor != null && item.useItem(actor));
        });
        methods.rawset("useItem", methods.rawget("UseItem"));

        methods.rawset("Drop", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Item:Drop requires (actor, [count])");
            }
            Object actor = resolveActor(args[off]);
            int count = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asInt() : item.getCount();
            return engine.wrapBoolean(actor != null && item.drop(actor, count));
        });
        methods.rawset("drop", methods.rawget("Drop"));

        bindItemAttributes(methods, engine, item, attrSignal);
    }

    private static void bindItemAttributes(ILuaTable methods, ILuaEngine engine, IInteractableItem item,
            ILuaValue attrSignal) {
        methods.rawset(InteractionBindingSupport.METHOD_GET_ATTRIBUTE, args -> {
            int off = LuaBinder.getOffset(args);
            if (InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                String val = item.getAttribute(args[off].asString());
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
                item.setAttribute(name, val);
                InteractionBindingSupport.fireAttributeChanged(engine, attrSignal, name, val);
            }
            return engine.nilValue();
        });
        methods.rawset("setAttribute", methods.rawget(InteractionBindingSupport.METHOD_SET_ATTRIBUTE));
    }

    private static Object resolveActor(ILuaValue actorVal) {
        Object actor = actorVal.toJavaObject();
        if (actorVal.isTable()) {
            ILuaValue inner = actorVal.asTable().rawget(InteractionBindingSupport.ENTITY_MARKER);
            if (inner != null && !inner.isNil()) {
                actor = inner.toJavaObject();
            }
        }
        return actor;
    }

    static void bindItemData(ILuaTable methods, ILuaEngine engine, IInteractableItem item) {
        methods.rawset("GetCustomData", args -> {
            int off = LuaBinder.getOffset(args);
            if (InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                Object val = item.getCustomData(args[off].asString());
                return InteractionLuaBinding.wrapValue(engine, val);
            }
            return engine.nilValue();
        });
        methods.rawset("getCustomData", methods.rawget("GetCustomData"));

        methods.rawset("SetCustomData", args -> {
            int off = LuaBinder.getOffset(args);
            if (InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                String name = args[off].asString();
                Object val = args[off + 1].toJavaObject();
                item.setCustomData(name, val);
            }
            return engine.nilValue();
        });
        methods.rawset("setCustomData", methods.rawget("SetCustomData"));

        methods.rawset(InteractionBindingSupport.METHOD_PLAY_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapBoolean(false);
            }
            String animName = args[off].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            return engine.wrapBoolean(item.playAnimation(animName, ctrl));
        });
        methods.rawset("playAnimation", methods.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));

        methods.rawset(InteractionBindingSupport.METHOD_STOP_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapBoolean(false);
            }
            String animName = args[off].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            return engine.wrapBoolean(item.stopAnimation(animName, ctrl));
        });
        methods.rawset("stopAnimation", methods.rawget(InteractionBindingSupport.METHOD_STOP_ANIMATION));
    }

    static void bindItemIndex(ILuaEngine engine, ILuaTable meta, ILuaTable methods,
            IInteractableItem item) {
        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key.toLowerCase(java.util.Locale.ROOT)) {
                case "id":
                    return engine.wrapString(item.getId());
                case "nbt":
                    return engine.wrapString(item.getNbt());
                case "count":
                    return engine.wrapNumber(item.getCount());
                case "slot":
                    return engine.wrapNumber(item.getSlot());
                case "owneruuid":
                    return item.getOwnerUuid() != null ? engine.wrapString(item.getOwnerUuid())
                            : engine.nilValue();
                case "damage":
                    return engine.wrapNumber(item.getDamage());
                case "maxdamage":
                    return engine.wrapNumber(item.getMaxDamage());
                case "customname":
                    return item.getCustomName() != null ? engine.wrapString(item.getCustomName())
                            : engine.nilValue();
                case "isdamageable":
                    return engine.wrapBoolean(item.isDamageable());
                case "isenchanted":
                    return engine.wrapBoolean(item.isEnchanted());
                default:
                    return resolveItemMember(engine, item, methods, key);
            }
        });
    }

    static void bindItemNewIndex(ILuaEngine engine, ILuaTable meta, IInteractableItem item) {
        meta.rawset("__newindex", args -> {
            String key = args[1].asString().toLowerCase(java.util.Locale.ROOT);
            ILuaValue val = args[2];
            switch (key) {
                case "count":
                    item.setCount(val.asInt());
                    break;
                case "slot":
                    item.setSlot(val.asInt());
                    break;
                case "damage":
                    item.setDamage(val.asInt());
                    break;
                case "customname":
                    item.setCustomName(val.asString());
                    break;
                case "nbt":
                    item.setNbt(val.asString());
                    break;
                default:
                    Object rawItem = item.getRawItemStack();
                    if (rawItem != null) {
                        InteractionBindingSupport.setDynamicFallback(engine, rawItem, args[1].asString(), val);
                    }
                    break;
            }
            return engine.nilValue();
        });
    }

    private static ILuaValue resolveItemMember(ILuaEngine engine, IInteractableItem item,
            ILuaTable methods, String key) {
        ILuaValue method = methods.rawget(key);
        if (method != null && !method.isNil()) {
            return method;
        }
        Object rawItem = item.getRawItemStack();
        if (rawItem != null) {
            ILuaValue fallback = InteractionBindingSupport.getDynamicFallback(engine, rawItem, key);
            if (fallback != null) {
                return fallback;
            }
        }
        return engine.nilValue();
    }
}
