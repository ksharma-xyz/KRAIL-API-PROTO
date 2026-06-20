# KRAIL-API-PROTO

Shared protobuf contract between KRAIL-BFF (server) and KRAIL (iOS/Android KMP client).
Both repos pin this as a git submodule. Never drift them — both must reference the same tag.

## Release flow (end-to-end)

```
make proto changes on a branch
→ update version.txt (e.g. echo "0.4.3" > version.txt)
→ open PR → CI runs buf lint + breaking-change check
→ merge PR to main
→ auto-tag.yml fires automatically → creates + pushes tag v0.4.3
→ release.yml release-on-tag fires → GitHub Release created with notes
→ proto-bump.yml fires in KRAIL-BFF → opens bump PR
→ proto-bump.yml fires in KRAIL     → opens bump PR
→ review + merge both bump PRs
→ BFF and KMP client both pinned to v0.4.3 ✓
```

**Critical rule:** always update `version.txt` when making proto changes. The auto-tag
workflow keys off `version.txt` — if you forget, no tag is created, and proto-bump in
BFF/KRAIL won't detect the change.

## Versioning

- `version.txt` is the single source of truth. It must match the latest tag exactly.
- Follows semver: patch for additive/optional field changes, minor for new messages,
  major for wire-breaking changes (field number reuse, type changes, renames).
- The `buf breaking` CI job enforces wire compatibility against the previous tag.

## File layout

```
proto/
  api/          # Screen-shaped messages consumed by the KMP client
    trip.proto
    track.proto
    departures.proto
    parking.proto
  data/         # Dataset schemas (GTFS-derived, distributed as binary blobs)
    routes_dataset.proto
    ...
version.txt     # Current version (no "v" prefix, e.g. "0.4.2")
```

## What CI checks

- `buf lint` — style and proto correctness
- `buf build` — all imports resolve, no parse errors
- `buf breaking` — no wire-incompatible changes vs previous tag
- `version-check` — version.txt never lower than latest tag

## What NOT to do

- Never push a commit to main without updating `version.txt` if proto files changed.
- Never manually `git tag` without first updating `version.txt` — the release-on-tag
  job validates they match and will fail otherwise.
- Never pin KRAIL-BFF and KRAIL to different proto tags — they share the wire contract.
