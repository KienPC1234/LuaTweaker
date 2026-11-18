# Plan: Thay Cobalt bang luava (Lua 5.4 JIT) trong LuaTweaker

Trang thai: **DA THUC THI (2026-11-18) — Cobalt da bi xoa hoan toan, khong con fallback.**
Ket qua: `./gradlew build` xanh, 445 tests pass / 0 fail / 0 skip.
Ngay lap: 2026-11-18.

> Ghi chu: tai thoi diem thuc thi, yeu cau doi thanh "bo het Cobalt", nen cac muc
> mo ta A/B flag / fallback ben duoi chi con gia tri lich su. Cay quyet dinh cuoi cung:
> `LuavaEngine` la implementation duy nhat cua `ILuaEngine`.

Nguon: https://github.com/KienPC1234/luava — `io.github.kienpc1234:luava:0.2.5`, Java 21,
zero transitive dependency (ASM duoc shade), MPL-2.0.

---

## 0. Ket luan ngan

**Thay full duoc, nhung khong phai swap dependency 1 dong.** Day la mot *engine migration* co
tam anh huong trung binh: ~25 file Java can doi/ viet lai + ~100 test swap constructor.

Tin tot: lop SPI (`ILuaEngine`, `ILuaValue`, `ILuaTable`, `ILuaFunction`), `LuaBinder`,
`RuntimeRemapper` va toan bo stub generator **da engine-agnostic**. Khoang 90% module code giu
nguyen. Diem kho tap trung o 10 file core-engine + 5 file downcast + mo hinh coroutine/thread.

**Ruiro so 1 (can spike truoc khi cam ket):** luava khuyen cao *mot LuaState moi thread* va khong
chia se state. Hien tai LuaTweaker dung chung 1 VM cho server thread + render thread va `synchronized`
moi entry point. Xung dot nay phai duoc giai quyet o Phase 0.

**Thanh cong khong duoc khang dinh neu chua co benchmark Cobalt vs luava.** Bang benchmark cua luava
so sanh voi LuaJ 3.0.1 (2.8x–19x), **khong so sanh voi Cobalt**. Phat dinh "nhanh gap chuc lan" hien
la **UNVERIFIED** doi voi engine dang dung. Phase 0 co spike do hieu nang bat buoc.

---

## 1. Muc tieu / gioi han

### Muc tieu
- Thay hoan toan `cc.tweaked:cobalt:0.9.9` bang `io.github.kienpc1234:luava:0.2.5`.
- Giu nguyen hop dong SPI va toan bo API Lua hien co (`require("LuaTweaker.X")`, `Events`, `Task`,
  `Signal`, entity table, recipe DSL, dynamic bridge...).
- Khong thay doi halo low-level API: van cung cap ham nguyen thuy cho Lua tu láp logic (AGENTS.md Section 1).
- Co the A/B so sanh trong qua trinh chuyen doi, sau do bo Cobalt hoan toan.

### Khong lam
- Khong viet wrapper high-level moi, khong doi cau truc module, khong doi manifest LuaMod.
- Khong sua 15 file dang uncommitted cua nguoi dung (xem muc 12).
- Khong khang dinh hieu nang / on dinh in-game truoc khi co benchmark + smoke test thuc te.

### Gia dinh can xac nhan lai
- Cobalt la LuaJ-derived (Lua 5.1/5.2-level); luava la Lua 5.4. Script bundled hien tai da quet:
  **khong co** `continue`, `+=`, type alias, `loadstring`, `getfenv`, `table.getn`, chi co
  `table.unpack` (hop le o 5.4). Risk dialect noi bo thap; mod cua third-party co the bi anh huong.
- Apache-2.0 (LuaTweaker) + MPL-2.0 (luava) tuong thich. Khi fat-jar phai kem `META-INF/LICENSE`
  cua luava va giu notice; neu co sua core luava thi phan do phai mo source.

---

## 2. Kien truc muc tieu

Ginguyen SPI, them mot implementation song song:

