# JRR — JRiver Remote

Language-agnostic specification for a remote control application
for JRiver Media Center via MCWS (Media Center Web Service).

**Version:** 0.7.0
**Status:** Draft — implemented in `jrr_f/` (Flutter)

---

## 1. Overview

JRR is a remote control application for JRiver Media Center.
It communicates with MCWS v1 over HTTP on a local network.

### 1.1 Goals

- Control audio playback across multiple zones
- Display now-playing information with artwork
- Manage the Playing Now queue
- Work consistently across mobile and desktop platforms

### 1.2 Scope

| Area                         | Status    |
|------------------------------|-----------|
| Playback control             | v1        |
| Now Playing info & artwork   | v1        |
| Zone management              | v1        |
| Playing Now queue            | v1        |
| Library browse & search      | v2 (done) |
| Library tree browsing        | v3 (done) |
| UI design system & polish    | v4 (done) |
| Multi-platform layouts       | v5 (done) |
| Local playback (client zone) | v5 (done) |
| Favorites (browse nodes)     | v5 (done) |
| Offline Mode (server-less)   | v6 (done) |
| Playlist management          | Later     |
| File metadata editing        | Later     |
| DSP & audio configuration    | Later     |
| Video streaming              | Skip      |
| Television & recording       | Skip      |

### 1.3 Non-Goals

- Video playback or transcoding
- Television tuner control
- MCC command passthrough
- Keyboard simulation
- License or skin management

---

## 2. Connection & Authentication

### 2.1 Server Address

The user provides the server address as either:

- a manual `host:port` (default MCWS port `52199`), or
- a 6-character **JRiver Access Key**, resolved through the public
  registry at `http://webplay.jriver.com/libraryserver/lookup?id=<key>`.

The lookup endpoint returns plain XML (not the MCWS `<Item Name="…">`
format):

```xml
<Response>
  <ip>1.2.3.4</ip>
  <port>52199</port>
  <localiplist>10.0.0.5,192.168.1.5</localiplist>
  ...
</Response>
```

Clients should prefer the first reachable address from `<localiplist>`,
falling back to `<ip>`. The lookup call carries no auth and must not be
routed through the MCWS auth interceptor.

### 2.2 Authentication Flow

1. Client calls `GET /MCWS/v1/Authenticate` with HTTP Basic auth
   (`Authorization: Basic base64(username:password)`).
2. Server responds with a token:
   ```xml
   <Response Status="OK">
     <Item Name="Token">token_value</Item>
     <Item Name="ReadOnly">0</Item>
   </Response>
   ```
3. Client stores the token for the session.
4. All subsequent requests append `Token=token_value` as a query
   parameter. No further Basic auth headers required.

### 2.3 Connection Validation

On startup, client calls `GET /MCWS/v1/Alive` to verify the server
is reachable and to retrieve server metadata.

Response fields:

- `RuntimeGUID` — unique server instance ID
- `ProgramVersion` — MC version string
- `FriendlyName` — user-assigned server name
- `Platform` — `Windows`, `Mac`, or `Linux`

The `FriendlyName` should be displayed to the user to confirm
they connected to the intended server.

### 2.4 Error Handling

MCWS returns `<Response Status="Failure">` for errors.
The client must handle:

- Connection refused — server unreachable
- HTTP 401 — invalid credentials
- `Status="Failure"` — command rejected by server

---

### 3.9 Case-Insensitivity

Many MCWS metadata tags (Artist, Album, Genre, Name) are inconsistent in their
casing across different files or server responses. Clients should treat these
fields case-insensitively for comparison and grouping.

| Field            | Comparison Policy | Used In                         | 
|------------------|-------------------|---------------------------------| 
| name             | Case-Insensitive  | Track/Album equality, grouping  | 
| artist           | Case-Insensitive  | Track/Album equality, filtering | 
| album            | Case-Insensitive  | Track/Album equality, grouping  | 
| genre            | Case-Insensitive  | Track equality, filtering       | 
| fileType         | Case-Insensitive  | Track equality                  | 
| albumArtist      | Case-Insensitive  | Album equality, filtering       | 
| folderPath       | Case-Insensitive  | Album equality, grouping        | 
| parentFolderPath | Case-Insensitive  | Album/Track grouping            | 

Normalization. Values used as keys for internal grouping or identification
(e.g., albumGroupId) must be normalized to a consistent case (prefer
lowercase) before use.

## 2.5 Offline Mode (Server-less)

Clients may allow users to enter the application without connecting to a server.
This mode is useful for accessing downloaded content on the device.

**Synthetic Session:**
The client should synthesize a ServerInfo object with a reserved ID (e.g., 'offline')
and an empty address to represent this state.

