# Arrow Refactor Plan

A staged migration from the current `Result<T>` / `try-catch` / mutable `MutableStateFlow.value =` style toward a functional Kotlin codebase built on Arrow Kt. The plan is deliberately phased so each step compiles, ships, and is independently revertible.

## Status

| Phase | Status | Notes |
|---|---|---|
| 0 — Dependencies | ✅ Done | `arrow-core`, `arrow-fx-coroutines`, `arrow-resilience` 2.0.1 wired into `commonMain`. All three targets (Android, JVM, iOS arm64) compile. |
| 1 — Typed errors at the data layer | ✅ Done | Sealed `DomainError` hierarchy with `McwsError`/`SettingsError`/`LookupError`. `McwsApi`, `JRiverMcwsClient`, `JRiverLookupService` now return `Either<DomainError, T>`. `runCatching` and `throw` are gone from `data/remote/**`. |
| 2 — Services use `Either` | ✅ Done | `McwsService`/`JRiverService` `browseChildren`/`browseFiles`/`search` propagate `Either<McwsError, T>` instead of swallowing errors as `emptyList()`. Command paths (play/pause/etc.) use `onLeft` for logging. Zones polling switched to `Schedule.spaced(30s).repeat { ... }`. Playback polling kept as `while (isActive)` loop because its cadence is state-dependent (1s vs 5s) which doesn't map cleanly onto a static `Schedule`. |
| 3 — ViewModels | ✅ Done | `LibraryViewModel` and `SetupViewModel` use `either { … bind() … }.fold(ifLeft, ifRight)` instead of `try/catch`. All `_uiState.value = _uiState.value.copy(...)` calls switched to `_uiState.update { … }`, fixing a latent read-modify-write race in concurrent updates. `LibraryUiState.error` typed as `DomainError?` so the UI can dispatch on error class later; `SetupUiState.error` left as `String?` because its messages are pre-formatted user-facing strings. |
| 4 — Optional polish | ⏭ Skipped | Retries, `Resource<HttpClient>`, optics, `Nel` — explicitly marked optional in the original plan and not requested. |

### Files added
- `composeApp/src/commonMain/kotlin/com/example/jrr/domain/model/Errors.kt`

### Files changed
- `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` — Arrow deps.
- `data/remote/mcws/McwsApi.kt` — returns `Either<McwsError, String>`.
- `data/remote/mcws/JRiverMcwsClient.kt` — every public method returns `Either<McwsError, T>`; XML decode wrapped in `catch { … } raise McwsError.Parse`.
- `data/remote/lookup/JRiverLookupService.kt` — returns `Either<LookupError, LookupResponse>`.
- `service/McwsService.kt` — `Either`-aware `logFailure`/`onLeft`/`onRight`; zones polling via `Schedule`.
- `service/JRiverService.kt` — library methods propagate `Either`.
- `ui/library/LibraryViewModel.kt`, `ui/library/LibraryBrowseScreen.kt` — `either { … }` blocks, typed error state.
- `ui/setup/SetupViewModel.kt` — `either { … }` blocks, `update { … }`.

### Deviations from the original plan
- **No `.toResult()` bridge in the final tree.** The plan suggested keeping it for staged migration; in practice all three layers (data, service, VM) flipped together cleanly enough that the bridge was never needed past the in-progress builds. It was added during Phase 1 and removed before commit.
- **Playback polling did not move to `Schedule`.** The interval changes based on `_playerStatus.value.state` (1s while playing, 5s otherwise). `Schedule.spaced` takes a static `Duration`, and the alternatives (`Schedule.withDelay { state -> if (playing) 1.s else 5.s }`) added more ceremony than the existing `while (isActive)` loop. Zones polling, which is static at 30s, did move.
- **`Json.parseToJsonElement` still used in two MCWS methods (`getPlayingNow`, `getTracksByKeys`).** These walk a `JsonObject` map with case-insensitive lookups that don't fit `@SerialName` cleanly. They're wrapped in `catch { … } raise McwsError.Parse` so failures are typed, but the parsing path itself is unchanged from before the refactor. Worth revisiting in a follow-up.

