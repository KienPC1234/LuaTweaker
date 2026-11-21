package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

/**
 * Lua table factory for {@code Instance}: Roblox-style property access
 * ({@code Name}, {@code ClassName}, {@code Parent}) plus child management
 * ({@code GetChildren}, {@code FindFirstChild}, {@code Clone}, {@code Destroy}).
 */
public final class InstanceTables {

    private static final int PARENT_ARG_INDEX = 2;
    private static final int FIND_NAME_ARG_INDEX = 1;
    private static final int MIN_FIND_ARGS = 2;
    private static final String GET_CHILDREN_KEY = "GetChildren";
    private static final String FIND_FIRST_CHILD_KEY = "FindFirstChild";
    private static final String CLONE_KEY = "Clone";
    private static final String DESTROY_KEY = "Destroy";

    private InstanceTables() {}

    static ILuaValue wrapFunction(ILuaEngine engine, ILuaFunction function) {
        ILuaTable temp = engine.createTable();
        temp.rawset("fn", function);
        return temp.rawget("fn");
    }

    public static ILuaTable createInstance(ILuaEngine engine, LuaInstance instance) {
        ILuaTable instTable = engine.createTable();

        ILuaTable meta = engine.createTable();
        meta.rawset("__index", args -> indexInstance(engine, instance, args[1].asString()));

        meta.rawset("__newindex", args -> {
            assignInstance(instance, args[1].asString(), args[2]);
            return null;
        });

        instTable.setMetatable(meta);
        instTable.rawset("__instance", engine.wrapUserdata(instance));
        return instTable;
    }

    private static ILuaValue indexInstance(ILuaEngine engine, LuaInstance instance, String key) {
        ILuaValue core = indexCoreProperty(engine, instance, key);
        if (core != null) {
            return core;
        }
        ILuaValue childFn = indexChildFunction(engine, instance, key);
        if (childFn != null) {
            return childFn;
        }
        return engine.nilValue();
    }

    private static ILuaValue indexCoreProperty(ILuaEngine engine, LuaInstance instance, String key) {
        if ("Name".equals(key) || "name".equals(key)) {
            return engine.wrapString(instance.getName());
        }
        if ("ClassName".equals(key) || "className".equals(key)) {
            return engine.wrapString(instance.getClassName());
        }
        if ("Parent".equals(key) || "parent".equals(key)) {
            if (instance.getParent() == null) {
                return engine.nilValue();
            }
            return createInstance(engine, instance.getParent());
        }
        return null;
    }

    private static ILuaValue indexChildFunction(ILuaEngine engine, LuaInstance instance, String key) {
        if (GET_CHILDREN_KEY.equals(key)) {
            return wrapFunction(engine, getChildrenArgs -> listChildren(engine, instance));
        }
        if (FIND_FIRST_CHILD_KEY.equals(key)) {
            return wrapFunction(engine, findArgs -> {
                if (findArgs.length < MIN_FIND_ARGS) {
                    return engine.nilValue();
                }
                LuaInstance child = instance.findFirstChild(findArgs[FIND_NAME_ARG_INDEX].asString());
                if (child == null) {
                    return engine.nilValue();
                }
                return createInstance(engine, child);
            });
        }
        if (CLONE_KEY.equals(key)) {
            return wrapFunction(engine, cloneArgs -> createInstance(engine, instance.cloneInstance()));
        }
        if (DESTROY_KEY.equals(key)) {
            return wrapFunction(engine, destroyArgs -> {
                instance.destroy();
                return null;
            });
        }
        return null;
    }

    private static ILuaTable listChildren(ILuaEngine engine, LuaInstance instance) {
        ILuaTable list = engine.createTable();
        int idx = 1;
        for (LuaInstance child : instance.getChildren()) {
            list.rawset(idx++, createInstance(engine, child));
        }
        return list;
    }

    private static void assignInstance(LuaInstance instance, String key, ILuaValue val) {
        if ("Name".equals(key) || "name".equals(key)) {
            instance.setName(val.asString());
        } else if ("Parent".equals(key) || "parent".equals(key)) {
            assignParent(instance, val);
        }
    }

    /**
     * Attaches a new parent from a Lua value: nil detaches, an instance table
     * attaches its wrapped instance, anything else leaves the parent unchanged.
     */
    private static void assignParent(LuaInstance instance, ILuaValue val) {
        if (val.isNil()) {
            instance.setParent(null);
            return;
        }
        if (val instanceof ILuaTable parentTable) {
            ILuaValue rawInst = parentTable.rawget("__instance");
            if (rawInst != null && !rawInst.isNil()) {
                Object userdata = rawInst.toJavaObject();
                if (userdata instanceof LuaInstance parent) {
                    instance.setParent(parent);
                }
            }
        }
    }

    /**
     * Reads the optional parent argument of {@code Instance.new}: absent or
     * unresolvable means no parent (matching the original lenient behavior).
     */
    static @Nullable LuaInstance parseInstanceParent(ILuaValue... args) {
        if (args.length <= PARENT_ARG_INDEX || !(args[PARENT_ARG_INDEX] instanceof ILuaTable parentTable)) {
            return null;
        }
        ILuaValue rawInst = parentTable.rawget("__instance");
        if (rawInst == null || rawInst.isNil()) {
            return null;
        }
        Object userdata = rawInst.toJavaObject();
        return userdata instanceof LuaInstance parent ? parent : null;
    }
}
