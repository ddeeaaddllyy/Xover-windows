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

### `desktop-ui-kotlin`

Compose Desktop UI. It observes `SessionViewState` and forwards user actions to `ListeningSessionService`.

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
Host -> Client: PLAY_AT(positionMillis, startAtHostMillis)
Host: schedules local playback
Client: converts host timestamp into local delay and schedules playback
```

The current MVP uses scheduled starts and clock offset estimation. Continuous drift correction is the next important improvement.

## Extension Rules

- Add a new network implementation by implementing `PeerTransportPort`.
- Add a new audio backend by implementing `AudioPlayerPort`.
- Add new use cases in `application-java`; do not put workflow logic in UI.
- Add external libraries only in adapter modules or `app`, unless they are pure domain utilities.