**Behavior:**
In Offline Mode:

- All server-bound API calls are skipped.
- The client-synthesized 'Offline' zone is the primary active zone.
- The library view should only show 'Downloads' or locally cached content.

## 2.6 Persistence & Startup

The client should persist the last active zone GUID.

**Server-less Startup:**
If the last active zone was 'Offline', the app should skip the initial network
'Alive' check and 'Authenticate' call on launch, booting directly into the
offline shell using the synthetic session. This allows for immediate music
access even without internet or server availability.

## 3. Domain Model

All implementations must use these types. Field names are
normative. Types may be extended with platform-specific
concerns (e.g., serialization annotations) but the core
fields must be present.

### 3.1 ServerInfo

Represents the connected MCWS server.

| Field    | Type   | Source                  |
|----------|--------|-------------------------|
| id       | string | Alive → RuntimeGUID     |
| name     | string | Alive → FriendlyName    |
| version  | string | Alive → ProgramVersion  |
| platform | string | Alive → Platform        |
| address  | string | user-provided host:port |

### 3.2 Zone

Represents a playback zone.

| Field   | Type   | Source                         |
|---------|--------|--------------------------------|
| id      | string | Playback/Zones → ZoneID        |
| name    | string | Playback/Zones → ZoneName      |
| guid    | string | Playback/Zones → ZoneGUID      |
| isDLNA  | bool   | Playback/Zones → ZoneDLNA      |
| isLocal | bool   | client-synthesized (see §4.13) |

A client may synthesize an additional **local zone** that represents
the client device itself as a player. The local zone does not appear
in `Playback/Zones`; it is appended client-side and is the marker for
local-playback mode (see §4.13).

### 3.3 PlaybackState (enum)

| Value   | MCWS State value |
|---------|------------------|
| stopped | 0                |
| paused  | 1                |
| playing | 2                |

### 3.4 ShuffleMode (enum)

| Value     | MCWS Mode string |
|-----------|------------------|
| off       | Off              |
| on        | On               |
| automatic | Automatic        |

### 3.5 RepeatMode (enum)

| Value    | MCWS Mode string |
|----------|------------------|
| off      | Off              |
| playlist | Playlist         |
| track    | Track            |

### 3.6 PlayerStatus

Snapshot of the current playback state for a zone.

| Field                     | Type          | Source                                    |
|---------------------------|---------------|-------------------------------------------|
| zoneId                    | string        | Playback/Info → ZoneID                    |
| zoneName                  | string        | Playback/Info → ZoneName                  |
| state                     | PlaybackState | Playback/Info → State                     |
| trackInfo                 | TrackInfo?    | see below (null when queue empty)         |
| positionMs                | int           | Playback/Info → PositionMS                |
| durationMs                | int           | Playback/Info → DurationMS                |
| positionDisplay           | string        | Playback/Info → PositionDisplay           |
| volume                    | float         | Playback/Info → Volume (0.0–1.0)          |
| volumeDisplay             | string        | Playback/Info → VolumeDisplay             |
| isMuted                   | bool          | derived (see §4.5)                        |
| shuffleMode               | ShuffleMode   | Playback/Shuffle                          |
| repeatMode                | RepeatMode    | Playback/Repeat                           |
| playingNowPosition        | int           | Playback/Info → PlayingNowPosition        |
| playingNowTracks          | int           | Playback/Info → PlayingNowTracks          |
| playingNowPositionDisplay | string        | Playback/Info → PlayingNowPositionDisplay |
| playingNowChangeCounter   | int           | Playback/Info → PlayingNowChangeCounter   |

### 3.7 TrackInfo

Metadata for the currently playing track.

| Field      | Type   | Source                                         |
|------------|--------|------------------------------------------------|
| fileKey    | string | Playback/Info → FileKey                        |
| name       | string | Playback/Info → Name                           |
| artist     | string | Playback/Info → Artist                         |
| album      | string | Playback/Info → Album                          |
| imageUrl   | string | Playback/Info → ImageURL (resolve to full URL) |
| bitrate    | int    | Playback/Info → Bitrate (kbps)                 |
| bitDepth   | int    | Playback/Info → Bitdepth                       |
| sampleRate | int    | Playback/Info → SampleRate (Hz)                |
| channels   | int    | Playback/Info → Channels                       |

`Playback/Info` is intentionally minimal. For richer metadata
(`Date (readable)`, `File Type`, `Album Artist (auto)`, `Total Discs`,
etc.) clients fetch the full track via `File/GetInfo` (§4.10) keyed on
`fileKey`.

