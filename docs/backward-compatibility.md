---
title: Backward compatibility
---

# Backward compatibility

Practical rules for evolving the schema without breaking shipped clients.
Downstream of the [contract: nullability default]({{ "/contract" | relative_url }}).

## The rules

1. **Never reuse a field number.** Mark removed fields with `reserved 7;`
   and add a comment. CI catches violations via `buf breaking`.
2. **Never change a field's type.** Add a new field with a new number;
   deprecate the old in a release note; remove later.
3. **Never change a field's `optional`-ness.** All scalar fields stay
   nullable (proto3 default) for the life of the schema.
4. **Enums:** always include `*_UNSPECIFIED = 0`. New variants are
   additive; old clients see `*_UNSPECIFIED` for unknown values.
5. **Oneofs:** adding new variants is fine; removing them is a major
   bump (clients exhaust-match).

## Examples

### Add a new field — minor bump

Trivial case. Old clients ignore the new field; new clients see it when
talking to the new BFF.

```proto
message JourneyCardInfo {
  string time_text = 1;
  // ... existing fields ...

  // contract: optional — added in v0.3.0; rendered as a "Quiet carriage"
  // badge on supported services.
  optional QuietCarriageInfo quiet_carriage = 14;
}
```

### Remove a field — minor bump

```proto
message JourneyCardInfo {
  string time_text = 1;
  // contract: optional — removed in v0.5.0, replaced by `quiet_carriage_info`.
  reserved 13;
  reserved "departure_deviation";

  // ... rest of the fields ...
}
```

The `reserved` clause prevents the field number being reused later. CI
fails if a future PR tries to add a field at number 13 or named `departure_deviation`.

### Rename a field — major bump

This is the painful one. Even though the wire is compatible (same number,
different name = same bytes), every consumer that referenced the field by
name needs source updates.

The recommended workflow:

1. PR adds the new name as a sibling, marks the old one `[deprecated = true]`.
2. Minor bump. Both fields exist; consumers gradually migrate to the new
   name on their own cadence.
3. After ≥ 2 consumer release cycles, a follow-up PR removes the old name.
4. Major bump.

```proto
// Step 1 (v0.6.0 — minor):
message ServiceAlert {
  string id = 1;
  // contract: required
  string headline = 6;
  // contract: required — deprecated in v0.6.0, removed in v1.0.0. Use `headline`.
  string subtitle = 2 [deprecated = true];
  // ...
}
```

```proto
// Step 3 (v1.0.0 — major):
message ServiceAlert {
  string id = 1;
  reserved 2;
  reserved "subtitle";
  // contract: required
  string headline = 6;
  // ...
}
```

### Change a field's type — never

`int32 stop_count = 5;` cannot become `int64 stop_count = 5;` even though
they encode similarly. Add a new field at a new number:

```proto
reserved 5;            // old int32 stop_count
reserved "stop_count"; // optional but helpful

// contract: required — replaces deprecated int32 stop_count = 5
int64 stop_count_v2 = 11;
```

This is rare. Type choices should be stable from day one — int64 unless
you're certain int32 suffices.

## Enforcement

`buf breaking --against` runs in CI on every PR and blocks anything that
breaks JSON or wire-binary compatibility. Major bumps require an explicit
`major-bump` label on the PR.

The check is run against the **previous git tag** — so the breaking
window is "between releases," not "between commits." This lets multiple
internal commits land on `main` cumulatively before the next release;
only the final aggregate change has to be wire-compatible (or labelled
major).
