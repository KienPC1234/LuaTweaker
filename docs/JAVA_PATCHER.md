# ⚡ Advanced Java Method Patcher, Bytecode Hooking & Reflection Guide (`patcher`, `unsafe`)

> **Stage:** `startup_scripts/` or `server_scripts/`  
> **Global Variable:** `patcher`, `unsafe`

LuaTweaker includes a deep JVM runtime manipulation engine allowing modpack creators to intercept Java method invocations, redirect execution flow, mutate private static/instance fields, and bypass JPMS encapsulation barriers.

---

## 🛡️ 1. NeoForge ClassLoader Resolution
`patcher` automatically searches across NeoForge's `ModuleClassLoader`, thread context classloaders, and system classloader to resolve third-party mod classes without throwing `ClassNotFoundException`.

---

## 🔧 2. Instance & Static Field Inspection/Mutation (`patcher`)

```lua
-- Mutate static field in a mod class
patcher:patchField("com.othermod.ModConfig", "MAX_TICKS", 500)

-- Read static field value
local maxTicks = patcher:getStaticField("com.othermod.ModConfig", "MAX_TICKS")

-- Mutate private instance field on a live object
patcher:patchInstanceField(tileEntity, "energyStorage", 1000000)

-- Read private instance field on a live object
local currentEnergy = patcher:getInstanceField(tileEntity, "energyStorage")
```

---

## 🪝 3. Method Interception & Hooking (`patcher:hookMethod`)

```lua
patcher:hookMethod("com.othermod.TileEntityFactory", "processItem", "(Lnet/minecraft/world/item/ItemStack;)Z", function(instance, args, proceed)
    print("Intercepted processItem! Instance: " .. tostring(instance))
    
    -- Call original method
    local result = proceed(args)
    return result
end)
```

---

## 🛑 4. Security & Malware Hardening
`JavaSecurityFilter` enforces security rules even when using `patcher` or `unsafe`:
- Prohibits reflective access to `java.lang.System.exit`, `java.lang.Runtime.exec`, or `ProcessBuilder`.
- Audits and logs all method hooks in `logs/luatweaker.log`.
