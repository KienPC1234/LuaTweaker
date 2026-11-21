package com.luatweaker.content;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;

/** Registers the Content.NewKeyMapping builder table and its key/mouse/modifier helpers. */
final class ContentKeyMappingBinding {

    private ContentKeyMappingBinding() {
    }

    static void bindKeyMappingCreator(ILuaEngine engine, ILuaTable startupTable) {
        ILuaFunction newKeyMappingFn = args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Content.NewKeyMapping requires (id)");
            }
            String id = args[off].asString();
            ILuaTable table = engine.createTable();
            final String[] displayName = { id };
            final String[] category = { "luatweaker" };
            final int[] defaultKey = { 90 };
            final String[] payload = { "" };
            final String[] inputType = { "key" };
            final String[] modifier = { "none" };

            bindKeyMappingText(table, displayName, category);
            bindKeyMappingKeys(table, defaultKey, inputType, modifier);
            bindKeyMappingAction(table, id, displayName, category, defaultKey, payload, inputType, modifier);
            return table;
        };
        startupTable.rawset("NewKeyMapping", newKeyMappingFn);
        startupTable.rawset("newKeyMapping", newKeyMappingFn);
    }

    private static void bindKeyMappingText(ILuaTable table, String[] displayName, String... category) {
        ContentBindingSupport.bindMethod(table, "displayName", a -> {
            ContentBindingSupport.bindFirstString(a, v -> displayName[0] = v);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "name", a -> {
            ContentBindingSupport.bindFirstString(a, v -> displayName[0] = v);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "description", a -> {
            ContentBindingSupport.bindFirstString(a, v -> displayName[0] = v);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "category", a -> {
            ContentBindingSupport.bindFirstString(a, v -> category[0] = v);
            return table;
        });
    }

    private static void bindKeyMappingKeys(ILuaTable table, int[] defaultKey,
            String[] inputType, String... modifier) {
        ContentBindingSupport.bindMethod(table, "defaultKey", a -> {
            int o = LuaBinder.getOffset(a);
            if (ContentBindingSupport.hasMinArgs(a, o, 1)) {
                com.luatweaker.api.client.KeyNames.KeyRef ref =
                        com.luatweaker.api.client.KeyNames.parseKeyRef(a[o], "DefaultKey");
                defaultKey[0] = ref.code();
                inputType[0] = ref.inputType();
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "key", a -> {
            int o = LuaBinder.getOffset(a);
            if (ContentBindingSupport.hasMinArgs(a, o, 1)) {
                com.luatweaker.api.client.KeyNames.KeyRef ref =
                        com.luatweaker.api.client.KeyNames.parseKeyRef(a[o], "Key");
                defaultKey[0] = ref.code();
                inputType[0] = ref.inputType();
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "mouseButton", a -> {
            int o = LuaBinder.getOffset(a);
            if (ContentBindingSupport.hasMinArgs(a, o, 1)) {
                defaultKey[0] = com.luatweaker.api.client.KeyNames.parseMouseButton(a[o].asString());
                inputType[0] = "mouse";
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "modifier", a -> {
            int o = LuaBinder.getOffset(a);
            if (ContentBindingSupport.hasMinArgs(a, o, 1)) {
                modifier[0] = com.luatweaker.api.client.KeyNames.parseModifier(a[o].asString());
            }
            return table;
        });
    }

    private static void bindKeyMappingAction(ILuaTable table, String id,
            String[] displayName, String[] category, int[] defaultKey, String[] payload,
            String[] inputType, String... modifier) {
        ContentBindingSupport.bindMethod(table, "onPress", a -> {
            ContentBindingSupport.bindFirstString(a, v -> payload[0] = v);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onPressPayload", a -> {
            ContentBindingSupport.bindFirstString(a, v -> payload[0] = v);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "register", a -> {
            Object keyBindService = com.luatweaker.core.service.LuaServiceRegistry.get("KeyBindService");
            if (keyBindService instanceof com.luatweaker.api.client.IKeyBindService kbs) {
                kbs.registerKeyBind(id, displayName[0], category[0], defaultKey[0], payload[0],
                        inputType[0], modifier[0]);
            }
            return table;
        });
    }
}