```
common-api   : ILuaEngine / ILuaValue / ILuaTable / ILuaFunction   (GIU NGUYEN + 2 method moi)
core-engine  : CobaltLuaEngine (CU, tam thoi)  +  LuavaEngine (MOI)
               CobaltLuaValue/Table            +  LuavaLuaValue/Table (MOI)
               LuaBinder / LtvmStubGenerator / RuntimeRemapper / LuaServiceRegistry (GIU NGUYEN)
neoforge     : LuaEngineFactory.create(...)  <- 3 diem tao engine chuyen qua day
```

**2 method bo sung vao `ILuaEngine` (default de khong pha FakeEngine/tests):**

```java
default String getAndClearLastExecutionError() { return null; }   // thay 2 downcast
default ILuaValue wrapDynamicJavaObject(Object obj) { return wrapUserdata(obj); } // thay 3 downcast
```

Ly do: hien co 5 noi `instanceof CobaltLuaEngine` (LuaModManager:290, LuaCommandWrapper:122,
UniversalEventForwarder:56, EntitiesLuaBinding:84/133, InteractionLuaBinding:690/708).
Day vao interface la dung OCP/DIP; khong them `getRawState()` vi se lam ro thung SPI.

LuavaEngine ben trong:
- `org.luava.runtime.LuaState` lam truong VM, `new LuaState()` (co sandbox? **KHONG** —
  can `os`/`io`? Thuc te engine hien tai da thay `os` chi con `clock`/`time`, va can `require`,
  `debug.traceback`, `java.*` cho dynamic bridge. Dung `JavaAccessPolicy.DEFAULT` +
  `javaAllow` theo danh sach cho phep cua project; **khong** go `sandbox()` vi no xoa `require`.
  Tuong duong: tu cau hinh `deny("os")`/`deny("io")` roi cai lai `os.clock`/`os.time`).
- Giu `synchronized` tren moi entry point `ILuaEngine` nhu hien tai (cho den khi spike S3 quyet dinh).
- Dung `LuaDataConverter` / `LuaUserdata` / `LuaFunction.of(...)` cua luava thay vi `ValueFactory`/
  `Constants`/`VarArgFunction` cua Cobalt.

---

## 3. Phase 0 — Spike go/no-go (lam truoc, khong viet code san pham)

| ID | Spike | Tieu chi dat (acceptance) | Neu that |
|---|---|---|---|
| S0.0 | Tach viec dang do: commit hoac stash 15 file uncommitted; ghi ro baseline `./gradlew build` + `./gradlew test` (Java 21, `JAVA_HOME` process-local — shell mac dinh Java 8) | Baseline xanh, working tree sach | Dung; khong tron code migration vao diff cua nguoi dung |
| S0.1 | Resolver: doi 3 dong Gradle sang luava, chay `:core-engine:classes`, kiem tra jar co `META-INF/LICENSE` va ASM da shade | Compile duoc, khong co `cobalt` trong dependency tree | Dung |
| S0.2 | SPI parity: `LuavaEngine` toi thieu implements ILuaEngine; chay `LuaBinderTest` + `LtvmStubGeneratorTest` | Xanh, khong sua module nao | Dung |
| S0.3 | **Thread model**: 2 thread (server + render gia lap) go `executeString`/`callFunction` dong thoi tren 1 LuaState; chay 1000 vong | Khong corrupt/crash.neu luava khong cho chia se -> chon phuong an B (xem muc 10 R1) | Phai redesign truoc khi tiep tuc |
| S0.4 | **Coroutine bridge**: port `task._run_deferred`, `task.wait`, `Signal:Wait`, RemoteFunction request/response sang `LuaCoroutine` cua luava | Bootstrap load duoc; deferred chay; khong leak control-flow exception ra Java | Dung hoac tach TaskService sang luong khac |
| S0.5 | **Interop parity**: port `DynamicJavaProxy` sang `LuaUserdata` + metatable, giu blacklist + RuntimeRemapper; chay lai `DynamicJavaProxyTest` + event forwarder |Getter/setter/overload/property/blacklist/obfuscation deu xanh | Dung |
| S0.6 | **Benchmark**: cung 1 script (`benchmarks/lua/*.lua` + 3 script that cua luamods), chay Cobalt vs luava (JIT on/off), ghi so lieu thuc | Co bang so lieu; xac nhan hay bac bo "10x" | Neu luava khong nhanh hon -> can nhac lai muc tieu |

