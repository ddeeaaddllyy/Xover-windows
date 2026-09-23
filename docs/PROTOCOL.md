# Xover Protocol

Transport is WebSocket JSON over Ktor.

Default host port: 47321

Routes: 
GET /health
WS  /sync

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
  "hostSentAtMillis": 0,
  "playlist": [
    {
      "id": "uuid",
      "title": "soundcloud.com / track-name",
      "sourceUrl": "https://soundcloud.com/artist/track-name"
    }
  ],
  "currentTrackIndex": 0
}
```

All fields are present to keep parsing simple. Message type decides which fields matter.

## Message Types

### `PLAYLIST_UPDATED`

Sent by host when the playlist changes, the current track changes, or a peer connects.

Relevant fields:

- `playlist`
- `currentTrackIndex`

Clients load the selected `sourceUrl` themselves. The host sends only metadata and links, not audio bytes.

### `TRACK_SELECTED`

Legacy single-track message. New clients use `PLAYLIST_UPDATED`.

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
