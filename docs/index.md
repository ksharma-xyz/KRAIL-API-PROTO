---
title: KRAIL-API-PROTO
---

# KRAIL-API-PROTO

The shared protobuf contract between [KRAIL-BFF](https://github.com/ksharma-xyz/KRAIL-BFF)
(server) and [KRAIL](https://github.com/ksharma-xyz/KRAIL) (the KMP transit app).

Each consumer pins this repo as a **git submodule at a tag** and runs Wire codegen
locally — there is no published Maven artifact and no generated code in this
repo. Just `.proto` source + automation.

## Start here

| Doc | What it covers |
|---|---|
| [Getting started]({{ "/getting-started" | relative_url }}) | Adding the submodule, Wire snippets for BFF (JVM) and KRAIL (KMP). |
| [Contract: required vs optional]({{ "/contract" | relative_url }}) | The nullability convention. Read this before adding any field. |
| [Versioning]({{ "/versioning" | relative_url }}) | SemVer rules per change type. Tag-based; consumers always pin a tag. |
| [Backward compatibility]({{ "/backward-compatibility" | relative_url }}) | Practical rules + examples for evolving the schema safely. |
| [Testing]({{ "/testing" | relative_url }}) | How each side enforces the contract — `buf` in this repo, unit tests in BFF, mappers in KRAIL. |
| [Releasing]({{ "/releasing" | relative_url }}) | Cutting a new version with the release workflow. |

## Current version

Look at [version.txt](https://github.com/ksharma-xyz/KRAIL-API-PROTO/blob/main/version.txt)
in the repo, or the [latest release](https://github.com/ksharma-xyz/KRAIL-API-PROTO/releases/latest).

## Layout

```
proto/
└── api/
    ├── trip.proto              JourneyList + screen-shaped trip results
    ├── stops_dataset.proto     Versioned stops dataset distributed via Releases
    └── routes_dataset.proto    Versioned routes dataset distributed via Releases
```

Future shared types (e.g. `LatLng`, `TransitLine` if reused across schemas)
will move to `proto/core/`. For now everything lives under `proto/api/`.

## License

[Apache 2.0](https://github.com/ksharma-xyz/KRAIL-API-PROTO/blob/main/LICENSE).
