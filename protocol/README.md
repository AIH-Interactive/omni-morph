# OmniMorph Cloud v1 protocol release

B0 schema release: 2026-10-17. This directory is the canonical source release;
`omnm-cloud/protocol` vendors an identical copy because the server is a separate
repository. Never edit the vendor copy independently. `verify-release.ps1` checks
both copies and the frozen SHA-256 before either build. Generated source belongs
in build output, not in either source repository.

## Toolchain and transport

- Java: JDK 25, protobuf Gradle plugin 0.9.5, protoc/protobuf-java 4.31.1.
- Rust: prost/prost-build 0.14.3, protoc-bin-vendored 3.2.0; Cargo.lock freezes
  transitive versions. Both generators consume `omnimorph/cloud/v1/cloud.proto`.
- Control HTTP: GET `/api/protobuf/v1/capabilities`, POST `/api/protobuf/v1/rpc`;
  `application/x-protobuf`, one Envelope per body, no concatenated envelopes.
- WS: `/ws/protobuf/v1`, subprotocol `omnimorph.cloud.v1`, binary message = one
  Envelope. Transport reassembly has the same byte limit as decoding.
- Files: authorized relative `/api/protobuf/v1/resources/...` paths, ordinary
  PUT/GET HTTP bodies, `application/octet-stream`. Never files in WS or Envelope.
- Authentication: existing Minecraft identity verification; token in headers or
  Auth body, never URL. Scoped expiring session returned by AuthResult. Hello
  does not authenticate. Envelope owner fields never confer write authority.

Zero enum values are unspecified and must fail validation where required.
Deleted fields/enum values must become reserved; incompatible changes need a new
major version. Unknown fields are tolerated. An unknown/absent body or enum has
a controlled INVALID_MESSAGE response. Request IDs correlate errors and results;
operation IDs provide account-scoped idempotency, including conflicting retries.

## Frozen limits

The server may negotiate lower limits, never higher than local client ceilings.
Budgets are aggregated by authenticated account across protocols and sessions.

| Budget | Ceiling |
| --- | --- |
| Complete encoded Envelope | 65,536 bytes |
| Encoded YSM state message | 32,768 bytes |
| Messages / total bytes each second | 32 / 262,144 |
| Subscriptions / queued bytes per connection | 128 / 262,144 |
| Repeated state entries per partition / nesting depth | 512 / 16 |
| Concurrent uploads per account / grant lifetime | 2 / 120 seconds |
| Event tail count / TTL | 64 / 30 seconds |
| Snapshot TTL | 300 seconds |
| Model raw bytes | configured existing avatar size, independent of control cap |
| Client decompressed resource budget | existing YSM package limits; never relaxed |
| Heartbeat interval / inactivity timeout | 20 / 60 seconds |

Allocation, repeated fields and decompression must be bounded before accepting
business input. NaN/Infinity, invalid indices and sequence conflicts fail closed.
Slow consumers must resnapshot or receive an explicit loss error for events.

## Stable errors

Numeric assignments are defined only in the schema. Keys use
`backend.protobuf.error.<lowercase enum name>`. Error details never contain tokens,
private model contents, source expressions or keys.

| Error | Retry policy / action |
| --- | --- |
| INVALID_MESSAGE | no; reject malformed fields/body/enum |
| UNSUPPORTED_VERSION | no; terminate negotiation |
| UNAUTHENTICATED | reauthenticate; no business dispatch |
| FORBIDDEN | no; owner/capability authorization failed |
| NOT_FOUND | no; missing/deleted resource |
| CONFLICT | refetch revision, use a new operation ID |
| LIMIT_EXCEEDED | retry only after budget recovers |
| SNAPSHOT_REQUIRED | owner must publish full baseline |
| CURSOR_EXPIRED | subscriber must refetch snapshot |
| AUTHORITY_UNAVAILABLE | no; entity binding is not advertised |
| OFFLINE | wait for publisher; do not simulate model on server |
| UNSUPPORTED_CAPABILITY | no; prompt explicit protocol selection |
| INTERNAL | retry with same operation ID if safe |

