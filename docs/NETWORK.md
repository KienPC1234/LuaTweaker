# Network: wire format, limits and chunking

LuaTweaker's network layer ships every RemoteEvent and RemoteFunction message through one
frame type (`luatweaker:packet`). The Lua API did not change: `RemoteEvent` and
`RemoteFunction` behave exactly as before. What changed is the bytes on the wire and the
guards around them.

---

## 1. Why it is binary

The previous path turned every message into JSON text:

```
Lua table -> Gson JsonArray -> String -> UTF-8 bytes -> String -> JsonParser -> Lua table
```

That meant three representations per message, a `String` allocation per hop, repeated keys
on every payload, and a parser that had to be guarded against hostile nesting. The current
codec writes tagged values straight to a byte array and decodes straight into Lua tables.

The four things the old JSON path guaranteed are still guaranteed:

- dense tables (`1..n`) stay Lua arrays (`#t` and `ipairs` work),
- string-keyed tables stay maps,
- values that cannot cross the wire (functions, userdata, threads) fail loudly,
- keys are stringified the same way.

Circular table references fail fast with a naming error instead of burning the
depth budget: the encoder tracks the active path (shared subtables referenced
twice are fine — only true cycles are rejected).

The binary codec adds two guarantees JSON could not give:

- integers keep their integer tag, so ids do not drift through `double`,
- `-0.0`, `NaN` and infinities round-trip instead of being rejected or mangled.

### Tag layout

One version byte, then the argument count, then one tagged value per argument:

| Tag | Value |
|---|---|
| `0` | nil |
| `1` | false |
| `2` | true |
| `3` | integer (zig-zag varint) |
| `4` | double (8-byte big-endian) |
| `5` | string (varint byte length + UTF-8) |
| `6` | array (varint count + values) |
| `7` | map (varint count + key/value pairs; keys are bare UTF-8 strings) |

`LuaPayloadCodec.VERSION` is `1`. A payload with any other version is rejected, never
guessed: the frame protocol is versioned at the NeoForge level as well.

---

## 2. Limits

Every limit is enforced on both send and receive; a peer cannot raise one by declaring a
larger value.

| Constant | Value | Meaning |
|---|---|---|
| `LuaPayloadCodec.MAX_NESTING` | 32 | table/array depth |
| `LuaPayloadCodec.MAX_ELEMENTS` | 65,536 | arguments or entries per collection |
| `LuaPayloadCodec.MAX_STRING_BYTES` | 1 MiB | one UTF-8 string |
| `LuaPayloadCodec.MAX_MESSAGE_BYTES` | 4 MiB | one logical message after reassembly |
| `ChunkAssembler.CHUNK_BYTES` | 32 KiB | bytes per outbound frame (conservative vs vanilla/NeoForge/pipeline-mod caps) |
| `ChunkAssembler.MAX_FRAME_BYTES` | 256 KiB | largest inbound frame accepted (older peers stay compatible) |
| `ChunkAssembler.MAX_CHUNKS` | 128 | frames per message (32 KiB x 128 = 4 MiB) |
| `ChunkAssembler.TTL_NANOS` | 5 s | lifetime of a partially received message |
| `ChunkAssembler.MAX_ASSEMBLIES` | 2,048 | concurrent partial messages, all connections |
| `ChunkAssembler.MAX_ASSEMBLIES_PER_KEY` | 4 | concurrent partial messages per connection |

A payload that exceeds a limit throws `IllegalArgumentException` at the call site (a Lua
error), not on the Netty thread. Previously an oversized payload reached the vanilla
encoder and tore the connection down.

---

## 3. Chunking

`NeoForgeNetworkPlatform` splits anything above `CHUNK_BYTES` into `total` frames before
calling `PacketDistributor`; `ChunkAssembler` on the receiving side rebuilds it before the
Lua layer sees it.

