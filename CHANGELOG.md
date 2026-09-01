# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The release procedure (prompt template and step-by-step instructions) lives in [docs/RELEASE.md](./docs/RELEASE.md).

---

## [Unreleased]

### Added
- **`flashAttn` works.** The knob was documented and settable since it was introduced, but could not
  be forwarded: `--flash-attn` takes a mandatory `on|off|auto` and `net.ladenthin:llama` offered only
  a bare-flag setter, which emitted the key alone and made llama.cpp's parser consume the following
  argv token — the load then died naming a flag the user never set. 1.2.0 shipped a refusal at plan
  time rather than that diagnostic.

  `net.ladenthin:llama` **5.2.0** adds `ModelParameters.setFlashAttn(FlashAttn)`, so the provider now
  emits `--flash-attn on` when the knob is set. `false` still emits nothing, which leaves llama.cpp's
  own `auto` default in force — that is the correct behaviour for an unconfigured run, and it is what
  the knob's javadoc now says.

  **The knob is exercised against a real model for the first time.** `flashAttn` moves out of
  `LlamaCppJniKnobSweepTest`'s `NOT_SWEPT` list into a real sweep case; until now only the refusal had
  ever been executed.

### Removed
- The plan-time and provider-side refusals (`EngineSupport.validateFlashAttnIsNotRequested`,
  `LlamaCppJniAiGenerationProvider.FLASH_ATTN_UNSUPPORTED_MESSAGE`) and the three tests that pinned
  them.

### Changed
- **`lazyMode` replaces `tensorReadLazy` — breaking.** llama.cpp b10731 renamed `--tensor-read-lazy`
  to `-lzm` / `--lazy-mode` with no alias, and `net.ladenthin:llama` followed the rename rather than
  papering over it, so the model-definition field, its `srcmorph.llama.*` surface, the
  `LlamaCppJniConfig` accessor and the sweep case move with it. Carrying the old name would leave the
  configuration describing a flag that no longer exists.

  Worth knowing why this is a real hazard and not cosmetics: a **new Java name paired with an old
  native** produces `Failed to parse model parameters` at load time and nothing earlier catches it.
  The knob sweep did — it is what surfaced the mismatch here.

- **`net.ladenthin:llama` 5.1.0 → 5.2.0-SNAPSHOT.** Deliberately a snapshot: the binding change this
  release depends on is not yet published. Building srcmorph therefore requires that snapshot to be
  resolvable, and CI stays red until it is — recorded here so nobody mistakes it for a regression.

## [1.2.0] - 2026-09-01

### Added
- **The plan phase now checks the configuration against the model itself, still without loading it.**
  `net.ladenthin:llama` ships `GgufInspector`, which parses only a GGUF's header key/value table — no
  native library, no tensor data — so it is usable from a phase whose whole promise is that it loads no
  model. srcmorph was not using it; its check was `new File(modelPath).isFile()`, which misses two
  things, both silently:
  - **The file is not a GGUF at all.** A Git LFS pointer, a truncated download, or simply the wrong
    file passes an existence check and dies much later inside the native loader — in a multi-model run,
    after the earlier model groups have already generated. Now a plan-time failure naming the file.
  - **`contextSize` exceeds what the model declares.** This is the one that costs the most and shows
    the least: *every* number the plan produces is derived from `contextSize` — `maxInputChars`, the
    oversize/chunking decision, the time estimate. Point the default 32768 at a 4096-context model and
    the plan is wrong by 8&times; before a token is generated. A warning rather than an error, because
    llama.cpp will deliberately run past a model's trained context with RoPE scaling.

  New `provider.GgufModelInspector` (the `jniConfinedToProvider` rule keeps binding types out of
  `engine`) and the framework-free `provider.GgufModelInfo` it returns, the latter on the PIT gate.

- **A truncated summary is now reported instead of being written as if it were complete.**
  `maxOutputTokens` defaults to 128; a model that hits that ceiling stops mid-sentence, and llama.cpp
  says so — the OpenAI finish reason is `length` rather than `stop`. The provider was calling
  `chatCompleteText(...)`, which returns only the text, so the signal was not merely ignored but
  unavailable. The generate path now parses the whole response, which costs **no extra inference**:
  `chatCompleteText` is literally `extractChoiceContent(chatComplete(...))`, the same native call.

  Worth recording for whoever touches this next: the check compares against the literal `"length"`,
  deliberately **not** against `StopReason`. Those are two different vocabularies — `getFinishReason()`
  is OpenAI's (`stop`/`length`/`tool_calls`), while `StopReason` maps llama.cpp's own `stop_type`
  (`eos`/`word`/`limit`). `StopReason.fromStopType("length")` returns `NONE`: a silent wrong answer,
  not a compile error.

