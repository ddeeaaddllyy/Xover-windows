# Architecture

Xover is structured around a small core and replaceable adapters.

## Core Principles

- Domain objects are framework-free.
- Application services depend on ports, not implementations.
- Infrastructure adapts external systems to ports.
- Dagger is used only in the composition root.
- UI is thin and calls use-case methods instead of owning session logic.

## Layer Responsibilities

### `domain-java`

Contains state that is safe to show and pass around:

- `DeviceRole`
- `ConnectionStatus`
- `PlaybackStatus`
- `PlaylistTrack`
- `SessionViewState`

No networking, audio, serialization, DI, or UI dependencies are allowed here.

### `application-java`

Owns the workflow:

- host a session;
- manage a URL playlist;
- connect to a host;
- send synchronized play, pause, and seek commands;
- estimate host/client clock offset;
- expose immutable `SessionViewState` updates to the UI.

Important classes:

- `ListeningSessionService`
- `ClockSynchronizer`
- `AudioPlayerPort`
- `PeerTransportPort`
- `TrackTitleLookup` for optional background title enrichment; manual renames remain authoritative.

### `infrastructure-kotlin`

Implements `PeerTransportPort` with Ktor.

Host mode:

- starts an embedded Ktor server;
- accepts WebSocket peers at `/sync`.
- broadcasts playlist links and playback commands.

Client mode:

- connects to the host WebSocket;
- receives playlist metadata and sync commands;
- loads the selected track URL locally;
- sends time-sync probes.

### `audio-java`

Implements `AudioPlayerPort` with JavaFX MediaPlayer. The application layer sees only `load`, `play`, `pause`, `seek`, and position/duration data.
It also supplies the HTTP metadata adapter used to read titles from supported track pages.

### `desktop-ui-kotlin`

Compose Desktop UI. It observes `SessionViewState` and forwards user actions to `ListeningSessionService`.
The saved panel opacity is applied to the composed panel as one offscreen layer.
The window itself stays interactive, while the desktop remains visible through the
panel and inactive controls cannot show through it.

The expanded panel and compact player remain composed across collapse/expand transitions.
Each is measured at its own fixed size while a single animation drives window size and
panel opacity, avoiding narrow constraints on the expanded form and retaining input state.
Native taskbar minimization uses Compose `WindowState.isMinimized`.
The compact player exposes host play/pause, next-track and stop actions, plus local mute
with volume restoration. Manual Next advances without wrapping; reaching the end of a
track advances automatically and wraps to the first track after the last one. Stop
cancels pending playback and rewinds using existing pause/seek protocol messages.

The UI is physically split into folders by responsibility:

- `state` for Compose subscriptions to application state;
- `panel` for the main window tabs and panel structure;
- `components` for shared UI building blocks;
- `theme` for colors, typography, sizes, and app icon loading.

These files intentionally keep the same Kotlin package to make the split mechanical and low-risk.

### `app`

Dagger composition root:

- creates the scheduler;
- binds ports to concrete adapters;
- starts the desktop UI.

## Synchronization Flow

```text
Client -> Host: TIME_SYNC_REQUEST(clientSentAt)
Host -> Client: TIME_SYNC_RESPONSE(clientSentAt, hostReceivedAt, hostSentAt)
Client: offset = hostMidpoint - clientMidpoint

Host: user clicks Play
Client -> Host: TRACK_READY(loadId), after media preparation and clock sync
Host: waits for local readiness and every connected listener
Host -> Client: PLAY_AT(positionMillis, startAtHostMillis)
Host: schedules local playback
Client: converts host timestamp into local delay and schedules playback
```

The current MVP uses scheduled starts and clock offset estimation. Continuous drift correction is the next important improvement.

## Playback Readiness and Preferences

`ListeningSessionService` owns a per-load readiness barrier. Host-created load identifiers
flow through the transport and audio callbacks, so stale callbacks and acknowledgements
cannot start a different track. Each listener must prepare its media and synchronize its
clock before acknowledging readiness. Session mutations and scheduled playback share the
service monitor; JavaFX position and duration reads use cached values to avoid waiting on
the FX thread while holding that monitor.

Ktor preserves outbound order with bounded per-session queues and provides the actual peer
identity to application callbacks. Connection generations suppress obsolete reconnect events.
UI session commands run on one background dispatcher, keeping server startup and shutdown
off the event thread. Non-secret UI preferences use an atomic properties file in the user
profile; debounced background writes and a final close flush preserve edits.

## Local Liked Tracks

`LikedTracksService` in the application layer validates saved URLs and uses the
`LikedTrackRepository` port. `SqliteLikedTrackRepository` in infrastructure implements
the port with Xerial SQLite JDBC. Dagger supplies the database path
`${user.home}/.xover/liked-tracks.db`; no database server or account is needed.
The `liked_tracks` table stores a unique source URL and display title, ordered newest
first. Saving the same URL updates its title without adding a duplicate. Prepared
statements handle values, a busy timeout supports multiple app instances, and every
operation closes its connection. Database failures are reported without replacing data.

The UI loads and updates bookmarks on its background action dispatcher. Hearts and the
right-click menu share the same persisted state; listeners may like tracks locally.
The Liked tab adds a saved URL through the existing playlist action, so session roles,
validation and synchronization still apply. Favorites do not alter the wire protocol,
and removing a favorite does not remove a track from the current playlist.

## Extension Rules

- Add a new network implementation by implementing `PeerTransportPort`.
- Add a new audio backend by implementing `AudioPlayerPort`.
- Add new use cases in `application-java`; do not put workflow logic in UI.
- Add external libraries only in adapter modules or `app`, unless they are pure domain utilities.

For protocol-level message details, read `docs/PROTOCOL.md`.
