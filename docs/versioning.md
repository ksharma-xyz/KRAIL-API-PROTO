---
title: Versioning
---

# Versioning

SemVer at the **package** level, encoded in git tags. Consumers always pin
a tag — never a branch, never `latest`.

## Bump table

| Change | Bump |
|---|---|
| Add a `contract: optional` field | minor |
| Add a `contract: required` field | minor *(but consumers must populate it on the next deploy)* |
| Remove a field | minor (mark `reserved`; document in release notes) |
| Rename a field | **major** (source-breaking even when wire-compatible) |
| Reuse a field number | **never** |
| Change a field's type | **never** (add a new field, deprecate the old) |
| Add a new enum variant | minor (old clients see `*_UNSPECIFIED`) |
| Remove an enum variant | **major** |
| Add a new oneof variant | minor |
| Remove an oneof variant | **major** (clients exhaust-match) |

## What "minor" means in practice

Most schema work lands as a minor bump. Adding `contract: required` fields
is technically additive on the wire (older clients still parse the
response, ignoring the new field), so it's a minor bump on this repo's
side. The downstream commitment is:

1. The BFF response builder **must populate** the new field by the next
   deploy (default substitution per [contract.md]({{ "/contract" | relative_url }})).
2. The BFF's contract-enforcement unit tests catch builders that miss it.
3. KRAIL can adopt the new field whenever convenient.

## What "major" means

Major bumps require an explicit `major-bump` label on the PR (see
[Backward compatibility]({{ "/backward-compatibility" | relative_url }})).
They imply:

- Coordinate release timing with the BFF + KRAIL maintainers.
- The BFF may need to ship a "compat shim" version that emits both old
  and new shape during the deprecation window.
- Both consumer release notes call out the rename / removal.

## Tag format

```
vMAJOR.MINOR.PATCH
```

The first published tag is `v0.1.0`. We're in pre-1.0 territory (`0.x`),
where the bump table above still applies but consumers should treat each
minor as potentially containing one breaking change clearly listed in the
release notes.

`v1.0.0` lands when the schema stabilises enough that breaking changes
become rare enough to warrant the stronger SemVer guarantee.