### 3.8 PlayingNowItem

An entry in the Playing Now queue.

| Field   | Type   | Source                         |
|---------|--------|--------------------------------|
| index   | int    | position in the list (0-based) |
| fileKey | string | from MPL/JSON response         |
| name    | string | Name field                     |
| artist  | string | Artist field                   |
| album   | string | Album field                    |

---

## 4. Operations

All operations are HTTP GET requests to the MCWS endpoint.
Parameters are passed as query string values.
The auth token is appended as `Token=<token>` to every request.

Base URL: `http://<host>:<port>/MCWS/v1`

### 4.1 Transport

| Operation | Endpoint           | Key Parameters      |
|-----------|--------------------|---------------------|
| play      | Playback/Play      | Zone, ZoneType      |
| playPause | Playback/PlayPause | Zone, ZoneType      |
| pause     | Playback/Pause     | State (0, 1, or -1) |
| stop      | Playback/Stop      | Zone, ZoneType      |
| stopAll   | Playback/StopAll   | —                   |
| next      | Playback/Next      | Zone, ZoneType      |
| previous  | Playback/Previous  | Zone, ZoneType      |

**Zone targeting:** Every transport command accepts `Zone` and
`ZoneType` parameters. The client should always pass
`Zone=<id>&ZoneType=ID` to target a specific zone.

### 4.2 Seek

**Endpoint:** `Playback/Position`

| Parameter | Type   | Description                                 |
|-----------|--------|---------------------------------------------|
| Position  | int    | Target position in ms or %                  |
| Relative  | int    | 1 = forward, -1 = backward, omit = absolute |
| Mode      | string | `ms` (default) or `%`                       |
| Zone      | string | Zone ID                                     |
| ZoneType  | string | `ID`                                        |

**Usage patterns:**

- Get current position: omit `Position`
- Seek to absolute time: `Position=30000&Mode=ms`
- Jump forward 10s: `Position=10000&Relative=1`
- Jump backward 10s: `Position=10000&Relative=-1`
- Seek to 50%: `Position=50&Mode=%`

**Response:** `<Item Name="Position">` — position in ms after change.

### 4.3 Volume

**Endpoint:** `Playback/Volume`

| Parameter | Type   | Description                     |
|-----------|--------|---------------------------------|
| Level     | float  | 0.0 to 1.0 (omit to query only) |
| Relative  | int    | 1 = add Level to current        |
| Zone      | string | Zone ID                         |
| ZoneType  | string | `ID`                            |

**Usage patterns:**

- Get current volume: omit `Level`
- Set to 75%: `Level=0.75`
- Increase by 10%: `Level=0.1&Relative=1`
- Decrease by 10%: `Level=-0.1&Relative=1`

**Response:**

- `<Item Name="Level">` — volume as 0.0–1.0
- `<Item Name="Display">` — volume as display string

### 4.4 Mute

**Endpoint:** `Playback/Mute`

| Parameter | Type   | Description          |
|-----------|--------|----------------------|
| Set       | int    | 1 = mute, 0 = unmute |
| Zone      | string | Zone ID              |
| ZoneType  | string | `ID`                 |

**Response:** `<Item Name="State">` — mute state after change.

### 4.5 Shuffle & Repeat

**Shuffle endpoint:** `Playback/Shuffle`

| Parameter | Type   | Values                                |
|-----------|--------|---------------------------------------|
| Mode      | string | Off, On, Automatic, Toggle, Reshuffle |
| Zone      | string | Zone ID                               |

- Omit `Mode` to query current state.
- Response: `<Item Name="Mode">` — current mode after change.

**Repeat endpoint:** `Playback/Repeat`

| Parameter | Type   | Values                             |
|-----------|--------|------------------------------------|
| Mode      | string | Off, Playlist, Track, Stop, Toggle |
| Zone      | string | Zone ID                            |

- Omit `Mode` to query current state.
- Response: `<Item Name="Mode">` — current mode after change.

### 4.6 Now Playing Info

**Endpoint:** `Playback/Info`

| Parameter | Type   | Description |
|-----------|--------|-------------|
| Zone      | string | Zone ID     |
| ZoneType  | string | `ID`        |

Returns all fields listed in `PlayerStatus` (§3.6) and
`TrackInfo` (§3.7). This is the primary polling endpoint.

### 4.7 Artwork

**Endpoint:** `File/GetImage`

| Parameter        | Type   | Description                     |
|------------------|--------|---------------------------------|
| File             | string | File key from TrackInfo.fileKey |
| Type             | string | `Thumbnail` (default) or `Full` |
| Width            | int    | Desired width in pixels         |
| Height           | int    | Desired height in pixels        |
| Format           | string | `jpg` (default) or `png`        |
| Square           | int    | 1 = crop to square              |
| FillTransparency | string | Hex color for transparency fill |

