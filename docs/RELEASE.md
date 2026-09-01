# Release Process

The maintainer-facing release procedure is **centralized in the workspace repo**:
[`../../workspace/workflows/release-process.md`](../../workspace/workflows/release-process.md).

**This repo-specific supplement applies here** — srcmorph is a 3-module Maven reactor
(`srcmorph`, `srcmorph-cli`, `srcmorph-maven-plugin`, all released together from one parent pom
at the same version), not a single-module project, so a few steps differ from the generic
procedure:

1. **Bump to the release version** before tagging:
   ```bash
   mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
   ```
   (No `-Dexcludes` needed — the reactor's former 4th module, a relocation-stub POM for the
   retired `net.ladenthin:llamacpp-ai-index-maven-plugin` coordinates, was removed after its one
   `1.0.4` release; see `CLAUDE.md`.)
2. **Commit, push, merge to `main`.**
3. **Tag the merge commit and push the tag:**
   ```bash
   git tag vX.Y.Z <merge-commit-sha>
   git push origin vX.Y.Z
   ```
4. **Manually trigger the release**: GitHub → Actions → *Publish* → **Run workflow**, select the
   `vX.Y.Z` tag, and check `publish_to_central`. `publish-release` only runs when **both** the ref
   is a `v*` tag **and** the workflow was dispatched with that flag — a plain tag push or a push to
   `main` alone does not publish anything.
5. **Verify** the release actually reached Central before considering it done: check
   `https://repo1.maven.org/maven2/net/ladenthin/<artifact>/X.Y.Z/` for each of the four artifacts
   — `srcmorph-parent`, `srcmorph`, `srcmorph-cli`, `srcmorph-maven-plugin`; the parent POM is easy to
   forget precisely because it carries no code, and a consumer who cannot resolve it cannot resolve any
   of the other three — and confirm the CI run's "Deploy release" step succeeded (not just
   "completed").
6. **Bump `main` forward** to the next `-SNAPSHOT` version (same `versions:set` command) so `main`
   never again sits at a version matching a published release.

See `CHANGELOG.md` for the format of the release notes entry that should exist for `X.Y.Z` before
tagging.