## Resource and equipment projection

Raw SHA-256 is over the uploaded distribution bytes. Content and schema hashes
are separate 32-byte identities supplied/validated through the client pipeline.
The server never decrypts or executes YSM. Old Figura `.moon` bytes and returned
hash remain unchanged when imported into the new resource index.
The legacy hash is SHA-256 of standard base64 text of the raw file, not raw
SHA-256. `legacy_figura_hash` preserves this identity separately. Legacy self
profile first probes its one-shot temporary slot, then the persistent slot; the
baseline freezes this behavior without changing it during protocol setup.

There is one authoritative primary equipment state with optimistic revision.
Equipping Figura updates both primary and Figura compatibility slots. Equipping
YSM clears compatibility unless `retain_figura_compatibility` explicitly keeps a
previous independent Figura slot. Old profiles expose only that Figura slot, or
their existing `equipped: []` representation when absent. Clearing primary clears
both slots. Deleting a resource clears every equipment reference to its revision
and writes a tombstone. Both protocol connections receive one logical change;
YSM bytes never appear in old Figura resource responses.

YSM publisher session/generation is exclusive; observer sessions coexist. Each
snapshot has all partitions, deltas use message/optional presence. Omitted means
preserve, present empty means clear. Recovery orders snapshot/watermark before
tail events and never replays historical one-shot effects. Missing baseline,
sequence gaps, expired tails, stale model/revision/session/generation all require
explicit recovery or rejection.

## Legacy baseline and scope

`vectors.tsv` freezes C2S auth/Ping/sub/unsub and all S2C encoders. Protobuf Hello,
opaque Figura Ping and present-empty YSM delta must encode to the exact same
vectors in Java and Rust; tests also decode the opposite producer's bytes.
`figura-nbt.hex` is a synthetic gzip-compressed empty NBT root, never a private
model. HTTP tests check its byte/hash identity and legacy equipped projection.

Old routes retain `/api//auth`, `/api//assets`, `/api/`, `/api/equip`,
`/api/:uuid`, `/api/:uuid/avatar`, `/api/avatar`, `/api/limits`, `/api/version`,
`/api/motd`, `/ws`, `/internal` and existing `/api/v1`. B0 HTTP tests exercise the
production legacy route constructor using a random loopback port, isolated data
directory and synthetic authenticated identity. B1 expands this baseline to
upload/equip/delete/Ping lifecycle and multiple sessions. Existing upload buffering
and token/session lifecycle require B1 validation, not assumptions of correctness.

## Requirement-to-test ownership

Frozen implementation locations: server `src/protocol/` (generated bindings and
wire validation), `src/service/{session,resource,equipment,event,ysm}.rs` (shared
business rules), `src/storage/mod.rs` (versioned persistent records),
`src/api/protobuf/` (HTTP/WS adapters); existing `src/api/figura/`, `src/auth/`,
`src/state/` and `src/main.rs` receive only required integration changes.
Client locations are `backend2/protocol/` (protocol interface and adapters),
`backend2/` and `backend2/websocket/` (existing entry points and lifecycle),
`config/Configs.java`, `config/ConfigType.java` if required by stable enum
serialization, existing language JSON, `model/ysm/sync/` (typed state mapping),
and the existing Avatar transfer/cache callers. No renderer changes are planned.

