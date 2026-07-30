package com.luatweaker.core.vm;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.engine.LuaEngine;
import com.luatweaker.core.linter.LuaLinter;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.function.VarArgFunction;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CobaltLuaEngine implements ILuaEngine {
    private final LuaState state;
    private final LuaEngine rawEngine;
    private boolean debugMode = false;

    public CobaltLuaEngine() {
        this(false);
    }

    public CobaltLuaEngine(boolean debugMode) {
        this.debugMode = debugMode;
        this.rawEngine = new LuaEngine();
        this.state = rawEngine.getState();
        setupGlobalBindings();
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        state.globals().rawset("DEBUG", ValueFactory.valueOf(debugMode));
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private void setupGlobalBindings() {
        LuaTable globals = state.globals();

        // 1. Expose DEBUG global boolean
        globals.rawset("DEBUG", ValueFactory.valueOf(debugMode));

        // 2. Redirect standard Lua print(...) to AsyncFileLogger
        globals.rawset("print", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                StringBuilder sb = new StringBuilder();
                int count = args.count();
                for (int i = 1; i <= count; i++) {
                    sb.append(args.arg(i).toString());
                    if (i < count) sb.append("\t");
                }
                AsyncFileLogger.get().info("PRINT", sb.toString(), state);
                return Constants.NIL;
            }
        });

        // 3. Expose structured log table (log.info, log.warn, log.error, log.debug)
        LuaTable logTable = new LuaTable();
        logTable.rawset("info", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                AsyncFileLogger.get().info("SCRIPT", args.arg(1).toString(), state);
                return Constants.NIL;
            }
        });
        logTable.rawset("warn", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                AsyncFileLogger.get().warn("SCRIPT", args.arg(1).toString(), state);
                return Constants.NIL;
            }
        });
        logTable.rawset("error", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                AsyncFileLogger.get().error("SCRIPT", args.arg(1).toString(), state);
                return Constants.NIL;
            }
        });
        logTable.rawset("debug", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                if (debugMode) {
                    AsyncFileLogger.get().info("DEBUG", args.arg(1).toString(), state);
                }
                return Constants.NIL;
            }
        });
        globals.rawset("log", logTable);

        // 4. Mod / game GetService service bindings
        LuaTable modTable = new LuaTable();
        VarArgFunction getServiceFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String name = args.arg(2).checkLuaString().toString();
                Object service = LuaServiceRegistry.get(name);
                if (service == null) {
                    throw new LuaError("Service '" + name + "' not found");
                }
                if (service instanceof ILuaTable customBinding) {
                    return ((CobaltLuaTable) customBinding).getCobaltValue();
                }
                return new LuaUserdata(service);
            }
        };
        modTable.rawset("GetService", getServiceFunc);
        globals.rawset("Mod", modTable);
        globals.rawset("game", modTable);

        // 5. item(id, count, [nbt/options]) function (KubeJS style)
        VarArgFunction itemFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaValue first = args.arg(1);
                if (first instanceof LuaTable tbl) {
                    LuaValue idVal = tbl.rawget(ValueFactory.valueOf("id"));
                    if (idVal.isNil()) idVal = tbl.rawget(ValueFactory.valueOf("item"));
                    String id = idVal.checkLuaString().toString();
                    LuaValue cVal = tbl.rawget(ValueFactory.valueOf("count"));
                    int count = (cVal instanceof LuaInteger || cVal instanceof LuaDouble) ? cVal.toInteger() : 1;

                    ItemCount ic = new ItemCount(id, count);
                    LuaValue dmgVal = tbl.rawget(ValueFactory.valueOf("damage"));
                    if (dmgVal instanceof LuaInteger || dmgVal instanceof LuaDouble) ic = ic.withDamage(dmgVal.toInteger());
                    LuaValue nameVal = tbl.rawget(ValueFactory.valueOf("name"));
                    if (nameVal instanceof LuaString) ic = ic.withName(nameVal.toString());
                    LuaValue nbtVal = tbl.rawget(ValueFactory.valueOf("nbt"));
                    if (!nbtVal.isNil()) ic = ic.withNbt(nbtVal.toString());
                    return createItemUserdata(ic);
                }

                String itemId = first.checkLuaString().toString();
                if (itemId.startsWith("#") || itemId.startsWith("tag:") || itemId.startsWith("oredict:")) {
                    return createIngredientUserdata(new IngredientWrapper(itemId));
                }

                int count = args.arg(2).isNil() ? 1 : args.arg(2).checkInteger();
                ItemCount ic = new ItemCount(itemId, count);

                LuaValue third = args.arg(3);
                if (!third.isNil()) {
                    if (third instanceof LuaTable opts) {
                        LuaValue dmg = opts.rawget(ValueFactory.valueOf("damage"));
                        if (dmg instanceof LuaInteger || dmg instanceof LuaDouble) ic = ic.withDamage(dmg.toInteger());
                        LuaValue name = opts.rawget(ValueFactory.valueOf("name"));
                        if (name instanceof LuaString) ic = ic.withName(name.toString());
                        LuaValue nbt = opts.rawget(ValueFactory.valueOf("nbt"));
                        if (!nbt.isNil()) ic = ic.withNbt(nbt.toString());
                    } else {
                        ic = ic.withNbt(third.toString());
                    }
                }
                return createItemUserdata(ic);
            }
        };

        // 6. ingredient(descriptor), tag(name), oredict(name)
        VarArgFunction ingFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String desc = args.arg(1).checkLuaString().toString();
                return createIngredientUserdata(new IngredientWrapper(desc));
            }
        };
        VarArgFunction tagFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String desc = args.arg(1).checkLuaString().toString();
                if (!desc.startsWith("#")) desc = "#" + desc;
                return createIngredientUserdata(new IngredientWrapper(desc));
            }
        };

        globals.rawset("item", itemFunc);
        globals.rawset("ingredient", ingFunc);
        globals.rawset("tag", tagFunc);
        globals.rawset("oredict", tagFunc);
    }

    private LuaUserdata createItemUserdata(ItemCount ic) {
        LuaTable meta = new LuaTable();

        VarArgFunction withCount = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int count = args.arg(2).checkInteger();
                return createItemUserdata(current.withCount(count));
            }
        };
        VarArgFunction withDamage = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int dmg = args.arg(2).checkInteger();
                return createItemUserdata(current.withDamage(dmg));
            }
        };
        VarArgFunction withName = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String name = args.arg(2).checkLuaString().toString();
                return createItemUserdata(current.withName(name));
            }
        };
        VarArgFunction withLore = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                List<String> loreList = new ArrayList<>();
                if (args.arg(2) instanceof LuaTable tbl) {
                    int len = tbl.length();
                    for (int i = 1; i <= len; i++) {
                        loreList.add(tbl.rawget(i).toString());
                    }
                } else {
                    loreList.add(args.arg(2).toString());
                }
                return createItemUserdata(current.withLore(loreList));
            }
        };
        VarArgFunction withEnchantment = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String ench = args.arg(2).checkLuaString().toString();
                int level = args.arg(3).isNil() ? 1 : args.arg(3).checkInteger();
                return createItemUserdata(current.withEnchantment(ench, level));
            }
        };
        VarArgFunction withNbt = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String nbt = args.arg(2).toString();
                return createItemUserdata(current.withNbt(nbt));
            }
        };

        try {
            meta.rawset("withCount", withCount);       meta.rawset("count", withCount);
            meta.rawset("withDamage", withDamage);     meta.rawset("damage", withDamage);
            meta.rawset("withName", withName);         meta.rawset("name", withName);
            meta.rawset("withLore", withLore);         meta.rawset("lore", withLore);
            meta.rawset("withEnchantment", withEnchantment); meta.rawset("enchant", withEnchantment);
            meta.rawset("withNbt", withNbt);           meta.rawset("nbt", withNbt);

            LuaTable indexTable = new LuaTable();
            indexTable.rawset("id", ValueFactory.valueOf(ic.itemId()));
            indexTable.rawset("count", ValueFactory.valueOf(ic.count()));
            indexTable.rawset("damage", ValueFactory.valueOf(ic.damage()));

            meta.rawset(Constants.INDEX, new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    LuaValue key = args.arg(2);
                    LuaValue val = meta.rawget(key);
                    if (!val.isNil()) return val;
                    return indexTable.rawget(key);
                }
            });
        } catch (LuaError ignored) {}

        LuaUserdata ud = new LuaUserdata(ic);
        ud.setMetatable(state, meta);
        return ud;
    }

    private LuaUserdata createIngredientUserdata(IngredientWrapper ing) {
        LuaTable meta = new LuaTable();
        VarArgFunction orFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                IngredientWrapper current = (IngredientWrapper) ud.instance;
                LuaValue otherArg = args.arg(2);
                if (otherArg instanceof LuaUserdata otherUd && otherUd.instance instanceof IngredientWrapper otherIng) {
                    return createIngredientUserdata(current.or(otherIng));
                }
                return createIngredientUserdata(current.or(otherArg.toString()));
            }
        };

        try {
            meta.rawset("orIngredient", orFunc);
            meta.rawset("alt", orFunc);
            meta.rawset("otherwise", orFunc);
            meta.rawset("or", orFunc);
            meta.rawset(Constants.INDEX, meta);
        } catch (LuaError ignored) {}

        LuaUserdata ud = new LuaUserdata(ing);
        ud.setMetatable(state, meta);
        return ud;
    }

    @Override
    public void registerService(String name, ILuaTable service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public void registerService(String name, Object service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public ILuaTable getGlobalEnvironment() {
        return new CobaltLuaTable(state.globals());
    }

    @Override
    public ILuaValue toLuaValue(Object obj) {
        if (obj == null) return nilValue();
        if (obj instanceof ILuaValue lv) return lv;
        if (obj instanceof String s) return wrapString(s);
        if (obj instanceof Number n) return wrapNumber(n.doubleValue());
        if (obj instanceof Boolean b) return wrapBoolean(b);
        if (obj instanceof ILuaTable t) return t;
        return wrapUserdata(obj);
    }

    @Override
    public ILuaValue callFunction(ILuaValue function, ILuaValue... args) {
        if (function instanceof CobaltLuaValue clv) {
            try {
                LuaValue cobaltFunc = clv.getCobaltValue();
                LuaValue[] cobaltArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    cobaltArgs[i] = ((CobaltLuaValue) args[i]).getCobaltValue();
                }
                Varargs result = org.squiddev.cobalt.function.Dispatch.invoke(state, cobaltFunc, ValueFactory.varargsOf(cobaltArgs));
                return new CobaltLuaValue(result.arg(1));
            } catch (Throwable e) {
                AsyncFileLogger.get().error("FUNCTION_CALL", "Lua error executing callback: " + e.getMessage(), state);
            }
        }
        return nilValue();
    }

    @Override
    public void registerGlobal(String name, ILuaValue value) {
        state.globals().rawset(name, ((CobaltLuaValue) value).getCobaltValue());
    }

    @Override
    public void registerGlobal(String name, ILuaFunction function) {
        state.globals().rawset(name, new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                int count = args.count();
                ILuaValue[] wrappedArgs = new ILuaValue[count];
                for (int i = 0; i < count; i++) {
                    wrappedArgs[i] = new CobaltLuaValue(args.arg(i + 1));
                }
                try {
                    ILuaValue result = function.invoke(wrappedArgs);
                    return result == null ? Constants.NIL : ((CobaltLuaValue) result).getCobaltValue();
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    @Override
    public void executeScript(File file, String context) {
        if (!file.exists()) {
            AsyncFileLogger.get().warn(context, "Script file does not exist: " + file.getAbsolutePath(), state);
            return;
        }

        try (InputStream stream = Files.newInputStream(file.toPath())) {
            LuaClosure closure = LoadState.load(state, stream, file.getName(), state.globals());
            org.squiddev.cobalt.function.Dispatch.call(state, closure);
            AsyncFileLogger.get().info(context, "Successfully executed script: " + file.getName(), state);
        } catch (CompileException e) {
            LuaLinter.logFancyCompileError(context, file.getName(), file, e);
        } catch (LuaError e) {
            e.fillTraceback(state);
            LuaLinter.logFancyRuntimeError(context, file.getName(), file, e);
        } catch (Throwable e) {
            AsyncFileLogger.get().error(context, "Unexpected error running script " + file.getName() + ": " + e.getMessage(), state);
        }
    }

    @Override
    public ILuaTable createTable() {
        return new CobaltLuaTable(new LuaTable());
    }

    @Override
    public ILuaValue wrapUserdata(Object userdata) {
        return new CobaltLuaValue(new LuaUserdata(userdata));
    }

    @Override
    public ILuaValue wrapString(String value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue wrapNumber(double value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue wrapBoolean(boolean value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue nilValue() {
        return new CobaltLuaValue(Constants.NIL);
    }
}
