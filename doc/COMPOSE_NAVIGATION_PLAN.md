# Compose Multiplatform Navigation Implementation Plan

Date: 2026-05-12

## Goal

Replace the current manual screen state in `App.kt` and `NowPlayingContainer.kt` with official
Compose Multiplatform Navigation while keeping the existing Koin ViewModels and service layer
intact.

Recommended dependency:

```kotlin
implementation(libs.androidx.navigation.compose)
```

Recommended artifact:

```toml
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version = "2.9.2" }
```

## Current State

- `App.kt` uses `currentScreen: Screen` and a `when` statement for `Setup` vs `Player`.
- `NowPlayingContainer.kt` uses `selectedTab: PlayerTab` and a `when` statement for bottom
  navigation tabs.
- ViewModels are resolved inside composables with `koinViewModel()`.
- `JRiverService` already acts as a UI-facing coordinator, so navigation should not change service
  responsibilities.

## Target Navigation Shape

Use typed serializable routes in `commonMain`.

Top-level routes:

```kotlin
@Serializable data object SetupRoute
@Serializable data object PlayerRoute
```

Player tab routes:

```kotlin
@Serializable data object NowPlayingRoute
@Serializable data object QueueRoute
@Serializable data object LibraryRoute
@Serializable data object ZonesRoute
@Serializable data object SettingsRoute
```

Keep the player tabs as nested navigation under `PlayerRoute`. This gives back stack behavior for
tab content and removes duplicated manual tab state.

## Files To Add

### `composeApp/src/commonMain/kotlin/com/example/jrr/navigation/AppRoutes.kt`

Owns all route definitions. Keep route classes small and stable.

### `composeApp/src/commonMain/kotlin/com/example/jrr/navigation/AppNavHost.kt`

Owns the top-level `NavHost`:

- start destination is computed from session state
- `SetupRoute` renders `SetupScreen`
- `PlayerRoute` renders the player shell

### `composeApp/src/commonMain/kotlin/com/example/jrr/navigation/PlayerNavHost.kt`

Owns nested player-tab navigation and bottom bar route selection.

## Files To Change

### `gradle/libs.versions.toml`

Add:

```toml
androidx-navigation = "2.9.2"
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
```

### `composeApp/build.gradle.kts`

Add to `commonMain.dependencies`:

```kotlin
implementation(libs.androidx.navigation.compose)
```

### `App.kt`

Remove:

- `currentScreen`
- local `Screen` sealed class
- direct `when` rendering

Keep:

- session collection from settings
- `mcwsClient.updateConfig(...)`
- `jRiverService.start()`

Replace rendering with:

```kotlin
AppNavHost(
    hasServer = !serverAddress.isNullOrBlank(),
    serverAddress = serverAddress.orEmpty()
)
```

The navigation host should react to session changes using `LaunchedEffect(hasServer)`:

- navigate to `PlayerRoute` after a valid server appears
- navigate to `SetupRoute` after logout clears the server

### `NowPlayingContainer.kt`

Convert from a stateful tab switcher into a player shell:

- keep `Scaffold`
- replace `selectedTab` with current nav destination
- bottom bar item clicks call `navController.navigate(route)`
- content area hosts `PlayerNavHost`

Remove the `PlayerTab` enum after route-based tabs are working.

## Navigation Behavior

### Setup To Player

Setup should not directly navigate on button click. Keep the current source of truth:
settings/session state.

Flow:

1. `SetupViewModel` authenticates and saves session.
2. `App.kt` observes session.
3. `AppNavHost` navigates to `PlayerRoute`.

This avoids race conditions and keeps authentication ownership out of navigation code.

### Logout To Setup

Flow:

1. `SettingsViewModel.logout()` clears session.
2. `App.kt` observes empty server address.
3. `AppNavHost` navigates to `SetupRoute` and pops player state.

Use `popUpTo` so the back stack cannot return to player after logout.

### Bottom Tabs

Tabs should navigate with single-top behavior:

```kotlin
navController.navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(navController.graph.startDestinationId) {
        saveState = true
    }
}
```

This preserves each tab state when switching, where supported by the navigation runtime.

## Implementation Phases

### Phase 1: Add Dependency And Routes

Status: completed.

1. Add navigation dependency to version catalog and `commonMain`.
2. Add `AppRoutes.kt`.
3. Compile with:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

### Phase 2: Top-Level Navigation

Status: completed.

1. Add `AppNavHost`.
2. Replace `currentScreen` in `App.kt`.
3. Keep setup/player screen behavior identical.
4. Validate:
    - first launch with no server shows setup
    - authenticated session shows player
    - logout returns to setup

### Phase 3: Player Tab Navigation

Status: completed.

1. Add `PlayerNavHost`.
2. Refactor `NowPlayingContainer` bottom bar to route-based navigation.
3. Keep existing tab UI and icons.
4. Validate:
    - each tab opens correctly
    - tab selection tracks current destination
    - settings back action navigates to now playing

### Phase 4: Cleanup

Status: completed.

1. Delete old `Screen` and `PlayerTab` state types.
2. Remove no-longer-needed parameters from `NowPlayingContainer` if possible.
3. Review logging to avoid duplicate navigation logs.
4. Compile and run the app.

## Risks And Notes

- Do not let `SetupScreen` own navigation. Session state should remain the trigger.
- Do not move service startup into destination composables. `App.kt` should remain responsible for
  configuring `JRiverMcwsClient` and starting/stopping `JRiverService`.
- Avoid passing `JRiverService` through navigation if not needed. ViewModels already receive
  dependencies through Koin.
- Bottom tab back behavior needs a product decision. Recommended initial behavior: tab switches
  should not build a deep history; system back from player should exit/minimize on Android rather
  than walk through every tab.

## Validation Checklist

- `./gradlew :composeApp:compileDebugKotlinAndroid` - passed.
- Launch with empty settings: setup screen appears.
- Authenticate: player appears and `JRiverService.start()` runs once.
- Switch all tabs: now playing, queue, library, zones, settings.
- Logout from settings: setup appears and back does not return to player.
- Relaunch with saved session: player appears directly.
- Local zone and remote zone command routing remains unchanged.

## Implementation Notes

- Top-level and player-tab routes use typed `@Serializable` route objects.
- `AppNavHost` owns setup/player navigation and reacts to session state.
- `PlayerNavHost` owns nested player destinations.
- `NowPlayingContainer` owns the nested player `NavHostController` and derives selected tab state
  from the active destination.
- The bottom bar uses a small custom tab item inside `NavigationBar`; `NavigationBarItem` was not
  available from the resolved Material3 artifact after adding navigation.
