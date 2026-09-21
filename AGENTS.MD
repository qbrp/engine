# AGENTS.MD

## Project Context

This repository is a Fabric Minecraft mod named `engine`. It is moving toward a reusable gameplay engine with server/client ECS state, Lua scripts, YAML content compilation, networking, persistence, and Minecraft-specific adapter layers.

Treat Minecraft integration as an adapter around engine logic. When possible, keep reusable gameplay code in engine packages and keep direct `net.minecraft.*` calls in `mc`, entrypoint, rendering, transport, or adapter code.

## Build And Runtime

- Language: Kotlin/JVM plus a small amount of Java mixin code.
- JVM target/toolchain: Java 21.
- Minecraft: `1.21.11`.
- Loader: Fabric, via `fabric-loom`.
- Kotlin compiler uses `-Xcontext-parameters`.
- Main build file: `build.gradle.kts`.
- Version pins: `gradle.properties`.

Common commands:

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat tasks --all
```

Dependency resolution may require network access and GitHub Packages credentials for `org.lain.cyberia`. If Gradle fails on dependency download or credentials, do not rewrite build logic as a workaround; report the environment issue.

## Source Layout

- `src/main/kotlin`: common/server-side engine code and shared domain code.
- `src/client/kotlin`: client-only game session, rendering, handlers, resources, and Minecraft client adapters.
- `src/main/java`: Java mixins.
- `src/main/resources`: Fabric metadata, common mixins, default Lua/YAML resources.
- `src/client/resources`: client mixins, client assets, client resources.
- `src/test/kotlin`: JVM tests. Some script tests are currently commented out.
- `src/main/resources/defaults`: built-in server config, Lua entrypoint, and core Lua libraries copied/used as defaults.

Important package areas:

- `world`: engine world state, locations, chunks, sounds, voxel events.
- `util/component`: custom ECS data (`ComponentWorld`, `ComponentArray`, command buffers).
- `server`: `EngineServer`, synchronization, server handler, global settings.
- `client/handler`: client packet handlers, task queue, synchronization receivers.
- `client/GameSession.kt`: client-side world/session tick pipeline.
- `script`: content compilation, namespaces, callbacks, script values, debug data.
- `script/lua`: LuaJ bridge, runtime context, Lua API tables.
- `script/yaml`: YAML content compilation.
- `transport`: packet endpoints and codecs.
- `data`: persistence, DTO conversion, save/load systems.
- `mc`: Minecraft-specific server adapters, commands, mixin access, block/entity bridging.
- `client/mc`: Minecraft client adapters, keybinds, audio, rendering integration.

## Architecture Notes

`EngineMinecraftServer` is the Minecraft-facing server wrapper. It creates `EngineServer`, registers dependencies, compiles/loads script content, creates engine worlds for Minecraft levels, and calls `engine.update(...)` from the Minecraft server tick.

`EngineServer` is the main server engine loop. It owns worlds, data loaders, chat, callbacks, server handler, and tick phases. Most gameplay systems run inside `EngineServer.update`.

`GameSession` is the client-side mirror. It owns a client `World`, local player data, client Lua context integration, prediction-ish action systems, rendering hooks, and client tick phases.

`World` delegates ECS reads/writes to `ComponentWorld`. It also owns script context, chunk data, item data, and registered script component types.

The Lua/content pipeline is:

1. YAML content is compiled from `ENGINE_DIR.contents`.
2. Lua entrypoint runs through `LuaContext`.
3. Lua and YAML content produce `CompilationResult`.
4. `NamespacedStorageAccess` is updated.
5. Worlds call `registerScriptComponents(...)`.
6. Callbacks and script component adapters run during server/client ticks.

## ECS And Threading Rules

`ComponentWorld` is effectively single-threaded for component operations. The intended owner thread is passed into `World`/`ComponentWorld`.

Only entity id creation is partially designed for cross-thread use. Component mutation, component reads, iteration, dirty masks, and script component registration should happen on the owning world/thread unless a command buffer or engine task queue explicitly moves work to that thread.

Do not make direct component mutations from arbitrary coroutine or network callback threads. Use existing queues/executors:

- `EngineServer.execute` / server task queue.
- `TaskExecutor` on the client.
- loader command buffers such as `ItemLoader`, `PlayerLoader`, `ChunkLoader`.
- `EntityCommandBuffer` when building deferred ECS writes.

Be careful with `assert(...)` in JVM code: assertions are usually disabled unless `-ea` is used. Do not rely on `assert` for production thread safety or validation.

## Networking And Synchronization

Packets live mostly in `src/main/kotlin/transport/packet`.

Server-to-client component synchronization is in `server/Synchronization.kt`. Client registration for these synchronizers is in `client/handler/Tasks.kt` and `client/handler/Endpoints.kt`.

For player interactions, prefer a dedicated `InteractionId` as the correlation key across:

- client input/prediction,
- server action processing,
- `ActionSyncEvent`,
- component dirty sync,
- sound or visual side effects.

Do not use world tick, entity id, or item persistent id as the only deduplication key for an interaction. They identify time or objects, not a specific attempt/action.

## Lua/Content Guidelines

The long-term direction is an engine driven by Lua scripts and content definitions. When adding engine features, consider whether the public surface should be exposed through:

- Kotlin engine API only,
- YAML content compiler,
- Lua table/API bridge,
- script components,
- callbacks,
- networking library.

Core Lua defaults live in `src/main/resources/defaults/scripts/core`. Keep these APIs stable where possible, because server content may depend on them.

Script component types are registered dynamically. When adding new script-facing components or DTO conversions, update all relevant paths:

- component registration,
- serialization/deserialization,
- networking flags,
- save/load flags,
- Lua conversion/adapters,
- client and server component registration.

## Persistence

Persistence code is under `data`. Persistent ids (`PersistentId`, `PersistentIdComponent`) are used to reconnect saved/networked entities with runtime ECS ids.

When changing components that can be saved or networked, check:

- component registry metadata,
- `ComponentSerialization.kt`,
- save/load systems,
- client DTO conversion,
- item/entity/chunk relationships.

Avoid changing persistent formats casually. If a format change is required, add migration or compatibility handling where practical.

## Minecraft Adapter Rules

Keep direct Minecraft API usage close to adapter boundaries:

- entrypoints: `CommonEngineServerMod`, `DedicatedServerEngineMod`, `EngineMinecraftClient`,
- server wrapper: `EngineMinecraftServer`,
- `mc` and `client/mc`,
- rendering and mixin integration packages.

Domain systems should prefer engine types (`World`, `EnginePlayer`, `EngineItem`, `Location`, `VoxelPos`, `PersistentId`) instead of Minecraft classes. This keeps systems testable outside a running Minecraft instance.

## Testing Strategy

Prefer fast JVM tests for engine logic:

- ECS behavior,
- component serialization,
- interaction/action systems,
- data DTO roundtrips,
- Lua/YAML compilation where possible,
- pure math/chunk/voxel/container logic.

Use Minecraft/Fabric runtime tests only for behavior that genuinely needs a Minecraft world, mixins, or loader runtime.

Run focused tests first, then broader Gradle tasks when touching shared systems:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

If tests cannot run because dependencies, credentials, or Minecraft runtime are unavailable, state that explicitly in the final report.

## Coding Conventions For Agents

- Follow existing package structure and naming.
- Keep changes scoped; do not perform broad refactors while fixing a specific bug.
- Prefer existing engine abstractions over new global state.
- Preserve client/server source-set boundaries. Do not import client-only Minecraft classes from common/server code.
- Do not mutate unrelated generated/runtime directories such as `.gradle`, `.kotlin`, `build`, `logs`, or `run` unless the task specifically requires it.
- Avoid changing Fabric metadata, mixin configs, Gradle dependency versions, or packet formats without checking all call sites.
- For packet format changes, update both endpoint codecs and all send/receive sites.
- For new networked/savable components, update registry, DTO conversion, client load path, server save path, and tests where feasible.
- For Lua-facing changes, update both Kotlin bridge code and default Lua libraries/docs/examples if the public API changes.
- Keep comments useful and specific. Do not add comments that merely restate the code.

## Review Checklist

Before finishing a substantial change, check:

- Does it compile for the correct source set (`main` vs `client`)?
- Does it preserve server/client tick ordering assumptions?
- Are component operations happening on the owning world thread?
- Are packet changes reflected on both client and server?
- Are persistent/networked/script component metadata and DTO paths complete?
- Does Lua content still compile and are default scripts compatible?
- Were tests run, or is there a clear reason they could not be run?
