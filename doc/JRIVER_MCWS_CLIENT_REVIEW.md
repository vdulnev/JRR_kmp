# JRiver MCWS Client Review

Date: 2026-05-12

Scope: current JRiver Media Center client code, primarily:

- `composeApp/src/commonMain/kotlin/com/example/jrr/data/remote/mcws/JRiverMcwsClient.kt`
- `composeApp/src/commonMain/kotlin/com/example/jrr/data/remote/mcws/McwsApi.kt`
- `composeApp/src/commonMain/kotlin/com/example/jrr/service/JRiverService.kt`
- related app startup and view model callers

## Findings

### High: service can start before token is available

Status: fixed after review.

Files:

- `composeApp/src/commonMain/kotlin/com/example/jrr/App.kt`
- `composeApp/src/commonMain/kotlin/com/example/jrr/ui/setup/SetupViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/example/jrr/data/local/JRiverSettings.kt`

`SetupViewModel.authenticateAndSave()` saves `serverAddress` before `authToken`. `App` observes both values and starts `JRiverService` as soon as `serverAddress` is non-blank, so it can configure `JRiverMcwsClient` with `authToken == null`.

Impact: auth-required servers may receive unauthenticated polling requests, then the service starts again once the token arrives.

Resolution: authenticated server details and token are now persisted in one DataStore edit, and `App` observes address/token through a combined flow so startup waits until both persisted values have loaded.

### High: `JRiverService.start()` is not idempotent

File:

- `composeApp/src/commonMain/kotlin/com/example/jrr/service/JRiverService.kt`

`start()` cancels/replaces playback and zone polling jobs, but it launches two `localPlayer` collectors every time and never cancels them in `stop()`.

Impact: re-login, token refresh, or repeated startup can accumulate collectors and duplicate local status updates.

Suggested fix: track collector jobs and cancel them, or make `start()` return early when the service is already running.

### Medium: local `next()` and `previous()` route to MCWS

File:

- `composeApp/src/commonMain/kotlin/com/example/jrr/service/JRiverService.kt`

Most transport methods special-case `activeZoneId == "local"`, but `next()` and `previous()` always call MCWS with the current zone ID.

Impact: when the local zone is active, these commands send `Zone=local` to MCWS and should fail or hit the wrong control path.

Suggested fix: either implement local queue navigation or no-op/log clearly for the local zone.

### Medium: stream URL construction is unsafe

File:

- `composeApp/src/commonMain/kotlin/com/example/jrr/data/remote/mcws/JRiverMcwsClient.kt`

`buildStreamUrl()` interpolates query parameters directly and always appends `Token=$token`, including `Token=null`.

Impact: local playback can break after the startup token race or when token/conversion/quality values contain reserved URL characters.

Suggested fix: build the URL through Ktor URL/query APIs and omit `Token` when absent.

### Medium: MCWS JSON parsing misses single-item responses

File:

- `composeApp/src/commonMain/kotlin/com/example/jrr/data/remote/mcws/JRiverMcwsClient.kt`

`getPlayingNow()`, `getTracksByKeys()`, and `parseTrackList()` only accept `Item` when it is a JSON array.

Impact: if MCWS returns a single `Item` object for one playlist/search result, queue parsing fails or metadata silently comes back empty.

Suggested fix: normalize `Item` into a list for both object and array shapes.

### Low: command failures are mostly swallowed

File:

- `composeApp/src/commonMain/kotlin/com/example/jrr/service/JRiverService.kt`

Most transport and queue commands ignore returned `Result` values.

Impact: failures from play/pause/seek/queue edits are not surfaced to the UI, and many are not logged.

Suggested fix: consistently log failed command results and consider exposing transient command errors in UI state.

## Validation

Command run:

```bash
./gradlew :composeApp:compileKotlinMetadata
```

Result: build successful.

Note: the first Gradle attempt was blocked by sandbox access to `~/.gradle`; it passed after allowing Gradle wrapper cache access.
