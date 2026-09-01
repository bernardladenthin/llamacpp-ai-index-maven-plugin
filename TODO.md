# TODO — srcmorph reactor (Maven plugin now `srcmorph-maven-plugin`, formerly `llamacpp-ai-index-maven-plugin`)

Open work items for this repo. Cross-cutting tracking lives in
[`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md); items here are
repo-specific or this repo's slice of a cross-cutting initiative.

**Completed work is not recorded here.** It lives in git history and in
`crossrepostatus.md`; a finished item is deleted from this file rather than annotated, so
everything below is genuinely still open.

## Open

- **Put `provider.LlamaCppJniAiGenerationProvider` on the PIT gate.** It is the one production class
  where a defect has actually reached users, and it is *not* on the `targetClasses` list — which is
  why the gate stayed at 775/775 through the 1.1.0-era `dry_penalty_last_n` regression and through
  its fix: the class generates no mutants, so neither the bug nor the tests that now cover it move
  the number. Do not read that stability as reassurance; PIT structurally could not have caught this.
  It is now worth adding, because the class finally has model-free coverage of the parts that matter
  (`buildInferenceParameters`, `warnOnTruncatedAnswer`, `logPromptCacheReuse`, `lazyMode`,
  `cacheType`). **Measure before committing to it**: the real-model tests take ~4-16 s each, and PIT
  re-runs every test covering a mutated line, so mutants in `model()` — the long `ModelParameters`
  chain — could make the run far slower than the current few minutes. If it does, the answer is
  probably `excludedTestClasses` for the real-model tests plus mutants restricted to the pure paths,
  not dropping the idea. Deliberately out of scope for 1.2.0: it is a build-time question, not a
  correctness one.

- **The sixteen GPU classifier fat jars are verified structurally, never launched.** Since 1.2.0
  `.github/verify-classifier-fatjars.sh` asserts each is the artifact its name claims (one jar per
  classifier, a native for the promised OS/arch, a native set that differs from the default jar's, so
  a broken `-Dllama.classifier=` cannot silently ship seventeen CPU builds). What it cannot assert is
  that the jar *works*: a GitHub-hosted runner has no CUDA/ROCm/SYCL/OpenVINO device, and the only
  command that would load the native library is a real generation. Closing this needs hardware —
  a self-hosted runner, or a manual pre-release pass on one GPU box per backend. Worth knowing which
  half is covered before reading the green check as "the CUDA jar runs".

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory (declared in `srcmorph/pom.xml`, the only reactor module with a jqwik test dependency).

- **`@VisibleForTesting` audit.** Nothing is annotated, but the members exist: `provider.LlamaCppJniAiGenerationProvider` has four (`buildChatTemplateKwargs`, `buildInferenceParameters`, `warnOnTruncatedAnswer`, `logPromptCacheReuse`) plus the static `tensorReadLazyMode`/`cacheType`, `document.AiMdDocumentCodec` has `read(List)`/`write`, `prompt.AiPromptPreparationSupport` has `trimSourceAtLineBreak`, and the three mojos have their `build*Configuration()`. Guava is not a dependency, so closing this means either a project-local marker annotation (there is precedent: `support.ConvertToRecord`) or recording that the convention is not adopted here. Decide and act rather than re-auditing.

- **Null-safety refinement.** JSpecify + NullAway are enforced at compile time in strict JSpecify mode in every module (see each module's own `pom.xml`); `@NullMarked` on the package; framework-populated POJOs carry class-level `@SuppressWarnings({"NullAway.Init","initialization.fields.uninitialized"})`. Open follow-up: review remaining unannotated public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default.

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review, package hierarchy review, and class/method naming review. This repo has no `@VisibleForTesting` usages today; the package and naming reviews are still open here.
