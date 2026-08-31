# TODO — srcmorph reactor (Maven plugin now `srcmorph-maven-plugin`, formerly `llamacpp-ai-index-maven-plugin`)

Open work items for this repo. Cross-cutting tracking lives in
[`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md); items here are
repo-specific or this repo's slice of a cross-cutting initiative. Completed work is
recorded in git history and `crossrepostatus.md`, not here.

## Open

- **Expand `srcmorph`'s own PIT mutation scope (optional).** `srcmorph/pom.xml` wires
  `<mutationThreshold>100</mutationThreshold>` over an explicit `<targetClasses>` list (config /
  document / engine / prompt / provider / support value+logic classes, plus `indexer.AiInputWindowCalculator`
  and `support.AiProgressBar`), all killed at 100%. Still out (optional, need careful fixtures):
  nothing, as it turns out: the remaining candidates were worked through and each is either now on
  the gate or documented below as unreachable.
  `document.AiMdDocumentCodec` reached 100% (13/13) and is now on the gate. **`document.AiMdHeaderCodec`
  is permanently out, and this is worth not re-litigating:** its last two survivors are *equivalent
  mutants* in the colon-position guard at `read`'s `colonIndex < 0 || colonIndex < HEADER_FIELD_PREFIX.length() + 1`.
  The preceding `startsWith(HEADER_FIELD_PREFIX)` guard means `colonIndex` is either `-1` or `>= 2`,
  so (a) the `< 0` boundary mutant `<= 0` differs only at the unreachable `colonIndex == 0`, and
  (b) the `+1 -> -1` arithmetic mutant differs only at `colonIndex == 2`, i.e. an empty field key,
  which `values.put("", value)` swallows invisibly because no `AiMdHeader` field is keyed `""`. Both
  are unkillable through the public API; reaching 100% would mean either exposing the parsed map or
  simplifying the (deliberately defensive, and strictly redundant) first disjunct away. The class's
  real coverage gaps *were* closed — the `read(Path)` overload and the malformed-input branches now
  have tests. **`support.AiSourceChunker` is out for the same reason** (28/34): its
  three genuinely observable boundaries are now pinned — `maxChars == 1`, the `end < length` guard
  that stops the *last* chunk being trimmed and re-split, and the `lastNewline > pos` guard that
  stops a chunk beginning on a newline collapsing to `"\n"` — but six mutants cannot be observed
  through the API at all: the `length / maxChars` ArrayList capacity hint (line 47), the empty-source
  `return chunks` versus `emptyList()` (49), the `maxChars - 1` overlap clamp and the `pos + 1`
  progress floor (51, 66) which the surrounding `Math.max` absorbs, the `pos < length` loop head (54)
  which the `end >= length` break already guarantees, and `select`'s `total <= maxChunks` (81), where
  the subset branch at equality computes `round(i * (total - 1) / (total - 1)) = i` and returns the
  same list. `config.AiConditionGroup`
  and `provider.LlamaCppJniConfig` were listed here too, but both measured 100% (2/2 and 36/36) without
  a single new test — the existing `AiConditionGroupTest` and
  `LlamaCppJniConfigFactoryTest#fromGenerationConfig_threadsEveryFieldThrough` already killed
  everything — so they are now on the gate. `prompt.AiPromptPreparationSupport` was likewise stale: it
  has been on the gate for a while. The orchestration layers (`indexer.*` walk,
  the plugin's `mojo.*`) and the JNI provider stay out of PIT — they need a Maven/native context
  rather than pure-unit mutation (see crossrepostatus "Deliberate non-parity").

- **Put `provider.LlamaCppJniAiGenerationProvider` on the PIT gate.** It is the one production class
  where a defect has actually reached users, and it is *not* on the `targetClasses` list — which is
  why the gate stayed at 775/775 through the 1.1.0-era `dry_penalty_last_n` regression and through
  its fix: the class generates no mutants, so neither the bug nor the tests that now cover it move
  the number. Do not read that stability as reassurance; PIT structurally could not have caught this.
  It is now worth adding, because the class finally has model-free coverage of the parts that matter
  (`buildInferenceParameters`, `warnOnTruncatedAnswer`, `logPromptCacheReuse`, `tensorReadLazyMode`,
  `cacheType`). **Measure before committing to it**: the real-model tests take ~4-16 s each, and PIT
  re-runs every test covering a mutated line, so mutants in `model()` — the long `ModelParameters`
  chain — could make the run far slower than the current few minutes. If it does, the answer is
  probably `excludedTestClasses` for the real-model tests plus mutants restricted to the pure paths,
  not dropping the idea. Deliberately out of scope for 1.2.0: it is a build-time question, not a
  correctness one.

- **Wire `flashAttn` up for real after `net.ladenthin:llama` 5.2.0 ships.** 1.2.0 *refuses* the knob
  rather than forwarding it, in two places (`LlamaCppJniAiGenerationProvider.model()` for the direct
  API path, `EngineSupport.validateFlashAttnIsNotRequested` for the plan phase), because the binding
  cannot currently spell it: `-fa` takes a mandatory `[on|off|auto]`, `enableFlashAttn()` routes
  through `setFlag` which stores `null`, and the argv renderer then emits the key with no token after
  it — so llama.cpp swallows the *next* argv token and the load dies naming a flag the user never set
  (`error: unknown value for --flash-attn: '--reasoning-format'`, reproduced against the shipped fat
  jar). No workaround exists downstream: `putScalar` is `protected`.

  When 5.2.0 lands with a value-taking setter: bump `llama.version`, replace both guards with the real
  call, delete `FLASH_ATTN_UNSUPPORTED_MESSAGE` and the four tests that pin the refusal (two in
  `EngineSupportTest`, one in `LlamaCppJniAiGenerationProviderTest`, and
  `LlamaCppJniKnobSweepTest#flashAttn_isRefusedRatherThanSilentlyDropped`), and restore the
  `flashAttn`/`cacheTypeV` guidance in `srcmorph-maven-plugin/README.md`. Then move `flashAttn` out of
  `LlamaCppJniKnobSweepTest.NOT_SWEPT` into a real sweep case — that is the step that finally sets the
  knob against a real model, which the refusal has never done. The sweep's completeness check keeps
  the bookkeeping honest: it fails if the knob is neither swept nor excluded, so it cannot be left
  half-migrated.

  **The trap to avoid: bumping `llama.version` alone changes nothing visible.** The guards keep
  throwing, the knob keeps looking broken, and every gate stays green — so this has to be an explicit
  line on the bump checklist rather than something a version bump surfaces on its own. The upstream
  fix is tracked in java-llama.cpp (`ModelParameters` needs `setFlashAttn(on|off|auto)` mirroring the
  `CacheType` enum pattern, `enableFlashAttn()` deprecated, and
  `ModelParametersExtendedTest.testToArrayComplexCombination` corrected — it currently pins the broken
  9-token argv shape as correct).

- **No CI run ever executes the Maven plugin.** `srcmorph-maven-plugin`'s mojos are covered by unit
  tests (`PluginArchitectureTest`, `MojoPhaseSkipTest`, PIT at 62/62) and by the `srcmorph-selftest`
  profile a human can run by hand — but no job in `publish.yml` performs a real
  `mvn srcmorph:generate` against a real Maven lifecycle. Everything CI proves about the plugin is
  proven by calling Java methods, so the parts only Maven exercises are untested: plexus binding of
  the `<configuration>` XML onto the `@Parameter` fields (the CLI's Jackson binding is a *different*
  code path, covered by `ExamplesConfigBindingTest`), goal-prefix resolution, the
  `${srcmorph.*}` property names, and the descriptor `maven-plugin-plugin` generates. A renamed
  property or a mistyped `@Parameter` would ship green. The fix is a `maven-invoker-plugin` IT: a
  tiny fixture project that runs one goal with the `mock` provider (no GGUF, no GPU, no network) and
  asserts the `.ai.md` it produced. Deliberately out of 1.2.0 — it is a new build plugin plus a
  fixture tree, not a one-line gate.

- **The sixteen GPU classifier fat jars are verified structurally, never launched.** Since 1.2.0
  `.github/verify-classifier-fatjars.sh` asserts each is the artifact its name claims (one jar per
  classifier, a native for the promised OS/arch, a native set that differs from the default jar's, so
  a broken `-Dllama.classifier=` cannot silently ship seventeen CPU builds). What it cannot assert is
  that the jar *works*: a GitHub-hosted runner has no CUDA/ROCm/SYCL/OpenVINO device, and the only
  command that would load the native library is a real generation. Closing this needs hardware —
  a self-hosted runner, or a manual pre-release pass on one GPU box per backend. Worth knowing which
  half is covered before reading the green check as "the CUDA jar runs".

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory (declared in `srcmorph/pom.xml`, the only reactor module with a jqwik test dependency).

- **`@VisibleForTesting` audit.** No usages currently in any module. Walk each module's production tree for package-private/protected methods or fields that exist purely so tests can reach them, and either annotate (`com.google.common.annotations.VisibleForTesting`) or move into the test source tree.

- **Null-safety refinement.** JSpecify + NullAway are enforced at compile time in strict JSpecify mode in every module (see each module's own `pom.xml`); `@NullMarked` on the package; framework-populated POJOs carry class-level `@SuppressWarnings({"NullAway.Init","initialization.fields.uninitialized"})`. Open follow-up: review remaining unannotated public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default.

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review, package hierarchy review, and class/method naming review. This repo has no `@VisibleForTesting` usages today; the package and naming reviews are still open here.
