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
| `ChunkAssembler.CHUNK_BYTES` | 256 KiB | bytes per frame |
| `ChunkAssembler.MAX_CHUNKS` | 16 | frames per message |
| `ChunkAssembler.TTL_NANOS` | 15 s | lifetime of a partially received message |
| `ChunkAssembler.MAX_ASSEMBLIES` | 128 | concurrent partial messages, all connections |
| `ChunkAssembler.MAX_ASSEMBLIES_PER_KEY` | 2 | concurrent partial messages per connection |

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
- Changing `total` mid-message, an out-of-range index, a frame above `CHUNK_BYTES`, more
  than 16 chunks, or more than 4 MiB total is rejected.
- A partial message expires after 15 seconds and is dropped.
- Partial messages are cleared per connection on logout.

Because chunking happens in the platform, the Lua service only ever handles whole messages.

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

Serverbound events are rate-limited **before** the payload is decoded, per player:

| Constant | Value |
|---|---|
| burst | 120 events |
| sustained | 60 events/second |

A flood is dropped with a warning in the mod log; the calling script is not blocked. The
budget is per player, so one client cannot starve the others. A missing player identity
(from a malformed flow) is dropped before the budget is touched. `FireServer` applies the
same numbers on the sending side, so a runaway client loop does not fill the socket with
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
| unanswered request lifetime | 60 s |
| invoke burst per player | 60 |
| invoke refill per player | 30/second |

Unknown function names consume the budget too: a client cannot turn the server into an
error-packet amplifier by calling functions that do not exist.

---

## 6. Control channels

These are clientbound only and carry a small UTF-8 JSON object rather than Lua values:

| Channel | Payload |
|---|---|
| `LuaTweaker:SyncData` | `{"key":..,"val":..,"type":..}` |
| `LuaTweaker:OpenScreen` | `{"id":..,"title":..}` |
| `LuaTweaker:CloseScreen` | `{}` (ignored) |

They travel through the same frame type, so they are also size-bounded; they are far below
one chunk in practice.

---

## 7. Implementing the transport on another platform

`IPlatformNetwork` moves bytes, not protocol objects:

```java
void sendPayloadBytes(String playerUuid, String channelName, byte[] data);
void broadcastPayloadBytes(String channelName, byte[] data);
void sendPayloadBytesToServer(String channelName, byte[] data);
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
  line. There is no acknowledgement or automatic resend at the Lua layer.
- A RemoteFunction answer for a cancelled or timed-out request is ignored (the pending
  entry is gone). This is intentional; resending would need an idempotency contract that
  scripts do not have.
- Encrypted or authenticated transport is vanilla's job. This layer never opens a second
  socket.
