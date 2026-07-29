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
  `document.AiMdDocumentCodec` / `AiMdHeaderCodec`, `prompt.AiPromptPreparationSupport`, and the newer
  `indexer.AiIndexPlan` / `config.AiConditionGroup`. The orchestration layers (`indexer.*` walk,
  the plugin's `mojo.*`) and the JNI provider stay out of PIT — they need a Maven/native context
  rather than pure-unit mutation (see crossrepostatus "Deliberate non-parity").

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory (declared in `srcmorph/pom.xml`, the only reactor module with a jqwik test dependency).

- **`@VisibleForTesting` audit.** No usages currently in any module. Walk each module's production tree for package-private/protected methods or fields that exist purely so tests can reach them, and either annotate (`com.google.common.annotations.VisibleForTesting`) or move into the test source tree.

- **Null-safety refinement.** JSpecify + NullAway are enforced at compile time in strict JSpecify mode in every module (see each module's own `pom.xml`); `@NullMarked` on the package; framework-populated POJOs carry class-level `@SuppressWarnings({"NullAway.Init","initialization.fields.uninitialized"})`. Open follow-up: review remaining unannotated public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default.

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review, package hierarchy review, and class/method naming review. This repo has no `@VisibleForTesting` usages today; the package and naming reviews are still open here.