Output Phase 0: mot file `plans/LUAVA_SPIKE_RESULTS.md` voi ket qua that (pass/fail + so lieu),
quyet dinh tiep tuc hay dung. **Khong merge code migration neu S0.3/S0.4 that ma khong co phuong an B.**

---

## 4. Phase 1 — SPI & value bridge

### File moi
- `core-engine/src/main/java/com/luatweaker/core/vm/LuavaEngine.java` (implements ILuaEngine)
- `core-engine/src/main/java/com/luatweaker/core/vm/LuavaLuaValue.java`
- `core-engine/src/main/java/com/luatweaker/core/vm/LuavaLuaTable.java`

### `LuavaLuaValue` can mo phong dung cac semantic ma module dang phu thuoc
- `asString()`: luava `LuaString.toLuaString()` tra UTF-8 (khong can re-decode ISO-8859-1 nhu Cobalt).
- `asInt()`: tra 0 cho non-number (giong Cobalt — LuaBinder.convertArg co du cac goi such).
- `toJavaObject()`: phai tra `Long`/`Double`/`Boolean`/`String` va **raw userdata instance**
  (`InteractionLuaBinding.toJavaTableValue` dung `instanceof Number`; `LuaBinder.unwrapEntity`
  doc `__entity` roi cast `IEntity`).
- `isFunction()/isTable()/asTable()`: chuan theo `LuaValue.type()`.
- `asTable()` cho userdata co `__index` table: phai tra table do (de property alias hoat dong).

### `LuavaLuaTable`
- `rawset/rawget/length/forEach/asMap/setMetatable` qua `LuaTable`.
- `rawset(String, ILuaFunction)` → `LuaFunction.of(args -> ...)` (khong co VarArgFunction).
- `__index`/`__newindex`/`__eq`/`__call` metatable: Lua 5.4 hoan toan ho tro; giu nguyen
  cach hand-binding cua ContentLuaBinding / MathLuaBinding / EntitiesLuaBinding.

### Sua 5 diem downcast (dung 2 method moi o muc 2)
- `core-engine/.../core/mod/LuaModManager.java:289-294`
- `modules/module-command/.../command/LuaCommandWrapper.java:122`
- `neoforge-platform/.../event/UniversalEventForwarder.java:56`
- `modules/module-entities/.../entities/EntitiesLuaBinding.java:84,133`
- `modules/module-interaction/.../interaction/InteractionLuaBinding.java:690,708`

---

## 5. Phase 2 — Port core-engine (phan nang nhat)

| File | Cong viec | Ghi chu |
|---|---|---|
| `core/vm/CobaltLuaEngine.java` → `LuavaEngine.java` | Viet lai toan bo: globals, `print`/`log`, `os.clock`/`os.time`, `Mod`/`game.GetService`, vong require, error tracking, bootstrap loading | Day la file lon nhat (~687 dong). **Khong** port reflection hack `LuaState.currentThread` — thay bang S0.4 |
| `core/vm/CobaltLuaValue.java`, `CobaltLuaTable.java` → Luava* | Xem Phase 1 | Giu lai Cobalt* den khi chot xoa |
| `core/engine/LuaEngine.java` | `checkSyntax` dung parser cua luava (`state.compile(...)`), giun contract `null == hop le` | `SyntaxCommand` va `SignalCallbackTest` phu thuoc |
| `core/vm/NativeModuleResolver.java` | `resolve(LuaTable, String)` → `resolve(ILuaTable, String)`; giu nguyen ~90 alias `LuaTweaker.X` | Da dua ve SPI, khong con Cobalt |
| `core/vm/ItemUserdataFactory.java` | `LuaUserdata` + metatable `__index` (`withCount`, `withDamage`, `withName`, `orIngredient`...) bang `LuaFunction.of` | Recipe DSL item()/ingredient()/tag()/oredict() |
| `core/bind/DynamicJavaProxy.java` | Port sang `org.luava.runtime.LuaUserdata` + `LuaTable` metatable (`LuaValue.Meta.INDEX/NEWINDEX`), `LuaDataConverter.toLua/toJava` | **Giu nguyen** PACKAGE_BLACKLIST, METHOD_BLACKLIST, cache, `RuntimeRemapper`. `LuaUserdata` cua luava da tu xu ly colon-call + field/method/overload; retain property→getter translation cua ta |
| `core/bind/LuaBinder.java` | **GIU NGUYEN** | Da SPI. Chi can sematic cua ILuaValue dung |
| `core/logger/AsyncFileLogger.java` | Bo overload `(String, String, LuaState)`; thay bang `CallSiteProvider` (engine-neutral, lay mod-id tu `activeModuleStack`) | `DebugHelpers.fileLine` khong co tuong duong trong luava |
| `core/linter/LuaLinter.java` | Generalize regex parse loi: `"^(\\d+): (.*)$"` va `":(\\d+):"`; can test format loi that cua luava | Format luava `LuaException` khac Cobalt |
| `core/remap/RuntimeRemapper.java` | **GIU NGUYEN** | Khong co Cobalt |
| `core/lsp/LtvmStubGenerator.java`, `LtvmStubExporter.java` | **GIU NGUYEN** | Da SPI. Co the (khong bat buoc) them luava EmmyDoc generator nhung khong thay the stub hien tai |
| `core/service/LuaServiceRegistry.java` | **GIU NGUYEN** | — |
| `core/mod/LuaModManager.java` | Chi doi `clearEngineError` (Phase 1); giu nguyen loader/manifest/config/topological sort | — |
| `resources/lua/luatweaker_bootstrap.lua` | Sua phan `task._run_deferred` bridge (S0.4); phan con lai (Signal/RemoteEvent/RunService/CFrame/Tween) la Lua 5.4-compatible | `table.unpack` ok |

