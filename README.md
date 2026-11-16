# ripp-server

Tracker for [RIPP](https://github.com/rankchef/ripp-client), a peer-to-peer file sharing program. The client splits a file into chunks and exchanges those chunks directly with other peers. This server does not store or transfer file bytes. It remembers what a rip is, which connected peers can seed it, and it carries the WebRTC signaling those peers need to connect.

The server listens on port **7070**. Rip metadata is stored in SQLite at `data/ripp.db`. Who is currently seeding, and the WebSocket used to reach them, live only in memory and disappear when the process stops.

## What the tracker keeps

A **rip** is the description of one file: its name, size, chunk size, and one SHA-256 hash per chunk. Creating a rip returns an id. Peers who already have the whole file announce that id. Peers who want the file look the rip up, then ask who can seed it.

A **seeder id** is assigned by the server when a WebSocket opens. It identifies that connection. The server does not send the id to the peer who owns it. Other peers learn it from `GET /rips/{id}/seeders`, and signaling messages carry it in `from` when the tracker forwards them.

Only a peer that has the entire file announces. The announce is a list of rip ids, not a list of chunks.

## HTTP

`POST /rips/create` stores a rip and returns `201` with the generated id. The body is JSON. Each hash is Base64 and must be 32 bytes. The number of hashes must equal the number of chunks (`fileSize / chunkSize`, rounded up; an empty file has zero hashes). `fileName` is required, trimmed, and at most 255 characters. `chunkSize` must be positive.

```json
{
  "fileName": "movie.mp4",
  "fileSize": 1048576,
  "chunkSize": 524288,
  "chunkHashes": [
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
  ]
}
```

`GET /rips` returns the ten most recently created rips, newest first, without the chunk hashes:

```json
[
  {
    "id": "3f2a7c2e-4b19-4d5a-9c1e-0a6b8d44e211",
    "fileName": "movie.mp4",
    "fileSize": 1048576,
    "chunkSize": 524288,
    "totalChunks": 2
  }
]
```

`GET /rips/{id}/seeders` returns the ids of peers who have announced that rip and are still connected. An unknown rip is `404`. A path that is not a UUID is `400`. A rip with nobody seeding it is `200` and `[]`.

```json
["9b1c2d3e-4f56-4789-a012-3456789abcde"]
```

## WebSocket

Connect to `ws://<host>:7070/ws`. The server assigns a seeder id, stores the socket under that id, and starts pinging so idle connections are not dropped by routers.

Every client message is JSON with a `type` field: `announce`, `offer`, or `answer`. A body that is not one of those, or that fails to parse, gets:

```json
{"type":"error","message":"Malformed message"}
```

Closing the socket removes that peer from every rip immediately. The same happens if the socket is already dead when the server tries to write to it.

### Announce

A peer sends the full set of rips it can seed. Sending again replaces the previous set and starts the lifetime over. An empty list means the peer is connected and seeding nothing. Every id must already exist. If any id is missing, nothing is stored and the peer gets an error. A valid announce gets no reply.

```json
{"type":"announce","rips":["3f2a7c2e-4b19-4d5a-9c1e-0a6b8d44e211"]}
```

```json
{"type":"error","message":"Rip not found: [3f2a7c2e-4b19-4d5a-9c1e-0a6b8d44e211]"}
```

An announcement lasts **one hour** unless the peer announces again or disconnects. After the hour, `GET /rips/{id}/seeders` no longer includes that peer. The server does not retry or queue announcements.

### Offer

The peer that wants the file is the offerer. It creates a WebRTC peer connection and a data channel, gathers candidates, and sends the resulting SDP. `to` is a seeder id from `GET /rips/{id}/seeders`.

```json
{
  "type": "offer",
  "to": "9b1c2d3e-4f56-4789-a012-3456789abcde",
  "ripId": "3f2a7c2e-4b19-4d5a-9c1e-0a6b8d44e211",
  "sdp": "v=0\r\n..."
}
```

The tracker checks that the rip exists, that `to` is a live seeder of that rip, and that the sender is not addressing itself. It then writes this to the seeder's socket. `from` is the sender's seeder id. The original `to` is left off because the seeder is the recipient.

```json
{
  "type": "offer",
  "from": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "ripId": "3f2a7c2e-4b19-4d5a-9c1e-0a6b8d44e211",
  "sdp": "v=0\r\n..."
}
```

The offer is forwarded once and not stored. If the seeder is not in the swarm or the socket is already gone, the sender gets `{"type":"error","message":"Seeder is not available"}`. A missing field, a self-offer, or an unknown rip gets its own error message.

### Answer

The seeder applies the offer, gathers its own candidates, and sends the answer SDP. `to` is the `from` value on the offer it received.

```json
{"type":"answer","to":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","sdp":"v=0\r\n..."}
```

The tracker checks that `to` and `sdp` are present and that the sender is not answering itself. It does not look up a rip: the recipient is the downloader and does not have to be seeding. It forwards:

```json
{"type":"answer","from":"9b1c2d3e-4f56-4789-a012-3456789abcde","sdp":"v=0\r\n..."}
```

`from` tells the downloader which offer this answer completes, when several offers are in flight. If that peer is gone, the seeder gets `{"type":"error","message":"Peer is not available"}`. The answer is not stored.

After the answer is delivered, the tracker is finished. ICE, DTLS, and the chunk transfer run directly between the two clients over the WebRTC data channel. The server never sees whether the connection succeeds and never relays file data.

## Run

Requires JDK 24.

```text
mvn compile exec:java -Dexec.mainClass=mk.ripp.Main
```

Or run `mk.ripp.Main` from the IDE. The process serves HTTP and the WebSocket on port 7070 and creates `data/ripp.db` on startup.
