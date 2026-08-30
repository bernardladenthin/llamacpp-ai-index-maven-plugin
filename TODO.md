# TODO — srcmorph reactor (Maven plugin now `srcmorph-maven-plugin`, formerly `llamacpp-ai-index-maven-plugin`)

Open work items for this repo. Cross-cutting tracking lives in
[`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md); items here are
repo-specific or this repo's slice of a cross-cutting initiative. Completed work is
recorded in git history and `crossrepostatus.md`, not here.

## Open

- **PIT mutation-testing gate for `srcmorph-cli` and the plugin module.** Only `srcmorph`
  (`srcmorph/pom.xml`) is PIT-gated today — `<mutationThreshold>100</mutationThreshold>` over an
  explicit `<targetClasses>` list of 47 classes across config/document/engine/indexer/prompt/
  provider/support, all killed at 100%. Neither `srcmorph-cli` nor
  `srcmorph-maven-plugin` has a `pitest-maven` execution of its own yet (both poms document
  this explicitly with a comment at the spot a PIT plugin block would go). For the CLI: the pure
  helpers worth mutation-gating are the config copy/round-trip (`Main#copyWithPlanOnlyForced`) and
  command dispatch; `Main`'s I/O-heavy entry points (`main`, `loadConfiguration`) are better served by
  integration tests than unit-mutation gating. For the plugin: the 4 goal mojos (+ the abstract AbstractAiIndexMojo base) are Maven-lifecycle
  orchestration (typically integration-tested via Maven invoker/executor, not unit-mutation-tested) —
  confirm that reasoning still holds before assuming it's a permanent exemption rather than a gap.

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

- **Prompt-cache reuse is observable during `calibrate`, not during a real `generate` run.** The
  provider now surfaces llama.cpp's `cache_n` as `AiGenerationTimings#cachedPromptTokens()`, and
  `srcmorph:calibrate` reports it per model (`AiCalibrationMeasurement#cachedPromptTokens()`), so the
  prefix-reuse settings — `cachePrompt`, `cacheReuse`, `swaFull` — can finally be measured instead of
  assumed. The indexing path does not see it: `AiFieldGenerationSupport` calls
  `AiGenerationProvider#generate(...)`, which returns only the text, so a full run still cannot say how
  much of each prompt was reused. Calibrate is a fair proxy (its runs share one system prompt, exactly
  like an indexing run does), which is why this is a follow-up and not a gap in the measurement itself.
  Switching the hot path to `generateWithTimings(...)` and logging the reuse at `DEBUG` would close it;
  weigh that against touching the per-file path and its tests for a diagnostic.

- **A PIT mutation minion runs out of memory on every `mutationCoverage` run — diagnosed, closed, not a
  defect.** The cause is an infinite-loop mutant, proven from the heap dump's own thread stack rather
  than inferred: PIT mutates the `i += 1` that advances the scan loop in
  `AiSourceExcludeFilter.globToRegex` (a gated class), the loop then appends `"[^/]"` forever, and
  `StringBuilder` grows until the heap ends. The surviving 1.2 GB array reads literally
  `^A[^/]A[^/]A[^/]…`, and the OOM frame sits under
  `AiSourceExcludeFilterTest.questionMark_matchesExactlyOneNonSeparatorChar`. `timeoutConstant` exists
  for runaway mutants, but this one allocates its way out of a 2 GB heap long before 30 s elapse; PIT
  restarts the minion and the mutation is killed on the retry, which is why every run still reports
  762/762, `MEMORY_ERROR 0`, exit 0.

  Nothing in production code needs changing — bounding `globToRegex` would be complexity added purely to
  appease a mutant, and no real `<excludes>` glob can produce a gigabyte-scale regex. What *was* wrong is
  where the crash diagnostics were applied: `srcmorph/pom.xml` now sets `parseSurefireConfig=false` plus
  explicit `<jvmArgs>` so minions stop inheriting surefire's `-XX:+HeapDumpOnOutOfMemoryError`. An OOM is
  a bug in a test JVM and a normal operating condition in a mutation minion; only the former deserves a
  1.2 GB `.hprof`. Twelve of them filled the disk mid-run once, and CI wrote one per run too.

  Dead ends, recorded so nobody repeats them:
  - `<jvmArgs>` alone is inert. Pitest places them *before* the inherited `argLine`, so surefire's
    `-Xmx2g` wins; the dump size did not move until `parseSurefireConfig=false` was added, then jumped
    1.2 GB → 2.2 GB.
  - `-Xmx4g` makes it worse, not better: two minions died at 2.2 GB each instead of one at 1.2 GB.
  - Not ArchUnit. Excluding `CoreArchitectureTest` — the top hit when the dump's class-name *strings* are
    counted — changed nothing. That count was a wrong inference; the class histogram (1.21 GB of `byte[]`,
    nothing else above a megabyte) and then the stack trace were what actually settled it.
  - Not the JaCoCo agent either; a control run without it still produced the same dump.

  To reproduce: add `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.` back to the `<jvmArgs>` block.

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory (declared in `srcmorph/pom.xml`, the only reactor module with a jqwik test dependency).

- **`@VisibleForTesting` audit.** No usages currently in any module. Walk each module's production tree for package-private/protected methods or fields that exist purely so tests can reach them, and either annotate (`com.google.common.annotations.VisibleForTesting`) or move into the test source tree.

- **Null-safety refinement.** JSpecify + NullAway are enforced at compile time in strict JSpecify mode in every module (see each module's own `pom.xml`); `@NullMarked` on the package; framework-populated POJOs carry class-level `@SuppressWarnings({"NullAway.Init","initialization.fields.uninitialized"})`. Open follow-up: review remaining unannotated public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default.

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review, package hierarchy review, and class/method naming review. This repo has no `@VisibleForTesting` usages today; the package and naming reviews are still open here.