### Cơ chế `require` (quan trong)
Khong dung `sandbox()` (xoa `require`). Giu cach **overwrite global `require`** nhu hien tai:
1. Rewrite `.src.x` → `<modRoot>.src.x` qua `activeModuleStack` (giu regex chan `..`).
2. Cache: dung `package.loaded` cua luava thay `moduleCache` rieng (hoac giu rieng neu can semantic cu).
3. `LuaTweaker.X` → `NativeModuleResolver.resolve(ILuaTable, name)`.
4. File module: dang ky `state.resourceLoader(path -> source)` + `resourcePath` tro vao `luaDirectory`
   va `luamods/<id>/`; loader tra null → fallback `_oldRequire`.
5. Push/pop `activeModuleStack` quanh moi load de per-mod log van dung.

---

## 6. Phase 3 — Platform & build

| File | Thay doi |
|---|---|
| `build.gradle:9-15` | Co san `mavenCentral()`; **xoa** repo `maven.squiddev.cc` sau khi chot xoa Cobalt |
| `core-engine/build.gradle:11` | `api 'cc.tweaked:cobalt:0.9.9'` → `api 'io.github.kienpc1234:luava:0.2.5'` (sau khi het downcast co thể ha xuong `implementation`) |
| `core-engine/build.gradle:19-23` | filter fat-jar `contains('cobalt')` → `contains('luava')` (kiem tra ASM da shade, khong keo them jar) |
| `neoforge-platform/build.gradle:160` | `additionalRuntimeClasspath "io.github.kienpc1234:luava:0.2.5"` |
| `neoforge-platform/.../LuaTweakerMod.java:170,210,432` | 3 lan `new CobaltLuaEngine(debugMode)` → `LuaEngineFactory.create(debugMode)`. Giu flag `luatweaker.engine=cobalt|luava` den cuoi Phase 5 |
| `neoforge-platform/.../bootstrap/LuaServiceBootstrap.java` | **GIU NGUYEN** (chi nhan ILuaEngine) |
| `neoforge-platform/.../command/core/SyntaxCommand.java` | Giu nguyen goi `LuaEngine.checkSyntax` |
| `neoforge-platform/.../event/UniversalEventForwarder.java` | Dung `wrapDynamicJavaObject` (Phase 1) |

File moi: `neoforge-platform/.../LuaEngineFactory.java` (mot diem tao engine + doc flag A/B).

---

## 7. Phase 4 — Tests & parity

### Chien luoc
1. **Parity suite**: chay *cung 1 bash test* tren ca 2 engine thong qua `LuaEngineFactory` + system property.
   Day la bang chung migration khong lam mat hanh vi — quan trong hon viec swap constructor.
