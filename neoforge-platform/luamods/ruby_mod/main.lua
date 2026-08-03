-- ========================================================
-- AUTONOMOUS LUAMOD: ruby_mod | ENTRYPOINT: main.lua
-- ========================================================

-- 1. NẠP NỘI DUNG ĐĂNG KÝ ITEMS, BLOCKS, ARMOR (Ruby Gear Set)
require(".src.startup.ruby_content")

-- 2. NẠP CÔNG THỨC CHẾ TẠO (Ruby Recipes)
require(".src.server.ruby_recipes")

-- 3. NẠP BOSS AI & MAGIC STAFF SKILLS (Server)
require(".src.server.ruby_boss")
require(".src.server.magic_staff")
require(".src.server.woodcrate")

-- 4. NẠP CLIENT CONTROLLER (KeyInput & RemoteEvent Dispatch)
require(".src.client.magic_staff_client")

-- 5. VÒNG ĐỜI MOD (OnEnable)
function mod.OnEnable()
    local cfg = mod:GetConfig()
    print("[RubyMod] Ruby Gear Set, Boss AI & Magic Staff loaded successfully! Debug: " ..
    tostring(cfg and cfg.debug_mode))
end
