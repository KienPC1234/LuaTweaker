# 📦 Server Mod Packs (per-server LuaMod downloads)

A server can offer its LuaMod pack to mismatched clients, resource-pack style:
the client is prompted once per pack, downloads a hash-verified zip into its own
per-bundle folder, and never auto-loads it. Installing stays a human step.

> **Trust model first.** A LuaMod is executable code, not data like a resource
> pack. Auto-running server-sent code would be remote code execution. So the
> engine guarantees three things and nothing more:
>
> 1. **Identity is the content hash.** Folder and consent are keyed by the first
>    16 hex chars of the file SHA-256 (`luatweaker/servers/<id>/`), never by
>    server address. Same bytes from any server share one folder; different
>    bytes always re-prompt. A hostile server cannot inherit another server's
>    consent because it cannot serve the same bytes under a different digest.
> 2. **Bytes are pinned before they land.** Only HTTPS URLs, no userinfo, no
>    redirects, public hosts only, 64 MiB cap, and a constant-time SHA-256 check
>    before the atomic write. A mismatch leaves no file behind.
> 3. **Consent is explicit and local.** Default is ASK (prompt every mismatch).
>    Remember-ALLOW persists per bundle; Remember-Skip persists DENY. A missing,
>    corrupt or oversized consent file degrades to ASK, never to auto-download.
>
> Downloaded zips are data: nothing in the engine loads, mounts or executes
> them. Review `luamods/` content before installing, exactly like any mod file
> from the internet.

---

## 1. Server setup (COMMON config)

```toml
# config/luatweaker-common.toml
serverModBundleUrl = "https://example.com/packs/aurora-rpg.zip"
serverModBundleSha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
serverModBundleVersion = "1.2.0"
serverModBundleName = "Aurora RPG"
serverModBundleFile = "aurora-rpg.zip"   # optional; else derived from the URL
```

Leave `serverModBundleUrl` empty (default) to disable the offer. A URL without a
valid 64-char digest fails closed with a server log warning: no download block
is advertised.

Host the zip yourself over HTTPS. The engine never follows redirects and never
contacts private/loopback hosts, so LAN-only testing needs the real deploy or a
manual install.

## 2. What the client sees

On a version mismatch the verdict carries a `download` block
(`{"url":..,"sha256":..,"version":..,"name":..,"filename":..}`). Old clients
ignore the extra field; new clients stash it as the last offer.

| Enforcement | Behaviour |
|---|---|
| `warn` | Still connected: ASK opens the prompt screen (Download / Skip + Remember toggle). Remember-ALLOW downloads in the background without asking again. |
| `kick` | Disconnect follows the verdict, so no screen is possible. The offer (URL + digest) is logged on both sides instead — install the zip manually into `luatweaker/servers/<id>/luamods/`. A remembered ALLOW from an earlier `warn` encounter still auto-downloads in the background. |
| singleplayer | Ignored: local mods already present. |

After a download the client chats/logs the saved path. Restart (or a manual
install) is required — there is intentionally no auto-load in this phase.

## 3. Client layout and consent

```
<gameDir>/luatweaker/servers/<bundleId>/
├── consent.json          # {"decision":"ask|allow|deny","label":"Aurora RPG"}
└── luamods/
    └── aurora-rpg.zip    # verified bytes only
```

Delete `consent.json` to be prompted again; delete the folder to forget the
pack entirely. `luamods/` (global) is never touched by downloads.

## 4. Lua API (low-level, read-only)

```lua
local ModSync = require("LuaTweaker.ModSync")
local raw = ModSync:GetBundleInfoJson()  -- "" when no server offers a pack
if raw ~= "" then
    local Json = require("LuaTweaker.Json")
    local offer = Json.Decode(raw)
    print(offer.id, offer.url, offer.sha256)  -- build your own prompt UI
end
```

Lua can *read* the offer to build a custom UI. Consent, fetching and
verification stay on the Java side: scripts must not be able to accept a
download on the player's behalf.

## 5. Limits and non-goals (this phase)

- One atomic zip per server (max 64 MiB), fetched whole — no resume, no delta.
- No login-phase gating: the offer arrives post-join with the verdict.
- No auto-load / auto-mount of downloaded packs (phase 2, needs an isolated
  load + unload lifecycle and permission stripping for server code).
- `ModSync:GetBundleInfoJson()` returns the configured bundle on the server
  VM and the last verdict offer on the client VM.
