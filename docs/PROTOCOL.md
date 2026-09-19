# Xover Protocol

Transport is WebSocket JSON over Ktor.

Default host port:

```text
47321
```

Routes:

```text
GET /health
GET /track
WS  /sync
```

## Message Shape

```json
{
  "type": "PLAY_AT",
  "nonce": "uuid",
  "trackName": "",
  "mediaUri": "",
  "positionMillis": 0,
  "startAtHostMillis": 0,
  "clientSentAtMillis": 0,
  "hostReceivedAtMillis": 0,
  "hostSentAtMillis": 0
}
```

All fields are present to keep parsing simple. Message type decides which fields matter.

## Message Types

### `TRACK_SELECTED`

Sent by host when a peer connects.

Relevant fields:

- `trackName`
- `mediaUri`

The client loads `mediaUri`, usually `http://host-vpn-ip:47321/track`.

### `TIME_SYNC_REQUEST`

Sent by client periodically.

Relevant fields:

- `nonce`
- `clientSentAtMillis`

### `TIME_SYNC_RESPONSE`

Sent by host.

Relevant fields:

- `nonce`
- `clientSentAtMillis`
- `hostReceivedAtMillis`
- `hostSentAtMillis`

The client calculates:

```text
clientMidpoint = clientSentAtMillis + (clientReceivedAtMillis - clientSentAtMillis) / 2
hostMidpoint = hostReceivedAtMillis + (hostSentAtMillis - hostReceivedAtMillis) / 2
offset = hostMidpoint - clientMidpoint
```

### `PLAY_AT`

Sent by host.

Relevant fields:

- `positionMillis`
- `startAtHostMillis`

Host and client both schedule playback for the same host timestamp.

### `PAUSE`

Sent by host.

Relevant fields:

- `positionMillis`

Client pauses and seeks to the host position.

### `SEEK`

Sent by host.

Relevant fields:

- `positionMillis`

Client seeks to the host position.
