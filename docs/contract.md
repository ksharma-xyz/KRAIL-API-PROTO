---
title: Contract — required vs optional
---

# Contract — required vs optional

Every proto field is **nullable on the wire** (proto3's default). The
*contract* layered on top distinguishes two flavours of field, called out
in `// contract:` comments above each declaration.

## Why nullable on the wire

- **Forward + backward compatibility is free.** A server can add a field
  without breaking older clients (they ignore it). A server can stop
  populating a field without crashing newer clients (they get `null`).
  No major bump needed.
- **Schema evolution doesn't gate releases.** BFF and KRAIL ship on
  independent cadences. Strict required-fields would force lock-step
  deploys.

## The two flavours

| Annotation | Wire | Server emits | Examples |
|---|---|---|---|
| `// contract: required` | nullable (proto3 default) | **Always populated. Never `null`.** Substitute a default when upstream data is missing. | `journey_id`, `origin_time`, `transport_mode_lines` |
| `// contract: optional` | nullable | `null` when not applicable. | `total_walk_time` (null when no walking leg), `trip_id` (null when not trackable) |

## Server-side default substitution table

When the BFF doesn't have data for a `contract: required` field, it
substitutes a sensible default. **It must never emit `null`.**

| Type | Default |
|---|---|
| `string` | `""` (empty string) — never null |
| `int32` / `int64` | `0` |
| `float` / `double` | `0.0` |
| `bool` | `false` |
| Enum | `*_UNSPECIFIED` (always field number `0`) |
| Nested message | An instance with all of *its* required fields populated to defaults |
| Repeated | Empty list — never null |

The display layer can render an em-space, "—", or skip a badge if the value
turns out to be empty, but it always receives a *defined* value.

## Enforcement

| Layer | Mechanism |
|---|---|
| Proto schema | Permissive (nullable). Both sides must accept null on the wire. |
| **Server (BFF)** | Unit tests assert no `contract: required` field is null in any response builder. CI fails if a builder leaves a promised field unset. |
| **Client (KRAIL)** | Maps nullable proto into a non-null domain model at the network-layer boundary. A `contract: required` field arriving null is treated as a parse error / kill-switch fallback, never a UI crash. |

The proto says "could be null"; the BFF's CI tests prove the promise holds.
Adding a new field is a one-side change — KRAIL doesn't have to do anything
until it wants to use it.

## How to choose

When adding a new field, ask: **what does the screen do when this field is
absent?**

- "Render a placeholder / fall back to something else" → `contract: optional`.
  The mapper passes the null through; the UI handles it.
- "This is intrinsic to the screen — without it the response is broken" →
  `contract: required`. Make sure the BFF response builder always
  populates it (with a default if upstream data is missing) and add the
  contract-enforcement test.

When in doubt, default to `required`. It's easier to relax `required → optional`
later than to tighten `optional → required` (the former is a no-op release
note; the latter risks NPEs in shipped clients).