Returns the image as binary data (not XML).

The `ImageURL` field from `Playback/Info` provides a relative
URL (e.g., `MCWS/v1/File/GetImage?File=12345`). The client
must prepend the base URL to form the full image URL.

### 4.8 Zone Management

**List zones:** `Playback/Zones`

Returns `NumberZones` and indexed fields: `ZoneName#`, `ZoneID#`,
`ZoneGUID#`, `ZoneDLNA#`. Parse into a list of `Zone` objects.

**Set active zone:** `Playback/SetZone`

| Parameter | Type   |
|-----------|--------|
| Zone      | string |
| ZoneType  | string |

**Link zones:** `Playback/LinkZones`

| Parameter | Type   |
|-----------|--------|
| Zone1     | string |
| ZoneType1 | string |
| Zone2     | string |
| ZoneType2 | string |

**Unlink zone:** `Playback/UnlinkZones`

| Parameter | Type   |
|-----------|--------|
| Zone      | string |
| ZoneType  | string |

**Local zone (client-synthesized).** A client may append a virtual
"Local" zone to the list returned from `Playback/Zones`. This zone is
not a server-known zone — it represents the client device acting as
its own player. When the local zone is active:

- Transport, volume, mute, shuffle, repeat, seek, and queue operations
  are handled entirely by the client's local player.
- The client streams individual files via `File/GetFile` (§4.14)
  rather than issuing `Playback/Play*` commands.
- `Playback/Info` polling is **suspended** (the server has no playback
  to report on for this zone).

### 4.9 Playing Now Queue

**Get queue:** `Playback/Playlist`

| Parameter        | Type   | Description                                |
|------------------|--------|--------------------------------------------|
| Action           | string | `JSON` for JSON array                      |
| Zone             | string | Zone ID                                    |
| ZoneType         | string | `ID`                                       |
| Fields           | string | semi-colon delimited field list            |
| NoLocalFilenames | int    | 1 (filenames meaningless to remote client) |

Use `Action=JSON` and `Fields=Name;Artist;Album` for efficient
retrieval. Response is a JSON array of file objects.

**Play by index:** `Playback/PlayByIndex`

| Parameter | Type   | Description            |
|-----------|--------|------------------------|
| Index     | int    | 0-based index in queue |
| Zone      | string | Zone ID                |

**Play by key:** `Playback/PlayByKey`

| Parameter | Type   | Description                             |
|-----------|--------|-----------------------------------------|
| Key       | string | File key (comma-separated for multiple) |
| Location  | string | `End`, `Next`, or numeric index         |
| Zone      | string | Zone ID                                 |

**Edit queue:** `Playback/EditPlaylist`

| Parameter | Type   | Description             |
|-----------|--------|-------------------------|
| Action    | string | `Move` or `Remove`      |
| Source    | int    | Source index (0-based)  |
| Target    | int    | Target index (for Move) |
| Zone      | string | Zone ID                 |

**Clear queue:** `Playback/ClearPlaylist`

| Parameter | Type   |
|-----------|--------|
| Zone      | string |
| ZoneType  | string |

### 4.10 Library Browse & Search

**Endpoint:** `Files/Search`

| Parameter  | Type   | Description                            |
|------------|--------|----------------------------------------|
| Action     | string | `JSON` for JSON array response         |
| Query      | string | MCWS search expression (see below)     |
| StartIndex | int    | Offset for pagination (0-based)        |
| Limit      | int    | Max results to return (-1 = unlimited) |

**Query syntax:**

Field filters: `[Field]=Value` — exact match on a metadata field.
Contains match: `[Field] contains Value`.
Grouping: `(expr1 OR expr2)`.
Negation: `-[Field]=Value`.

Special modifiers appended to the query string:

- `~sort=[Field]` — sort results by field
- `~limit=N,M,[Field]` — return M items per distinct value of Field, N total groups
- `~n=N` — random selection of N results

**Escaping:** Characters `[ ] ( ) -` are special in MCWS query syntax.
To use them literally in a value, prefix with `/`:
`/[`, `/]`, `/(`, `/)`, `/-`.

**Common queries:**

