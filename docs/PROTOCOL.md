# Xover Protocol

Transport is WebSocket JSON over Ktor. This revision uses **protocol version 2**;
all peers must update together. Incoming messages without `protocolVersion: 2`
are rejected. Outbound frames are bounded to 64 KiB and queued in order per peer.

Default host port: 47321

Routes: 
GET /health
WS  /sync

## Message Shape

```json
{
  "protocolVersion": 2,
  "loadId": "track-load-uuid",
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
Renaming a track updates its title in the playlist while keeping its ID, source URL, and `loadId`.
Clients display the new title without reloading or interrupting playback.

`loadId` identifies one loading attempt, including reselecting the same track. The host
generates it; all readiness and playback messages refer to it. Reordering the playlist
preserves it. A new identifier makes clients reload even if the track ID is unchanged.

### `TRACK_READY` / `TRACK_FAILED`

Clients send `TRACK_READY` with the current `loadId` only after the audio engine is ready
and an initial matching clock-sync response has been applied. The host associates this
message with the actual WebSocket sender, not an identity supplied in JSON.

Play is queued until local readiness and acknowledgements from every currently connected
listener are present. Old load IDs, unknown peers, and duplicate acknowledgements do not
advance the barrier. `TRACK_FAILED` cancels pending playback and pauses the session;
reselecting the track starts a new loading attempt. Detailed local errors stay on that device.

Pause, track changes, and disconnects cancel pending starts. A new listener joining during
playback pauses the group until it is ready. A 120-second readiness timeout cancels the request;
late readiness does not automatically restart playback. Play explicitly retries the wait.

### `TRACK_SELECTED`

Legacy single-track message. New clients use `PLAYLIST_UPDATED`.

### `TIME_SYNC_REQUEST`

Sent by client periodically.

Relevant fields:

- `nonce`
- `clientSentAtMillis`

### `TIME_SYNC_RESPONSE`

Sent by host only to the requesting client. Clients validate the nonce and original send
timestamp against their own pending requests; reconnecting clears clock samples.

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

`loadId` must match the prepared track. Only after the readiness barrier succeeds does the
host send `PLAY_AT`, normally 750 ms ahead. Host and clients schedule the same host timestamp.
The client rejects the command if its track or initial clock sample is not ready.

This is a playback-readiness barrier, not a full-file download guarantee or a distributed
transaction. A peer may still lose connectivity or exhaust its streaming buffer after
acknowledging readiness. Continuous drift correction and buffering recovery are future work.

### `PAUSE`

Sent by host.

Relevant fields:

- `positionMillis`

Client pauses and seeks to the host position.
The message carries `loadId`; commands for previous loads are ignored.

### `SEEK`

Sent by host.

Relevant fields:

- `positionMillis`

Client seeks to the host position.
The message carries `loadId`. Seeking during playback first pauses the group, updates the
position, and schedules another coordinated start.
