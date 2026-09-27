# Repository Guidelines

## Project Structure & Module Organization

Xover is a desktop application for synchronized music listening over a private VPN. Its six Gradle modules follow Clean Architecture:

- `domain-java`: framework-free enums and immutable read models.
- `application-java`: use cases, ports, session orchestration, and clock synchronization.
- `audio-java`: JavaFX playback and media resolvers.
- `infrastructure-kotlin`: Ktor HTTP/WebSocket transport and serialization.
- `desktop-ui-kotlin`: Compose Desktop presentation.
- `app`: Java entry point and Dagger composition root.

Sources live under each module's `src/main/java` or `src/main/kotlin`. Tests currently reside in `application-java/src/test/java`. Shared icons are in `assets/`; fonts are in `desktop-ui-kotlin/src/main/resources/fonts/`. Consult `docs/ARCHITECTURE.md` and `docs/PROTOCOL.md` before changing module boundaries or messages. Keep frameworks in adapters and session logic in the application layer.

## Build, Test, and Development Commands

Use JDK 21 and the checked-in Gradle wrapper from the repository root. PowerShell examples:

- `.\gradlew.bat clean build`: compile, test, and package all modules.
- `.\gradlew.bat :application-java:test`: run the current unit test suite.
- `.\gradlew.bat :app:run`: launch the desktop app; Gradle stays attached until it closes.
- `.\gradlew.bat :app:releaseObfuscated`: create the obfuscated ZIP in `app/build/distributions/`.

CI runs `clean build --no-daemon` on Windows with JDK 21.

## Coding Style & Naming Conventions

Follow existing Java/Kotlin style: four-space indentation, same-line opening braces, `PascalCase` types, and `camelCase` methods and variables. Compose UI functions use `PascalCase`. Packages start with `com.xover.music`; UI subfolders intentionally share `com.xover.music.ui`. Preserve Kotlin trailing commas where used. No formatter or linter is configured; match surrounding code.

## Testing Guidelines

Use JUnit 5 (Jupiter), with classes named `*Test` and descriptive methods such as `estimatesHostOffsetFromMidpoints`. Mirror production packages under `src/test/java`. Add regression tests for changed application behavior; no numeric coverage threshold is configured. For playback or transport changes, exercise two app instances using the README's host/client workflow, including play, pause, and seek.

## Commit & Pull Request Guidelines

History uses version subjects such as `1.8.4` and occasional descriptions such as `1.4.0 ui update`; no consistent Conventional Commits scheme is evident. Use concise, descriptive subjects. PRs should explain the behavior change, link relevant issues, report validation, and include screenshots for UI changes. Update architecture or protocol documentation when affected.

## Configuration

Use `.env.example` for local token configuration. Keep `.env` and credentials uncommitted.