### Acceptance criteria — verified
- ✅ No `runCatching` in `data/remote/**`.
- ✅ No `try { ... } catch (e: Exception)` in `ui/**`.
- ✅ No `.fold(onSuccess = …, onFailure = …)` chains anywhere.
- ✅ All `_uiState.value = _uiState.value.copy(...)` → `_uiState.update { ... }` in the migrated ViewModels (Library, Setup). `SettingsViewModel` and `PlayerViewModel` weren't using the read-modify-write pattern.
- ✅ `:composeApp:compileDebugKotlinAndroid`, `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinIosArm64` all green with only pre-existing deprecation warnings.

## Scope

In scope (commonMain):
- `data/remote/mcws/*` — `JRiverMcwsClient`, `McwsApi`
- `data/remote/lookup/JRiverLookupService`
- `data/local/JRiverSettings`
- `service/*` — `McwsService`, `JRiverService`, `LocalAudioService`
- `ui/library/LibraryViewModel`, plus the other ViewModels under `ui/`
- `domain/model/DomainModels.kt` (additive: errors only)
- `di/AppModule` (Koin) — wire the new types

Out of scope:
- Compose UI composables (UI stays as is — they consume `StateFlow<UiState>` exactly as today)
- Platform-specific players (`androidMain`/`iosMain`/`jvmMain` `actual`s) — unchanged unless they leak `Throwable` into commonMain
- Build flavours, navigation graph, persistence schema

## Why Arrow

Concrete pains in the current code that Arrow targets:

1. **Result<T> erases the error type.** `JRiverMcwsClient.browseFiles` returns `Result<List<Track>>` where the `Throwable` could be a network error, an auth failure, a parse failure, or a server 5xx. Callers can't distinguish, so `McwsService` collapses every failure into `emptyList()` plus a log line, and the UI shows nothing rather than a meaningful message. → `Either<McwsError, T>` with a sealed error hierarchy fixes this without exceptions.
2. **Boilerplate `.fold(onSuccess, onFailure)` everywhere.** `McwsService` has 7+ identical `fold` blocks. → `either { ... }` / `raise` DSL collapses them to straight-line code.
3. **Throwing for control flow.** `parseTrackList`, `McwsApi.get`, and `JRiverLookupService` all throw `Exception` inside `runCatching`. → typed `Raise<E>` removes the throw/catch dance.
4. **Imperative state mutation in ViewModels.** `_uiState.value = _uiState.value.copy(...)` repeated 4× per handler. → Arrow Optics (`@optics`) gives lens-based copies, and the `raise` block makes the success vs. error branches one expression.
5. **Polling jobs hand-rolled.** `McwsService.startPlaybackPolling` is a `while (isActive) { ... ; delay(...) }`. → `arrow.fx.coroutines.Schedule` declares the cadence as data and composes with backoff/retry.

Things Arrow is **not** brought in for:
- Type-class-level abstraction (no `Monad<F>` style code in app land — overkill).
- Replacing kotlinx.coroutines (Arrow Fx complements it; we keep `suspend` + `Flow`).
- Replacing Koin.

## Phase 0 — Wire up dependencies

`gradle/libs.versions.toml`:

```toml
[versions]
arrow = "2.0.1"

[libraries]
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
arrow-fx-coroutines = { module = "io.arrow-kt:arrow-fx-coroutines", version.ref = "arrow" }
arrow-resilience = { module = "io.arrow-kt:arrow-resilience", version.ref = "arrow" }
arrow-optics = { module = "io.arrow-kt:arrow-optics", version.ref = "arrow" }
arrow-optics-ksp = { module = "io.arrow-kt:arrow-optics-ksp-plugin", version.ref = "arrow" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version = "2.3.21-2.0.0" } # match Kotlin
```

`composeApp/build.gradle.kts` — add to `commonMain.dependencies` (optics-ksp is set up only if/when Phase 4 lands):