- Frames may arrive in any order; out-of-order chunks are kept.
- A duplicate frame is ignored (it can only arrive from a buggy or hostile peer).
- Changing `total` mid-message, an out-of-range index, a frame above
  `MAX_FRAME_BYTES`, more than 128 chunks, or more than 4 MiB total is rejected.
- A partial message expires after 5 seconds and is dropped.
- Partial messages are cleared per connection on logout, and all of them on
  `/lt reload` so pre-reload frames never complete inside the new VM's handlers.
  Frames already queued in Netty still arrive and start a fresh assembly, like
  any late packet — same as a logout/login race, by design.
- Frames are 32 KiB on the wire but up to 256 KiB are accepted, so older peers
  keep working during a mixed-version rollout. A near-ceiling 4 MiB payload
  travels as up to 128 frames and occupies the link for several ticks instead
  of one: framing adds no bytes, but the total bytes dominate a weak link
  either way — keep huge payloads rare and prefer deltas.

Because chunking happens in the platform, the Lua service only ever handles whole messages.

---

## 3a. Forged packets: trust model and low-level defenses

A hostile client (modified Lua, custom build, raw packets) can send **any bytes on
any channel**. It cannot forge three things, and the engine guarantees all three:

1. **Player identity.** The `player` first argument of `OnServerEvent`/`OnServerInvoke`
   comes from the server-side connection (`ServerPlayer`), never from packet bytes.
   It is always safe to branch on *who* sent a packet; it is never safe to trust
   *what* the packet claims beyond its validated shape.
2. **Payload shape.** The binary codec rejects malformed, over-nested, oversized
   payloads before Lua sees them; sizes are capped both ways.
3. **Rate.** Serverbound events/functions are token-bucketed per player; floods are
   dropped with a log line instead of reaching Lua.

Everything else is the mod's job, and the engine only supplies primitives
(no policies, no frameworks):

- **Block inbound on push-only channels.** `event:SetInboundEnabled(false)`
  (and the same on `RemoteFunction`) drops forged client packets *before decode*.
  Use it on every HUD/sync channel the server only fires at clients.
- **Check operator level.** `player:HasPermissionLevel(n)` reads the server-side
  op list (vanilla levels 0-4, fail-closed `false` with no server). Guard every
  privileged handler first:
  ```lua
  event.OnServerEvent:Connect(function(player, ...)
      if not player:HasPermissionLevel(2) then return end
      -- ... privileged work ...
  end)
  ```
- **Validate semantics in Lua.** After identity + permission, check arg types and
  ranges, cooldowns/timestamps, and game-state preconditions (is the player near
  the block? does the stack actually exist?). Malformed shapes never arrive, but
  *well-formed lies* do — a level-99 item count in a valid envelope is still a lie.
- **Namespace channels per mod** (`myMod:ShopBuy`, not `ShopBuy`). The engine
  records the first registering mod as the channel owner (see `/lt doctor`
  below); sharing a name is allowed for interop, but a collision with a
  malicious mod is only visible if names are distinct.
- **Treat server-to-client data as untrusted on the client.** A server you joined
  can send anything; client scripts must validate before applying (especially
  before spending/traveling/teleporting the local player).
- **Same-VM mods are trusted code.** Two LuaMods on one side share one VM: a
  malicious mod there already has the full Lua API. Install mods from sources
  you trust; audit with the channel report below.

**Wire attackers** (third-party tampering in transit) are vanilla's domain:
payloads travel inside the normal (encrypted, when enabled) Minecraft connection.
This layer adds no custom crypto and never opens a second socket.

### Audit: `/lt doctor` remote channels

`doctor` (op level 2) lists every registered channel with its owning mod and its
inbound switch:

```
Remote channels: 3
  - ShopBuy (owner: my_shop) inbound open
  - HudSync (owner: my_hud) inbound blocked
```

A channel owned by a mod you did not install, or an `inbound open` flag on a
channel that should be push-only, is where packet-misbehavior investigations
start. The same data is available to Java via
`NetworkServiceImpl.getChannelOwners()` / `getInboundBlockedNames()`.

---

## 4. RemoteEvents