- **Prompt-cache reuse is now visible during an indexing run**, not only during `calibrate`, closing
  the follow-up that release left open. Parsing the full response also makes `Usage` available, so the
  `DEBUG` line reports cached / total prompt tokens and tokens generated, per file — the run that
  actually pays `swaFull`'s KV-memory surcharge had no visibility into whether it was paying off.

- **`srcmorph-cli` and `srcmorph-maven-plugin` are PIT-gated**, at the same `mutationThreshold` 100
  as the core. They were the last two modules without one, and the gap was not theoretical: PIT
  measured the plugin at **53%** (33/62) and the CLI at **84%** (16/19) before any test was written.

  The plugin's hole was the interesting one. `AbstractAiIndexMojoTest` covers the *shared*
  `buildConfiguration()`, but each goal adds its own mapping step on top — and every one of
  `buildGenerateConfiguration` / `buildAggregatePackagesConfiguration` /
  `buildAggregateProjectConfiguration`, plus all eight `getLlamaContextSize`/`getLlamaThreads`
  accessors, had **no coverage at all**. Drop `setExcludes(...)` from `GenerateMojo` and the plugin
  quietly indexes files the user excluded, with nothing failing. `MojoConfigurationMappingTest` now
  pins each goal's own parameters with values distinct within their type, so a transposition fails
  rather than cancelling out — verified by swapping `minFileSizeBytes`/`maxFileSizeBytes`, which
  reds the test. The three mapping methods went from `private` to package-private for it.

  Two survivors that the new gate exposed were weaknesses in *existing* tests, not missing ones:
  `AbstractAiIndexMojoTest` asserted `generationProvider` was `"mock"`, which is
  `SrcMorphConfiguration`'s own default — so dropping the setter call was invisible; it now uses a
  non-default provider name. And `CalibrateMojo.execute()`'s deliberate blank separator line could
  not be seen by a `contains` check, so it is asserted by position.

  The CLI gate excludes exactly one method, `Main.main(String[])`, with the reason recorded in the
  pom: the `smoke-fatjar` job — a release gate for both publish jobs — already runs the real
  `java -jar` artifact and asserts `Main#run end.`, which only `run()` emits and only `main()`
  calls. Its one true survivor, the `System.out.println` that makes the `<calibration>` block
  paste-ready rather than a log line, is now pinned by a stdout-capturing test.

  CI runs the goal reactor-wide instead of `-pl srcmorph -am`, and the survivor extraction and
  report upload cover every module's `target/pit-reports`.

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

- **`seed` model-definition knob** (`InferenceParameters.withSeed`, default `-1`). Upstream draws a
  random seed per request, so with a non-zero temperature every generation samples differently: a
  re-index of an unchanged file, or any `force=true` run, produced a different `.ai.md` body than the
  one already committed. That sat awkwardly next to the project's own "deterministic indexing"
  principle. Setting a seed makes the body stable for a given machine and configuration, which turns a
  re-index into a reviewable diff. Forwarded only when `>= 0`, so an unconfigured run is byte-identical
  to before. Documented deliberately as *not* bit-reproducibility: llama.cpp results move with thread
  count, batch size and backend, so the seed pins the sampling, not the arithmetic.

- **The prompt cache is now measured instead of assumed** — `cache_n` reaches Java. The binding has
  always reported how many leading prompt tokens llama.cpp served straight from the KV cache; the
  provider read the `Timings` object and dropped that number. It is the *only* observable the whole
  prefix-reuse stack produces (`cachePrompt`, `cacheReuse`, `swaFull`), so with it discarded a run paid
  `swaFull`'s KV-memory surcharge on the assumption that it was working, with no way to tell whether it
  was. `AiGenerationTimings` now carries it as `cachedPromptTokens()` next to the evaluated
  `promptTokens()`, plus their sum as `totalPromptTokens()`; `AiCalibrationMeasurement` carries the
  near-window run's count, and `srcmorph:calibrate` logs it per model — an `INFO` line naming the reused
  token count, or a `WARN` when nothing was reused, which is the case where those settings cost memory
  and buy nothing. The real-model provider test asserts that a second request against the same system
  prompt finds it cached, so the reuse is proven end to end and not just plumbed.