| Operation        | Query                                                                                          |
|------------------|------------------------------------------------------------------------------------------------|
| Search tracks    | `[Media Type]=Audio ([Name] contains term OR [Artist] contains term OR [Album] contains term)` |
| List all artists | `[Media Type]=Audio ~limit=-1,1,[Artist] ~sort=[Artist]`                                       |
| Albums by artist | `[Media Type]=Audio [Artist]=[artist] ~limit=-1,1,[Album] ~sort=[Album]`                       |
| Album tracks     | `[Media Type]=Audio [Album]=[name] [Artist]=[artist]`                                          |
| Tracks by folder | `[Media Type]=Audio [Filename (path)]="folderPath"`                                            |
| Random albums    | `[Media Type]=[Audio] ~limit=10,-1,[Album],[Filename (path)] ~n=10`                            |

**Client-side filtering:** MCWS field matching (`[Artist]=value`) performs
substring matching. The client must post-filter results for exact matches
when needed (e.g., artist names containing `-`).

**Multi-disc albums:** For albums with `Total Discs > 1` or `Disc # > 1`,
use `FileParent([Filename (path)])` in a `~limit` expression to group by
parent folder, ensuring each disc isn't listed as a separate album.

### 4.11 Album Model

An album is derived from track metadata, not a first-class MCWS entity.

| Field      | Type   | Source                                        |
|------------|--------|-----------------------------------------------|
| name       | string | Track → Album field                           |
| artist     | string | Track → Artist field                          |
| folderPath | string | Track → Filename path (parent for multi-disc) |
| date       | string | Track → Date field (unix timestamp → year)    |

### 4.12 Browse Tree

MCWS exposes a hierarchical browse tree via two endpoints.

**Browse/Children — list child nodes**

**Endpoint:** `Browse/Children`

| Parameter      | Type   | Description                        |
|----------------|--------|------------------------------------|
| ID             | string | Node ID (`-1` for root)            |
| Version        | int    | API version (use `1`)              |
| ErrorOnMissing | int    | `0` = return empty on missing node |

Response is XML with `<Item Name="label">childId</Item>` elements.
Each item represents a child node in the browse hierarchy. The `Name`
attribute is the display label and the element text is the child ID
used for further traversal.

Example (root):

```xml
<Response Status="OK">
  <Item Name="Audio">1</Item>
  <Item Name="Playlists">4</Item>
</Response>
```

**Browse/Files — get tracks at a leaf node**

**Endpoint:** `Browse/Files`

| Parameter | Type   | Description                    |
|-----------|--------|--------------------------------|
| ID        | string | Browse node ID                 |
| Action    | string | `JSON` for JSON array response |

Returns a JSON array of track objects (same schema as `Files/Search`
with `Action=JSON`). Used when a browse node has no children (leaf).

### 4.13 Single-File Lookup

**Endpoint:** `File/GetInfo`

| Parameter | Type   | Description                        |
|-----------|--------|------------------------------------|
| Action    | string | `JSON` for JSON array response     |
| File      | int    | File key                           |
| Fields    | string | use `Calculated` for full metadata |

Returns a one-element JSON array with the same shape as `Files/Search`.
Used to enrich `Playback/Info` (which has only basic fields) with
full metadata for the now-playing screen.

### 4.14 Streaming a File to the Client (Local Playback)

**Endpoint:** `File/GetFile`

| Parameter  | Type   | Description                                    |
|------------|--------|------------------------------------------------|
| File       | int    | File key                                       |
| FileType   | string | `Key`                                          |
| Playback   | int    | `1` (mark as playback for stats)               |
| Conversion | string | `wav` (lossless) or `opus` (lossy)             |
| Quality    | string | `high`, `normal`, or `low`                     |
| Token      | string | auth token (query param, like other endpoints) |

Returns the file as a continuous binary HTTP stream suitable for
direct consumption by a media player (e.g. just_audio, ExoPlayer,
AVPlayer).

This is the basis of **local-zone playback**: rather than telling the
JRiver server to play to one of its zones, the client streams the
file itself and renders audio locally.

### 4.15 BrowseItem Model

| Field | Type   | Source                       |
|-------|--------|------------------------------|
| id    | string | Item element text (child ID) |
| name  | string | Item Name attribute (label)  |

### 4.16 Track Model (Library)

Extended track metadata returned by `Files/Search?Action=JSON&Fields=Calculated`
(or `File/GetInfo?Action=JSON&Fields=Calculated` for a single file).

| Field           | Type   | JSON Key            |
|-----------------|--------|---------------------|
| fileKey         | int    | Key                 |
| name            | string | Name                |
| artist          | string | Artist              |
| album           | string | Album               |
| albumArtist     | string | Album Artist        |
| albumArtistAuto | string | Album Artist (auto) |
| genre           | string | Genre               |
| duration        | float  | Duration            |
| trackNumber     | int    | Track #             |
| discNumber      | int    | Disc #              |
| totalDiscs      | int    | Total Discs         |
| totalTracks     | int    | Total Tracks        |
| imageUrl        | string | Image File          |
| bitrate         | int    | Bitrate             |
| bitDepth        | int    | Bit Depth           |
| sampleRate      | int    | Sample Rate         |
| channels        | int    | Channels            |
| fileType        | string | File Type           |
| filePath        | string | Filename            |
| dateReadable    | string | Date (readable)     |

