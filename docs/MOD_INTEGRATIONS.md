# 🔌 Mod Integrations, JEI Custom GUI Categories, Async HTTP & Network Messaging (`mods`, `registry`, `jei`, `http`, `network`, `unsafe`)

> **Global Variables:** `mods`, `registry`, `jei`, `http`, `network`, `unsafe`  
> **Service Lookup:** `game:GetService("Mods")`, `game:GetService("Jei")`, `game:GetService("Http")`, `game:GetService("Network")`

LuaTweaker provides a rich suite of APIs for inspecting loaded mods, querying registries, registering custom JEI recipe tabs with custom GUI textures, fetching remote HTTP APIs asynchronously, sending client-server network packets, and executing low-level JVM memory operations.

> 💡 **Service Registry Paradigm**: All mod integration services are accessible via global shortcuts or `game:GetService(...)` / `Mod:GetService(...)`.

---

## 👁️ 1. JEI / REI / EMI Mod Integration & Custom GUI Category Builder (`jei`)

### A. Basic Item & Category Hiding
```lua
-- Hide specific item from JEI
jei:hide("minecraft:dirt")

-- Hide all items from a mod
jei:hideMod("secretmod")

-- Hide entire recipe category
jei:hideCategory("minecraft:anvil")

-- Add item description page
jei:addDescription("luatweaker:custom_ruby", "Found deep underground in ruby ore veins.")

-- Register custom recipe workstation item for category
jei:addWorkstation("minecraft:crafting", "luatweaker:custom_ruby_block")
```

### B. Custom JEI Recipe Category Registration with GUI Textures (`jei:registerCategory`)
Register brand-new JEI recipe tabs with custom GUI textures, layout width/height, icon item, title, and catalyst items:

```lua
jei:registerCategory("luatweaker:ruby_fusion", function(cat)
    cat:title("Ruby Fusion Altar")
       :icon("luatweaker:custom_ruby_block")
       :guiTexture("luatweaker:textures/gui/fusion_jei.png", 176, 86)
       :catalyst("luatweaker:custom_ruby_block")
end)
```

### C. Adding Custom Recipe Displays (`jei:addRecipe`)
Add custom recipe displays to any registered JEI category tab:

```lua
jei:addRecipe("luatweaker:ruby_fusion", {
    inputs = { item("minecraft:diamond"), item("minecraft:emerald") },
    outputs = { item("luatweaker:custom_ruby") }
})
```

### D. Container GUI Click Areas (`jei:addClickArea`)
Bind container GUI screen regions to open JEI tabs when clicked:

```lua
jei:addClickArea("com.othermod.FusionScreen", 80, 35, 22, 15, "luatweaker:ruby_fusion")
```

---

## 🌐 2. Async HTTP Web API (`http`)

```lua
http:get("https://api.example.com/modpack_config.json", function(response)
    print("Received HTTP status: " .. response.status)
end)
```

---

## 📡 3. Client-Server Network Messaging (`network`)

```lua
network:sendToServer("my_packet_channel", { action = "OPEN_GUI", target = 42 })

network:registerChannel("my_packet_channel", function(payload)
    print("Server received packet action: " .. payload.action)
end)
```

---

## 🔍 4. Mod Query API (`mods`)

```lua
if mods:isLoaded("mekanism") then
    print("Mekanism mod is active!")
end
```
