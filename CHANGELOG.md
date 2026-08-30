# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The release procedure (prompt template and step-by-step instructions) lives in [docs/RELEASE.md](./docs/RELEASE.md).

---

## [Unreleased]

### Added
- **`seed` model-definition knob** (`InferenceParameters.withSeed`, default `-1`). Upstream draws a
  random seed per request, so with a non-zero temperature every generation samples differently: a
  re-index of an unchanged file, or any `force=true` run, produced a different `.ai.md` body than the
  one already committed. That sat awkwardly next to the project's own "deterministic indexing"
  principle. Setting a seed makes the body stable for a given machine and configuration, which turns a
  re-index into a reviewable diff. Forwarded only when `>= 0`, so an unconfigured run is byte-identical
  to before. Documented deliberately as *not* bit-reproducibility: llama.cpp results move with thread
  count, batch size and backend, so the seed pins the sampling, not the arithmetic.

### Fixed
- **`AiMdHeaderSupport.shouldWrite`'s change-detection chain was not covered by a single test.** The
  seven-way header comparison that decides whether a `.ai.md` is regenerated could be replaced
  wholesale with `return false;` and the class's tests stayed green — verified, not inferred. Two of
  them wrote the fixture document with a header but no body, so the method returned at its blank-body
  guard before the comparison ever ran. Since checksum-driven regeneration is a stated design
  principle, dropping any disjunct would have meant stale files silently never regenerating, with
  nothing failing. Both tests now write a body, and a parameterized case covers each compared field
  (`h`/`x`/`title`/`c`/`d`/`g`/`a`) plus the unchanged-header negative. Re-running the same mutation
  now fails 9 of 15 tests, and removing a single disjunct fails 2.
- **No `execute()` verified that it honours the skip flag.** `shouldSkip()` itself was well tested, but
  a mojo that never called it would have passed every one of those tests — and would have loaded a
  model and written files under `-Dsrcmorph.skip=true`. All four goals now assert it, `CalibrateMojo`
  included (it previously had no test constructing it at all).

### Changed
- `AiGenerationConfig.getStopStrings()` no longer declares a `@Nullable` return. The field is
  initialised to an empty list and the setter normalises `null`, so the documented "or `null` if not
  set" case was unreachable; the getter now matches its sibling `getDrySequenceBreakers()`. The two
  null-guards this made dead in `LlamaCppJniConfigFactory` were dropped with it — the real guard lives
  in `LlamaCppJniConfig`'s constructor, and is now pinned directly by a new `LlamaCppJniConfigTest`.
- **PIT gate widened from 632 to 717 mutations, all killed at `mutationThreshold` 100.** Newly gated:
  `provider.LlamaCppJniConfig`, `config.AiConditionGroup` (both already at 100% with no new test —
  `TODO.md` had listed them as needing "careful fixtures", which was stale), `document.AiMdDocumentCodec`
  and `indexer.AiIndexPlan` (survivors killed here). `document.AiMdHeaderCodec` is documented as
  permanently out: its last two survivors are equivalent mutants in the colon-position guard, unkillable
  through the public API. Coverage was also added for the CLI's `.js`/`.yml` extension aliases and all
  six `CCommand` dispatch arms, the plugin's `buildConfiguration()`/`messageOf()`, and `GenerateEngine`'s
  missing-subtree and unknown-`factsKey` paths.

## [1.2.0] - 2026-08-29

