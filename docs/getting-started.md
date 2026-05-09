---
title: Getting started
---

# Getting started

Add KRAIL-API-PROTO to your repo, point Wire at it, generate Kotlin classes.

## §1 · Add as a git submodule

Both KRAIL-BFF and KRAIL pin this repo at a tag (never a branch). One-time
setup, from the consumer repo root:

```bash
git submodule add https://github.com/ksharma-xyz/KRAIL-API-PROTO.git krail-api-proto
git -C krail-api-proto checkout v0.1.0
git add .gitmodules krail-api-proto
git commit -m "chore: add krail-api-proto submodule pinned to v0.1.0"
```

After that, fresh clones need `--recurse-submodules`:

```bash
git clone --recurse-submodules https://github.com/ksharma-xyz/KRAIL-BFF.git
# Or, if already cloned:
git submodule update --init --recursive
```

CI workflows must pass `submodules: true` to `actions/checkout`:

```yaml
- uses: actions/checkout@v4
  with:
    submodules: true
```

## §2 · Wire codegen — KRAIL-BFF (JVM)

In `server/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.wire)
}

wire {
    kotlin {
        // Read .proto files from the submodule instead of an in-tree directory.
        sourcePath { srcDir("$rootDir/krail-api-proto/proto") }
    }
}
```

Generated Kotlin classes appear under `build/generated/source/wire/` with
the same package paths as declared in the `.proto` (`app.krail.bff.proto`,
`app.krail.bff.proto.data`).

## §3 · Wire codegen — KRAIL (KMP)

KRAIL needs the classes to be visible from `commonMain` so both Android and
iOS compile against them. In a dedicated `:io:bff-api` module:

```kotlin
// io/bff-api/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.wire)
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.wire.runtime)
        }
    }
}

wire {
    kotlin {
        targets { commonMain }
        sourcePath { srcDir("$rootDir/krail-api-proto/proto") }
    }
}
```

The Wire plugin will generate KMP-compatible Kotlin under
`build/generated/source/wire/commonMain/`. iOS targets reuse the same
generated source — Wire's KMP runtime handles the platform actuals.

## §4 · Bumping to a new version

When KRAIL-API-PROTO cuts a new tag (e.g. `v0.2.0`):

```bash
# In the consumer repo:
git -C krail-api-proto fetch --tags
git -C krail-api-proto checkout v0.2.0
git add krail-api-proto
git commit -m "chore(proto): bump krail-api-proto to v0.2.0"
```

Both KRAIL-BFF and KRAIL run a daily `proto-bump.yml` workflow that opens
this PR automatically. Humans review and merge — never auto-merge, since
schema changes can shift UI in subtle ways (a new enum variant might need
UI handling).