- **Seven further model-definition knobs**, wired through to `net.ladenthin:llama` 5.1.0 exactly like the
  existing `gpuLayers` / `cpuMoeLayers` set — an `<aiDefinition>` element in the Maven plugin, or a key
  under `srcMorph.aiDefinitions[]` in a CLI JSON/YAML config — and each forwarded to the binding only when
  explicitly set, so an unconfigured build behaves exactly as before. They are added now rather than later
  because configuration surface is the part of this library that cannot be extended without a release.
  - `repeatLastN` (`--repeat-last-n`, default `-1`) — **the gap this closes is a pair that was half
    wired**: `repeatPenalty` was configurable, the window it acts on was not, so the strength was
    adjustable while the reach stayed on llama.cpp's default of 64 tokens. `0` disables the penalty and is
    a meaningful value, so the guard is `>= 0`; the binding rejects negatives. Per-request, next to
    `repeatPenalty` itself.
  - `cacheTypeK` / `cacheTypeV` (`--cache-type-k` / `--cache-type-v`, default empty) — KV-cache
    quantization, the most direct trade of quality for context length: at `q8_0` the cache costs about
    half of `f16`, so a larger `contextSize` (or `swaFull`) fits the same VRAM. Carried as a `String`
    through the `config` package — the `jniConfinedToProvider` ArchUnit rule keeps `net.ladenthin.llama`
    types out of it — and resolved to the binding's `CacheType` inside the provider, matched
    case-insensitively against the CLI strings the enum itself declares. An unrecognised value is
    **rejected**, and the message names which of the two knobs was wrong, since one resolver serves both.
  - `flashAttn` (`--flash-attn`, default `false`) — lower KV memory and faster attention where the
    backend supports it, and the precondition for quantizing the V cache.
  - `batchSize` / `ubatchSize` (`--batch-size` / `--ubatch-size`, default `-1`) — prefill sizing, and
    prefill is what dominates an indexing run: every file is one large prompt with a short answer. The
    binding's own default for both is `0`, which llama.cpp reads as "decide for me", so `0` is not a
    meaningful user value and the guard is `> 0`.
  - `threadsBatch` (`--threads-batch`, default `-1`) — prompt-processing threads, separate from the
    decode `threads`. The two optima differ on machines with efficiency cores; `-1` keeps llama.cpp's
    behaviour of reusing `threads`.

  `srcmorph:calibrate` is what makes these measurable rather than guesswork: it reports prefill and
  decode throughput, and — since this release — the prompt-cache reuse the extra KV room buys.

  Note the cost this exposed: `LlamaCppJniConfig`'s positional constructor reached 37 arguments. It was
  covered field-by-field by `LlamaCppJniConfigFactoryTest`, which gives every field a distinct value so a
  mis-ordered pair fails — but the shape was past its limit, and this same release replaces it with a
  builder (see *Changed* below). The constructor is private now; nothing outside the class can call it.

- **Fail-fast check on a routed model's `<modelPath>`.** A typo previously survived the whole plan
  phase — walk, classify, rendered plan — and only died inside the native loader, which with several
  model groups means *after* the earlier groups had already generated. The check joins the fail-fast
  block the engines already run.

  Two placement constraints, both load-bearing. It is **gated on
  `generationProvider == llamacpp-jni`**, because only that provider loads a GGUF and every shipped
  example deliberately points at a non-existent `unused-with-mock-provider.gguf` while running the
  mock; an ungated check would red the examples, their binding tests, the CLI end-to-end test and the
  fat-jar release smoke. And it runs **after the `planOnly` early-out**, because `planOnly` is
  documented to "stop before loading any model" and `Plan` is the CLI's default command — the
  workflow it serves is configuring routing on a machine where the GGUFs are not present and running
  for real on the one that has them. Stat'ing a file is not loading it, but failing the plan on a
  missing model would break that workflow all the same.

  The ordering was wrong in the first cut and no test saw it: every other `GenerateEngineTest`
  fixture uses the `mock` provider, so the check never ran through the engine at all. Both halves are
  now pinned — a plan-only run with the real provider and a missing model still plans; the same
  configuration without `planOnly` still fails fast.

