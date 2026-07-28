# CLAUDE.md — srcmorph (reactor)

This document provides guidance for AI assistants working on this codebase.

---

## Project Overview

**srcmorph** is a prompt-driven source-tree transformer: it walks a source tree and processes each
file through a configurable local LLM prompt (via llama.cpp / GGUF models, no cloud calls), producing
layered output — per-file, then per-package, then per-project. Today it emits structured `.ai.md`
Markdown summaries for AI-assisted code navigation; the same rule-routed pipeline is generic enough
to eventually emit wikis, architecture docs, diagrams, or source-to-source transformations.

**This repository completed its migration to a 3-module Maven reactor.** It started as a single
Maven plugin (`net.ladenthin:llamacpp-ai-index-maven-plugin`) and was restructured into: a
framework-free core library, a standalone CLI, and the original plugin (now a thin wrapper around
the library, **renamed** to `net.ladenthin:srcmorph-maven-plugin`). **The plugin rename is done**:
coordinates, package, goal prefix, and every `@Parameter` property changed in this step — do not
write `aiIndex.*` properties, the `ai-index` goal prefix, or the
`net.ladenthin.maven.llamacpp.aiindex` package in new documentation or code; use `srcmorph.*`,
`srcmorph`, and `net.ladenthin.maven.srcmorph.mojo` instead (see the plugin module's own section
below). The `1.1.1` reactor release was published to Maven Central; development on `main` now
continues at the next SNAPSHOT version.

**A fourth module existed temporarily**: a tiny relocation-stub POM
(`net.ladenthin:llamacpp-ai-index-maven-plugin`, pinned at `1.0.4`, only a
`<distributionManagement><relocation>` pointing at `srcmorph-maven-plugin:1.1.1`) so existing
consumers of the old coordinates get transparently redirected. It was published once, verified
working end-to-end (a clean-environment Maven resolution of the old coordinates correctly followed
the relocation through to the real artifact and its full dependency graph), and then **removed from
this reactor** — the published `1.0.4` artifact is permanent on Maven Central regardless of what
this repo's module list contains, and it will never need another release, so there was no reason to
keep carrying it (and its `versions:set` exclusion footgun) in active development.

- **Group ID:** `net.ladenthin`
- **Java:** target bytecode 1.8 (production code), Java 21 test sources, built with JDK 21
- **License:** Apache 2.0
- **Author:** Bernard Ladenthin (Copyright 2026)
- **Reactor version:** `1.2.0-SNAPSHOT` (single shared version across `srcmorph`, `srcmorph-cli`,
  and `srcmorph-maven-plugin`). Last released version: `1.1.1`.

---

## Repository layout — Maven reactor

```
llamacpp-ai-index-maven-plugin/            (repo root; reactor parent)
├── pom.xml                                net.ladenthin:srcmorph-parent:1.2.0-SNAPSHOT (packaging=pom)
│                                           shared build plugins + dependencyManagement + release profile
├── srcmorph/                               CORE LIBRARY  net.ladenthin:srcmorph  (Java 8, Maven-API-free)
│   └── src/main/java/net/ladenthin/srcmorph/
│       ├── config/      18+ POJOs (incl. the shared root SrcMorphConfiguration)
│       ├── engine/      GenerateEngine, AggregatePackagesEngine, AggregateProjectEngine,
│       │                CalibrateEngine, SrcMorphException, GenerateResult, CalibrationReport, EngineSupport
│       ├── indexer/      SourceFileIndexer, PackageIndexer, ProjectIndexer, AiFieldGenerationSupport, ...
│       ├── document/  prompt/  provider/  support/
│   └── src/test/resources/SmolLM2-135M-Instruct-Q3_K_M.gguf   (real-model tests live here)
├── srcmorph-cli/                           CLI  net.ladenthin:srcmorph-cli  (fat jar = deliverable)
│   └── src/main/java/net/ladenthin/srcmorph/cli/
│       ├── Main.java                       BitcoinAddressFinder cli/Main.java pattern
│       └── configuration/                  CConfiguration + CCommand (BAF public-field style)
├── srcmorph-maven-plugin/                   Maven plugin  net.ladenthin:srcmorph-maven-plugin, goalPrefix srcmorph
│   └── src/main/java/net/ladenthin/maven/srcmorph/mojo/   (5 mojos; renamed package/properties)
├── examples/                               config_*.json/.yaml + run_*.sh/.bat + logbackConfiguration.xml
├── docs/                                   RELEASE.md + the ai-index model-benchmark writeups
└── .github/workflows/                      CI adapted to the 3-module reactor
```

