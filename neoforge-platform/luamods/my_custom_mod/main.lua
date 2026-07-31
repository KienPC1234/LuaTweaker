-- ========================================================
-- MOD ID: my_custom_mod | ENTRYPOINT: main.lua
-- ========================================================

-- 1. NẠP MODULE TĨNH (Không dùng biến toàn cục trôi nổi)
local Content = require("LuaTweaker.Content")
local Events  = require("LuaTweaker.Events")
local Task    = require("LuaTweaker.Task")

-- 2. NẠP MODULE NỘI BỘ (Từ thư mục src/)
local BossAI  = require(".src.server.boss_ai")
if BossAI and BossAI.Initialize then
    BossAI.Initialize()
end

-- 3. GIAI ĐOẠN STARTUP: ĐĂNG KÝ VẬT PHẨM (Thay cho startup_scripts)
local CustomSword = Content.NewItem("my_custom_mod:shadow_blade")
    :DisplayName("§5Lưỡi Kiếm Bóng Đêm")
    :AttackDamage(12.0)
    :Register()

-- 4. GIAI ĐOẠN RUNTIME: LẮNG NGHE SỰ KIỆN (Thay cho server_scripts)
if Events and Events.OnEntityDamaged then
    Events.OnEntityDamaged:Connect(function(event)
        local attacker = event.Attacker
        local target   = event.Target

        if attacker and attacker:GetHeldItem() and attacker:GetHeldItem().Id == "my_custom_mod:shadow_blade" then
            Task:delay(0.5, function()
                if target and target:IsAlive() then
                    target:TakeDamage(5.0, "magic")
                end
            end)
        end
    end)
end

-- 5. VÒNG ĐỜI MOD (Quản lý Bật/Tắt)
function mod.OnEnable()
    local cfg = mod:GetConfig()
    print("[MyCustomMod] Successfully loaded! Debug Mode: " .. tostring(cfg and cfg.debug_mode))
end
