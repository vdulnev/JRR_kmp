# Implementation Plan: JRiver Multiplatform Client (KMP)

## 1. Objective

Develop a fully-featured, cross-platform remote control application for JRiver Media Center using
Kotlin Multiplatform (KMP) and Compose Multiplatform. The application will adhere to the provided
platform-independent specification (v0.7.0) and implement the "Obsidian Audio" premium dark design
system from the provided Stitch project.

## 2. Background & Motivation

JRiver Media Center provides a powerful HTTP-based API (MCWS). This project aims to create a highly
responsive, aesthetically premium, and consistent remote control experience across Android, iOS, and
Desktop. It uses a modern KMP stack to maximize code sharing across the Data, Domain, and
Presentation layers, while rendering a unified UI with Compose Multiplatform.

## 3. Architecture & Tech Stack

- **UI Framework**: Compose Multiplatform (Android, iOS, Desktop).
- **Presentation Layer**: MVVM (Model-View-ViewModel) with Kotlin Coroutines `StateFlow` for
  reactive UI state.
- **Service Layer**: Kotlin Coroutines & Flows for polling orchestration, state aggregation, and
  local playback bridging.
- **Networking**: Ktor Client for HTTP communication and MCWS XML/JSON parsing (using
  `kotlinx.serialization` with an XML parser like `pdvrieze/xmlutil`).
- **Persistence**: Jetpack DataStore (Preferences) for KMP to store server connection details, auth
  tokens, and the last active zone.
- **Local Media Playback**: Platform-specific audio players accessed via expected
  interfaces/expect-actual (e.g., `androidx.media3` on Android, `AVPlayer` on iOS).

## 4. Design System: Obsidian Audio

The application will implement the "Obsidian Audio" theme:

- **Colors**: Deep obsidian background (`#080809` or `#131314` base), desaturated charcoal surfaces,
  with Gold/Amber (`#C8922A` / `#f8bc51`) as the primary accent for interactions and active states.
- **Typography**: `Inter` font family exclusively. Heavy reliance on weight contrast (e.g.,
  Semi-Bold/Bold headers, upper-case technical labels).
- **Shapes & Layout**: "Soft-Technical" shapes (4px standard radius, 8px for artwork), 4px baseline
  grid, ample negative space, and "Glassmorphism" for depth instead of harsh shadows.

## 5. Phased Implementation Plan

### Phase 1: Core Networking & Domain Models [COMPLETED]

* **Domain Models**: Implement `ServerInfo`, `Zone`, `PlayerStatus`, `TrackInfo`, `PlayingNowItem`,
  `Album`, `Track` as Kotlin data classes.
* **JRiver Lookup Client**: Implement the unauthenticated key lookup service (
  `webplay.jriver.com/libraryserver/lookup`).
* **MCWS HTTP Client**: Setup Ktor client. Implement the two-layer HTTP approach: a thin API layer
  executing GET requests and a Domain Client mapping XML/JSON to domain models.
* **Authentication Interceptor**: Implement a mechanism to inject `Token=` into all MCWS requests
  after authentication.

### Phase 2: Design System & Shared UI Components [COMPLETED]

* **Theme Definition**: Create `ObsidianTheme` in Compose providing colors, typography (`Inter`),
  and shape definitions.
* **Core Components**: Build reusable widgets:
    * Primary Gold buttons, Ghost buttons.
    * Technical labels and typography wrappers.
    * Custom Sliders/Progress bars (square caps, gold fill).
    * Media Cards with 1px borders and subtle gradients.
    * High-fidelity icons.

### Phase 3: Server Setup & Authentication [COMPLETED]

* **DataStore Setup**: Configure Jetpack DataStore to persist server IP, port, Access Key, and auth
  Token.
* **Server Setup UI**: Build the "Server Setup" screen allowing manual IP entry or Access Key
  lookup.
* **Auth Flow**: Implement the `Alive` check and `Authenticate` call, securely storing the token and
  transitioning to the main app flow upon success.

### Phase 4: State Management & Service Orchestration [COMPLETED]

* **Polling Engine**: Create a `JRiverService` that manages the polling loop (1s when playing, 5s
  when paused/stopped, 30s for zones) using Coroutines.
* **Change Detection**: Implement logic to monitor `PlayingNowChangeCounter` and trigger queue
  refetches only when necessary.
* **Transport Commands**: Expose play, pause, seek, volume, and zone management commands to the
  ViewModels.

### Phase 5: Player UI & Queue [COMPLETED]

* **Now Playing Screen**: Implement the main player view showing high-res artwork (`File/GetImage`),
  track metadata, and transport controls.
* **Zone Controller**: Implement the bottom sheet / drawer to switch and link/unlink zones.
* **Play Queue Screen**: Build the interactive queue UI (`Playback/Playlist`), allowing reordering
  and track selection.

### Phase 6: Library Browsing [COMPLETED]

* **Search Integration**: Implement `Files/Search` with robust MCWS query building and escaping.
* **Browse Tree**: Implement navigation through `Browse/Children` and `Browse/Files`.
* **Library Views**: Build the Grid and List views for Albums, Artists, and Tracks. Implement
  case-insensitive grouping for Multi-Disc albums client-side.

### Phase 7: Local Playback Integration [COMPLETED]

* **Local Zone Synthesis**: Synthesize a "Local" zone in the KMP shared code.
* **Stream Proxying**: Implement logic to construct streaming URLs (`File/GetFile`).
* **Platform Media Players**: Implement KMP interfaces for a background media player, bridging to
  Media3 (Android) and AVPlayer (iOS/Mac).

## 6. Verification & Testing

* **Unit Testing**: Thoroughly test MCWS query construction, XML parsing, and the change-detection
  polling logic in the Service layer.
* **UI Testing**: Use Compose Multiplatform testing tools for critical UI flows (e.g., Setup,
  Transport controls).
* **Manual Validation**: Test connection via Access Key vs IP. Validate polling suspend/resume on
  app backgrounding. Verify local playback streams correctly and falls back smoothly on network
  disruptions.
