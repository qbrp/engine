# AGENTS.MD

## Repository Purpose

This repository is the Fabric Minecraft mod `engine`. It is both a Minecraft integration and a reusable gameplay runtime built around:

- a server/client ECS simulation;
- Lua-defined behavior and a Lua/YAML module composer;
- revisioned entity replication and client prediction;
- persistent entities, items, players, characters, chunks, and worlds;
- chat and acoustic simulation;
- Minecraft adapter, rendering, resource, networking, and mixin layers.

Keep reusable gameplay behavior independent of Minecraft where practical. Code that directly depends on `net.minecraft.*`, Fabric events, rendering, resources, or mixins belongs near `mc`, `client/mc`, entrypoints, transport adapters, or rendering code.

## Build And Toolchain

The version catalog at `gradle/libs.versions.toml` is the source of truth for dependency versions. At the time of this document:

- Kotlin/JVM with Java mixins;
- Java 21 toolchain and target;
- Kotlin compiler flag `-Xcontext-parameters`;
- Minecraft `1.21.1` with official Mojang mappings;
- Fabric Loader and Fabric API through Loom;
- split common/client source sets;
- JUnit 5 tests;
- Shadow plus Loom remapping for the distributable jar.

Common PowerShell commands:

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "org.lain.engine.test.EntityCoordinatorTest"
.\gradlew.bat compileKotlin
.\gradlew.bat compileClientKotlin
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat tasks --all
```

Run focused tests or compilation first, then `test` and `build` when shared code, source-set boundaries, serialization, or packaging are affected.

Dependency resolution may require network access and GitHub Packages credentials for `org.lain.cyberia`. If resolution fails because credentials or external repositories are unavailable, report the environment problem. Do not rewrite dependency or repository configuration as a workaround.

## Source And Runtime Layout

- `src/main/kotlin`: shared and server engine code.
- `src/client/kotlin`: client-only session, prediction, rendering, resources, account UI, and Minecraft client adapters.
- `src/main/java`: common/server Java mixins.
- `src/client/java`: client Java mixins and accessors.
- `src/main/resources`: Fabric metadata, common mixins, default configuration, Lua libraries, and installer resources.
- `src/client/resources`: client mixins, class tweaker, assets, models, textures, sounds, and translations.
- `src/test/kotlin`: JVM/JUnit tests.
- `src/main/resources/defaults/scripts/core/composer`: default content/module composition pipeline.
- `src/main/resources/defaults/scripts/core/meta`: Lua API metadata and type declarations.
- `src/main/resources/defaults/scripts/core/util`: built-in Lua utility modules.
- `src/main/resources/defaults/install.lua`: default compilation entrypoint.
- `src/main/resources/defaults/modules/modules.yaml`: default enabled-module set.

The root `engine/` directory, `run/`, `logs/`, `build/`, `.gradle/`, and `.kotlin/` are runtime, generated, or local state unless a task explicitly targets them. Do not edit or clean them incidentally.

Important package areas:

- `EngineSimulation.kt`: shared simulation orchestration.
- `world`: worlds, chunks, voxels, events, locations, decals, lights, sounds, and ballistics.
- `util/ecs`: component registry, arrays, world storage, command buffers, serialization registration, and relations.
- `player`, `item`, `container`: gameplay-domain entities and systems.
- `server`: server loop, packet handler, settings, connections, and notifications.
- `server/replication`: interests, tracking, snapshots, revisions, frames, and synchronization.
- `data`: persistence DTOs, database access, transactions, entity coordination, materialization, and chunk/player saving.
- `script`: namespaces, modules, callbacks, systems, values, compilation models, and diagnostics.
- `script/lua`: LuaJ runtime, compiler bridge, userdata, and public Lua libraries.
- `transport`: shared endpoints, codecs, packets, and dedicated-server transport.
- `mc`: common/server Minecraft adapters, entrypoints, commands, mixin bridges, and acoustic integration.
- `client/handler`: join flow, packet application, task queues, replication, and prediction.
- `client/GameSession.kt`: client simulation/session lifecycle.
- `client/render`, `client/resources`, `client/mc`: client-only presentation and Minecraft integration.

## Bootstrap And Component Registration

`bootstrap()` initializes the global component type registry and Cyberia component type providers once. Tests that use registered engine components normally extend `EngineTest`, whose `@BeforeAll` calls `bootstrap()`.

Kotlin component registration metadata determines whether a component is savable and/or networked. Script components have dynamic `ScriptComponentType` instances, while built-in script component types live in `CoreScriptComponents`.

When adding or changing a component, check every applicable path:

- Kotlin or script component registration;
- stable component type id;
- savable/networking metadata;
- snapshot and persistent DTO conversion;
- replication snapshot conversion;
- materialization/revival;
- client registration and application;
- Lua bridge or metatable;
- player/item/world construction defaults;
- tests.

Do not change a registered component id casually. It is part of persisted and replicated data. Do not reorder or recreate indexed component types without understanding how `ComponentWorld` validates type/index stability.

## Simulation And Tick Ordering

`EngineSimulation` is shared by server and client. It owns worlds, callbacks, the script-system dispatcher, player storage integration, and the tick count.

The per-world tick order is semantically important:

1. platform data preparation;
2. item ownership and player-model preparation;
3. player input;
4. built-in interaction/action systems;
5. post-interaction hooks;
6. Lua callback and script systems;
7. narration and container operations;
8. platform/application hooks;
9. processed-input confirmation;
10. synchronization, saving, and unload hooks;
11. event cleanup;
12. tick increment.

Do not reorder phases as a local cleanup. Prediction, dirty tracking, packet output, saving, and one-tick event lifetime depend on this sequence.

`EngineMinecraftServer` is the Minecraft-facing lifecycle owner. It creates `EngineServer`, loads content, maps Minecraft levels to engine worlds, ticks the engine from the server tick, handles joins/leaves/chunks, saves state, and shuts the engine down.

`GameSession` is the client-side mirror. It owns the client `World`, main and low-detail players, replication controller, movement and prediction state, chat, rendering-facing caches, and session-scoped asynchronous work. Any resource added to a session must be released from `GameSession.destroy()`.

## ECS And Thread Ownership

`World` delegates component storage to `ComponentWorld`. Component reads, writes, iteration, dirty marking, destruction, and component-array registration belong to the world's owner thread.

Only entity-id allocation is designed to be partially cross-thread safe. An `EntityId` is a reusable integer slot:

- it can be reused after destruction;
- it is not a persistent identity;
- it is not a safe network identity;
- it is not a sufficient interaction/deduplication key.

Use `PersistentId` for persisted or replicated entity identity and `InteractionId` for a particular player attempt.

Move off-thread results back through the existing ownership boundary:

- `EngineServer.execute` for server-world mutations;
- `TaskExecutor` or the client execution path for client-world mutations;
- `EntityCommandBuffer` or transaction command buffers for deferred component writes.

Be precise about `EntityCommandBuffer`: entity ids may be allocated before buffered component mutations are applied. Transaction commit/rollback still controls whether prepared state becomes visible.

`ComponentWorld.withoutThreadRestriction` is an exceptional mechanism used for controlled parallel snapshot work. Do not use it to make arbitrary mutation from worker threads acceptable.

For networked components:

- setting or removing a component records a change;
- mutating a component object in place requires the appropriate `markDirty`/`markUpdated` path;
- destruction and persistent-id hooks must keep `persistentIdToEntity`, item storage, replication state, and coordinator state consistent.

JVM `assert(...)` is normally disabled. Use `check`, `require`, explicit protocol errors, or another production validation mechanism for invariants.

## Networking, Replication, And Prediction

Shared packet DTOs and endpoints live under `transport` and `transport/packet`. Dedicated multiplayer uses `ServerNetworkTransport`; integrated singleplayer has client/server transports under `src/client/kotlin/mc`.

An endpoint or packet-format change must update:

- packet DTO and codec/serializer;
- server registration and handler;
- client registration and handler;
- every send/broadcast call;
- authorization behavior where applicable;
- integrated and dedicated transports;
- tests or compatibility handling.

Server replication is revisioned:

- networked component changes produce deltas;
- newly tracked or resynchronized entities receive full snapshots;
- interest/tracking state determines active, resident, fresh, and out entities;
- clients detect revision gaps and request a full resync;
- persistent ids correlate replicated entities;
- processed input ticks reconcile client prediction.

Preserve the distinction between active and resident entities. A dormant entity can remain resident without receiving deltas and must receive a full snapshot when it becomes active again. An entity belongs in a frame's `out` set only after it stops being resident.

Prediction must use `InteractionId` across input, predicted mutation, server processing, action synchronization, component confirmation, and related audiovisual effects. Never substitute world tick, runtime entity id, item id, or persistent id as the sole attempt identifier.

Packet callbacks may arrive outside the ECS owner thread. Decode/validate there if safe, but schedule world access onto the correct task queue.

Registration is lifecycle state. Every Fabric/global/client/singleplayer receiver registered for a server or session must be tracked and actually unregistered during shutdown. An `unregisterAll()` implementation is only valid if the corresponding registration path populates the collection it iterates.

## Persistence And Entity Coordination

Persistence uses SQLite/Exposed records plus file-backed data where appropriate. Persistent entities are represented through stable `PersistentId` values and component records. Player data, characters, world state, items, relationships, and chunk-local dynamic voxels have distinct save/load paths.

`TransactionContext` coordinates asynchronous loading:

- reservations prevent duplicate loads;
- child transactions merge into a parent;
- commit applies command buffers, completes load reservations, and releases leases;
- rollback clears commands, releases leases, aborts reservations, and destroys prepared entities on the owner thread.

Every acquired lease or reservation must reach exactly one terminal path. Do not bypass the coordinator when destroying a coordinated persistent entity. Because runtime entity ids are reused, coordinator validation must include the world's persistent-id mapping rather than trusting the integer id alone.

Chunk persistence serializes operations per path, separates off-thread preparation from owner-thread commit, and has an explicit close path. Preserve these properties when changing chunk loading:

- do not mutate ECS state from the IO dispatcher;
- do not commit a load after persistence has closed;
- remove completed operation tails and pending loads;
- finish or abort entity-unload handles;
- surface failed saves at shutdown.

When changing persisted data:

- preserve stable ids and serialized names;
- update serializers, snapshots, records, materialization, and resolver behavior together;
- add compatibility or migration for format changes where practical;
- use atomic replacement for file writes;
- test round trips and failure/rollback behavior.

Do not treat a successful asynchronous launch as a successful save. Shutdown must define whether pending writes are awaited, completed non-cancellably, or cancelled.

## Lua, Modules, And Content Compilation

The current default compilation flow is module-driven:

1. `engine/modules/modules.yaml` selects enabled module namespaces.
2. `ModuleManager` recursively discovers `module.yaml` files under `engine/modules`.
3. `engine/scripts/install.lua` invokes the built-in composer.
4. The composer scans enabled modules for YAML and Lua declarations.
5. Declarations are categorized, symbolized, resolved, templated, and lowered.
6. Lua output is converted to `BuildDraft`.
7. Kotlin validates namespaces and links callbacks/system phases into a `Build`.
8. `NamespacedStorageAccess` is replaced and worlds register the resulting script component types.
9. A compilation manifest may be written to `engine/debug/compilation-manifest.json`.

`module.yaml` describes one module; `modules.yaml` is the enabled-module set. Keep this distinction when changing discovery or defaults.

Do not assume the legacy `engine/contents` directory is the active composer input without tracing the current installer and composer. `FileSystem` still contains legacy migration support.

Public Lua API changes may require coordinated edits to:

- Kotlin Lua library/userdata/metatable code;
- `src/main/resources/defaults/scripts/core/meta` declarations;
- composer scripts and drafts;
- built-in default scripts;
- compilation diagnostics and manifest output;
- client and server Lua engines;
- examples or tests.

Keep built-in API ids stable where possible. Modules and server content may rely on them. Compilation should report source/path/phase/target diagnostics rather than throwing an unstructured error when recovery is possible.

Script reload and full compilation are different operations. A module reload updates Lua package state; applying a new `Build` also replaces namespaced storage, callbacks, phases, and component registrations.

## Minecraft Adapter And Source-Set Boundaries

Keep Minecraft API use near:

- `CommonEngineMod`, `DedicatedServerEngineMod`, and `EngineMinecraftClient`;
- `EngineMinecraftServer` and integrated/dedicated subclasses;
- `mc` and `client/mc`;
- renderer/resource packages;
- Java mixins and accessors;
- transport adapters.

Domain systems should prefer `World`, `EnginePlayer`, `EngineItem`, `Location`, `VoxelPos`, `PersistentId`, and engine interfaces over Minecraft objects.

Respect Loom's split source sets:

- common/server code must not import client-only Minecraft classes or `src/client` code;
- client code may depend on common code;
- common mixins belong in `src/main/java` and `engine.mixins.json`;
- client mixins belong in `src/client/java` and `engine.client.mixins.json`.

Mixin, accessor, and class-tweaker changes are high-risk:

- verify the target exists under the current Mojang mappings;
- update the correct mixin config;
- keep environment restrictions correct;
- check descriptor/signature changes;
- run the relevant compile task and preferably the affected runtime.

Fabric lifecycle and gameplay callbacks are global registrations. Register callbacks once from entrypoints, and keep per-server/per-session state behind a replaceable lifecycle owner rather than capturing stale instances in global lambdas.

## Lifecycle And Resource Ownership

Every server- or session-owned asynchronous resource needs an explicit, idempotent terminal path. This includes:

- `CoroutineScope` and `Job`;
- `ExecutorService` and executor-backed coroutine dispatchers;
- packet, Fabric event, and callback registrations;
- pending response jobs;
- caches retaining players, worlds, levels, textures, or entities;
- global injector/access-registry entries;
- database and persistence workers.

`EngineChat` owns a fixed thread pool and coroutine scope and exposes `close()`. Preserve that ownership boundary and ensure the server shutdown path invokes it; invalidating `ServerHandler` does not itself close chat workers. Decide whether accepted messages drain or cancel, then cancel/complete the scope and close the executor/dispatcher accordingly.

Prefer a scope owned by `EngineServer`, `EngineMinecraftServer`, `GameSession`, or another lifecycle owner over an ad-hoc `CoroutineScope(...)` in a request method.

Be careful after `EngineServer.stopped` becomes true: `update()` returns without draining the server task queue. Background callbacks must not continue enqueueing closures that retain worlds, players, snapshots, or the stopped server.

During shutdown or integrated-server disconnect, verify that:

- new work is rejected before workers are closed;
- receivers and listeners are unregistered;
- persistence work follows its durability contract;
- scopes and executors terminate;
- injector entries and access registries no longer reference the stopped instance;
- world tables and acoustic scenes release unloaded levels;
- client/session listeners and replication hooks are detached;
- queues and caches cannot retain obsolete players or worlds.

Long-lived collections keyed by player, character, entity, chunk, request, or exception identity need an explicit removal/eviction condition or a justified fixed bound. Small entries still form a leak if the key space grows for the lifetime of the process.

## Testing Guidance

Prefer fast JVM tests for reusable logic:

- component arrays, registration, dirty tracking, and entity-id reuse;
- command buffers and thread restrictions;
- entity coordinator reservation/lease/unload behavior;
- replication revisions, interest transitions, input ordering, and resync;
- persistence round trips, rollback, and shutdown races;
- Lua table bridges, id resolution, compilation diagnostics, and manifests;
- pure container, acoustic, math, chunk, and voxel behavior.

Use `EngineTest` when a test requires the global engine component registry. Use `@TempDir` for filesystem/database tests. Do not write test data into the repository's root `engine/` directory.

Minecraft/Fabric runtime testing is appropriate only for behavior that genuinely depends on loader initialization, mixins, Minecraft levels/entities, rendering, resources, or real networking.

For lifecycle fixes, add a regression test where feasible: repeated start/stop, close during an in-flight operation, registration/unregistration symmetry, or a bounded collection after removal.

If tests cannot run because credentials, dependency repositories, native/runtime requirements, or the Minecraft environment are unavailable, state exactly which command failed and why.

## Editing And Repository Hygiene

- The working tree may already contain user changes. Inspect it first and preserve unrelated modifications.
- Keep task scope narrow; do not combine a bug fix with broad refactoring or formatting.
- Follow existing package and naming conventions, including existing filenames unless a rename is part of the task.
- Preserve UTF-8. The repository contains Russian text; mojibake in terminal output is not permission to rewrite strings.
- Do not modify Gradle versions, Fabric metadata, mixin configs, packet formats, persistent formats, or default resources without checking all consumers.
- Do not edit generated/runtime directories unless the task explicitly asks for it.
- Do not rely on client runtime availability from common code.
- Prefer existing abstractions and queues over new global mutable state.
- Comments should explain invariants, ordering, ownership, compatibility, or a non-obvious decision; do not merely restate code.

## Review Checklist

Before finishing a substantial change, check:

- Does it compile in the correct source set?
- Is bootstrap/component registration still valid and deterministic?
- Are ECS operations performed on the owning thread?
- Does tick ordering remain compatible with input, scripts, synchronization, saving, and event cleanup?
- Are in-place networked component mutations marked dirty?
- Are runtime entity ids being confused with persistent or interaction identity?
- Do client and server packet DTOs, codecs, registrations, and handlers agree?
- Are replication revisions, full/delta snapshots, resident/out transitions, and resync behavior preserved?
- Are savable/networked/script component metadata and snapshot/revival paths complete?
- Do transaction reservations, leases, commit, rollback, and unload handles reach terminal states?
- Are Lua compiler, composer, metadata, defaults, and manifest behavior consistent?
- Are common/client boundaries and mixin environments correct?
- Are scopes, executors, listeners, caches, globals, and registrations released by their lifecycle owner?
- Are long-lived collections bounded or cleaned?
- Were focused tests run, followed by broader tasks proportional to the risk?
- If something could not be verified, is the exact limitation reported?
