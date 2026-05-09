# KRAIL-API-PROTO

The shared protobuf contract between [KRAIL-BFF](https://github.com/ksharma-xyz/KRAIL-BFF)
(server) and [KRAIL](https://github.com/ksharma-xyz/KRAIL) (the KMP transit app).

This repo holds the `.proto` source. Each consumer runs Wire codegen against it
to produce its own Kotlin classes — there is no published Maven artifact and
no generated code in this repo.

[![ci](https://github.com/ksharma-xyz/KRAIL-API-PROTO/actions/workflows/ci.yml/badge.svg)](https://github.com/ksharma-xyz/KRAIL-API-PROTO/actions/workflows/ci.yml)

---

## Layout

```
proto/
└── api/
    ├── trip.proto              JourneyList + screen-shaped trip results
    ├── stops_dataset.proto     Versioned stops dataset distributed via Releases
    └── routes_dataset.proto    Versioned routes dataset distributed via Releases
```

Future shared types (e.g. `LatLng`, `TransitLine`) will move to `proto/core/`
when more than one schema needs them. For now everything lives under `proto/api/`.

---

## §1 · Consuming as a git submodule

Both KRAIL-BFF and KRAIL pin this repo as a submodule at `krail-api-proto/`
in their respective repo roots, **at a tag** (never a branch).

```bash
# One-time, from the consumer repo root:
git submodule add https://github.com/ksharma-xyz/KRAIL-API-PROTO.git krail-api-proto
git -C krail-api-proto checkout v0.1.0
git add .gitmodules krail-api-proto
git commit -m "chore: add krail-api-proto submodule pinned to v0.1.0"
```

Subsequent bumps are mechanical:

```bash
git -C krail-api-proto fetch --tags
git -C krail-api-proto checkout vX.Y.Z
git add krail-api-proto
git commit -m "chore(proto): bump krail-api-proto to vX.Y.Z"
```

Both consumer repos run a daily `proto-bump.yml` workflow that opens this
PR automatically; humans review and merge.

### Wire snippet — KRAIL-BFF (JVM)

```kotlin
// server/build.gradle.kts
wire {
    kotlin {
        // Read .proto files from the submodule.
        sourcePath { srcDir("$rootDir/krail-api-proto/proto") }
    }
}
```

### Wire snippet — KRAIL (KMP)

```kotlin
// io/bff-api/build.gradle.kts (or wherever the proto module lives)
wire {
    kotlin {
        targets { commonMain }
        sourcePath { srcDir("$rootDir/krail-api-proto/proto") }
    }
}
```

---

## §2 · Versioning

SemVer at the **package** level, encoded in git tags. Consumers always pin a
tag.

| Change | Bump |
|---|---|
| Add a contract-optional field | minor |
| Add a contract-required field | minor *(consumers must populate it on the next deploy)* |
| Remove a field | minor (mark `reserved`; document in release notes) |
| Rename a field | major (source-breaking even when wire-compatible) |
| Reuse a field number | never |
| Change a field's type | never (add a new field, deprecate the old) |
| Add a new enum variant | minor (old clients see `*_UNSPECIFIED`) |
| Remove an enum variant | major |
| Add a new oneof variant | minor |
| Remove an oneof variant | major (clients exhaust-match) |

`buf breaking --against` runs in CI on every PR and blocks anything that
breaks JSON or wire-binary compatibility. Major bumps require an explicit
`major-bump` label on the PR.

---

## §3 · Contract-required vs genuinely optional

Every field is nullable on the wire (proto3 default). The **contract**
distinguishes two kinds of field, documented in proto comments above each
declaration:

| Annotation | Wire | Server emits | Examples |
|---|---|---|---|
| `// contract: required` | nullable (proto3 default) | **Always populated. Never `null`.** Substitute a sensible default when upstream data is missing. | `journey_id`, `origin_time`, `transport_mode_lines` |
| `// contract: optional` | nullable | `null` when not applicable. | `total_walk_time` (null when no walking leg), `trip_id` (null when not trackable) |

### Server-side default substitution table

When the BFF is missing data for a `contract: required` field, it substitutes:

| Type | Default |
|---|---|
| `string` | `""` (empty string) — never null |
| `int32` / `int64` | `0` |
| `float` / `double` | `0.0` |
| `bool` | `false` |
| Enum | `*_UNSPECIFIED` (always field number `0`) |
| Nested message | An instance with all of *its* required fields populated to defaults |
| Repeated | Empty list — never null |

### Enforcement

| Layer | Mechanism |
|---|---|
| Proto schema | Permissive (nullable). Both sides must accept null. |
| **Server (BFF)** | Unit tests assert no `contract: required` field is null in any response builder. CI fails if a builder leaves a promised field unset. |
| **Client (KRAIL)** | Maps nullable proto to non-null domain models at the network-layer boundary. A `contract: required` field arriving null is treated as a parse error / kill-switch fallback, never a UI crash. |

The proto says "could be null"; the BFF's CI tests prove the promise holds.
Adding a new field is a one-side change — KRAIL doesn't have to do anything
until it wants to use it.

---

## §4 · Backward compatibility rules

Practical rules, downstream of the nullability default:

1. **Never reuse a field number.** Mark removed fields with `reserved 7;` and
   add a comment. CI catches violations.
2. **Never change a field's type.** Add a new field with a new number;
   deprecate the old in a release note; remove later.
3. **Never change a field's `optional`-ness.** All scalar fields stay
   nullable (proto3 default) for the life of the schema.
4. **Enums:** always include `*_UNSPECIFIED = 0`. New variants are additive;
   old clients see `_UNSPECIFIED` for unknown values.
5. **Oneofs:** adding new variants is fine; removing them is a major bump
   (clients exhaust-match).

---

## §5 · CI / GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | every PR + push to main | `buf lint`, `buf build`, `buf breaking --against` previous tag, version.txt sanity. |
| `release.yml` | tag push (`v*.*.*`) **or** workflow_dispatch | Validates tag matches `version.txt`, runs lint + build, creates a GitHub Release with auto-generated notes, publishes a `proto/` tarball. Manual dispatch opens a PR that bumps `version.txt`; merging + tagging triggers the release. |

---

## §6 · Releasing a new version

```bash
# 1. Open the bump PR via the Actions tab → release.yml → Run workflow.
#    Pick patch / minor / major. The workflow opens release/vX.Y.Z.
# 2. Review the PR, merge it.
# 3. Push the tag:
git fetch
git tag vX.Y.Z origin/main   # or the release branch's merge commit
git push origin vX.Y.Z
# 4. The release workflow runs on the tag push and creates the GitHub Release.
```

Manual fallback (skip the bump-PR step) if you need to release fast:

```bash
echo "X.Y.Z" > version.txt
git commit -am "chore(release): bump to vX.Y.Z" && git push
git tag vX.Y.Z && git push origin vX.Y.Z
```

---

## §7 · License

[Apache 2.0](LICENSE). Matches the KRAIL app repo.

---

## §8 · For maintainers — adding a new proto

1. Add the new file under `proto/api/<name>.proto`. Include `// contract:`
   annotations on every field per §3.
2. Open a PR. CI runs lint + breaking-change check.
3. Once merged: trigger `release.yml` with `bump_type: minor`.
4. After the release, expect daily `proto-bump.yml` workflows in
   KRAIL-BFF and KRAIL to open their submodule-bump PRs within 24h.
   Coordinate with whoever needs the new field on the BFF side to make
   sure the BFF response builder is updated before the bump merges.
