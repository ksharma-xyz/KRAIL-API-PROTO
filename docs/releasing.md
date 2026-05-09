---
title: Releasing
---

# Releasing a new version

Two paths: interactive (recommended) and manual.

## §1 · Interactive — workflow_dispatch

1. Open <https://github.com/ksharma-xyz/KRAIL-API-PROTO/actions/workflows/release.yml>.
2. Click **Run workflow**.
3. Pick `bump_type`: `patch`, `minor`, or `major`. See [versioning]({{ "/versioning" | relative_url }})
   for which to choose.
4. The workflow:
   - Reads `version.txt`, computes the next version.
   - Pushes a branch `release/vX.Y.Z` with `version.txt` bumped.
   - Opens a PR titled `chore(release): vX.Y.Z`.
5. Review the PR. Merge it once CI is green.
6. Push the tag:
   ```bash
   git fetch
   git tag vX.Y.Z origin/main
   git push origin vX.Y.Z
   ```
7. The release workflow runs on the tag push and creates a GitHub Release
   with auto-generated notes (titles of PRs since the previous tag) and a
   `proto/` tarball asset.

## §2 · Manual — for the impatient

Skips the bump-PR step. Use only for solo work where review isn't needed.

```bash
echo "X.Y.Z" > version.txt
git commit -am "chore(release): bump to vX.Y.Z"
git push
git tag vX.Y.Z
git push origin vX.Y.Z
```

The same release workflow runs and produces the same Release artifact.

## §3 · After the release

Within ~24 hours, both consumer repos open a `proto-bump.yml` PR
automatically:

- KRAIL-BFF — opens a PR that updates the submodule SHA + runs the BFF's
  test suite. The BFF maintainer reviews + merges.
- KRAIL — same shape on the app side.

If your release adds a `contract: required` field, expect the BFF's
auto-bump PR to **fail** until the BFF's response builder is updated to
populate the field — that's the [contract-enforcement test]({{ "/testing" | relative_url }}#2--krail-bff--server-side-contract-enforcement)
doing its job. Add the builder change to the same PR before merging.

## §4 · Release notes

Auto-generated from the PR titles since the previous tag. To get clean
release notes, write good PR titles:

- ✅ `feat: add quiet_carriage to JourneyCardInfo`
- ✅ `fix: remove unused departure_deviation field`
- ❌ `update protos`
- ❌ `wip`

The `release.yml` workflow uses `gh release create --generate-notes`,
which categorises by PR labels:

| Label on PR | Goes into release notes section |
|---|---|
| `breaking-change`, `major-bump` | "💥 Breaking changes" |
| `enhancement` | "✨ Enhancements" |
| `bug` | "🐛 Bug fixes" |
| `documentation` | "📝 Documentation" |
| (none) | "Other changes" |

## §5 · What if I tagged the wrong commit?

```bash
# Delete the tag locally + remote.
git tag -d vX.Y.Z
git push origin :refs/tags/vX.Y.Z

# Then delete the GitHub Release via the UI or:
gh release delete vX.Y.Z --yes

# Now re-tag and push.
```

If consumers have already pulled the bad tag, ask them to fetch + re-checkout
to the new SHA. (This is rare for fresh tags but check the consumer
auto-bump PRs before deleting.)
