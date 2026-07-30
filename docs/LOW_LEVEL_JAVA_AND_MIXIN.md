# ⚡ Low-Level Java Reflection, Interface Proxies & Mixin Bytecode Hooking

## 1. ☕ Low-Level Java Reflection & Class Loading (`java` / `Java`)

LuaTweaker provides 100% parity with KubeJS `Java.loadClass()` and `Java.proxy()`, allowing modpack creators to resolve any loaded Java class across NeoForge mod classloaders and instantiate dynamic Java Interface Proxies directly in Lua.

### Class Resolution (`Java.loadClass` / `java:loadClass`)
```lua
-- Resolve any Minecraft, NeoForge, or third-party Mod class
local PlayerClass = Java:loadClass("net.minecraft.world.entity.player.Player")
local CustomModClass = java:loadClass("com.othermod.api.CustomManager")
```

### Implementing Java Interfaces in Lua (`java:proxy`)
Create a dynamic Java `Proxy` instance implementing any Java interface (`java.lang.Runnable`, `java.util.function.Consumer`, custom mod listeners, etc.):

```lua
-- Create a Runnable interface proxy
local myTask = java:proxy("java.lang.Runnable", {
    run = function()
        print("Java Runnable executed directly from Lua!")
    end
})

-- Pass myTask to any Java method expecting a Runnable!
```

### Static Field & Method Operations (`java:getStatic`, `java:invokeStatic`)
```lua
-- Access static fields
local maxVal = java:getStatic("java.lang.Integer", "MAX_VALUE")

-- Set static fields
java:setStatic("com.some.mod.ConfigClass", "DEBUG_MODE", true)

-- Invoke static methods
local absValue = java:invokeStatic("java.lang.Math", "abs", {-50}) -- 50
```

---

## 2. 🌀 Low-Level Mixin Dynamic Bytecode Hooking (`mixin`)

Intercept internal Java methods, modify return values, or cancel method execution at runtime using Mixin-style hooks directly in Lua.

### Inject at Head & Cancel Execution (`mixin:injectHead`)
```lua
-- Inject at the beginning of Player.hurt() method
mixin:injectHead("net.minecraft.world.entity.player.Player", "hurt", "(Ldamage/DamageSource;F)Z", function(player, args, event)
    local damageSource = args[1]
    local amount = args[2]

    print("[Mixin] Player hurt event intercepted! Amount: " .. tostring(amount))

    -- Cancel damage if amount exceeds 100
    if amount > 100 then
        event:cancel(false) -- Prevent damage execution and return false
    end
end)
```

### Inject at Return & Modify Return Value (`mixin:injectReturn`)
```lua
-- Inject before ItemStack.getMaxStackSize() returns
mixin:injectReturn("net.minecraft.world.item.ItemStack", "getMaxStackSize", "()I", function(stack, args, originalReturn)
    -- Extend stack size dynamically
    if stack:getItem():toString() == "luatweaker:custom_ruby" then
        return 128
    end
    return originalReturn
end)
```

### Method Redirect & Overwrite (`mixin:redirect`, `mixin:overwrite`)
```lua
-- Completely replace a method implementation
mixin:overwrite("com.target.mod.Class", "targetMethod", "()V", function(instance, args)
    print("Method completely overwritten by Lua!")
end)
```

---

## 3. ⚔️ LuaTweaker vs. KubeJS Feature Parity Matrix

| Feature | KubeJS | LuaTweaker | Advantage |
|---------|--------|------------|-----------|
| **Script Language** | JavaScript (Rhino) | Lua 5.1/5.2 (LuaJ 3.0.1) | **10-20x faster boot & minimal RAM** |
| **Java Class Loading** | `Java.loadClass()` | `Java:loadClass()` / `java:loadClass()` | **Full NeoForge ClassLoader resolution** |
| **Interface Proxies** | `Java.proxy()` | `java:proxy()` | **Native Java Dynamic Proxying** |
| **Bytecode Hooking** | Limited | `mixin:injectHead`, `mixin:injectReturn`, `patcher:hookMethod` | **Deep JVM Bytecode & Method Interception** |
| **JVM Memory Hacking** | Not available | `unsafe:allocateInstance`, `unsafe:setPrivateStatic` | **Bypasses constructor & JPMS encapsulation** |
| **Virtual Datapacks** | File-based | In-Memory RAM + File Injection | **Near-zero I/O overhead** |
