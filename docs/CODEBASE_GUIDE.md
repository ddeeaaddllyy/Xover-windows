# Xover Codebase Guide

Этот документ нужен как быстрый вход в проект: где что лежит, какие слои за что отвечают и по каким маршрутам проходит основная логика. Он намеренно не документирует текущую систему ошибок и исключений.

## Как Читать Проект

Начинай с этих файлов в таком порядке:

1. `README.md` - что делает приложение и как его запустить.
2. `docs/ARCHITECTURE.md` - общая архитектурная идея и границы слоев.
3. `docs/PROTOCOL.md` - формат WebSocket-сообщений между host и client.
4. `application-java/src/main/java/com/xover/music/application/session/ListeningSessionService.java` - главный use-case facade.
5. `desktop-ui-kotlin/src/main/kotlin/com/xover/music/ui/DesktopApplication.kt` - точка старта Compose Desktop UI.
6. `app/src/main/java/com/xover/music/app/AppModule.java` - где собираются зависимости.

Если нужно понять конкретный пользовательский сценарий, обычно достаточно читать `ListeningSessionService` и соответствующий адаптер: UI, Ktor transport или JavaFX audio.

## Модули

### `domain-java`

Самый внутренний слой. Здесь находятся простые модели состояния, которые можно безопасно показывать в UI и передавать между слоями.

Главные файлы:

- `DeviceRole.java` - роль текущего приложения: idle, host или client.
- `ConnectionStatus.java` - состояние сетевого подключения.
- `PlaybackStatus.java` - состояние проигрывания.
- `PlaylistTrack.java` - один элемент плейлиста.
- `SessionViewState.java` - immutable read model для UI.

Правило слоя: домен не знает про UI, сеть, Dagger, JavaFX, Ktor, JSON и файловую систему.

### `application-java`

Слой сценариев приложения. Он содержит бизнес-процесс синхронного прослушивания и порты, через которые общается с внешним миром.

Главные папки:

- `session` - orchestration: старт host, подключение client, плейлист, play/pause/seek.
- `network` - framework-neutral сетевые порты и сообщения.
- `audio` - framework-neutral аудио-порт.
- `clock` - abstraction над временем.
- `sync` - расчет clock offset для синхронизации.

Главный файл:

- `ListeningSessionService.java`

Этот класс является facade для UI. UI не управляет сессией напрямую, а вызывает методы use-case:

- `startHost(...)`
- `connectToHost(...)`
- `addTrackUrl(...)`
- `selectTrack(...)`
- `play()`
- `pause()`
- `seek(...)`
- `disconnect()`

Сервис хранит текущий `SessionViewState` и уведомляет `SessionObserver`, когда состояние меняется.

### `infrastructure-kotlin`

Сетевой adapter. Реализует `PeerTransportPort` через Ktor.

Главный файл:

- `KtorPeerTransport.kt`

Host mode:

- поднимает embedded Ktor server;
- открывает `GET /health`;
- открывает WebSocket `/sync`;
- хранит активные WebSocket-сессии клиентов;
- отправляет broadcast-команды всем подключенным client-ам.

Client mode:

- создает Ktor `HttpClient`;
- подключается к host WebSocket `/sync`;
- принимает команды от host;
- отправляет time-sync запросы и служебные сообщения.

Важно: application-слой видит только `PeerTransportPort`, а не Ktor.

### `audio-java`

Аудио adapter. Реализует `AudioPlayerPort` через JavaFX MediaPlayer.

Главные файлы:

- `JavaFxAudioPlayer.java` - загружает, проигрывает, ставит на паузу, делает seek.
- `SoundCloudMediaResolver.java` - превращает SoundCloud page URL в playable stream URL, когда это возможно.

Важно: application-слой видит только методы `load`, `play`, `pause`, `stop`, `seek`, `setVolume`, `currentPosition`, `duration`.

### `desktop-ui-kotlin`

Compose Desktop UI. UI тонкий: он показывает `SessionViewState` и отправляет действия пользователя в `ListeningSessionService`.

Файлы разложены физически по папкам, но используют единый Kotlin package `com.xover.music.ui`. Это сделано, чтобы разделить большой UI-файл без изменения поведения и без лишней миграции импортов.

Главные файлы:

