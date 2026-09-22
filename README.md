# Xover

Xover is a desktop MVP for synchronized music listening with a friend over a private VPN network such as Hamachi, Radmin VPN, or Porthole. It does not use a central server: one desktop app instance becomes the host, and the second app connects directly to it by VPN IP and port.

## Current MVP

- Java domain and application logic.
- Kotlin desktop UI.
- Ktor embedded HTTP/WebSocket transport.
- Dagger composition root.
- JavaFX MediaPlayer audio adapter.
- Host builds a URL playlist; each client loads the selected track URL locally.
- Host and client synchronize control messages through `/sync`.
- Host controls play, pause, and seek.
- Client estimates host clock offset with lightweight ping samples.

Scala is intentionally not used yet. The project has no heavy compute problem at this stage; adding Scala now would increase complexity without improving the first working version. A separate compute module can be added later for audio analysis, waveforms, beat detection, or DSP.

## Requirements

- JDK 21.
- Windows firewall must allow the selected port for the host app.
- Both users must be in the same VPN network.

## Run

Build everything:

```powershell
.\gradlew.bat clean build
```

Start the desktop app:

```powershell
.\gradlew.bat :app:run
```

If this command keeps running after the window appears, that is normal. Gradle is attached to the desktop process until the app window is closed.

## Friend Test

1. Host opens the app.
2. Host opens `Player` and adds SoundCloud or direct audio URLs to the playlist.
3. Host enters their VPN IP, for example a Hamachi or Radmin VPN IPv4 address.
4. Host keeps the default port `47321` or chooses another open port.
5. Host clicks `Start Host`.
6. Client opens another app instance on another PC.
7. Client enters the host VPN IP and the same port.
8. Client clicks `Connect`.
9. After the client loads the selected URL, host controls `Play`, `Pause`, and seek.

Quick connection check from the client PC after the host clicks `Start Host`:

```powershell
Invoke-WebRequest http://HOST_VPN_IP:47321/health
```

The expected response is:

```text
Xover host is alive
```

If this check fails, the problem is outside the app protocol: wrong VPN IP, closed firewall port, different VPN networks, or the host app is not running.

## Modules

| Module | Language | Responsibility |
| --- | --- | --- |
| `domain-java` | Java | Pure enums and immutable read models. No frameworks. |
| `application-java` | Java | Use cases, ports, session orchestration, clock synchronization. |
| `audio-java` | Java | JavaFX implementation of `AudioPlayerPort`. |
| `infrastructure-kotlin` | Kotlin | Ktor host/client transport and protocol serialization. |
| `desktop-ui-kotlin` | Kotlin | Compose Desktop UI. Thin presentation layer. |
| `app` | Java | Dagger graph and executable application entry point. |

## Architecture

The dependency direction follows Clean Architecture:

```text
app
 +- desktop-ui-kotlin
 +- infrastructure-kotlin
 +- audio-java
 +- application-java
     +- domain-java
```

Inner layers do not depend on Ktor, JavaFX, Compose, or Dagger. Frameworks are adapters around application ports.

## Developer Docs

- `docs/CODEBASE_GUIDE.md` explains where the main code lives and how the core flows work.
- `docs/ARCHITECTURE.md` summarizes module boundaries.
- `docs/PROTOCOL.md` documents the WebSocket protocol.
- `docs/ERROR_LAYOUT_PROPOSAL.md` proposes a future error package layout without moving code.

## Format Support

Audio is currently handled by JavaFX MediaPlayer. MP3 is the primary target for the MVP. Actual codec support can depend on the operating system and installed media stack. If broad codec support becomes important, replace `audio-java` with a VLCJ adapter while keeping the same `AudioPlayerPort`.

## Next Practical Milestones

- Add drift correction while playback is running.
- Add multi-client support with per-client time-sync responses.
- Add a connection QR/code screen for faster pairing.
- Add host-side firewall/port diagnostics.
- Add VLCJ audio backend.
- Add packaging with `jpackage`.
