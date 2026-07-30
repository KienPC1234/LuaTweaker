# 🔍 Troubleshooting & Security Hardening Guide

This guide helps diagnose syntax errors, security policy settings, watchdog instruction limits, and hot-reload behavior in LuaTweaker.

---

## 🛡️ 1. Security & Sandbox Protection Guards

LuaTweaker enforces strict security guards to protect server environments and prevent malicious script behavior:

### A. Path Traversal Guard
File operations and script loaders enforce path normalization checks (`resolvedPath.normalize().startsWith(rootDir)`). Any attempt to access files outside the game root `lua/` directory using `../` directory traversal will be blocked automatically with a `SecurityException`.

### B. HTTP SSRF & Scheme URL Security Guard
`http:get()` and `http:post()` validate request URLs:
- Only `http://` and `https://` protocol schemes are permitted. Schemes like `file://` or `ftp://` are blocked.
- Connections to loopback or private IP addresses (`localhost`, `127.0.0.1`, `192.168.x.x`, `10.x.x.x`) are blocked by default to prevent Server-Side Request Forgery (SSRF).
- To allow localhost connection for local development, set `"allowLocalhostHttp": true` in `config/luatweaker.json`.

### C. `Unsafe` API Configurable Security Guard
The JVM memory hacking API (`unsafe`) can be toggled via `config/luatweaker.json`:
```json
{
  "enableUnsafeAPI": true
}
```
When set to `false`, script calls to `unsafe:allocateInstance()` or `setPrivateStatic()` are safely intercepted and blocked.

---

## 📝 2. Log File Location (`lua/logs/luatweaker.log`)

Execution logs and script timing metrics are written to:
`lua/logs/luatweaker.log`

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

## ⏱️ 4. Infinite Loop Watchdog Limit

If a script enters an infinite loop (e.g. `while true do end`), LuaTweaker's instruction counter watchdog terminates execution automatically based on `maxInstructionLimit` in `config/luatweaker.json`:

```log
[LuaTweaker/ERROR] Lua execution terminated: Exceeded instruction limit (500000). Infinite loop detected.
```

---

## ⚡ 5. Hot-Reloading Diagnostics

Run `/lt reload` in-game to re-evaluate scripts across `startup_scripts/`, `server_scripts/`, and `client_scripts/`.

- Custom item and block definitions, recipe queues, event handlers, tooltips, tags, and commands are reset and re-registered cleanly in-place without duplicate registry errors.
- Run `/lt log` or `/lt doctor` to inspect memory health, total loaded script count, and active custom items/blocks.
