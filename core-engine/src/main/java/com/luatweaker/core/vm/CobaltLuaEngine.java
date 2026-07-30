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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

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

        // 5. item(id, count) function using abstract PAL helper
        globals.rawset("item", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String itemId = args.arg(1).checkLuaString().toString();
                int count = args.arg(2).isNil() ? 1 : args.arg(2).checkInteger();
                if (Platform.isInitialized()) {
                    IItem item = Platform.get().createItem(itemId, count);
                    return new LuaUserdata(item);
                } else {
                    throw new LuaError("Platform helper is not initialized");
                }
            }
        });

        // 6. ingredient(descriptor) function
        globals.rawset("ingredient", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String descriptor = args.arg(1).checkLuaString().toString();
                return new LuaUserdata(new com.luatweaker.api.wrapper.IngredientWrapper(descriptor));
            }
        });
    }

    @Override
    public void registerService(String name, ILuaTable service) {
        LuaServiceRegistry.register(name, service);
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
