-- ==== My Mod (LuaTweaker template) ====
local Events = require("LuaTweaker.Events")

local config = mod:GetConfig()
local GREETING = "Hello from my_mod"
if type(config) == "table" and config.greeting ~= nil then
    GREETING = config.greeting
end

local function onPlayerJoin(payload)
    print(GREETING .. ", " .. tostring(payload.player))
end

Events:Listen("PlayerJoin", onPlayerJoin)

-- ==== Optional interop ====
-- Hard dep: "dependencies": ["required_base"] in manifest.json (skipped when absent).
-- Soft dep: "optional_dependencies": ["extras"] (loads after it when present).
if mod:HasMod("extras") then
    local api = mod:ImportAPI("extras", "ExtrasApi")
    if api ~= nil then
        print("extras v" .. tostring(mod:GetModVersion("extras")) .. " detected")
    end
end

-- ==== Share an API for other mods ====
local myApi = {}
function myApi.Ping()
    return "pong"
end
mod:ExportAPI("MyApi", myApi)

function mod.OnEnable()
    print(GREETING .. " enabled")
end

function mod.OnDisable()
    print("my_mod disabled")
end

print(GREETING .. " loaded")