| Requirement | Required test gate |
| --- | --- |
| Common schema, field layout, presence, legacy frames, old routes/hash | B0 networkProtocolSmokeTest + Rust protocol tests |
| Old resource/Ping/equip/delete, session lifetime | B1 legacy integration + multi-session tests |
| Real HTTP/WS, auth/Hello, Figura cross-protocol bridge | B2 socket integration and adversarial protocol tests |
| Raw Figura/plain/encrypted YSM, no file envelopes, atomic bounded transfer | B3 resource integration with request capture and failure injection |
| Publisher authority, snapshots/deltas/events, persistent restart/TTL/cursors | B4 recovery and durable-store tests |
| Both client adapters, upload/download/sync, epochs and original loaders | B5 adapter integration + YSM payload/sync/crypto corpus gates |
| Default/reset/migration/unknown values, switch cancellation, offline loading | B6 configuration matrix and connection lifecycle tests |
| All section 7 URI/debug combinations, redirects, retries, revocation, logs | B7 endpoint security matrix and captured-log tests |
| Migration idempotence, interrupted commit, rollback, full mixed clients | B8 migration/rollback + full automated matrix + recorded game validation |

No later gate is satisfied by B0 vectors. Model corpus is dynamically enumerated
at execution. Entity authority needs an external authoritative source and remains
disabled. GUI/game/manual evidence must be recorded honestly and separately.

## Deployment and migration checklist

1. Record paired client/backend commits, schema digest, binaries and hashes.
2. Back up configuration, legacy `.moon`/temp resources, accounts and metadata.
3. Run versioned additive import; record source bytes/hash, never overwrite old
   compatibility files with YSM. Repeated import must produce no extra revisions.
4. Test failed/interrupted upload and index commit recovery in a temporary store.
5. Test restart with publisher state, schema version, TTL, watermark and tombstones.
6. Deploy dual protocol backend to test environment; verify old clients first.
7. Verify mixed two-client Figura/YSM HTTP transfers, Ping and recovery.
8. Publish default-Protobuf client only after capability endpoint is deployed.
9. Rollback: disable new routes or run old server against preserved legacy files;
   retain additive protocol/YSM records. User can explicitly choose Figura Cloud.
10. Public deployment and real game acceptance are separate evidence; local builds
    never count as production rollout or real two-client PASS.

## B4 YSM recovery and legacy M7 bridge

`YSM_RECOVERY` is advertised for authenticated player owners. Entity-qualified
owners have no authoritative world binding and return AUTHORITY_UNAVAILABLE;
entity authority is not advertised. Publisher identity binds the authenticated
connection scope, UUID and generation. Retired publishers cannot reclaim state.
Equipment revision changes and deletion revoke the stored publisher atomically.

Recovery persists a materialized snapshot, original full-snapshot timestamp,
event watermark and bounded tail in the atomic manifest. Deltas do not renew the
300-second full-snapshot TTL. Resume requires contiguous retained frames; expired
or missing history requests a fresh snapshot. Historical one-shot events never
replay from a snapshot, and queued events expire after 30 seconds. Offline owners
are not simulated. Slow consumers reconnect/resnapshot rather than silently drop
events. Snapshot refresh requests to owner connections are throttled per owner.

Extended Figura connections declare `x-omnimorph-models: ysm`; valid inbound M7
also identifies support. The adapter translates typed state to/from the existing
Java M7 `YSMS`/`YSMP` formats inside the reserved legacy Ping. Ordinary Ping bytes
remain opaque. Legacy combined state/events are committed atomically and split
into ordered canonical frames; their source cursor and fingerprint deduplicate
retries across restart. An imported `avatar` resource lacks a runtime schema:
the first authorized publication binds its schema, which cannot change for the
same resource revision/content. The resource descriptor itself is immutable.

The old M7 format has no snapshot-request or structured-error message. Recovery
for an unchanged legacy owner therefore waits for its existing periodic full
snapshot; no new legacy control message is invented. A new owner can receive the
typed SnapshotRequest. Unsupported upstream Figura clients receive no YSM bytes.

`m7-vectors.tsv` contains synthetic vectors exported by the production Java codec.
Compile `protocol/tools/M7VectorExporter.java` with YsmSyncMessage, YsmSyncCodec
and YsmSyncPackets, then run `M7VectorExporter --verify protocol/m7-vectors.tsv`.
Rust tests decode these vectors and compare re-encoded bytes/CRC/fragments.