- `DesktopApplication.kt` - создает основное окно приложения, хранит локальные UI-переключатели окна и подключает панели.
- `state/ApplicationState.kt` - подписки UI на application state.
- `panel/XoverPanel.kt` - контейнер основного expanded/collapsed UI.
- `panel/Navigation.kt` - верхняя панель и tab strip.
- `panel/SetupTab.kt` - экран host/client подключения.
- `panel/PlayerTab.kt` - плейлист, playback controls, volume.
- `panel/StatusAndMoreTabs.kt` - статус сессии и настройки панели.
- `components/SurfacesAndControls.kt` - переиспользуемые surface, section, buttons, status dot, drag behavior.
- `theme/Theme.kt` - цвета, typography, размеры окна, enum-ы UI tabs.
- `theme/AppIcon.kt` - загрузка иконки приложения из resources.

Этот документ не описывает окно ошибок и текущие классы исключений по просьбе владельца проекта.

### `app`

Composition root и executable entry point.

Главные файлы:

- `XoverMain.java` - запускает приложение.
- `AppModule.java` - Dagger providers.
- `AppComponent.java` - Dagger component.

В этом модуле создаются concrete adapters:

- `JavaFxAudioPlayer`
- `KtorPeerTransport`
- `DesktopApplication`
- scheduler
- clock
- use-case service

Правило: Dagger остается здесь. Остальные слои не должны знать, как именно собирается граф зависимостей.

## Поток Запуска

```text
XoverMain.main()
  -> DaggerAppComponent.create()
  -> AppModule creates ports/adapters/use-cases
  -> DesktopApplication.start()
  -> Compose Window renders current SessionViewState
```

Основное окно создается в `DesktopApplication.start()`. Оно хранит только локальное UI-состояние:

- свернута ли панель;
- закреплено ли окно поверх других;
- прозрачность панели;
- последнее событие, которое нужно показать отдельным окном.

Состояние сессии не принадлежит UI. Оно приходит из `ListeningSessionService`.

## Поток Состояния

```text
User action in Compose
  -> ListeningSessionService method
  -> service updates SessionViewState
  -> SessionObserver receives new state
  -> Compose MutableState is updated on AWT event queue
  -> UI recomposes
```

Ключевой принцип: UI не должен сам решать бизнес-логику. Например, кнопка `Play` просто вызывает `sessionService.play()`. Проверки роли, текущего трека, отправка сетевого сообщения и планирование playback находятся в application-слое.

## Host Сценарий

Пользователь выбирает Host и нажимает `Start Host`.

```text
SetupTab
  -> onHost(advertisedHost, port)
  -> ListeningSessionService.startHost(...)
  -> HostStartupConfig
  -> PeerTransportPort.startHost(...)
  -> KtorPeerTransport starts /health and /sync
  -> state becomes HOSTING or CONNECTED
```

После запуска host:

- `/health` можно проверить через browser или PowerShell;
- `/sync` принимает WebSocket-клиентов;
- host может редактировать playlist;
- host рассылает playlist updates клиентам.

## Client Сценарий

Пользователь выбирает Client и нажимает `Connect`.

```text
SetupTab
  -> onConnect(host, port)
  -> ListeningSessionService.connectToHost(...)
  -> PeerAddress
  -> PeerTransportPort.connect(...)
  -> KtorPeerTransport opens WebSocket /sync
  -> service starts time sync loop
```

Client не редактирует playlist и не управляет синхронным playback. Он принимает команды от host.

## Плейлист

Host добавляет URL в `PlayerTab`.

```text
PlayerTab
  -> onAddTrackUrl(sourceUrl)
  -> ListeningSessionService.addTrackUrl(...)
  -> validate URL
  -> PlaylistTrack.create(...)
  -> state.playlist changes
  -> broadcast PLAYLIST_UPDATED
```

Если это первый трек и приложение host, сервис сразу запускает загрузку текущего трека через `AudioPlayerPort`.

Client получает playlist через protocol message и загружает выбранный URL локально. Аудио байты между компьютерами не передаются.

## Playback

Host нажимает `Play`.

```text
ListeningSessionService.play()
  -> reads current audio position
  -> calculates startAtHostMillis
  -> broadcasts PLAY_AT(positionMillis, startAtHostMillis)
  -> schedules local play
```