Every module inherits its `<version>` from the parent (`net.ladenthin:srcmorph-parent`), so
`srcmorph`/`srcmorph-cli`/`srcmorph-maven-plugin` ship in lockstep by construction. Bump their
version reactor-wide with `mvn versions:set -DnewVersion=X -DgenerateBackupPoms=false` from the
repo root — no exclusions needed today (the relocation-stub module that once required one was
removed after its `1.0.4` release; see "Project Overview" above).

### `srcmorph` — the core library

Framework-free: **no dependency on `org.apache.maven..`** anywhere (enforced by
`CoreArchitectureTest#coreIsMavenFree`, the load-bearing ArchUnit rule for this module). Depends on
`net.ladenthin:llama` (the llama.cpp JNI binding, used only by the `provider` package), SLF4J, jspecify
+ checker-qual, Lombok (provided). Package root: `net.ladenthin.srcmorph`.

- **`config/`** — mutable JavaBeans (no Maven annotations) bindable structurally from Maven plexus XML,
  a Jackson `ObjectMapper`/`YAMLMapper`, or plain Java code. The root object is
  **`SrcMorphConfiguration`**: one bean holding everything a run needs (`baseDirectory`,
  `outputDirectory`, `subtrees`, `excludes`, `fileExtensions`, the size band, `force`, `planOnly`,
  `generationProvider`, `promptDefinitions`, `aiDefinitions`, `fieldGenerations`, `factDefinitions`, the
  `llama*` fallback params, `pluginVersion`/`aiVersion`/`projectName`). **Field names intentionally
  mirror the Maven plugin's current `@Parameter` names** so a JSON/YAML key reads identically to the
  matching `<configuration>` XML element — see `SrcMorphConfiguration`'s own Javadoc.
- **`engine/`** — one class per phase, each constructed from a `SrcMorphConfiguration` and owning its
  own AI provider lifecycle (try-with-resources; one model resident at a time):
  `GenerateEngine` (plan → validate → planOnly early-out → per-model-group indexing loop + progress
  bar), `AggregatePackagesEngine`, `AggregateProjectEngine` (deterministic listing + optional one-call
  AI overview), `CalibrateEngine` (per-model preflight + timing). All four throw the checked
  `SrcMorphException` on misconfiguration, never a Maven `MojoExecutionException` — callers (the
  plugin's mojos, the CLI's `Main`) wrap it into whatever their own surface expects.
- **`indexer/`** — the walk/plan/write orchestration (`SourceFileIndexer`, `PackageIndexer`,
  `ProjectIndexer`, `AiFieldGenerationSupport`, `AiIndexPlan`, `AiCalibrationRunner`, ...). Logs via
  `org.slf4j.Logger` (a private static final field per class), not a Maven `Log` — this is what makes
  the module Maven-free; Maven's own `maven-slf4j-provider` (ships since Maven ≥ 3.1) makes these lines
  surface as ordinary `[INFO]`/`[WARN]` output inside a plugin execution with zero glue, and the CLI
  ships a logback binding for the same log lines outside Maven.
- **`document/`** — the `.ai.md` model + codecs (`AiMdDocument`, `AiMdHeader`, `AiMdDocumentCodec`,
  `AiMdHeaderCodec`, `AiMdHeaderSupport`, `AiMdChildEntryLineFormatter`, `AiMdLeadExtractor`,
  `AiGenerationRequest`/`AiGenerationResult`).
- **`prompt/`** — `AiPromptDefinition`, `AiPreparedPrompt`, `AiPromptSupport`,
  `AiPromptPreparationSupport`.
- **`provider/`** — the AI backend abstraction: `AiGenerationProvider` (`Closeable`),
  `AiGenerationProviderFactory` (looks up `"mock"` / `"llamacpp-jni"`), `MockAiGenerationProvider`,
  `LlamaCppJniAiGenerationProvider`, `LlamaCppJniConfig` + `LlamaCppJniConfigFactory` (the pure 26-field
  mapping from a resolved `AiGenerationConfig` to the native binding's parameter objects — extracted
  from the old mojo so it is unit- and PIT-testable without a Maven runtime), `AiCompletionParser`.
