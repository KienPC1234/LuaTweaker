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
    private final java.util.Map<String, LuaValue> moduleCache = new java.util.concurrent.ConcurrentHashMap<>();

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

        // 7. Bootstrap Roblox task and Signal libraries
        // 7. Bootstrap Roblox task and Signal libraries
        executeString(
            "local task = {}\n" +
            "local deferred = {}\n" +
            "local delays = {}\n" +
            "function task.spawn(fn, ...)\n" +
            "    local thread = coroutine.create(fn)\n" +
            "    local ok, err = coroutine.resume(thread, ...)\n" +
            "    if not ok then\n" +
            "        print(\"[ERROR][task.spawn] Coroutine error: \" .. tostring(err))\n" +
            "    end\n" +
            "    return thread\n" +
            "end\n" +
            "function task.defer(fn, ...)\n" +
            "    local args = {...}\n" +
            "    table.insert(deferred, { fn = fn, args = args })\n" +
            "end\n" +
            "function task.delay(sec, fn, ...)\n" +
            "    local args = {...}\n" +
            "    table.insert(delays, { time = os.clock() + sec, fn = fn, args = args })\n" +
            "end\n" +
            "function task.wait(sec)\n" +
            "    sec = sec or 0\n" +
            "    local thread = coroutine.running()\n" +
            "    task.delay(sec, function()\n" +
            "        coroutine.resume(thread)\n" +
            "    end)\n" +
            "    return coroutine.yield()\n" +
            "end\n" +
            "function task._tick()\n" +
            "    local def = deferred\n" +
            "    deferred = {}\n" +
            "    for _, item in ipairs(def) do\n" +
            "        task.spawn(item.fn, table.unpack(item.args))\n" +
            "    end\n" +
            "    local now = os.clock()\n" +
            "    local remaining = {}\n" +
            "    for _, item in ipairs(delays) do\n" +
            "        if now >= item.time then\n" +
            "            task.spawn(item.fn, table.unpack(item.args))\n" +
            "        else\n" +
            "            table.insert(remaining, item)\n" +
            "        end\n" +
            "    end\n" +
            "    delays = remaining\n" +
            "end\n" +
            "_G.task = task\n" +
            "\n" +
            "local Signal = {}\n" +
            "Signal.__index = Signal\n" +
            "function Signal.new()\n" +
            "    local self = setmetatable({}, Signal)\n" +
            "    self._listeners = {}\n" +
            "    return self\n" +
            "end\n" +
            "function Signal:Connect(fn)\n" +
            "    local listener = { fn = fn, connected = true }\n" +
            "    table.insert(self._listeners, listener)\n" +
            "    local conn = {\n" +
            "        Disconnect = function()\n" +
            "            listener.connected = false\n" +
            "            for i, l in ipairs(self._listeners) do\n" +
            "                if l == listener then\n" +
            "                    table.remove(self._listeners, i)\n" +
            "                    break\n" +
            "                end\n" +
            "            end\n" +
            "        end\n" +
            "    }\n" +
            "    conn.disconnect = conn.Disconnect\n" +
            "    return conn\n" +
            "end\n" +
            "Signal.connect = Signal.Connect\n" +
            "function Signal:Once(fn)\n" +
            "    local connection\n" +
            "    connection = self:Connect(function(...)\n" +
            "        connection:Disconnect()\n" +
            "        fn(...)\n" +
            "    end)\n" +
            "    return connection\n" +
            "end\n" +
            "Signal.once = Signal.Once\n" +
            "function Signal:Fire(...)\n" +
            "    local args = {...}\n" +
            "    for _, listener in ipairs(self._listeners) do\n" +
            "        if listener.connected then\n" +
            "            task.spawn(listener.fn, table.unpack(args))\n" +
            "        end\n" +
            "    end\n" +
            "end\n" +
            "Signal.fire = Signal.Fire\n" +
            "function Signal:Wait()\n" +
            "    local thread = coroutine.running()\n" +
            "    local connection\n" +
            "    connection = self:Connect(function(...)\n" +
            "        connection:Disconnect()\n" +
            "        coroutine.resume(thread, ...)\n" +
            "    end)\n" +
            "    return coroutine.yield()\n" +
            "end\n" +
            "Signal.wait = Signal.Wait\n" +
            "_G.Signal = Signal\n",
            "setup_bootstrap"
        );

        executeString(
            "local RemoteEvent = {}\n" +
            "RemoteEvent.__index = RemoteEvent\n" +
            "function RemoteEvent:new(name, javaNetworkService)\n" +
            "    local self = setmetatable({}, RemoteEvent)\n" +
            "    self.Name = name\n" +
            "    self.OnServerEvent = Signal.new()\n" +
            "    self.OnClientEvent = Signal.new()\n" +
            "    self._javaService = javaNetworkService\n" +
            "    return self\n" +
            "end\n" +
            "function RemoteEvent:FireClient(player, ...)\n" +
            "    local args = {...}\n" +
            "    local uuid = \"\"\n" +
            "    if type(player) == \"string\" then\n" +
            "        uuid = player\n" +
            "    elseif type(player) == \"userdata\" or type(player) == \"table\" then\n" +
            "        local raw = player.__instance or player\n" +
            "        if type(raw) == \"userdata\" then\n" +
            "            local ok, res = pcall(function() return tostring(raw:getUUID()) end)\n" +
            "            if ok then uuid = res else\n" +
            "                local ok2, res2 = pcall(function() return tostring(raw:getUUIDString()) end)\n" +
            "                if ok2 then uuid = res2 end\n" +
            "            end\n" +
            "        end\n" +
            "    end\n" +
            "    self._javaService:FireClient(self.Name, uuid, args)\n" +
            "end\n" +
            "function RemoteEvent:FireAllClients(...)\n" +
            "    local args = {...}\n" +
            "    self._javaService:FireAllClients(self.Name, args)\n" +
            "end\n" +
            "function RemoteEvent:FireServer(...)\n" +
            "    local args = {...}\n" +
            "    self._javaService:FireServer(self.Name, args)\n" +
            "end\n" +
            "_G.RemoteEvent = RemoteEvent\n" +
            "\n" +
            "local RemoteFunction = {}\n" +
            "RemoteFunction.__index = RemoteFunction\n" +
            "function RemoteFunction:new(name, javaNetworkService)\n" +
            "    local self = setmetatable({}, RemoteFunction)\n" +
            "    self.Name = name\n" +
            "    self.OnServerInvoke = nil\n" +
            "    self.OnClientInvoke = nil\n" +
            "    self._javaService = javaNetworkService\n" +
            "    return self\n" +
            "end\n" +
            "function RemoteFunction:InvokeServer(...)\n" +
            "    local args = {...}\n" +
            "    return self._javaService:InvokeServer(self.Name, args)\n" +
            "end\n" +
            "function RemoteFunction:InvokeClient(player, ...)\n" +
            "    local args = {...}\n" +
            "    local uuid = \"\"\n" +
            "    if type(player) == \"string\" then uuid = player end\n" +
            "    return self._javaService:InvokeClient(self.Name, uuid, args)\n" +
            "end\n" +
            "_G.RemoteFunction = RemoteFunction\n" +
            "\n" +
            "local UserInputService = {\n" +
            "    InputBegan = Signal.new(),\n" +
            "    InputEnded = Signal.new()\n" +
            "}\n" +
            "function UserInputService:IsKeyDown(keyCode)\n" +
            "    return false -- Handled client-side via PAL\n" +
            "end\n" +
            "_G.UserInputService = UserInputService\n" +
            "\n" +
            "local RunService = {\n" +
            "    Heartbeat = Signal.new(),\n" +
            "    RenderStepped = Signal.new(),\n" +
            "    Stepped = Signal.new()\n" +
            "}\n" +
            "function RunService:IsServer()\n" +
            "    return true\n" +
            "end\n" +
            "function RunService:IsClient()\n" +
            "    return false\n" +
            "end\n" +
            "_G.RunService = RunService\n" +
            "\n" +
            "local CFrame = {}\n" +
            "CFrame.__index = CFrame\n" +
            "function CFrame.new(x, y, z)\n" +
            "    local self = setmetatable({}, CFrame)\n" +
            "    if type(x) == \"table\" and x.X then\n" +
            "        self.Position = x\n" +
            "    else\n" +
            "        self.Position = Vector3.new(x or 0, y or 0, z or 0)\n" +
            "    end\n" +
            "    self.LookVector = Vector3.new(0, 0, -1)\n" +
            "    self.UpVector = Vector3.new(0, 1, 0)\n" +
            "    return self\n" +
            "end\n" +
            "function CFrame.lookAt(eye, target)\n" +
            "    local cf = CFrame.new(eye)\n" +
            "    if eye and target then\n" +
            "        local dir = (target - eye).Unit\n" +
            "        cf.LookVector = dir\n" +
            "    end\n" +
            "    return cf\n" +
            "end\n" +
            "_G.CFrame = CFrame\n" +
            "\n" +
            "local TweenInfo = {}\n" +
            "TweenInfo.__index = TweenInfo\n" +
            "function TweenInfo.new(time, easingStyle, easingDirection, repeatCount, reverses, delayTime)\n" +
            "    local self = setmetatable({}, TweenInfo)\n" +
            "    self.Time = time or 1.0\n" +
            "    self.EasingStyle = easingStyle or \"Linear\"\n" +
            "    self.EasingDirection = easingDirection or \"Out\"\n" +
            "    self.RepeatCount = repeatCount or 0\n" +
            "    self.Reverses = reverses or false\n" +
            "    self.DelayTime = delayTime or 0\n" +
            "    return self\n" +
            "end\n" +
            "_G.TweenInfo = TweenInfo\n" +
            "\n" +
            "local TweenService = {}\n" +
            "function TweenService:Create(instance, tweenInfo, propertyTable)\n" +
            "    local tween = {}\n" +
            "    tween.Completed = Signal.new()\n" +
            "    function tween:Play()\n" +
            "        task.spawn(function()\n" +
            "            if tweenInfo and tweenInfo.DelayTime and tweenInfo.DelayTime > 0 then\n" +
            "                task.wait(tweenInfo.DelayTime)\n" +
            "            end\n" +
            "            if propertyTable then\n" +
            "                for prop, val in pairs(propertyTable) do\n" +
            "                    pcall(function() instance[prop] = val end)\n" +
            "                end\n" +
            "            end\n" +
            "            tween.Completed:Fire()\n" +
            "        end)\n" +
            "    end\n" +
            "    function tween:Stop() end\n" +
            "    function tween:Pause() end\n" +
            "    return tween\n" +
            "end\n" +
            "_G.TweenService = TweenService\n" +
            "\n" +
            "local RaycastParams = {}\n" +
            "RaycastParams.__index = RaycastParams\n" +
            "function RaycastParams.new()\n" +
            "    local self = setmetatable({}, RaycastParams)\n" +
            "    self.FilterDescendantsInstances = {}\n" +
            "    self.FilterType = \"Exclude\"\n" +
            "    return self\n" +
            "end\n" +
            "_G.RaycastParams = RaycastParams\n" +
            "\n" +
            "local Players = { LocalPlayer = nil }\n" +
            "_G.Players = Players\n",
            "ROBLOX_BOOTSTRAP"
        );

        setupNativeRequire(globals);
    }

    private File luaDirectory;

    @Override
    public void setLuaDirectory(File luaDirectory) {
        this.luaDirectory = luaDirectory;
    }

    private void setupNativeRequire(LuaTable globals) {
        LuaValue oldRequire = globals.rawget("require");
        if (oldRequire != null && !oldRequire.isNil()) {
            globals.rawset("_oldRequire", oldRequire);
        }
        globals.rawset("require", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError, org.squiddev.cobalt.UnwindThrowable {
                if (args.count() < 1) throw new LuaError("require requires 1 argument (moduleName)");
                String modName = args.arg(1).toString();
                LuaValue result = moduleCache.get(modName);
                if (result == null) {
                    result = resolveNativeModule(globals, modName);
                    if (result == null || result.isNil()) {
                        result = resolveFileModule(modName);
                    }
                    if (result != null && !result.isNil()) {
                        moduleCache.put(modName, result);
                    }
                }
                if (result != null && !result.isNil()) {
                    return result;
                }
                LuaValue oldReq = globals.rawget("_oldRequire");
                if (oldReq instanceof org.squiddev.cobalt.function.LuaFunction) {
                    return org.squiddev.cobalt.function.Dispatch.invoke(state, oldReq, args);
                }
                throw new LuaError("Module not found: " + modName);
            }
        });
    }

    private LuaValue resolveFileModule(String modName) {
        File dir = luaDirectory;
        if (dir == null || !dir.exists()) {
            dir = new File("lua");
        }
        if (dir == null || !dir.exists()) return Constants.NIL;

        String relPath = modName.replace('.', '/');
        List<String> candidates = List.of(
                relPath + ".lua",
                relPath + "/init.lua",
                "lib/" + relPath + ".lua",
                "lib/" + relPath + "/init.lua",
                "startup/" + relPath + ".lua",
                "startup/" + relPath + "/init.lua",
                "server/" + relPath + ".lua",
                "server/" + relPath + "/init.lua",
                "client/" + relPath + ".lua",
                "client/" + relPath + "/init.lua"
        );

        for (String cand : candidates) {
            File file = new File(dir, cand);
            if (file.exists() && file.isFile()) {
                try (InputStream stream = Files.newInputStream(file.toPath())) {
                    LuaClosure closure = LoadState.load(state, stream, file.getName(), state.globals());
                    Varargs res = org.squiddev.cobalt.function.Dispatch.call(state, closure);
                    LuaValue ret = res.arg(1);
                    return (ret != null && !ret.isNil()) ? ret : ValueFactory.valueOf(true);
                } catch (Throwable e) {
                    AsyncFileLogger.get().error("REQUIRE", "Failed to load Lua module file '" + cand + "': " + e.getMessage(), state);
                    return Constants.NIL;
                }
            }
        }
        return Constants.NIL;
    }

    private LuaValue resolveNativeModule(LuaTable globals, String modName) {
        return switch (modName) {
            case "LuaTweaker.Content", "Content", "startup" -> {
                LuaValue val = globals.rawget("Content");
                yield (val != null && !val.isNil()) ? val : globals.rawget("startup");
            }
            case "LuaTweaker.Recipe", "Recipe", "Recipes", "recipes" -> {
                LuaValue val = globals.rawget("Recipe");
                yield (val != null && !val.isNil()) ? val : globals.rawget("recipes");
            }
            case "LuaTweaker.Events", "Events", "events" -> {
                LuaValue val = globals.rawget("Events");
                yield (val != null && !val.isNil()) ? val : globals.rawget("events");
            }
            case "LuaTweaker.World", "World", "Workspace", "workspace" -> {
                LuaValue val = globals.rawget("World");
                yield (val != null && !val.isNil()) ? val : globals.rawget("Workspace");
            }
            case "LuaTweaker.Task", "Task", "task" -> {
                LuaValue val = globals.rawget("Task");
                yield (val != null && !val.isNil()) ? val : globals.rawget("task");
            }
            case "LuaTweaker.Entities", "Entities", "EntityService" -> {
                LuaValue val = globals.rawget("Entities");
                yield (val != null && !val.isNil()) ? val : globals.rawget("EntityService");
            }
            case "LuaTweaker.AIGoals", "AIGoals" -> globals.rawget("AIGoals");
            case "LuaTweaker.Loot", "Loot" -> globals.rawget("Loot");
            case "LuaTweaker.Storage", "Storage", "storage" -> {
                LuaValue val = globals.rawget("Storage");
                if (val == null || val.isNil()) {
                    val = globals.rawget("storage");
                }
                yield val;
            }
            case "LuaTweaker.WorldStorage", "WorldStorage" -> globals.rawget("WorldStorage");
            case "LuaTweaker.PlayerStorage" -> globals.rawget("PlayerStorage");
            case "LuaTweaker.SessionStorage" -> globals.rawget("SessionStorage");
            case "LuaTweaker.Datapack", "Datapack", "datapack" -> globals.rawget("Datapack");
            case "LuaTweaker.Network", "Network", "NetworkService" -> globals.rawget("NetworkService");
            case "LuaTweaker.Interception", "Interception", "InterceptionService" -> {
                LuaValue val = globals.rawget("Interception");
                yield (val != null && !val.isNil()) ? val : globals.rawget("InterceptionService");
            }
            case "LuaTweaker.Camera", "Camera" -> globals.rawget("Camera");
            case "LuaTweaker.ClientEffects", "ClientEffects" -> globals.rawget("ClientEffects");
            case "LuaTweaker.Signal", "Signal" -> globals.rawget("Signal");
            case "LuaTweaker.Utils", "Utils" -> globals.rawget("Utils");
            case "LuaTweaker.TweenService", "TweenService" -> globals.rawget("TweenService");
            case "LuaTweaker.Math.Vector3", "Vector3" -> globals.rawget("Vector3");
            case "LuaTweaker.Math.Vector2", "Vector2" -> globals.rawget("Vector2");
            case "LuaTweaker.Math.Color3", "Color3" -> globals.rawget("Color3");
            default -> Constants.NIL;
        };
    }

    @Override
    public void executeString(String code, String name) {
        try (InputStream stream = new java.io.ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            LuaClosure closure = LoadState.load(state, stream, name, state.globals());
            org.squiddev.cobalt.function.Dispatch.call(state, closure);
        } catch (Throwable e) {
            AsyncFileLogger.get().error("BOOTSTRAP", "Error running bootstrap script " + name + ": " + e.getMessage(), state);
        }
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

    private LuaValue toCobaltValue(ILuaValue val) {
        if (val == null || val.isNil()) return Constants.NIL;
        if (val instanceof CobaltLuaValue clv) return clv.getCobaltValue();
        Object raw = val.toJavaObject();
        if (raw instanceof String s) return ValueFactory.valueOf(s);
        if (raw instanceof Number n) return ValueFactory.valueOf(n.doubleValue());
        if (raw instanceof Boolean b) return ValueFactory.valueOf(b);
        return ValueFactory.userdataOf(raw);
    }

    @Override
    public ILuaValue callFunction(ILuaValue function, ILuaValue... args) {
        if (function != null && !function.isNil()) {
            try {
                LuaValue cobaltFunc = toCobaltValue(function);
                LuaValue[] cobaltArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    cobaltArgs[i] = toCobaltValue(args[i]);
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
        state.globals().rawset(name, toCobaltValue(value));
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
                    return result == null ? Constants.NIL : toCobaltValue(result);
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