- **Every model knob is now exercised against the real model, one knob per test case.**
  `LlamaCppJniKnobSweepTest` sets each of the 34 sweepable `LlamaCppJniConfig` knobs to a non-default
  value in turn and runs a real generation with the committed 135M model. The failure class it exists
  for is precisely the one that produced this release's two provider fixes: a knob is not exercised by
  being *set*, it is exercised by the binding *accepting* the value derived from it, and those two came
  apart twice — `dryPenaltyLastN` at its own `-1` default (rejected outright, so every generation threw
  before a token) and `flashAttn` (mapped onto a setter that cannot express what llama.cpp now
  demands). Neither is visible to a mapping unit test, and neither is visible to the rest of the suite,
  which runs on the `mock` provider and never loads the native library.

  Per-knob rather than all-at-once on purpose: an all-knobs config catches the same breakage but names
  no culprit, and one rejected value masks every knob behind it. 36 cases in ~35&nbsp;s. Three knobs
  are excluded with stated reasons (`modelPath`, `devices`, `flashAttn`), and a reflective completeness
  check fails the class when a knob is neither swept nor excluded — in **both** directions, plus a
  count assertion so an empty sweep list cannot satisfy it. A knob added later therefore reds this test
  until somebody decides how it is covered. Verified by falsification: a deliberately invalid value
  reds exactly the case carrying it and names the knob.

- **The Maven plugin is now run as a plugin in CI** (`plugin-it` job, `.github/plugin-it.sh`,
  fixture in `.github/plugin-it/`). Until now every check on `srcmorph-maven-plugin` called Java
  methods directly -- `PluginArchitectureTest`, `MojoPhaseSkipTest`, `MojoConfigurationMappingTest`,
  PIT at 62/62 -- so nothing covered the parts only Maven performs: plexus binding of the
  `<configuration>` XML onto the `@Parameter` fields (the CLI's Jackson binding is a different code
  path), goal-prefix resolution, the `srcmorph.*` property strings, lifecycle-phase binding, and the
  descriptor `maven-plugin-plugin` generates. **A renamed `@Parameter` property would have shipped
  green.**

  Six checks: the full lifecycle with all three goals configured entirely from the fixture's pom XML
  (the nested `<condition><extensions>` routing tree, both `<subtrees>` entries, `<excludes>`, the
  per-execution `<configuration>` override, and both readonly `${project.*}` injections); the
  generated descriptor read out of the packaged jar (goal prefix, the four goal names, and a
  **two-way diff of all 19 `srcmorph.*` property strings**, so a rename fails and a new property
  fails until it is listed deliberately); `mvn srcmorph:generate -Dsrcmorph.planOnly=true` via the
  goal prefix; `-Dsrcmorph.aiVersion=9.9.9` reaching the written document header; all four skip
  properties including one **off-diagonal** case (`srcmorph.file.skip` must not skip the packages
  goal -- the diagonal alone cannot catch a copy-pasted property); and the `calibrate` goal, the one
  goal the lifecycle run does not reach. The fixture uses the default `mock` provider, so no GGUF,
  no GPU and no network.

  The fixture's `<configuration>` deliberately mirrors the worked example in
  `srcmorph-maven-plugin/README.md`, so it also fails when the documented XML stops being the XML
  that works.

  Every assertion was falsified rather than assumed, and that pass rewrote three of them. A first
  version passed a `settings.xml` whose `<pluginGroups>` entry was believed necessary for
  `mvn srcmorph:generate` to resolve; it is not (Maven matches the prefix against the descriptor of
  the plugin the fixture declares), and adding it opened a second resolution path through repository
  metadata that **masked a changed goal prefix entirely** -- on a pristine runner too, since Central
  serves that metadata. Removing the file turned that check into a real second guard. Separately,
  `<subtrees>`, `<excludes>` and all three execution-level `<configuration>` blocks could each be
  deleted with the test staying green -- the first because the fixture's only value equalled the
  engine's own fallback, the second because the pattern matched no file that existed, the third
  because the overrides changed nothing observable. All three are now pinned by a fixture that makes
  them observable.