- **`support/`** — foundation helpers with no dependency on anything above them: `AiChecksumSupport`,
  `AiTimeSupport`, `AiPathSupport`, `AiSourceExcludeFilter`, `AiProgressBar`, `AiSourceChunker`,
  `AiDeterministicSummary`, `AiGenerationTimeEstimator`, `Java8CompatibilityHelper`, `ConvertToRecord`.

**Architecture rules** (`CoreArchitectureTest`, ArchUnit): `coreIsMavenFree` (the load-bearing rule
above), `layeredArchitecture` (`engine` on top → `indexer` → `provider`/`document`/`prompt` → `config`
→ `support`), `noPackageCycles`, `loggersArePrivateStaticFinal`, `noSystemExit`,
`noTestFrameworksInProduction`, `noJavaUtilLogging`, `noSystemOutOrErrInProduction`,
`noInternalJdkImports`, `noPublicMutableFields`, `noNewRandom`, `noThreadSleep`,
`jniConfinedToProvider` (only the `provider` package may touch the llama.cpp JNI binding).

**Test suite** (`srcmorph/src/test/java/net/ladenthin/srcmorph/`, package-renamed 1:1 with production):
~63 test files, incl. jqwik properties, an ArchUnit suite, a Lincheck race test
(`AiGenerationKindLincheckTest`), and the model-backed real tests gated on
`src/test/resources/SmolLM2-135M-Instruct-Q3_K_M.gguf`. **PIT mutation testing**: `mutationThreshold`
100 over an explicit `targetClasses` list in `srcmorph/pom.xml` — currently 47 classes across
config/document/indexer/prompt/provider/support, all killed at 100%. `srcmorph-cli` and the plugin
module do not have a PIT gate yet (see `TODO.md`). The `gpu-cuda`/`gpu-vulkan` profiles (swap the
`net.ladenthin:llama` classifier via the `llama.classifier` property) live here; the `jcstress` and
`vmlens` profiles/tests currently still live in the **plugin** module (they were not moved in the
extraction — see that module's section below), not here.

### `srcmorph-cli` — the standalone CLI

`net.ladenthin:srcmorph-cli`, packaging `jar`, package root `net.ladenthin.srcmorph.cli`. A BAF-style
CLI driven by a single JSON or YAML configuration file:

- **`cli/Main.java`** — extension-dispatched loader (`.json`/`.js` → Jackson `ObjectMapper`,
  `.yaml`/`.yml` → `YAMLMapper`, both with `FAIL_ON_UNKNOWN_PROPERTIES` enabled so a config typo fails
  fast, mirroring what plexus does on the Maven XML side); echoes the parsed configuration back
  (re-serialized as both JSON and YAML) for review before anything runs; no `System.exit` anywhere — a
  failure propagates as an unchecked exception out of `main(String[])`. Dispatches on `CConfiguration`'s
  `command` field to one or more `net.ladenthin.srcmorph.engine.*` engines.
- **`cli/configuration/CConfiguration`** / **`CCommand`** — public-mutable-field JavaBeans (the BAF
  `cli.configuration.CConfiguration` convention; carved out of the `noPublicMutableFields` ArchUnit rule
  via this package's explicit exception). `CConfiguration.srcMorph` is the **same**
  `net.ladenthin.srcmorph.config.SrcMorphConfiguration` the Maven plugin's mojos build from their own
  `@Parameter` fields — a JSON/YAML key under `srcMorph` reads identically to the matching plugin XML
  element. `CCommand` is `Plan | GenerateFileIndex | AggregatePackages | AggregateProject | All |
  Calibrate`; the default is `Plan` (safe: no model load, nothing written).
- The fat jar (`srcmorph-cli-<version>-jar-with-dependencies.jar`, main class
  `net.ladenthin.srcmorph.cli.Main`) is bound **unconditionally** to the `package` phase (a deliberate
  divergence from BAF's `-P assembly` opt-in — for this module the fat jar IS the deliverable).
- Ships its own logback binding (`ch.qos.logback:logback-classic`, runtime scope) — unlike the library
  (consumer picks any SLF4J binding) and the plugin (gets one for free from Maven's own
  `maven-slf4j-provider`), a standalone `java -jar` process needs to bring its own or every log line is
  silently dropped.
- **Architecture rules** (`CliArchitectureTest`): `cliIsLeaf` (nothing else in the reactor may depend on
  this module — it is the leaf-most consumer), `noPublicMutableFields` (with the `configuration`
  package carve-out), `noSystemExit`, `mavenFree` (must never depend on the Maven Plugin API — that
  boundary belongs to the plugin module), `noTestFrameworksInProduction`, `noInternalJdkImports`,
  `loggersArePrivateStaticFinal`.
- **Tests**: `MainTest`, `configuration.ConfigBindingTest` (round-trips a private
  `src/test/resources/test-fixtures/minimal-generate.{json,yaml}` pair through both parsers),
  `ExamplesConfigBindingTest` (sweeps every shipped `examples/config_*.{json,yaml}` fixture — the
  public, documented examples — through the same strict mappers), `CliEndToEndTest` (drives the `All`
  and `Plan` commands against the mock provider end to end, no forked process).

### `srcmorph-maven-plugin` — the Maven plugin (renamed; formerly `llamacpp-ai-index-maven-plugin`)

**Renamed in the final migration step**: coordinates `net.ladenthin:srcmorph-maven-plugin` (was
`net.ladenthin:llamacpp-ai-index-maven-plugin`), package `net.ladenthin.maven.srcmorph.mojo` (was
`net.ladenthin.maven.llamacpp.aiindex.mojo`), goal prefix `srcmorph` (was `ai-index`), every
`@Parameter` property now spelled `srcmorph.*` (e.g. `srcmorph.skip`, `srcmorph.file.skip`,
`srcmorph.planOnly`, `srcmorph.generationProvider`, `srcmorph.llama.modelPath` — was `aiIndex.*`).
The old coordinates were kept alive on Maven Central via a one-time relocation-stub publish (see
"Project Overview" above and its own paragraph below) — never describe the plugin using the old
coordinates/package/properties in new documentation or code. The plugin's *contents* stay thin: it
depends on `net.ladenthin:srcmorph` (compile scope) for everything except the 5 mojo classes
themselves.

- **`AbstractAiIndexMojo`** — shared `@Parameter` fields + `buildConfiguration()`, which maps them onto
  a new `SrcMorphConfiguration` for the matching engine to run. Concrete mojos
  (`GenerateMojo`/`AggregatePackagesMojo`/`AggregateProjectMojo`/`CalibrateMojo`) each add their own
  goal-specific `@Parameter`s (e.g. `skipFile`/`skipPackage`/`skipProject`, `planOnly`, `fileExtensions`,
  `excludes`, `factDefinitions`), build the configuration, and delegate the whole run to one
  `net.ladenthin.srcmorph.engine.*` engine — mojos are now thin (≤ ~30 lines of actual logic each) and
  translate a caught `SrcMorphException`/`IOException` into a `MojoExecutionException`. The class
  itself keeps its historical name (`AbstractAiIndexMojo`) even though the package/goal-prefix/
  properties around it were renamed — only the Maven-facing surface (coordinates, package, goal
  prefix, `@Parameter` property strings) was in scope for the rename.
- **Skip flags stay mojo-side** (`skip`, `skipFile`, `skipPackage`, `skipProject`) — a Maven lifecycle
  concern, not part of `SrcMorphConfiguration`; an engine built from a configuration always executes
  when asked. See `MojoPhaseSkipTest`.
- **Architecture rules** (`PluginArchitectureTest`): Maven-annotation confinement to `mojo`, every mojo
  extends `AbstractMojo`, plus this module's slice of the shared conventions.
- **jcstress** (`jcstress/AiGenerationKindRace.java`) and **vmlens**
  (`vmlens/VmlensInterleavingSmokeTest.java`) tests/profiles currently live in this module, not in
  `srcmorph` — they were not relocated during the core extraction.
- Full goal/parameter reference: `srcmorph-maven-plugin/README.md`.

### `llamacpp-ai-index-maven-plugin` — the retired relocation stub (no longer in this repo)

Not a module in this reactor anymore. It existed briefly as a minimal 4th reactor module — **not**
a renamed copy of the plugin above, and not a child of `srcmorph-parent` (no `<parent>` at all).
Its entire `pom.xml` was `groupId` + `artifactId` (`llamacpp-ai-index-maven-plugin`) + a version
pinned independently at `1.0.4` + `<distributionManagement><relocation>` pointing at
`net.ladenthin:srcmorph-maven-plugin:1.1.1`. No source, no tests, no dependencies. Its sole purpose
was so a consumer still declaring the old Maven coordinates gets redirected by Maven to the renamed
plugin — published once, verified working end-to-end from a clean environment, then removed from
active development: the published `1.0.4` artifact is permanent on Maven Central regardless of
this repo's module list, and it will never need another release. Anyone still depending on the old
coordinates continues to be redirected correctly; nothing here needs to change again for that to
keep working.

---

## Build Commands

### Whole reactor (repo root)

```bash
mvn compile          # Compiles every module (Java + generates nothing native; pure Java reactor)
mvn test             # Runs every module's tests
mvn package          # Builds all four reactor projects: parent pom + 3 jars (incl. the CLI's fat jar)
mvn install          # Installs all four into ~/.m2 (needed before iterating on a single module — see below)
```

### Iterating on one module

Maven resolves inter-module dependencies (`srcmorph-maven-plugin` and `srcmorph-cli` both
depend on `net.ladenthin:srcmorph`) via the local repository, not in-reactor classes, unless you use
`-pl`/`-am`:

```bash
# Build/install the core first if you're iterating on the CLI or the plugin against local core changes:
mvn -pl srcmorph -am install -DskipTests

# Then work on just one module:
mvn -pl srcmorph-cli test
mvn -pl srcmorph-maven-plugin test
```

### Offline / restricted-network environments

```bash
mvn test -o                 # requires a warm ~/.m2/repository cache
mvn package -o -DskipTests
```

### Run the self-test profile (plugin module only)

```bash
mvn -pl srcmorph-maven-plugin srcmorph:generate -P srcmorph-selftest
```

### Run the CLI

```bash
mvn -pl srcmorph-cli package
java -jar srcmorph-cli/target/srcmorph-cli-1.2.0-SNAPSHOT-jar-with-dependencies.jar examples/config_All.json
```

See `examples/` (repo root) for ready-to-run `config_*.json`/`.yaml` + paired `run_*.sh`/`.bat`
launcher scripts, all using the `mock` provider (no GGUF model required).

---

## Testing

Every module uses JUnit Jupiter + Hamcrest; `MockAiGenerationProvider` gives fully deterministic tests
with no model or JNI dependency. Model-backed tests (in `srcmorph`) are gated on
`srcmorph/src/test/resources/SmolLM2-135M-Instruct-Q3_K_M.gguf` and self-skip when the native library
is unavailable.

- `srcmorph` — the bulk of the test suite (framework-free logic); see that module's section above.
- `srcmorph-cli` — `MainTest`, `ConfigBindingTest`, `ExamplesConfigBindingTest`, `CliArchitectureTest`,
  `CliEndToEndTest`.
- `srcmorph-maven-plugin` — `PluginArchitectureTest`, `MojoPhaseSkipTest`, plus the jcstress/
  vmlens tests noted above.

See `TEST_WRITING_GUIDE.md` (repo root, applies to every module) for full conventions.

---

## Code Conventions

### Logging

`srcmorph` and `srcmorph-cli` log via `org.slf4j.Logger` (`private static final Logger LOGGER = ...`,
enforced by each module's own `loggersArePrivateStaticFinal` ArchUnit rule). The plugin module's mojos
still use `AbstractMojo#getLog()` (a Maven `Log`) at the mojo boundary only — everything they delegate
to below that boundary is SLF4J.

### Null Safety

- JSpecify `@Nullable`/`@NonNull` (default) annotations; NullAway + Checker Framework enforce this at
  compile time in every module. Mark nullable return types and parameters explicitly.
- Prefer early null/empty guards with a logged warning over silent skips.

### Named Constants

Every meaningful literal (string keys, header field names, node types, version strings) must be a
`public static final` or `private static final` named constant with Javadoc.

### License Headers

All source files must include the Apache 2.0 license header wrapped in `// @formatter:off` /
`// @formatter:on` (or the file type's equivalent comment syntax — see `examples/` for the `#`/`REM`/
XML-comment conventions used there; JSON/YAML example fixtures carry no inline header, see
`REUSE.toml`).

### Records

Immutable value types are Java `record`s where practical (e.g. `AiMdDocument`, `AiMdHeader`,
`AiPreparedPrompt`, `AiGenerationRequest`, `GenerateResult`).

### `useModulePath=false` (all three modules)

Every module's `maven-surefire-plugin` configuration forces `<useModulePath>false</useModulePath>`.
Each module ships a `module-info.java`, but it is release metadata for module-path *consumers* only —
this reactor's own build, tests, and every real consumer today load these jars on the plain classpath
(the production bytecode targets Java 8, where `module-info.class` is inert). Leaving Surefire's
module-path auto-detection on would patch test classes into the named module and then also require
every test-only dependency (e.g. `archunit-junit5`) to be explicitly module-readable, which
`module-info.java` intentionally does not declare — so classpath mode is not a workaround, it is the
actually-representative test environment.

---

## CI/CD Pipelines (`.github/workflows/`)

| Workflow | Trigger | Purpose |
|---|---|---|
| `publish.yml` | Push, PR, manual dispatch | Unified build/test/coverage/package pipeline; publishes snapshots and Maven Central releases |
| `codeql.yml` | Schedule/Push | GitHub CodeQL security scanning |
| `scorecard.yml` | Schedule / Push | OpenSSF Scorecard supply-chain security analysis |
| `osv-scanner.yml` | Schedule / Push / PR | Google OSV-Scanner dependency vulnerability scan |
| `reuse.yml` | Push / PR | FSFE REUSE license-compliance check (`fsfe/reuse-action`) |
| `claude-code-review.yml` | PR | AI-powered code review |
| `claude.yml` | Issue/PR comment with `@claude` | Claude Code interactive assistant |

`publish.yml` still reflects the pre-reactor single-module layout in places (report globs, artifact
paths); adapting it fully to the 3-module reactor is a separate, later step (see `TODO.md`) — do not
assume it has already been updated.

---

## Dependencies Summary

| Dependency | Version | Used by |
|---|---|---|
| `net.ladenthin:llama` | 5.0.6 | `srcmorph` (`provider` package only) — llama.cpp JNI binding |
| `org.slf4j:slf4j-api` | 2.0.18 (converged in the parent) | `srcmorph`, `srcmorph-cli`, the plugin |
| `ch.qos.logback:logback-classic` | 1.6.1 (converged in the parent) | `srcmorph-cli` (runtime binding) |
| `com.fasterxml.jackson.core:jackson-databind` | pinned in parent | `srcmorph-cli` (JSON config) |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | pinned in parent | `srcmorph-cli` (YAML config) |
| `org.apache.maven:maven-plugin-api` | 3.9.16 | `srcmorph-maven-plugin` (provided) |
| `org.apache.maven.plugin-tools:maven-plugin-annotations` | 3.15.2 | `srcmorph-maven-plugin` (provided) |

Test-only (every module): `org.junit.jupiter:junit-jupiter`, `org.hamcrest:hamcrest`,
`com.tngtech.archunit:archunit-junit5`. `srcmorph` additionally uses jqwik (pinned ≤ 1.9.3 — see the
jqwik policy link below), JMH, jcstress, Lincheck, vmlens.

---

## Test / Code Writing Compliance

After modifying or creating any `.java` file, in whichever module it lives:

- For `*Test.java` files, follow the workspace version chain:
  [`../workspace/guides/test/TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md)
  (baseline) **and** this repo's own `TEST_WRITING_GUIDE.md` (repo-wide supplement; applies to every
  module — there is one guide file at the repo root, not one per module).
- For production sources, follow the workspace version chain:
  [`../workspace/guides/src/CODE_WRITING_GUIDE-8.md`](../workspace/guides/src/CODE_WRITING_GUIDE-8.md)
  (baseline) **and** this repo's own `CODE_WRITING_GUIDE.md`.
- Apply all fixable violations automatically; report only those that cannot be resolved without a
  large refactor.

---

## Pull Request Workflow

See [`../workspace/workflows/pull-request-workflow.md`](../workspace/workflows/pull-request-workflow.md).

---

## Key Design Principles

1. **Local-first** — all AI inference runs locally via llama.cpp; no cloud API calls, no data leaves
   the machine.
2. **Deterministic indexing** — the same source produces the same `.ai.md` skeleton (deterministic
   header); only the AI-generated body varies.
3. **Incremental updates** — files with existing summaries are skipped unless `force=true`; checksums
   detect source changes.
4. **One shared configuration object** — `net.ladenthin.srcmorph.config.SrcMorphConfiguration` is
   bindable from Maven plexus XML, Jackson JSON/YAML (the CLI), or plain Java code, so a JSON/YAML key
   always reads identically to the matching Maven `<configuration>` XML element.
5. **Provider abstraction** — AI backends are pluggable through `AiGenerationProvider`; the mock
   provider enables fully deterministic tests everywhere.
6. **Configuration-driven prompts & rule-based routing** — prompt templates and the `<fieldGenerations>`
   routing rules (composable `<condition>` tree, priority, skip, exactly one fallback) are data, never
   hardcoded in Java; see `SrcMorphConfiguration`'s Javadoc and each engine's own Javadoc for the
   `generate`/`aggregate-packages`/`aggregate-project`/`calibrate` semantics.
7. **Staged, always-green migration** — the plugin's public coordinates never change out from under an
   existing consumer mid-migration; the rename to `srcmorph-maven-plugin` is a deliberately isolated,
   later step (see `TODO.md`).

## Javadoc Conventions

See [`../workspace/policies/javadoc-conventions.md`](../workspace/policies/javadoc-conventions.md).

## SpotBugs Suppressions

See [`../workspace/policies/spotbugs-suppressions.md`](../workspace/policies/spotbugs-suppressions.md).
Each module has its own `spotbugs-exclude.xml` (`srcmorph/spotbugs-exclude.xml`,
`srcmorph-cli/spotbugs-exclude.xml`, `srcmorph-maven-plugin/spotbugs-exclude.xml`).

## Spotless Formatting

See [`../workspace/policies/spotless-formatting.md`](../workspace/policies/spotless-formatting.md).
Run `mvn spotless:apply` before every commit that touches `.java` files (reactor-wide from the root, or
scoped to one module with `-pl`).

## jqwik Policy

See [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md).
jqwik is a test dependency of `srcmorph` only.

## Lombok Config

See [`../workspace/policies/lombok-config.md`](../workspace/policies/lombok-config.md).
`lombok.config` lives once at the repo root and is inherited by every module (Lombok walks up the
directory tree from each source file looking for `lombok.config`).

## CI Test Diagnostics

See [`../workspace/policies/ci-test-diagnostics.md`](../workspace/policies/ci-test-diagnostics.md).

## PIT Mutation Testing

See [`../workspace/policies/pit-mutation-testing.md`](../workspace/policies/pit-mutation-testing.md).
Run PIT with the lifecycle prefix, scoped to the gated module —
`mvn test-compile org.pitest:pitest-maven:mutationCoverage -f srcmorph/pom.xml` (only `srcmorph` is
PIT-gated today; see `TODO.md`).

## JPMS Module Descriptor

Each module ships a `module-info.java` compiled in a separate `release 9` execution, and each module's
Javadoc runs in **classpath mode** (`<source>` resolves to `8`), which is the *only* thing keeping it
clear of the JPMS module-mode javadoc trap that bit BAF. **Before raising the Java / javadoc source
level to ≥ 9 in any module, read**
[`../workspace/policies/jpms-module-descriptor.md`](../workspace/policies/jpms-module-descriptor.md).

## Fat-jar release assets

The `srcmorph-cli` fat jar (`jar-with-dependencies`) is a **GitHub-Release asset only — never
Maven Central** (`srcmorph-cli/pom.xml` sets `<attach>false</attach>`), attached with a detached
GPG `.asc`. CI builds **one fat jar per `net.ladenthin:llama` classifier** (default CPU + every GPU
classifier) and signs them via the cross-repo shared `.github/sign-fatjars.sh` (byte-identical with
java-llama.cpp). The convention + per-repo shapes + the classifier keep-in-sync rule are documented
in [`../workspace/policies/fat-jar-release-assets.md`](../workspace/policies/fat-jar-release-assets.md).

## Open TODOs

Open TODOs for this repo live in [`TODO.md`](TODO.md). Cross-repo status
tracking lives in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md).