Client получает `PLAY_AT` и переводит host timestamp в локальную задержку через `ClockSynchronizer`.

```text
client delay = target host time - estimated current host time
```

После задержки client делает seek на нужную позицию и запускает local playback.

## Clock Sync

Client периодически отправляет `TIME_SYNC_REQUEST`.

```text
Client -> Host: TIME_SYNC_REQUEST(clientSentAtMillis)
Host -> Client: TIME_SYNC_RESPONSE(clientSentAtMillis, hostReceivedAtMillis, hostSentAtMillis)
Client -> ClockSynchronizer.applySample(...)
```

`ClockSynchronizer` использует midpoint calculation. Это не идеальная аудио-синхронизация, но для MVP дает понятную базу без постоянной коррекции drift.

## UI Структура

Основной UI состоит из двух режимов:

- expanded panel;
- collapsed rail.

`DesktopApplication.kt` выбирает режим и передает callbacks в `XoverPanel`.

`XoverPanel.kt` управляет вкладками:

- `SETUP` - host/client setup;
- `PLAYER` - playlist and playback;
- `STATUS` - state diagnostics;
- `MORE` - настройки окна и disconnect.

`SurfacesAndControls.kt` содержит общие строительные блоки. Если нужно поменять внешний вид кнопок или surface, обычно это первый файл, который стоит открыть.

`Theme.kt` содержит цвета, размеры окна и typography. Если нужно изменить визуальный стиль без изменения поведения, начинай там.

## Resources

UI-модуль использует ресурсы из двух мест:

- `desktop-ui-kotlin/src/main/resources/fonts/Xover-text.ttf`
- `assets/icon/XoverIcon.png`

`desktop-ui-kotlin/build.gradle.kts` добавляет `assets` как resource directory, поэтому иконка попадает в classpath UI-модуля.

Окна используют `AppIconResource`, который сейчас указывает на:

```text
icon/XoverIcon.png
```

## Где Менять Типовые Вещи

Добавить новую кнопку или настройку в UI:

```text
desktop-ui-kotlin/src/main/kotlin/com/xover/music/ui/panel
```

Поменять внешний вид кнопок:

```text
desktop-ui-kotlin/src/main/kotlin/com/xover/music/ui/components/SurfacesAndControls.kt
```

Поменять цвета, размеры, шрифт:

```text
desktop-ui-kotlin/src/main/kotlin/com/xover/music/ui/theme/Theme.kt
```

Добавить новый пользовательский сценарий:

```text
application-java/src/main/java/com/xover/music/application/session/ListeningSessionService.java
```

Добавить новый сетевой backend:

```text
application-java/.../network/PeerTransportPort.java
infrastructure-kotlin/.../KtorPeerTransport.kt
```

Добавить новый audio backend:

```text
application-java/.../audio/AudioPlayerPort.java
audio-java/.../JavaFxAudioPlayer.java
```

Поменять WebSocket protocol:

```text
application-java/.../network/PeerMessage.java
application-java/.../network/MessageType.java
infrastructure-kotlin/.../KtorPeerTransport.kt
docs/PROTOCOL.md
```

## Правила Для Будущих Изменений

- Не добавляй framework imports в `domain-java`.
- Не добавляй Compose, JavaFX, Ktor или Dagger в `application-java`.
- UI должен вызывать use-case методы, а не владеть workflow.
- Adapters должны реализовывать ports, а не протекать во внутренние слои.
- Dagger bindings должны оставаться в `app`.
- Если меняется protocol, обновляй `docs/PROTOCOL.md`.
- Если меняется архитектура модулей, обновляй `docs/ARCHITECTURE.md`.

## Быстрые Команды

Полная чистая проверка:

```powershell
.\gradlew.bat clean build
```

Запуск приложения:

```powershell
.\gradlew.bat :app:run
```

Проверка host health endpoint:

```powershell
Invoke-WebRequest http://HOST_VPN_IP:47321/health
```

## Что Здесь Намеренно Не Описано

Текущая система ошибок и исключений не документируется в этом файле. Для нее есть отдельный архитектурный план раскладки: `docs/ERROR_LAYOUT_PROPOSAL.md`.
