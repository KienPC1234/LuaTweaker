# 🔍 Troubleshooting & Security Hardening Guide

This guide helps diagnose syntax errors, security policy settings, resource caps, and hot-reload behavior in LuaTweaker.

---

## 🛡️ 1. Security & Sandbox Protection Guards

LuaTweaker enforces strict security guards to protect server environments and prevent malicious script behavior:

### A. Path Traversal Guard
File operations and script loaders enforce path normalization checks (`resolvedPath.normalize().startsWith(rootDir)`). Any attempt to access files outside the game root `lua/` directory using `../` directory traversal will be blocked automatically with a `SecurityException`.

### B. HTTP & `Net:HttpGet` Guard
`Net:HttpGet` is denied by default. A LuaMod must declare the `net.http` permission in its `manifest.json` to call it; without that permission the call returns an error and LuaTweaker prints a red warning in chat and the log. Only `http://` and `https://` schemes are accepted.

### C. `Runtime` API Permission Guard
JVM access through `LuaTweaker.Runtime` is gated by manifest permissions, not by a config file:
- `runtime.reflection` for `Runtime.Class`, field/static access and `Runtime.Proxy`.
- `runtime.bytecode_hook` for bytecode patching.

Mods that do not declare these permissions never get the `Runtime` module bound, so the calls fail at the binding layer before any JVM access happens.

---

## 📝 2. Log File Location (`logs/luatweaker/latest.log`)

Execution logs and script timing metrics are written to:
`logs/luatweaker/latest.log`, with one file per LuaMod under `logs/luatweaker/mods/<mod_id>.log`.

Log messages follow CraftTweaker-style categorization:
- `[LuaTweaker/INFO]`: Script load & bootstrap status.
- `[LuaTweaker/DEBUG]`: Verbose state & binding registration.
- `[LuaTweaker/RECIPE]`: Recipe additions and removals.
- `[LuaTweaker/EVENT]`: Event channel subscriptions and posts.
- `[LuaTweaker/ERROR]`: Syntax and runtime errors with stack tracebacks.
- `[LuaTweaker/TIMING]`: Exact millisecond execution timings per script.

---

## 🛑 3. Syntax & Runtime Error Isolation

LuaTweaker runs under strict sandbox and exception isolation guards. If a script encounters a syntax or runtime error:
- The error is logged cleanly with line numbers to `lua/logs/luatweaker.log`.
- An error message is sent to in-game chat.
- **Minecraft will NOT crash or freeze.**

To validate syntax across all script stage directories:
```bash
/lt syntax
```

---

## ⏱️ 4. Runaway Script Protection

LuaTweaker does **not** install an instruction-count watchdog. An infinite loop such as
`while true do end` blocks the thread running it (the server thread for tick-time Lua) and the
game must be restarted. No config key can interrupt it today.

What the VM does enforce are resource caps, configured in `config/luatweaker-startup.toml`
(the STARTUP config, so the caps are in place before LuaMods load). Every cap is `0` (disabled)
by default:

```toml
[limits]
maxMemoryBytes = 0        # heap budget for the VM; strongest protection against table/string runaway
maxCallDepth = 0          # frames; note luava disables its JIT while this is non-zero
maxCoroutines = 0         # live coroutines; covers deferred-task leaks
```

A tripped cap raises a normal Lua error instead of exhausting the JVM heap:

```log
[LuaTweaker/ERROR] ... stack overflow
[LuaTweaker/ERROR] ... memory limit exceeded
[LuaTweaker/ERROR] ... coroutine limit exceeded
```

Recommendations: set `maxMemoryBytes` to a value above your heaviest worldgen mod's peak usage,
and leave `maxCallDepth` at `0` unless you are diagnosing runaway recursion (it costs JIT speed).

---

## ⚡ 5. Hot-Reloading Diagnostics

Run `/lt reload` in-game to re-evaluate scripts across `startup_scripts/`, `server_scripts/`, and `client_scripts/`.

- Custom item and block definitions, recipe queues, event handlers, tooltips, tags, and commands are reset and re-registered cleanly in-place without duplicate registry errors.
- Run `/lt log` or `/lt doctor` to inspect memory health, total loaded script count, and active custom items/blocks.
