# Review fixes — 2026-09-10

## Objective and scope

Fix the five reproduced review findings without replacing the low-level Lua API architecture.
Keep each phase independently testable. Do not commit, publish, or modify real game saves/configuration.

## Sequence and acceptance criteria

1. **Filesystem boundaries (P1)** — fixed and covered by tests
   - Validate mod IDs before using them in config/log filenames; reject separators, traversal,
     absolute paths, and Windows reserved filenames.
   - Share path validation between config and per-mod logging; reject symlink escapes.
   - Validate manifest entrypoint paths and report malformed manifests visibly.
   - Tests: valid IDs, invalid IDs/paths, preserved external sentinel, normal config merge/logging.
2. **Environment selection (P1)** — fixed and covered by tests
   - Preserve the existing shared-VM host semantics: universal host supports client and server mods;
     dedicated server excludes client-only mods, client-only host excludes server-only mods.
   - Validate host environment and test the full compatibility matrix plus actual loader execution.
3. **Load status and lifecycle (P2, grouped with loader changes)** — fixed and covered by tests
   - Missing/unreadable entrypoint, syntax/runtime errors and OnEnable failure become load errors.
   - Only successful mods enter the loaded registry; skip OnEnable after failed entrypoint.
   - Clear stale execution errors between operations; test successful dependency API imports.
4. **Java overload dispatch (P2)** — fixed and covered by tests
   - Select public overloads from the actual argument count/types, preserving colon/dot calls.
   - Reject missing/extra/ambiguous/incompatible arguments instead of silently choosing a method.
   - Preserve blacklist checks and property getter/setter behavior.
   - Tests: overload count/type, numeric conversions, nil, ambiguous input, properties and blacklist.
5. **RemoteFunction request/response (P1)** — Lua binding fixed; PAL transport design still pending
   - Complete the network transport contract in a follow-up; no blocking wait on game/VM threads.
   - Use request IDs, directional routing, authenticated sender identity, timeout and reload cleanup.
   - Preserve synchronous-looking Lua results through yielding in scheduled coroutines.
   - Binding-level tests now cover Lua wrapper calls to InvokeServer/InvokeClient.
   - Transport tests should run from Lua across two isolated engines with a simulated transport, both directions,
     nil/error response, missing handler, timeout, wrong sender and reload cleanup.
   - Update API documentation for coroutine usage, timeout and supported result types.
6. **Final integration gate** — pending
   - Run Java 21 full build and all tests; inspect actual XML counts and final diff.
   - Review compatibility with bundled Lua mods; document runtime checks not performed.
   - Report changed files, verification and remaining limitations; update this plan as phases finish.

## Verification strategy

Start with regression tests that fail against the existing behavior, apply each fix, then run its
targeted tests. Run the full suite after integration. Temporary tooling stays outside the repository.
Minecraft multiplayer/client rendering cannot be claimed verified from unit tests; an actual game
smoke test remains explicitly separate. Avoid unrelated refactors while fixing these findings.

## Baseline

- Working tree clean at start.
- Previous review: Java 21 build successful; 374 tests passed, none failed/skipped.
- Shell defaults to Java 8; use process-local JAVA_HOME for Gradle.

## Progress and evidence

- Added shared mod path validation, loader environment/error handling, overload-aware Java proxy dispatch,
  and Lua-facing RemoteFunction binding tests.
- Added deterministic mod discovery and dependency gating: missing or circular dependencies now produce
  load errors and cannot be partially activated. Documentation now matches malformed-manifest behavior.