**Tolerant parsing.** Some fields (e.g. `Key`, `Name`) come back from
MCWS as JSON numbers in some queries and strings in others. Clients
should coerce both shapes to the declared type instead of failing.

**Album artist field — always use `Album Artist (auto)`.**
JRiver populates two related fields per track: the user-set
`Album Artist` (frequently empty) and the computed `Album Artist (auto)`
which is **always populated** (JRiver's own fallback chain through
compilation/album/track artist runs server-side). Clients must use
`albumArtistAuto` everywhere a canonical album artist is needed —
MCWS query construction (`~limit=…,[Album Artist (auto)]`,
`[Album Artist (auto)]=…`), client-side grouping/filtering, the
`Album` domain model, persistence (offline downloads), and UI display.
Never use the raw `albumArtist` for grouping or display: it can be
empty and will fragment albums under "Unknown Artist". When an `Album`
is in hand its album-artist field already holds the auto value (set
when constructing the model from a track).

### 4.17 AlbumGroup (Multi-Disc)

Albums with `Total Discs > 1` are returned by MCWS as one row per disc
when grouped on `[Album]`. Clients should fold rows with the same
`(album, parentFolderPath)` into an `AlbumGroup`:

| Field | Type        | Notes                                        |
|-------|-------------|----------------------------------------------|
| album | Album       | Lead disc (lowest `Disc #`)                  |
| discs | List<Album> | All discs, sorted by `Disc #`                |
| date  | string      | Latest non-empty `dateReadable` across discs |

`isMultiDisc` ⇔ `discs.length > 1`. Tracks under a multi-disc album
should be rendered grouped by `Disc #` with `Disc N of M` headers.

---

## 5. Polling Strategy

MCWS has no push notification mechanism. Clients must poll
for state changes.

### 5.1 Intervals

| Condition                | Endpoint       | Interval   |
|--------------------------|----------------|------------|
| Playback state = playing | Playback/Info  | 1 second   |
| Playback state = paused  | Playback/Info  | 5 seconds  |
| Playback state = stopped | Playback/Info  | 5 seconds  |
| Zone list                | Playback/Zones | 30 seconds |
| Active zone = local      | —              | suspended  |

### 5.2 Change Detection

The `PlayingNowChangeCounter` field (from `Playback/Info`)
increments whenever the Playing Now queue is modified. The
client should:

1. Store the last known counter value.
2. On each poll, compare with the new value.
3. If changed, re-fetch the queue via `Playback/Playlist`.

This avoids polling the full playlist on every tick.

### 5.3 Lifecycle

- **Start polling** when the connection is established and
  authenticated.
- **Pause polling** when the app is backgrounded or loses
  focus (mobile: on pause; desktop: on minimize/hide).
- **Resume polling** when the app returns to foreground.
  Immediately fetch `Playback/Info` on resume to sync state.
- **Stop polling** on disconnect or app termination.

---

## 6. MCWS Response Parsing

### 6.1 XML Format

Most endpoints return:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
<Response Status="OK">
  <Item Name="FieldName">value</Item>
  ...
</Response>
```

Parse into a `Map<string, string>` keyed by the `Name` attribute.
All values are strings — cast to appropriate types per the domain
model.

### 6.2 JSON Format

File queries with `Action=JSON` return a JSON array:

```json
[
  {
    "Key": 123,
    "Name": "Track Name",
    "Artist": "Artist Name",
    ...
  }
]
```

### 6.3 Binary Responses

`File/GetImage` and `File/GetFile` return binary data directly.
Content-Type header indicates the format.

### 6.4 Error Responses

```xml
<Response Status="Failure" />
```

May include additional information in `Item` elements. Treat
any `Status` value other than `"OK"` as an error.

---

## 7. Architecture Guidelines

### 7.1 Layer Structure

Implementations should follow this layering:

```
┌─────────────────────────┐
│         UI Layer         │  Platform-specific widgets/views
├─────────────────────────┤
│      State Layer         │  PlayerStatus, zone list, queue
├─────────────────────────┤
│     Service Layer        │  Polling orchestration, commands
├─────────────────────────┤
│     API Client Layer     │  HTTP calls, XML/JSON parsing
└─────────────────────────┘
```

**API Client** — handles HTTP, auth token injection, response
parsing. Returns domain model types. One method per operation.

**Service** — orchestrates polling, batches state updates,
exposes commands (play, pause, etc.) as simple method calls.
Owns the polling timer and change detection logic.

**State** — holds current `PlayerStatus`, zone list, and queue.
Notifies the UI of changes. Implementation varies by platform
(BLoC, ViewModel, Redux, etc.).

**UI** — renders state, dispatches user actions to the service.

### 7.2 API Client Interface

The API client must expose these methods (pseudocode):

```
interface McwsClient {
  // Connection
  alive() → ServerInfo
  authenticate(username, password) → Token