### Added
- **Four new model-definition knobs, wired through to `net.ladenthin:llama` 5.1.0's new
  `ModelParameters` setters.** They are configured exactly like the existing `gpuLayers` /
  `mainGpu` / `devices` knobs — as elements of an `<aiDefinition>` in the Maven plugin, or as
  keys under `srcMorph.aiDefinitions[]` in a CLI JSON/YAML config — and each is only forwarded
  to the binding when explicitly set, so an unconfigured build behaves exactly as before:
  - `cpuMoeLayers` (`--n-cpu-moe` / `-ncmoe`, default `-1`) — keep the MoE expert weights of the
    first *n* layers on the CPU. Usually the better trade than lowering `gpuLayers` on a MoE
    model: it moves only the expert weights (the class that dominates such a model's size), so a
    substantially larger model fits the same VRAM at a smaller speed cost. `0` is a valid,
    meaningful value, so the forwarding guard is `>= 0`, not `> 0`.
  - `cpuFfnLayers` (`--n-cpu-ffn` / `-ncffn`, default `-1`) — the dense-model counterpart; same
    `>= 0` guard for the same reason.
  - `kvUnifiedPerSlot` (`--kv-unified-per-slot`, default `-1`) — per-slot unified KV context cap.
    The binding rejects `0` and negatives, so only a positive value is forwarded.
  - `tensorReadLazy` (`--tensor-read-lazy`, default empty) — `off` / `auto` / `on`. Shortens model
    load time, which matters here because a run loads one model per model group and `calibrate`
    preflights every model in turn. Carried as a `String` through the `config` package (the
    `jniConfinedToProvider` ArchUnit rule keeps `net.ladenthin.llama` types out of it) and
    resolved to the binding's `TensorReadLazyMode` inside the provider, matched
    case-insensitively against the CLI strings the enum itself declares. An unrecognised value is
    **rejected**, not silently dropped — a typo there would otherwise hand the user a run that
    quietly did not do what was asked.

### Changed
- **`net.ladenthin:llama` 5.0.6 → 5.1.0** (llama.cpp b10456 → b10682). Unlike the earlier bumps in
  this series, 5.1.0 is **not** purely additive: it deprecates six `InferenceParameters` methods that
  were always silent no-ops, and this provider called one of them — `withUseChatTemplate(true)`.
  Because `srcmorph` compiles with `-Xlint:all -Werror`, that deprecation is a **compile error**, so
  the bump would have broken the build. The call was **removed rather than suppressed**: it never had
  an effect (upstream reads no such key from a request body), and llama.cpp defaults `use_jinja` to
  true, so chat templating and tool calling are unchanged. The rest of the surface this provider uses
  — `LlamaModel`, `InferenceParameters`, `ModelParameters`, `ChatResponse`/`Timings`/`Pair`,
  `ChatResponseParser`, `ReasoningFormat` — is untouched by 5.1.0. Verified by a full reactor
  `clean test`: 30 + 17 tests, 0 failures, all four modules SUCCESS.
- CI actions bumped to latest: `actions/setup-java` v5 → v6.
- **Build tooling bumped and NullAway aligned with the sibling repos**: `nullaway` 0.13.8 → 0.14.0,
  `spotless-maven-plugin` 3.10.0 → 3.10.1, `palantir-java-format` 2.96.0 → 2.97.0, `pitest-maven`
  1.25.9 → 1.30.0. All three of the first are declared **per module** here (`srcmorph`,
  `srcmorph-cli`, `srcmorph-maven-plugin`), not in the reactor parent, and `pitest-maven` has two
  separate version declarations — every one was moved. nullaway is an alignment rather than a plain
  bump: streambuffer had already merged a Dependabot bump to 0.14.0, so the four repos had silently
  stopped being identical. Deliberately **not** taken: `jqwik` 1.9.3 → 1.10.1, forbidden by
  [`workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md).

### Fixed
- **`publish.yml` concurrency group: every non-PR run now gets its own group.** The block previously
  claimed a push to `main` or a `v*` tag *"always runs to completion"* because `cancel-in-progress`
  is scoped to `pull_request`. GitHub cancels a **pending** run whenever a newer run joins the same
  group behind an in-progress one, and that rule is **independent of** `cancel-in-progress` — so a
  queued release run on `main` could be dropped silently by a later push. The group expression now
  appends the unique `github.run_id` for non-PR runs; PR runs still share a group per ref and
  supersede each other as intended.
- Bumped `jackson.version` 2.22.0 → 2.22.1 (`jackson-databind` / `jackson-dataformat-yaml`,
  pinned in the parent `pom.xml`) to close
  [GHSA-5jmj-h7xm-6q6v](https://github.com/advisories/GHSA-5jmj-h7xm-6q6v) (CVSS 5.3, Medium),
  flagged by OSV-Scanner against `srcmorph/pom.xml` and `srcmorph-cli/pom.xml` after the `main`
  merge of the relocation-stub removal.

### Removed
- **Relocation-stub module** (`llamacpp-ai-index-maven-plugin/`, `net.ladenthin:llamacpp-ai-index-maven-plugin`)
  removed from the active reactor. It was published once at `1.0.4` as part of the `1.1.1` release
  and the redirect verified working end-to-end (a clean-environment Maven resolution of the old
  coordinates correctly follows the relocation through to `srcmorph-maven-plugin:1.1.1` and its
  full dependency graph). The published `1.0.4` artifact is permanent on Maven Central regardless
  of this repo's module list and will never need another release, so there is no reason to keep
  carrying the module (and the `versions:set -Dexcludes=...` caveat it required) in ongoing
  development. `.github/workflows/publish.yml` updated accordingly (no more per-module handling for
  a fourth artifact).

## [1.1.1] - 2026-07-15

### Added
- **Reactor split**: the former single-module `llamacpp-ai-index-maven-plugin` is now a 3-module
  Maven reactor under a new parent, `net.ladenthin:srcmorph-parent` — `srcmorph` (new core library,
  `net.ladenthin:srcmorph`, framework-free — no Maven Plugin API dependency), `srcmorph-cli` (new
  standalone CLI, `net.ladenthin:srcmorph-cli`, driven by a single JSON/YAML configuration file,
  ships as a `java -jar`-ready fat jar), and `srcmorph-maven-plugin` (the original plugin, now a
  thin wrapper depending on `srcmorph`). All three (plus the parent pom) release together at one
  shared version.
- **Plugin renamed** from `net.ladenthin:llamacpp-ai-index-maven-plugin` to
  `net.ladenthin:srcmorph-maven-plugin` in this same release (goal prefix `ai-index` → `srcmorph`;
  package `net.ladenthin.maven.llamacpp.aiindex.mojo` → `net.ladenthin.maven.srcmorph.mojo`; every
  `@Parameter` property `aiIndex.*` → `srcmorph.*`). A new, independently-versioned relocation-stub
  module/POM (`net.ladenthin:llamacpp-ai-index-maven-plugin:1.0.4`, no source, no dependencies, only
  `<distributionManagement><relocation>`) keeps the old coordinates resolvable on Maven Central,
  redirecting to `net.ladenthin:srcmorph-maven-plugin:1.1.1`.
- New engine layer in `srcmorph` (`GenerateEngine`, `AggregatePackagesEngine`,
  `AggregateProjectEngine`, `CalibrateEngine`) extracted from what used to be each mojo's
  `execute()` body, plus a new shared root configuration object,
  `net.ladenthin.srcmorph.config.SrcMorphConfiguration`, bindable identically from Maven plexus XML,
  Jackson JSON/YAML (the new CLI), or plain Java code.
- New `examples/` directory at the repo root: paired `config_*.json`/`.yaml` fixtures for every
  `srcmorph-cli` command (`Plan`, `GenerateFileIndex`, `All`, `Calibrate`), paired `run_*.sh`/`.bat`
  launcher scripts, and an example `logbackConfiguration.xml` — all runnable out of the box with the
  `mock` provider (no GGUF model required).
- Per-module `README.md` files (`srcmorph/README.md`, `srcmorph-cli/README.md`) and a rewritten,
  product-level root `README.md`/`CLAUDE.md` describing the reactor.

### Changed
- Logging in the extracted core/CLI layers moved from a constructor-injected Maven `Log` to
  `org.slf4j.Logger` (see the `1.0.x` entries below for the indexer-layer half of this change,
  already shipped before the reactor split).
- `.github/workflows/publish.yml` adapted to the 4-module reactor: per-module jar upload/release
  globs, a repo-wide crash-dump glob, the PIT step scoped to `srcmorph` (the only module with a
  mutation-testing gate), the `vmlens` job scoped to `srcmorph-maven-plugin` (where its
  test actually lives), and Coveralls/Codecov pointed at `srcmorph`'s jacoco report.

### Notes
- **This release renames the Maven plugin's coordinates, package, goal prefix, and `@Parameter`
  property names.** `net.ladenthin:llamacpp-ai-index-maven-plugin` → `net.ladenthin:srcmorph-maven-plugin`;
  package `net.ladenthin.maven.llamacpp.aiindex.mojo` → `net.ladenthin.maven.srcmorph.mojo`; goal
  prefix `ai-index` → `srcmorph`; every `aiIndex.*` property → `srcmorph.*`. Existing consumers of
  the old coordinates are not broken: a new, independently-versioned relocation-stub artifact
  (`net.ladenthin:llamacpp-ai-index-maven-plugin:1.0.4`, POM-only, no source/dependencies) is
  published with a `<distributionManagement><relocation>` pointing at
  `net.ladenthin:srcmorph-maven-plugin:1.1.1`, so Maven transparently redirects any build still
  declaring the old artifactId.

### Fixed
- **Sources-jar signing**: `maven-source-plugin`'s `attach-sources` execution was bound to the
  `verify` phase instead of `package` in all three real modules. `maven-gpg-plugin`'s signing
  execution is also bound to `verify`, and within the same phase execution order follows
  declaration/inheritance order — the inherited gpg execution ran before the sources jar was
  built, so it was silently omitted from every signed bundle. Rebound `attach-sources` to
  `package` (its own goal default). Also gave the relocation-stub module a `<parent>` so it
  inherits the release profile's signing/publishing plugins at all (it previously had none),
  while keeping its own version pinned independently.

## [1.1.0] - 2026-07-11

> Reconstructed on 2026-08-29 from `git log v1.0.2..v1.1.0` (49 commits). This section was missing
> entirely: `v1.1.0` was tagged and published, but neither a heading nor a compare-link was ever
> added, so the chain jumped `1.0.2 → 1.1.1` and a shipped version was undocumented. The omission is
> recorded in [`../workspace/workflows/release-process.md`](../workspace/workflows/release-process.md)
> as the reason the CHANGELOG footer now has a mechanical headings/links/tags check.

### Added
- **`srcmorph-cli` module** — a standalone JSON/YAML-driven CLI (`net.ladenthin:srcmorph-cli`), with
  the fat jar as its deliverable, bound unconditionally to the `package` phase.

### Changed
- **Restructured into a 3-module Maven reactor** (migration steps 3–9): a parent pom plus the
  framework-free core library `srcmorph`, the new `srcmorph-cli`, and the Maven plugin. The engine
  layer was extracted out of the mojos so the core carries no dependency on the Maven API.
- **The Maven plugin was renamed** to `net.ladenthin:srcmorph-maven-plugin` (package
  `net.ladenthin.maven.srcmorph.mojo`, goal prefix `srcmorph`, `@Parameter` properties `srcmorph.*`).
  The old coordinates stayed resolvable via a one-time relocation-stub POM published at `1.0.4`.
- **Maven `Log` replaced with SLF4J** throughout the indexer layer, which is what makes the core
  module Maven-free.
- CI adapted to the reactor; a Gradle/BouncyCastle signing-key preflight added (cross-repo sync);
  `net.ladenthin:llama` 5.0.4 → 5.0.6; `pitest-maven` and `junit-jupiter` bumped.

### Fixed
- `module-info` for `srcmorph` was missing `requires org.slf4j`.
- The code-style job used bare `spotless`/`spotbugs` prefix goals, which do not resolve in a reactor.
- REUSE compliance: SPDX headers / license sidecars added to the benchmark files.

## [1.0.2] - 2026-07-02

### Changed
- Split the big-window size-routing rule per source kind in the `ai-index-selftest` example POM: the former single `big-window` rule is now `big-window-java` (prompt `file-body-java`), and a matching `big-window-sql` rule (prompt `file-body-sql`) routes oversized `.sql` sources to the same large-context model — so an oversized `.sql` file keeps the SQL prompt instead of being misrouted/uncovered.
- Bumped `net.ladenthin:llama` 5.0.3 → 5.0.4.

## [1.0.1] - 2026-06-29

### Added
- Third-level **project index** (`aggregate-project` goal): a single `project.ai.md` table of contents harvesting each package's lead, with an optional one-call AI `#### Overview` paragraph.
- **Rule-based file routing** for the `generate` goal: each `<fieldGeneration>` routes a file to a `(model, prompt)` via a composable `<condition>` tree (`<and>`/`<or>`/`<not>` over `extensions`/`size`/`lines`/`modifiedAfter`/`modifiedBefore`/`pathGlob`), with `<priority>`, `<skip>`, and exactly one explicit `<fallback>`. Plan-then-execute loads each model once; `aiIndex.planOnly=true` prints the routing plan tree without loading a model.
- Plan-time **context-window fit check**: oversized files fail the build up front; new big-window fallback preset (IBM Granite 4.0-H-Tiny, Apache-2.0) covers source files up to ~1 MB.
- Independently switchable phases via `aiIndex.file.skip` / `aiIndex.package.skip` / `aiIndex.project.skip` (plus the global `aiIndex.skip`).
- File-size **band filter** (`minFileSizeBytes` / `maxFileSizeBytes`) for size-tiered indexing, and `excludes` globs to skip generated/trivial sources.
- Deterministic child-link list (`F` header field) for project → package → file navigation.
- Per-file **progress bar with ETA** and measured (actual vs estimated) per-file generation time.
- **GPU support**: opt-in `gpu-cuda` / `gpu-vulkan` profiles, parameterized native classifier, and `gpuLayers` / `mainGpu` / `devices` knobs.
- Opt-in sampling controls (default off): `min_p`, `top_n_sigma`, DRY repetition suppression, reasoning/think budget, and model-level `swa-full` + cache-reuse.
- Extension-selected file-body prompts (java / sql / fallback).

### Changed
- Default model preset is now `gpt-oss-20B-mxfp4`; all gpt-oss presets are GPU-ready.
- Pinned `net.ladenthin:llama` to the released `5.0.3` (dropped the SNAPSHOT dependency and snapshot repository).
- Bumped JUnit 6.1.0 → 6.1.1 and palantir-java-format 2.92.0 → 2.94.0.

### Removed
- The empty-body generation retry mechanism.

## [1.0.0] - 2026-06-08

First public release on Maven Central. Pre-OpenSSF history themes (March–May 2026): Java 8 compatibility, key-indexed `aiDefinitions` (PR #21), Sonatype Central Portal migration, JaCoCo+Coveralls+Codecov, GH Actions major-version bumps, CodeQL v3→v4, model catalogue (Qwen2.5-Coder, Ministral 8B/14B, Gemma 4 MoE).

### Added
- OpenSSF Best Practices badge and passing-level artifacts (CONTRIBUTING.md, SECURITY.md, CHANGELOG.md).

### Changed
- Switched runtime dependency to `net.ladenthin:llama` 5.0.2 (official Maven Central release).
- CI: added `startgate` abort-window environment before publish pipeline.
- CI: separated snapshot and release publish paths; added `check-snapshot` / `check-tag` gate jobs.
- CI: bumped `softprops/action-gh-release` v2 → v3 (Node 24 compatibility).
- CI: added JaCoCo coverage reporting with Coveralls and Codecov integration.
- README: grouped badges by category (Build / Coverage / Package / License / Community); added Maven Central dependency section.

### Fixed
- CI: quoted gate job names to avoid YAML colon-in-scalar parsing error.
- CI: use `GITHUB_TOKEN` for Coveralls `github-token` parameter instead of `COVERALLS_TOKEN`.

---

[Unreleased]: https://github.com/bernardladenthin/srcmorph/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/bernardladenthin/srcmorph/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/bernardladenthin/srcmorph/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/bernardladenthin/srcmorph/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/bernardladenthin/srcmorph/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/bernardladenthin/srcmorph/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/bernardladenthin/srcmorph/releases/tag/v1.0.0