- **The classifier fat jars are verified before they are attached** (`.github/verify-classifier-fatjars.sh`).
  The publish jobs build seventeen fat jars — one per `net.ladenthin:llama` native classifier plus the
  default — and exactly one of them was ever checked: `smoke-fatjar` launches the default. The sixteen
  GPU jars still cannot be *launched* meaningfully (a GitHub-hosted runner has no such device, and the
  only command that loads the native library is a real generation), so the script asserts instead that
  each is the artifact its name claims: one jar per requested classifier and no unexpected one, a
  native library present, the native for the OS/arch the name promises, a default jar spanning more
  than one OS, and — the load-bearing check — a native set that **differs** from the default jar's. If
  `-Dllama.classifier=` ever stops being wired through, Maven resolves the default artifact and the
  loop ships sixteen copies of the CPU build under GPU names; nothing else in the pipeline would
  notice. A classifier shape nobody has mapped fails the script rather than passing unchecked.

- **A release can no longer attach an unsigned asset quietly.** The attach jobs collect
  `target/*.jar.asc` with `|| true`, so a signing step that produced nothing yielded an attach that
  looked complete and was not. The check deliberately does **not** gate the upload: both attach jobs
  run even when the publish job failed, because when Central is unreachable the GitHub assets are the
  only way to get the build output at all. So it reports before the upload, uploads unconditionally,
  and fails the job afterwards — the assets always land, an unsigned release is loudly red instead of
  quietly wrong. The same two steps are now byte-identical in all four sibling repositories.

### Changed
- **Two further public constructors gained a parameter, and a mojo parameter was removed.** Neither was
  listed as breaking when the changes landed; both are, so they are recorded here rather than left for a
  consumer to discover at compile time.

  `AiGenerationTimings` and `AiCalibrationMeasurement` each went from five parameters to six, both
  gaining `cachedPromptTokens` — the `cache_n` value this release surfaces. Anyone constructing either
  directly must add the argument; both are value types a downstream extension could plausibly build.

  `srcmorph.llama.libraryPath` is gone from the plugin (see *Removed*), and Maven does not ignore an
  unknown `<configuration>` element: a consumer POM still declaring it fails the goal outright rather
  than warning. That is the intended outcome — a parameter that never worked should not keep looking
  like it does — but it is a hard break on first invocation, not a silent one. CLI users hit the same
  wall at parse time, because both JSON and YAML are read with `FAIL_ON_UNKNOWN_PROPERTIES` enabled.

- **`LlamaCppJniConfig` is built through a builder; its positional constructor is private.** Breaking for
  anyone who constructed one directly — deliberately taken in this release rather than later, because the
  class is already breaking here and a second break for the same type is worse than one.

  It had grown to a **37-argument** constructor, which is past the point where a call site can be read or
  a swapped pair of same-typed neighbours is noticeable: the compiler accepts
  `…, batchSize, ubatchSize, …` in either order. `LlamaCppJniConfig.builder(modelPath)` replaces it, and
  `modelPath` — the one value with no meaningful default — is required at the entry point rather than
  checked in `build()`, so a half-formed builder cannot be constructed at all.

  The builder is also where the defaults now live, and that removes a real duplication:
  `LlamaCppJniConfigFactory.fromFallbackParameters` used to restate all 30+
  `AiGenerationConfig.DEFAULT_*` constants by hand, so a knob added to `AiGenerationConfig` could end up
  with a different default there by simple omission. It is now five lines. Each builder field starts at
  the matching `AiGenerationConfig.DEFAULT_*`, and a `null` on a String or list setter restores that
  knob's default instead of storing `null` — note this is "the default", not "empty":
  `reasoningEffort(null)` gives back `"low"`.

  Two tests carry this, and both were verified to fail against a deliberately broken build rather than
  assumed to: `builder_leavesEveryUnsetValueAtItsAiGenerationConfigDefault` (every unset value equals its
  config default — reds when one builder field is initialised to `0` instead) and
  `builder_everySetterWritesItsOwnField` (36 distinct values — reds when `ubatchSize(…)` is made to write
  `batchSize`). The old positional-constructor tests they supersede are removed.

