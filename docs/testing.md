---
title: Testing
---

# Testing

Three layers of enforcement, each owned by a different repo.

## §1 · This repo (KRAIL-API-PROTO)

What we check here:

- **`buf lint`** — proto style + correctness (field numbering monotonicity,
  enum zero-value naming, package conventions). Runs on every PR via
  `.github/workflows/ci.yml`.
- **`buf build`** — every proto parses, all imports resolve. Catches
  typos and broken cross-file references.
- **`buf breaking --against` previous tag** — wire-format compatibility.
  Blocks PRs that break JSON or binary wire compat unless they carry a
  `major-bump` label.
- **`version.txt` sanity check** — the file's version is never lower
  than the most recent tag.

What we deliberately don't check here:

- Codegen across consumer toolchains. KRAIL-BFF and KRAIL each run their
  own Wire codegen; if anything regresses on one platform but not the other,
  the consumer's CI catches it on its `proto-bump.yml` PR. Adding a Wire
  smoke test here is on the v0.2.0+ wishlist but isn't worth the CI
  complexity for v0.1.0.

## §2 · KRAIL-BFF — server-side contract enforcement

The BFF is the only writer; it owes consumers a non-null value for every
`contract: required` field. Enforcement is unit tests.

For each response message there's a "contract enforcement" test:

```kotlin
// server/src/test/kotlin/.../JourneyListContractTest.kt
class JourneyListContractTest {
  @Test
  fun `every contract-required field is populated even when upstream data is missing`() {
    val response = TripPlanService(stubNswClientReturningEmpty()).planTrip(...)

    response.journeys.shouldNotBeEmpty()
    response.journeys.forAll { journey ->
      journey.timeText.shouldNotBeNull()
      journey.originTime.shouldNotBeNull()
      journey.originUtcDateTime.shouldNotBeNull()
      journey.destinationTime.shouldNotBeNull()
      journey.destinationUtcDateTime.shouldNotBeNull()
      journey.travelTime.shouldNotBeNull()
      journey.transportModeLines.shouldNotBeNull()
      journey.legs.shouldNotBeNull()
      journey.totalUniqueServiceAlerts.shouldNotBeNull()

      // Genuinely-optional fields may be null — don't assert on them:
      // journey.platformText
      // journey.platformNumber
      // journey.totalWalkTime
      // journey.departureDeviation
    }
  }
}
```

The test fixture deliberately starves the response builder of upstream
data. If a builder relies on upstream data without a default, the test
fails — the builder must substitute a default per the
[server-side default table]({{ "/contract" | relative_url }}#server-side-default-substitution-table).

These tests are part of the BFF's regular `./gradlew :server:test` run; no
separate workflow.

## §3 · KRAIL — client-side mapper enforcement

KRAIL is the only reader. It owes the UI a non-null domain model.

At the network-layer boundary (the `:io:bff-api` module's mapper), every
nullable proto field gets an explicit decision:

```kotlin
// :io:bff-api/.../mapper/JourneyMapper.kt
fun JourneyCardInfo.toDomain(): Either<ParseError, Journey> {
    // contract: required fields — null is a bug. Bail out, log, fall back.
    val timeText = timeText ?: return ParseError.MissingRequired("timeText").left()
    val originTime = originTime ?: return ParseError.MissingRequired("originTime").left()
    // ... rest of required fields ...

    // contract: optional fields — null is a normal absent value.
    val platform = platformText  // may be null; UI hides the platform badge.
    val totalWalk = totalWalkTime  // may be null; UI hides the walk-time row.

    return Journey(
        timeText = timeText,
        originTime = originTime,
        platform = platform,
        totalWalk = totalWalk,
        // ...
    ).right()
}
```

`ParseError` is logged with the request's correlation ID (so you can
correlate with the BFF logs) and surfaces as a degraded response — the
screen shows a fallback "trip not available" state, never crashes.

KRAIL's per-screen unit tests cover both branches:

- Happy path: every required field populated → mapper succeeds.
- Sad path: each required field nulled out in turn → mapper returns
  `ParseError.MissingRequired(<field>)`.

## §4 · End-to-end check

Once a quarter (or whenever it feels stale), an end-to-end test:

1. Run the live BFF against NSW.
2. Capture a real response per endpoint.
3. Run KRAIL's mapper against the captured response.
4. Assert no `ParseError`s.

This catches subtle "contract drift" — fields the BFF has been quietly
not populating because the upstream data is missing in some edge case
nobody anticipated. The dashboard at `docs/tools/api-tester.html` in
KRAIL-BFF (use Compare-with-NSW mode) is the UI for capturing responses.