2. **Mechanical swap** (`new CobaltLuaEngine` → `LuaEngineFactory.create`) cho ~100 test constructor
   trong modules/* va neoforge-platform. Giu nguyen assertion.
3. **Viet lai hoan toan** (khong swap duoc):
   - `core-engine/.../vm/DynamicJavaProxyTest.java` (dung `getCobaltState`, `LuaUserdata`, `LuaError`)
   - `core-engine/.../vm/SignalCallbackTest.java` (UnwindThrowable, `LuaEngine.checkSyntax`, require aliases)
   - `core-engine/.../vm/DeferredTaskReloadReproTest.java` (Cobalt ClassCastException repro)
   - `core-engine/.../vm/ConcurrentEngineAccessTest.java` (viet lai theo ket qua S0.3)
   - `modules/module-interaction/.../WorldEditBindingTest.java:120` (`instanceof CobaltLuaEngine`)
4. **Giữ nguyên**: `LuaBinderTest`, `LtvmStubGeneratorTest`, `FakeEngine` (bang chung SPI hoat dong),
   va cac test module khac sau khi swap constructor.
5. **Test moi bat buoc** (theo AGENTS.md 0.6):
   - `LuavaEngineTest`: executeString/executeScript/callFunction/callFunctionMulti/error tracking/wrap*.
   - `LuavaLuaTableTest`: rawset/rawget/length/metatable/asMap/colon function.
   - `LuavaRequireTest`: dot-relative, `..` bi tu choi, `LuaTweaker.X` aliases, file fallback, cache.
   - `LuavaDynamicBridgeTest`: property getter/setter, overload, blacklist, error path.
   - `LuavaCoroutineTaskTest`: spawn/defer/delay/wait/FireSync/RemoteFunction (ke ca duong loi).
   - `EngineParityTest`: cung script, so sanh ket qua Cobalt vs luava (chay 2 property khac nhau).

### Lenht verify (Java 21!)
```
./gradlew :core-engine:test --tests '*Luava*'
./gradlew :neoforge-platform:test --tests '*LuaScriptsIntegrationTest'
./gradlew test
./gradlew build
```
Shell mac dinh la Java 8 → dat `JAVA_HOME` process-local (xem `plans/REVIEW_FIX_PLAN.md:53`).

---

## 8. Phase 5 — Verification gate & rollout

1. Build + full test xanh; **ghi so luong pass/fail/skip that** (baseline truoc do la 374 tests — can xac nhan lai).
2. Grep toan repo: khong con `org.squiddev.cobalt`, `cc.tweaked:cobalt`, `getCobaltState`,
   `CobaltLuaEngine`, `UnwindThrowable` (ngoai file JLint/stale docs can sua).
3. Sua stale docs `docs/ADDON_DEVELOPER_GUIDE.md:40,55,61,126,127,141` (dang noi `org.luaj.vm2`).
4. Cap nhat AGENTS.md: version engine, Lua 5.4 dialect, luava benchmark (neu da verify).
5. Smoke test **trong game** (`runClient` + `/lt reload`, `/lt doctor`, `/lt hand`, `/lt syntax`,
   HUD, skill/projectile, network) — **khong duoc khang dinh bang unit test**.
6. Rollout: flag mac dinh `cobalt` → opt-in `luava` → mac dinh `luava` → xoa Cobalt + factory + flag.
7. Boot JIT: goi `JitCompiler.prewarm` sau khi load script neu first-request latency quan trong.

---

## 9. Bang file can doi (tom tat)

**Viet moi (7):** `LuavaEngine`, `LuavaLuaValue`, `LuavaLuaTable`, `LuaEngineFactory`,
`Luava*Test` (5 file test), `plans/LUAVA_SPIKE_RESULTS.md`.

**Viet lai / port (11):** `CobaltLuaEngine`→LuavaEngine, `CobaltLuaValue`→LuavaLuaValue,
`CobaltLuaTable`→LuavaLuaTable, `LuaEngine`, `NativeModuleResolver`, `ItemUserdataFactory`,
`DynamicJavaProxy`, `AsyncFileLogger`, `LuaLinter`, `luatweaker_bootstrap.lua`, `build.gradle` x3.

**Sua nho (6):** `ILuaEngine` (+2 default method), `LuaModManager`, `LuaCommandWrapper`,
`UniversalEventForwarder`, `EntitiesLuaBinding`, `InteractionLuaBinding`.

**Giu nguyen (nong cot):** `LuaBinder`, `RuntimeRemapper`, `LtvmStubGenerator/Exporter`,
`LuaServiceRegistry`, `LuaServiceBootstrap`, toan bo `*ServiceImpl`, `common-api/*`,
`modules/*/src/main` tru 2 file co Cobalt leak.

**Xoa cuoi Phase 5:** `CobaltLuaEngine`, `CobaltLuaValue`, `CobaltLuaTable`, dependency Cobalt.

---

## 10. Rủi ro & giảm thiểu

| ID | Rui ro | Muc do | Giam thieu |
|---|---|---|---|
| R1 | luava khong cho chia se 1 LuaState giua server thread + render thread (docs: "one state per thread") | **CAO** | S0.3. Phuong an B: mo hinh "state theo thread" + route event qua active engine (can sua UniversalEventForwarder, EventServiceImpl, render callback FireSync). Phuong an C: giu 1 state + lock toan cu (nhu hien tai) neu stress test that chung minh on dinh — luava khong cam doan, chi khong khuyen khich |
| R2 | Coroutine khac mo hinh: `LuaThread` + `UnwindThrowable` + reflection `currentThread` → virtual-thread coroutine | **CAO** | S0.4. Viet lai bridge; khong port hack. Co the can task service chay rieng |
| R3 | "10x" la so sanh voi LuaJ, khong phai Cobalt | CAO | S0.6 — do thuc; neu khong thua/Cobalt danh gia lai |
| R4 | Dong thoi 2 engine (A/B) lam shared event bus double-fire | TRUNG | Giu 1 active engine tai moi thoi diem; flag chi chon luc tao, khong chay 2 VM cung luc (AGENTS.md 5.3) |
| R5 | Loi khac format → LuaLinter / per-mod error tracking sai | TRUNG | generalize regex + test duong loi (AGENTS.md 0.12) |
| R6 | Lua 5.4 co integer/float subtype → script mong `3/2 == 1.5` (Cobalt 5.1 cung ra 1.5) nhung `math.random`, `//`, `%` khac | TRUNG | quet script + chay lai toan bo luamods; document dialect change |
| R7 | luava 0.2.5 pre-1.0, API co the doi | THAP | pin exact version; repo do chinh ban so giu semver de hon |
| R8 | MPL-2.0 trong fat-jar | THAP | kem LICENSE, giu notice; khong sua core luava (hoac mo source phan sua) |
| R9 | Dang co 15 file uncommitted | TRUNG | S0.0: commit/stash truoc; khong tron diff |

