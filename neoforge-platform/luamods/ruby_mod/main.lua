-- ========================================================
-- AUTONOMOUS LUAMOD: ruby_mod | ENTRYPOINT: main.lua
-- ========================================================

-- 1. NẠP NỘI DUNG ĐĂNG KÝ ITEMS, BLOCKS, ARMOR (Ruby Gear Set)
require(".src.startup.ruby_content")

-- 2. NẠP CÔNG THỨC CHẾ TẠO (Ruby Recipes)
if mod:IsServer() then
    require(".src.server.ruby_recipes")
    
    -- 3. NẠP BOSS AI & MAGIC STAFF SKILLS (Server)
    require(".src.server.ruby_boss")
    require(".src.server.magic_staff")
    require(".src.server.woodcrate")
end

-- 4. NẠP CLIENT CONTROLLER (KeyInput & RemoteEvent Dispatch)
if mod:IsClient() then
    require(".src.client.magic_staff_client")
end

-- 5. VÒNG ĐỜI MOD (OnEnable)
function mod.OnEnable()
    local cfg = mod:GetConfig()
    print("[RubyMod] Ruby Gear Set, Boss AI & Magic Staff loaded successfully! Debug: " ..
    tostring(cfg and cfg.debug_mode))
end
