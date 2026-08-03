-- ==== COMMAND TEST MOD ====
-- Demos the server-side command registration system (Commands:Register).
-- Commands are TOP-LEVEL (no /lt prefix): /hello, /sum, /announce, plus
-- WorldEdit-style editing: /wpos1 /wpos2 /wsel /wset /wreplace /wwalls /wfill.

local commands = require(".src.server.commands")
commands.RegisterAll()

function mod.OnEnable()
    print("[command_test_mod] Loaded - try /hello <name>, /sum <a> <b>, /announce <message>, /shop buy <item>")
    print("[command_test_mod] WorldEdit - mark a box with /wpos1 + /wpos2, then /wset minecraft:glass")
end