---

## 11. Uoc luong (rough)

| Phase | Uoc luong |
|---|---|
| 0 — Spikes | 1-2 ngay (S0.3 + S0.4 co the ton them neu phai redesign) |
| 1 — SPI bridge | 1-2 ngay |
| 2 — Port core-engine | 4-6 ngay (file lon nhat la LuavaEngine) |
| 3 — Platform/build | 0.5 ngay |
| 4 — Tests + parity | 2-3 ngay |
| 5 — Verify + smoke game | 1-2 ngay |

Tong: **~10-16 ngay lam viec** (chua tinh smoke test game that). Có the rut nếu bo A/B flag
va chay thang luava — nhung mat khang chung parity.

---

## 12. Quyet dinh can ban chot truoc khi bat dau

1. **Chien luoc chuyen doi**: (a) A/B co flag + parity roi xoa Cobalt [khuyen nghi], hay (b) xoa Cobalt
   thang, khong giu fallback?
2. **Thread model**: neu S0.3 that, ban chon phuong an B (state theo thread, sua event routing —
   ton them) hay C (giu lock toan cu nhu hien tai)?
3. **Dialect**: chap nhuan Lua 5.4 (bo cac tien ich Luau neu co trong mod ngoai) hay can lop tuong thich?
4. **Code dang do**: ban se commit hay stash 15 file hien tai truoc khi bat dau? (Khong bao gio de AI
   tu y stash/commit.)
5. **Benchmark**: co can so lieu Cobalt vs luava truoc khi quyet dinh khong? (Khuyen nghi: co.)