```lua
local Network = require("LuaTweaker.Network")
local event = Network.GetOrCreateRemoteEvent("StaffSwapSkill")

-- server
event.OnServerEvent:Connect(function(player, skillIndex) ... end)

-- client
event:FireServer(3)

-- server -> one client / all clients
event:FireClient(player, 3)
event:FireAllClients(3)
```

Serverbound events are rate-limited **before** the payload is decoded, in two layers:
a per-channel budget for the named channel, then the per-player budget below.
Unknown channel names skip the channel layer (they must not mint unlimited
buckets) and still pay the per-player budget, so probing random names cannot
amplify work either:

| Constant | Value |
|---|---|
| burst per player per channel | 60 events |
| sustained per player per channel | 30 events/second |
| burst per player (all channels) | 120 events |
| sustained per player (all channels) | 60 events/second |

A flood is dropped with a warning in the mod log; the calling script is not blocked. The
per-channel budget is why one chatty mod throttles itself instead of starving the
player's other mods. The budget is per player, so one client cannot starve the others. A missing player identity
(from a malformed flow) is dropped before the budget is touched. Buckets are dropped
on logout (a rejoin starts from full budgets) and only evicted globally past 16,384
entries as a memory backstop. `FireServer` applies the
same two layers on the sending side, so a runaway client loop does not fill the socket with
packets the server would drop anyway.

### Reserved channel names

`LuaTweaker:` is reserved for engine channels (`LuaTweaker:SyncData`,
`LuaTweaker:OpenScreen`, `LuaTweaker:CloseScreen`, `LuaTweaker:Rpc`). Registering a
RemoteEvent or RemoteFunction with that prefix throws instead of silently shadowing a
control channel.

---

## 5. RemoteFunctions

RemoteFunction is a real round trip: the request travels as an `RpcEnvelope` on
`LuaTweaker:Rpc`, the answering side runs `OnServerInvoke`/`OnClientInvoke`, and the caller
yields until the result arrives (or times out after 10 s, see
`luatweaker_bootstrap.lua`).

**Envelope fields** (all validated):

| Field | Limit |
|---|---|
| request id | 64 chars, non-empty |
| phase | `0` invoke, `1` result |
| function name | 128 chars, non-empty |
| error text | 4,096 chars (long messages are truncated with `...`) |
| payload | a `LuaPayloadCodec` message, at most 4 MiB |

**Budgets** (`NetworkServiceImpl`):

| Guard | Value |
|---|---|
| outstanding requests per VM | 256 |
| outstanding requests per player (server) / local client | 16 |
| unanswered request lifetime | 30 s |
| invoke burst per player | 60 |
| invoke refill per player | 30/second |
| invoke burst per player per function | 30 |
| invoke refill per player per function | 15/second |

Unknown function names consume the budget too: a client cannot turn the server into an
error-packet amplifier by calling functions that do not exist.

---

## 6. Control channels

Most are clientbound only and carry a small UTF-8 JSON object rather than Lua values:

| Channel | Direction | Payload |
|---|---|---|
| `LuaTweaker:SyncData` | server -> client | `{"key":..,"val":..,"type":..}` |
| `LuaTweaker:OpenScreen` | server -> client | `{"id":..,"title":..}` |
| `LuaTweaker:CloseScreen` | server -> client | `{}` (ignored) |
| `LuaTweaker:ModHello` | client -> server | `{"mods":{"id":"version",...}}` (required universal mods only, max 64 KiB) |
| `LuaTweaker:ModSync` | server -> client | server mod list on join, then `{"ok":..,"message":..}` verdict |

They travel through the same frame type, so they are also size-bounded; they are far below
one chunk in practice.

---

## 6a. LuaMod sync handshake (server <-> client)

Each side loads its own `luamods/` directory, so a client missing a `universal`
mod (or running another version) would previously join and silently lose features.
The handshake closes that gap, Forge-style:

1. On login the server records the join and sends its required mod list
   (`universal` + `sync: "required"`, the manifest default for `universal`).