  // Transport
  play(zoneId)
  playPause(zoneId)
  pause(zoneId, state?)
  stop(zoneId)
  stopAll()
  next(zoneId)
  previous(zoneId)

  // Seek & Volume
  getPosition(zoneId) → int
  setPosition(zoneId, positionMs, relative?)
  getVolume(zoneId) → (float, string)
  setVolume(zoneId, level, relative?)
  mute(zoneId, set)

  // Modes
  getShuffle(zoneId) → ShuffleMode
  setShuffle(zoneId, mode)
  getRepeat(zoneId) → RepeatMode
  setRepeat(zoneId, mode)

  // Info
  getPlaybackInfo(zoneId?) → PlayerStatus
  getImage(fileKey, width?, height?, square?) → bytes

  // Zones
  getZones() → List<Zone>
  setActiveZone(zoneId)
  linkZones(zoneId1, zoneId2)
  unlinkZone(zoneId)

  // Playing Now
  getPlayingNow(zoneId, fields?) → List<PlayingNowItem>
  playByIndex(zoneId, index)
  playByKey(zoneId, key, location?)
  editQueue(zoneId, action, source, target?)
  clearQueue(zoneId)

  // Library
  searchFiles(query, startIndex?, count?) → List<Track>
  searchByFileKey(fileKey) → Track?      // File/GetInfo
  getArtists() → List<string>
  getAlbumsByArtist(artist) → List<Album>
  getAlbumTracks(album) → List<Track>
  getTracksByFolder(folderPath) → List<Track>
  getRandomAlbums() → List<Album>

  // Browse tree
  browseChildren(id) → List<BrowseItem>
  browseFiles(id) → List<Track>