- **`net.ladenthin:llama` 5.0.6 → 5.1.0** (llama.cpp b10456 → b10682). Unlike the earlier bumps in
  this series, 5.1.0 is **not** purely additive: it deprecates six `InferenceParameters` methods that
  were always silent no-ops, and this provider called one of them — `withUseChatTemplate(true)`.
  Because `srcmorph` compiles with `-Xlint:all -Werror`, that deprecation is a **compile error**, so
  the bump would have broken the build. The call was **removed rather than suppressed**: it never had
  an effect (upstream reads no such key from a request body), and llama.cpp defaults `use_jinja` to
  true, so chat templating and tool calling are unchanged. The rest of the surface this provider uses
  — `LlamaModel`, `InferenceParameters`, `ModelParameters`, `ChatResponse`/`Timings`/`Pair`,
  `ChatResponseParser`, `ReasoningFormat` — is untouched by 5.1.0. Verified by a full reactor
  `clean test`: 639 / 39 / 32 tests, 0 failures, 0 skipped, all four modules SUCCESS.

- CI actions bumped to latest: `actions/setup-java` v5 → v6.

- **Build tooling bumped and NullAway aligned with the sibling repos**: `nullaway` 0.13.8 → 0.14.0,
  `spotless-maven-plugin` 3.10.0 → 3.10.1, `palantir-java-format` 2.96.0 → 2.97.0, `pitest-maven`
  1.25.9 → 1.30.0. All three of the first are declared **per module** here (`srcmorph`,
  `srcmorph-cli`, `srcmorph-maven-plugin`), not in the reactor parent, and `pitest-maven` has two
  separate version declarations — every one was moved. nullaway is an alignment rather than a plain
  bump: streambuffer had already merged a Dependabot bump to 0.14.0, so the four repos had silently
  stopped being identical. Deliberately **not** taken: `jqwik` 1.9.3 → 1.10.1, forbidden by
  [`workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md).

- `AiGenerationConfig.getStopStrings()` no longer declares a `@Nullable` return. The field is
  initialised to an empty list and the setter normalises `null`, so the documented "or `null` if not
  set" case was unreachable; the getter now matches its sibling `getDrySequenceBreakers()`. The two
  null-guards this made dead in `LlamaCppJniConfigFactory` were dropped with it — the real guard lives
  in `LlamaCppJniConfig`'s constructor, and is now pinned directly by a new `LlamaCppJniConfigTest`.

- **PIT gate widened from 632 to 775 mutations in the core module, all killed at `mutationThreshold` 100.** Newly gated:
  `provider.LlamaCppJniConfig`, `config.AiConditionGroup` (both already at 100% with no new test —
  `TODO.md` had listed them as needing "careful fixtures", which was stale), `document.AiMdDocumentCodec`
  and `indexer.AiIndexPlan` (survivors killed here). `document.AiMdHeaderCodec` is documented as
  permanently out: its last two survivors are equivalent mutants in the colon-position guard, unkillable
  through the public API. Coverage was also added for the CLI's `.js`/`.yml` extension aliases and all
  six `CCommand` dispatch arms, the plugin's `buildConfiguration()`/`messageOf()`, and `GenerateEngine`'s
  missing-subtree and unknown-`factsKey` paths. The count reached 775 by release: later work in this
  cycle added `provider.GgufModelInfo` to the list and grew the gated classes to 52. Measured on
  `main` at release time: **775 / 16 / 62**, one hundred percent in each of the three modules.

- **Line-based routing is now covered end to end.** `<lines>` is documented in the plugin README and
  the condition layer was tested directly, but no test ever made the indexer's `anyRuleUsesLines`
  return true, so `countLines` never ran during planning. A silent failure there would send every
  file to the fallback rule with nothing failing. Verified by inverting the `usesLines` check: the
  new test goes red. Writing it also surfaced a semantic worth knowing — a condition node evaluates
  exactly one leaf and returns on the first present, with `extensions` before `lines`, so combining
  the two needs an `<and>` group; the first version of the fixture set both on one node and matched
  on extension alone.

- **`AiSourceChunker`'s observable boundaries are pinned** (28/34 mutants, up from 25): `maxChars == 1`,
  the guard that stops the last chunk being trimmed and re-split, and the guard that stops a chunk
  beginning on a newline collapsing to a single character — the last two are silent-content-change
  shapes. The six remaining mutants are equivalent (capacity hint, empty-list return, two arithmetic
  terms an enclosing `Math.max` absorbs, a loop head an earlier `break` already guarantees, and a
  subset branch that at equality reproduces its input); they are enumerated in `TODO.md` so nobody
  re-hunts them.

### Fixed
- **The `llamacpp-jni` provider produced nothing at all, and had done so since 1.1.0.** Every real
  generation threw `IllegalArgumentException: Invalid dry_penalty_last_n value: -1` before a single
  token, because `buildInferenceParameters` forwarded the DRY penalty window unconditionally while
  the window's default is the `-1` sentinel and the binding rejects any negative value — llama.cpp
  b10273 dropped the old "`-1` = context size" meaning, and `net.ladenthin:llama` has enforced that
  since 5.0.5. The neighbouring `repeatLastN` was already guarded with `>= 0`, and its comment even
  spells out the rule; DRY's window was written earlier and never caught up. Both are guarded now,
  so `-1` means "send nothing, llama.cpp keeps its own window", which is what the javadoc always
  claimed. That javadoc was wrong too, on five sites, and is corrected.

  What let it survive two releases is worth recording. The belief behind the unguarded call is in
  the code: *"multiplier 0.0 (default) = off, so the base/allowed-length/penalty-last-n knobs have
  no effect unless opted in."* True of the window's **effect** — and irrelevant, because the wither
  **validates** whatever it is handed, DRY enabled or not. Everything that runs in CI uses the
  `mock` provider (the test suite, `Plan`, the fat-jar smoke), so no gate ever touched the failing
  path, and every one of them stayed green throughout.

- **The three real-model tests now run** instead of being disabled by a flag nobody set.
  `-DrunNativeLlamaTests=true` dates from the initial commit, when the repository carried no model
  and a developer had to supply one — a correct opt-in at the time. The model was committed six
  minutes later, and the flag was never revisited: it appears in **no** workflow and **no** POM in
  the repository's entire history, so those tests had never executed anywhere while the ~90 MB GGUF
  they need was checked out on every job. Turning them on found the defect above in ten seconds.
  The gate is now a capability check (`NativeLlamaAvailability`): the tests run by default and skip
  only where the model or a loadable native library is genuinely absent, each skip naming its cause.

- **`warnOnTruncatedAnswer` and `logPromptCacheReuse`, both added in this release, had no executed
  coverage at all** — they sit on the same provider path. Five model-free tests now drive them from
  a hand-built `ChatResponse`, pinning the `"length"`-versus-`"stop"` decision (the vocabulary trap
  the code documents), the empty-`choices` guard, and the `DEBUG` line's three counts. Four further
  tests pin the penalty-window guards without a model, so the regression is caught on every platform
  rather than only where a GGUF is present; removing either guard reds them.

- **`publish.yml` concurrency group: every non-PR run now gets its own group.** The block previously
  claimed a push to `main` or a `v*` tag *"always runs to completion"* because `cancel-in-progress`
  is scoped to `pull_request`. GitHub cancels a **pending** run whenever a newer run joins the same
  group behind an in-progress one, and that rule is **independent of** `cancel-in-progress` — so a
  queued release run on `main` could be dropped silently by a later push. The group expression now
  appends the unique `github.run_id` for non-PR runs; PR runs still share a group per ref and
  supersede each other as intended.

- Bumped `jackson.version` 2.22.0 → 2.22.2 (`jackson-databind` / `jackson-dataformat-yaml`,
  pinned in the parent `pom.xml`) to close
  [GHSA-5jmj-h7xm-6q6v](https://github.com/advisories/GHSA-5jmj-h7xm-6q6v) (CVSS 5.3, Medium),
  flagged by OSV-Scanner against `srcmorph/pom.xml` and `srcmorph-cli/pom.xml` after the `main`
  merge of the relocation-stub removal.

- **`AiMdHeaderSupport.shouldWrite`'s change-detection chain was not covered by a single test.** The
  seven-way header comparison that decides whether a `.ai.md` is regenerated could be replaced
  wholesale with `return false;` and the class's tests stayed green — verified, not inferred. Two of
  them wrote the fixture document with a header but no body, so the method returned at its blank-body
  guard before the comparison ever ran. Since checksum-driven regeneration is a stated design
  principle, dropping any disjunct would have meant stale files silently never regenerating, with
  nothing failing. Both tests now write a body, and a parameterized case covers each compared field
  (`h`/`x`/`title`/`c`/`d`/`g`/`a`) plus the unchanged-header negative. Re-running the same mutation
  now fails 9 of 15 tests, and removing a single disjunct fails 2.

- **`srcmorph:calibrate`'s chars-per-token silently depended on a cache hit nothing checked.** It divided
  the near-window run's *source* characters by the prompt tokens the model had *evaluated* — a figure that
  excludes whatever the KV cache served. That is only the source's own token count while the base prompt
  is a cache hit; with no reuse the same divisor also covers the base prompt, so the ratio came out too
  low and the plan's time estimate with it. Nothing enforced the assumption and, until `cache_n` was
  surfaced, nothing could even observe whether it held. The ratio is now read from the mid&rarr;near
  *size differential* over `totalPromptTokens()` (evaluated **plus** cache-served): both runs share the
  same base prompt, so subtracting them cancels it and the result describes the source text alone,
  identically whether or not the prefix was reused. A regression test measures the same prompt twice —
  once fully reused, once not — and requires both to agree; it fails on the previous formula.

- **No `execute()` verified that it honours the skip flag.** `shouldSkip()` itself was well tested, but
  a mojo that never called it would have passed every one of those tests — and would have loaded a
  model and written files under `-Dsrcmorph.skip=true`. All four goals now assert it, `CalibrateMojo`
  included (it previously had no test constructing it at all).

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

- **`llamaLibraryPath` / `srcmorph.llama.libraryPath` — a parameter that never worked.**

  It was declared on every mojo and threaded through
  `SrcMorphConfiguration`, `EngineSupport`, both `LlamaCppJniConfigFactory` methods and
  `LlamaCppJniConfig` — and no code read it, so setting it was a silent no-op. Git history shows this
  was never a working feature a refactor broke: in `a1df3e0 "Initial version."` it is already the
  first field of the then six-field `LlamaCppJniConfig` record, next to `modelPath`, while that
  commit's provider built its `ModelParameters` from `modelPath` / `contextSize` / `threads` alone.

  It is removed rather than implemented, because the binding already offers the same knob and offers
  it *better*. `net.ladenthin.llama.lib.path` is an ordinary JVM system property and the loader's
  highest-precedence source; `MAVEN_OPTS` and `.mvn/jvm.config` set it before the JVM starts, so it
  is in place before any class loads. A plugin parameter can only act once the provider builds a
  model — by then `LlamaModel`'s static initializer may already have resolved the library, and every
  later value is silently ignored. Implementing it would have added a weaker path with a
  silent-failure mode alongside one that always works. Verified empirically that the property reaches
  the build JVM: `mvn -Dnet.ladenthin.llama.lib.path=…` is readable via `System.getProperty` from
  inside the build.

  **Migration:** replace `<llamaLibraryPath>DIR</llamaLibraryPath>` with
  `-Dnet.ladenthin.llama.lib.path=DIR` on the command line, in `MAVEN_OPTS`, or in `.mvn/jvm.config`.
  A POM that still sets the element will fail with an unknown-parameter error; it was doing nothing
  before, so no behaviour changes with it gone. The plugin README's GPU section already documented
  the property as the runtime override.

- **`config.AiGenerationKind` — dead code from a superseded design.** The enum (`FILE_SUMMARY`,
  `PACKAGE_SUMMARY`, `FILE_KEYWORDS`, `PACKAGE_KEYWORDS`) is the original *fixed* generation taxonomy
  from when the goals were `SummarizeFilesMojo` / `SummarizePackagesMojo`, and `8d1be51` and `0a6f462` deliberately replaced
  that with configurable prompt keys and rule routing — which is design principle 6, "prompt templates
  and routing rules are data, never hardcoded in Java". No production class referenced it in any
  commit. Its two apparent users were concurrency-infrastructure examples that had borrowed the name:
  the Lincheck one never touched the enum at all (it exercises an `AtomicInteger`) and is now
  `AtomicCounterLincheckTest`; the jcstress one now races a real enum and is `AiOversizeStrategyRace`.
  Both examples are kept — only their misleading names and the dead enum are gone. It was `public` in
  a published artifact, so formally a breaking change; nothing could construct or receive it, so
  practically not.

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