2. The client answers on `LuaTweaker:ModHello` with its own required list
   (also sent on `ClientPlayerNetworkEvent.LoggingIn`, plus auto-reply when the
   server list arrives, so a missed login event cannot skip the handshake).
3. The server compares id and version both ways and answers on
   `LuaTweaker:ModSync`. On mismatch the default (`modSyncMode: "kick"` in the
   COMMON config, no setup required) disconnects the player with the exact
   missing/mismatched ids, like Forge; `"warn"` only chats and logs.
   Singleplayer (integrated server) never kicks, it only warns.
4. A client that never answers within `modSyncTimeoutSeconds` (default 10 s,
   e.g. an outdated LuaTweaker) is treated as missing everything.
5. Every `/lt reload` re-checks all online players, and Lua observes each
   check through the `ModSyncCompleted` event plus the low-level
   `ModSync` service (`GetServerMods`, `GetClientMods`, `CheckPlayer`,
   `SetEnforcementMode`) composed with `Server:Kick` for custom conditions.

`sync: "optional"` in a manifest exempts that mod from enforcement; `server`
and `client` mods are side-only and never enforced.

A mismatched verdict can carry a server bundle offer (`download` block with
`url/sha256/version/name/filename`) when the server sets `serverModBundleUrl`
+ `serverModBundleSha256` in the COMMON config. See [SERVER_MOD_PACKS](SERVER_MOD_PACKS.md):
per-content-hash folders under `luatweaker/servers/<id>/`, prompt on `warn`,
log-only on `kick`, hash-verified download, never auto-loaded.

---

## 7. Implementing the transport on another platform

`IPlatformNetwork` moves bytes, not protocol objects:

```java
void sendPayloadBytes(String playerUuid, String channelName, byte[] data);
void broadcastPayloadBytes(String channelName, byte[] data);
void sendPayloadBytesToServer(String channelName, byte[] data);
// batch fan-out: one encode/split, then one frame set per player
default void sendPayloadBytesToPlayers(java.util.List<String> playerUuids, String channelName, byte[] data);
```

A platform implementation is responsible for:

1. splitting `data` with `ChunkAssembler.split(...)` and sending each frame,
2. reassembling inbound frames with `ChunkAssembler` before dispatching,
3. clearing a connection's partial messages on disconnect.

The `String` overloads (`sendPayloadPacket`, ...) are UTF-8 convenience methods for the
control channels; Lua traffic must use the byte methods.

---

## 8. What is still not handled

- Delivery is best-effort: a missing player or absent server drops the message with a log
  line. There is no acknowledgement or automatic resend at the Lua layer. The mod-sync
  handshake uses the same transport, so it pairs every hello with a server-driven timeout
  instead of assuming delivery.
- Delivery is unordered across frames and uncoalesced: if you `FireAllClients` a
  progress value 20 times a second, a lag spike makes the client execute 19 stale
  callbacks before the fresh one. The engine deliberately keeps no send queue
  (a queue would add latency and ordering trade-offs to every mod), so state sync
  stays a Lua pattern: send on change (or on a config interval), attach a
  monotonically increasing sequence number, and let the receiver ignore anything
  older than the newest sequence it has applied.
- Targeted sends scale: `FireClients` encodes and splits once per call and fans the same
  frames out to N players (use it instead of looping `FireClient`), `FireAllClientsExcept`
  skips one player without Lua-side filtering, and server-to-client events are
  rate-limited per player, per channel and per broadcast channel, so one runaway
  server script cannot flood every client. Assemblies allow 2,048 global / 4 per
  connection with a 5 s TTL for busy servers; per-tick `FireAllClients` storms still
  need Lua-side intervals and deltas.
- A RemoteFunction answer for a cancelled or timed-out request is ignored (the pending
  entry is gone). This is intentional; resending would need an idempotency contract that
  scripts do not have.
- Encrypted or authenticated transport is vanilla's job. This layer never opens a second
  socket.