```kotlin
implementation(libs.arrow.core)
implementation(libs.arrow.fx.coroutines)
implementation(libs.arrow.resilience)
```

Verify: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm :composeApp:compileKotlinIosArm64`.

**Done when:** all three targets compile with the new deps and zero source-level changes.

## Phase 1 — Typed errors at the data layer

Introduce a sealed error hierarchy and switch the McWS/lookup/settings layer from `Result<T>` to `Either<DomainError, T>`. No service or ViewModel changes yet — they keep working via thin `.toResult()` adapters.

### 1.1 Error model

New file `domain/model/Errors.kt`:

```kotlin
package com.example.jrr.domain.model

sealed interface DomainError { val message: String }

sealed interface McwsError : DomainError {
    data class Network(override val message: String, val cause: Throwable? = null) : McwsError
    data class HttpStatus(val status: Int, override val message: String) : McwsError
    data class Parse(override val message: String, val cause: Throwable? = null) : McwsError
    data class Auth(override val message: String) : McwsError
    data class Unknown(override val message: String, val cause: Throwable? = null) : McwsError
}

sealed interface SettingsError : DomainError {
    data class Read(override val message: String, val cause: Throwable? = null) : SettingsError
    data class Write(override val message: String, val cause: Throwable? = null) : SettingsError
    data object Missing : SettingsError { override val message: String = "Setting not present" }
}
```

### 1.2 McwsApi — narrow `get` to `Either`

`McwsApi.get` today throws on non-2xx and returns `String`. Replace with:

```kotlin
context(raise: Raise<McwsError>)
suspend fun get(
    baseUrl: String, endpoint: String,
    params: Map<String, String> = emptyMap(),
    token: String? = null,
): HttpResponse = either {
    val url = "$baseUrl/MCWS/v1/$endpoint"
    val response = catch({ httpClient.get(url) { /* params, token */ } }) {
        raise(McwsError.Network("GET $url failed", it))
    }
    ensure(response.status.isSuccess()) {
        McwsError.HttpStatus(response.status.value, "MCWS $endpoint -> ${response.status}")
    }
    response
}.bind() // raises into the calling context
```

Keep returning `HttpResponse` (not `String`) so the streaming path from the OOM fix in `JRiverMcwsClient.streamTracks` keeps working — there's no longer any value in materialising the body inside `McwsApi`.

Migrate callers in two waves:
- Wave A: introduce a sibling `getEither(...)` returning `Either<McwsError, HttpResponse>`; leave the old `get(...): String` in place.
- Wave B: convert each call site, then delete the old `get`.

### 1.3 JRiverMcwsClient — `Either` over `Result`

For each `suspend fun ...: Result<T>` in `JRiverMcwsClient.kt`:

```kotlin
suspend fun browseChildren(id: String = "-1"): Either<McwsError, List<BrowseItem>> = either {
    val response = api.getEither(baseUrl, "Browse/Children", mapOf("ID" to id, "Version" to "1"), token).bind()
    val xmlBody = catch({ response.bodyAsText() }) { raise(McwsError.Network("read body", it)) }
    val mcws = catch({ xml.decodeFromString(McwsResponse.serializer(), xmlBody) }) {
        raise(McwsError.Parse("Browse/Children XML", it))
    }
    mcws.items.map { BrowseItem(it.value, it.name) }
}
```

The streaming `browseFiles` / `searchFiles` flow stays the same shape — just wrap `decodeFromSource` and `bodyAsChannel().readBuffer()` in `catch { ... } reraise McwsError.Parse|Network`.

Lookup and Settings get the same treatment with their own error types.

### 1.4 Bridge for un-migrated callers

Until services move in Phase 2, add an extension once:

```kotlin
fun <E, A> Either<E, A>.toResult(map: (E) -> Throwable = { Exception(it.toString()) }): Result<A> =
    fold({ Result.failure(map(it)) }, { Result.success(it) })