  // Streaming
  buildStreamUrl(fileKey, conversion, quality) → string
}
```

A separate `JRiverLookupApi` client handles the public registry
(`webplay.jriver.com/libraryserver/lookup`). It is not session-scoped,
carries no auth, and must not be routed through the MCWS interceptors.

### 7.3 URL Construction

All requests follow the pattern:

```
http://{host}:{port}/MCWS/v1/{endpoint}?{params}&Token={token}
```

The API client must:

- URL-encode parameter values
- Always include `ZoneType=ID` when passing a zone ID
- Resolve relative image URLs against the base URL

---

## 8. Local Playback Semantics

When the active zone is the client-synthesized local zone (§4.8):

- **Transport** is fully client-side. `Playback/Play*` endpoints are
  not used. The client manages its own queue and playhead.
- **Stream source** is `File/GetFile` (§4.14) per track. URLs include
  `Conversion=<wav|opus>`, `Quality=<high|normal|low>`, and the auth
  `Token`.
- **Quality switching** rebuilds the stream URLs and reloads the
  current track at the saved playhead position.
- **Persistence** of the local queue and playhead is the client's
  responsibility (a server-side DLNA renderer would not survive a
  client restart). Queue state should be persisted in structured
  storage; scalar state (index, position, volume) may live in
  preferences.
- **Now-playing metadata** for the currently-streaming local track is
  not available via `Playback/Info`. The client reads the track
  metadata from its local queue entry (which it already has from the
  library or `File/GetInfo` lookup) and renders it directly.
- **Polling** of `Playback/Info` is suspended while local is active
  (§5.1).

The local zone is conceptually similar to a DLNA renderer that
happens to run inside the same process as the remote-control UI.

---

## 9. Architectural Best Practices

These are cross-platform recommendations distilled from the reference
Flutter implementation (`jrr_f/`). They apply to any client.

1. **Two-layer HTTP.** Keep a thin code-generated HTTP layer (one
   method per MCWS endpoint, no business logic) and a separate domain
   client that builds queries, escapes, parses responses, and maps
   errors to a closed exception union. Tests mock the HTTP layer; the
   rest of the app talks to the domain client.

2. **Closed error union.** Map every transport failure into a finite
   set of variants (`connectionRefused`, `unauthorized`,
   `serverFailure`, `parseError`, `timeout`, `unknown`). UI matches on
   the variant — never on raw HTTP/socket errors.

3. **Functional return types at the repository boundary.** Use a
   `Result`/`Either` type (or equivalent) so callers can't silently
   swallow errors.

4. **Token injection by closure, not capture.** The auth interceptor
   should read the current token at request-time. Rotating the token
   (re-auth, silent reconnect) must not require rebuilding the HTTP
   client.

5. **Explicit `skipAuth` opt-out.** `Authenticate` and `Alive` must
   bypass the auth interceptor declaratively (per-call flag), not via
   in-interceptor branching.

6. **Always send `ZoneType=ID`** alongside any `Zone` parameter — make
   it a default in the HTTP layer.

7. **Tolerate type drift in JSON.** MCWS sometimes returns the same
   field as a number, sometimes as a string. Coerce, don't fail.

8. **Client-side exact filter.** MCWS field equality is substring
   match. For unique-key lookups, post-filter in the client.

9. **Multi-disc grouping is a client concern.** Fold rows on
   `(album, parentFolderPath)` into a single album group; render
   tracks under disc headers.

10. **Logout discards the network client, never the local player.**
    The local zone outlives sessions. Scope your auth-bound state
    accordingly.

11. **Wipe persisted auth tokens on logout.** Token reuse across
    sessions is a footgun.

12. **Schema migrations are append-only.** Never edit a past
    migration; add a new one and bump the schema version.

13. **Redact `Token` in logs.** Always. Logged HARs/cURLs leak.

14. **Top-level error capture.** Install handlers for both framework
    errors and async errors that escape the framework, route them to
    a single logger.

15. **Mini-player in layout flow, not overlay.** Overlays cover modal
    sheets and popup menus.

16. **One mini popup-menu pattern across the app.** Same items
    (Play / Play next / Add to playing now), same icon, same density.

---

## 10. Versioning

This spec follows semantic versioning.

- **v1.0** — playback, zones, playing now (this document)
- **v2.0** — library browse & search, playlist management
- **v3.0** — file metadata, DSP, audio configuration
- **v4.0** — UI design system, multi-platform parity
- **v5.0** — local-zone playback, favorites, access-key lookup

---

## Appendix A: MCWS Endpoint Reference (v1 Scope)

| Endpoint               | Method | Description                    |
|------------------------|--------|--------------------------------|
| Alive                  | GET    | Server health & version        |
| Authenticate           | GET    | Get auth token                 |
| Playback/Play          | GET    | Start playback                 |
| Playback/PlayPause     | GET    | Toggle play/pause              |
| Playback/Pause         | GET    | Set pause state                |
| Playback/Stop          | GET    | Stop playback                  |
| Playback/StopAll       | GET    | Stop all zones                 |
| Playback/Next          | GET    | Next track                     |
| Playback/Previous      | GET    | Previous track                 |
| Playback/Position      | GET    | Get/set position               |
| Playback/Volume        | GET    | Get/set volume                 |
| Playback/Mute          | GET    | Set mute state                 |
| Playback/Shuffle       | GET    | Get/set shuffle mode           |
| Playback/Repeat        | GET    | Get/set repeat mode            |
| Playback/Info          | GET    | Get playback info              |
| Playback/Zones         | GET    | List zones                     |
| Playback/SetZone       | GET    | Set active zone                |
| Playback/LinkZones     | GET    | Link two zones                 |
| Playback/UnlinkZones   | GET    | Unlink a zone                  |
| Playback/Playlist      | GET    | Get playing now queue          |
| Playback/PlayByIndex   | GET    | Play queue item by index       |
| Playback/PlayByKey     | GET    | Play file(s) by key            |
| Playback/EditPlaylist  | GET    | Move/remove queue items        |
| Playback/ClearPlaylist | GET    | Clear the queue                |
| File/GetImage          | GET    | Get file artwork               |
| File/GetInfo           | GET    | Get full metadata for one file |
| File/GetFile           | GET    | Stream a file (local playback) |
| Files/Search           | GET    | Search/browse library          |
| Browse/Children        | GET    | List child browse nodes        |
| Browse/Files           | GET    | Get tracks at browse leaf node |

### Appendix B: External Endpoints (non-MCWS)

| Endpoint                                  | Purpose                                      |
|-------------------------------------------|----------------------------------------------|
| `webplay.jriver.com/libraryserver/lookup` | Resolve JRiver Access Key → server host/port |

17. Treat core metadata as case-insensitive. MCWS tags are often inconsistently cased. Comparison
    and grouping (especially for albums) must use case-insensitive logic to avoid fragmented
    results.