```

so `McwsService` can keep calling `.fold(onSuccess, onFailure)` unchanged during the transition.

**Done when:** every public function in `data/remote/**` returns `Either<DomainError, T>`, no `runCatching` remains in that package, and the app still runs.

## Phase 2 — Services switch to `raise`

`McwsService`, `JRiverService`, `LocalAudioService` lose their `.fold(onSuccess, onFailure)` boilerplate.

Before (`McwsService.kt:83-101`):

```kotlin
suspend fun browseFiles(id: String): List<Track> =
    mcwsClient.browseFiles(id).fold(
        onSuccess = { it },
        onFailure = {
            logger.e { "Failed to browse files for ID $id: ${it.message}" }
            emptyList()
        },
    )
```

After:

```kotlin
suspend fun browseFiles(id: String): Either<McwsError, List<Track>> =
    mcwsClient.browseFiles(id).onLeft { logger.e { "browseFiles($id): ${it.message}" } }
```

Key design choice: **stop swallowing errors at the service layer.** The current behaviour silently substitutes `emptyList()` on failure, which is why the OOM showed up as a blank screen instead of an error. The service now propagates `Either`, and the ViewModel decides whether to project the error into UI.

For the fire-and-forget command methods (`play`, `pause`, …) keep them as `Unit`-returning, but log via `.onLeft`:

```kotlin
fun play(zoneId: String) = scope.launch {
    mcwsClient.play(zoneId).onLeft { logger.w { "play failed: ${it.message}" } }
}
```

Polling loops also benefit:

```kotlin
private fun startPlaybackPolling() {
    playbackPollingJob = scope.launch {
        Schedule.spaced<Unit>(1.seconds)
            .doWhile { _, _ -> isActive && _playerStatus.value?.state == PlaybackState.PLAYING }
            .repeat { pollPlaybackInfo() }
    }
}
```

(`pollPlaybackInfo` itself becomes `suspend fun (): Either<McwsError, Unit>` with `.onLeft { logger.w {...} }`.)

**Done when:** no `.fold(onSuccess = ..., onFailure = ...)` blocks remain under `service/`, and the manual `while (isActive) { ...; delay(...) }` loops are gone.

## Phase 3 — ViewModels: `raise` + UiState as ADT

This is the biggest win for readability. `LibraryViewModel.browse` today is 30 lines of try/catch + four `copy(...)` calls. Refactor target:

```kotlin
fun browse(id: String, name: String = "Library") {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        either<DomainError, LibraryUiState> {
            val children = jRiverService.browseChildren(id).bind()
            val tracks = if (children.isEmpty()) jRiverService.browseFiles(id).bind() else emptyList()
            val newStack =
                if (id == "-1") listOf(BrowseItem("-1", "Library"))
                else _uiState.value.navigationStack + BrowseItem(id, name)
            _uiState.value.copy(
                navigationStack = newStack,
                children = children,
                tracks = tracks,
                isLoading = false,
                error = null,
            )
        }.fold(
            ifLeft = { err ->
                logger.e { "browse($id): ${err.message}" }
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            },
            ifRight = { next -> _uiState.value = next },
        )
    }
}
```

Same shape for `navigateBack`, `search`, and the player/setup VMs.

Optional cleanup: convert `LibraryUiState.error: String?` into `LibraryUiState.error: DomainError?` so the UI can show typed-error specific UI ("retry" for `Network`, "re-auth" for `Auth`, etc.). Keep `String?` if that's a yak.

**Done when:** every `try { ... } catch (e: Exception)` block under `ui/` is gone, and `_uiState.value = _uiState.value.copy(...)` is replaced by `_uiState.update { ... }` everywhere (this also fixes the latent read-modify-write race in the current code).

## Phase 4 — Optional polish

These are independent — pick any/all/none.

### 4.1 Retries & timeouts via `Schedule`

Replace ad-hoc retry in `JRiverLookupService` with composable schedules:

```kotlin
val policy = Schedule.exponential<McwsError.Network>(250.milliseconds) and Schedule.recurs(3)
Schedule.retry(policy) { api.getEither(...).bind() }
```

### 4.2 `Resource<HttpClient>` for the Ktor client

In `di/AppModule`, wrap `HttpClient` construction in `arrow.fx.coroutines.resource { ... } release { it.close() }` so shutdown is deterministic. Koin can provide the `Resource<HttpClient>` and let the app `use { }` it for its lifetime.

### 4.3 Optics for `UiState`

Add `arrow-optics-ksp` and annotate the bigger UiState classes with `@optics`. Updates become:

```kotlin
_uiState.update { LibraryUiState.tracks.set(it, tracks) }
```

Only worth it for UiState shapes with ≥4 nested fields. `LibraryUiState` itself is borderline; the player/queue states are clearer wins.

### 4.4 `NonEmptyList` where it matters

`PlayingNowItem` queues and `Zone` lists are sometimes "must have at least one." Switching select APIs to `Nel<T>` removes a class of empty-list-edge-case bugs from the player.

## Migration order (concrete)

| # | Change | Files | Reverting cost |
|---|--------|-------|----------------|
| 1 | Add deps | `libs.versions.toml`, `build.gradle.kts` | trivial |
| 2 | Add `Errors.kt` | `domain/model/Errors.kt` (new) | trivial |
| 3 | `McwsApi.getEither` alongside `get` | `McwsApi.kt` | trivial |
| 4 | Migrate `JRiverMcwsClient` functions one-by-one | `JRiverMcwsClient.kt` | per-function |
| 5 | Migrate `JRiverLookupService`, `JRiverSettings` | those two files | per-file |
| 6 | Delete `McwsApi.get(): String` once unused | `McwsApi.kt` | trivial |
| 7 | Services use `Either` directly | `service/*.kt` | per-file |
| 8 | Replace polling loops with `Schedule` | `McwsService.kt` | isolated |
| 9 | ViewModels: `either { ... }.fold(...)` | `ui/**/*.kt` | per-VM |
| 10 | Optional: optics, `Resource`, `Nel` | various | each optional |

Each row is its own commit and PR. Steps 1–6 leave the rest of the app on `Result` unchanged. Steps 7–9 each have their own kill switch (just keep using `.fold(onSuccess, onFailure)` until you're ready).

## Trade-offs and risks

- **Context parameters / Raise DSL stability.** Arrow 2.x has stabilised `Raise<E>` and the `either { }` builder. `context(Raise<E>)` syntax remains an opt-in Kotlin feature. If the team isn't on the context-receivers train yet, prefer the `either { … bind() … }` form everywhere; it works on plain Kotlin without `-Xcontext-receivers`.
- **Binary size.** `arrow-core` adds ~250KB to the Android APK; `arrow-fx-coroutines` ~150KB; `arrow-resilience` ~80KB. Acceptable for this app.
- **Learning curve.** New contributors will see `bind()` and `raise(...)`. Mitigation: a short `doc/ARROW_PATTERNS.md` with the 5 patterns we actually use (either-block, bind, onLeft, fold, Schedule).
- **Don't over-abstract.** Resist the urge to introduce `Effect<E, A>`, typeclasses, or `IO`. The codebase only needs `Either`, `Raise`, `Schedule`, and (maybe) optics.
- **Tests.** Each Either-returning function gains a "left path" test that today doesn't exist (because `Result.failure` was logged and dropped). This is the point — but budget for it.

## Acceptance criteria (overall)

- No `runCatching` in `data/remote/**`.
- No `try { ... } catch (e: Exception)` in `ui/**`.
- No `.fold(onSuccess, onFailure)` chains in `service/**`.
- All `_uiState.value = _uiState.value.copy(...)` → `_uiState.update { ... }`.
- Three targets still compile: `:composeApp:compileDebugKotlinAndroid`, `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinIosArm64`.
- Existing manual smoke tests (browse library, play track, search, switch zones) still pass.

## Non-goals

- Rewriting `Flow`/`StateFlow` into Arrow's `flow` builders. Coroutines and Arrow coexist; `StateFlow` stays.
- Replacing Koin with Arrow's manual DI patterns.
- Going purely effectful (no `IO`/`Effect<E, A>` wrapper) — `suspend` + `Either` is enough.
