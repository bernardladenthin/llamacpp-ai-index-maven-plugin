# srcmorph (core library)

`net.ladenthin:srcmorph` is the framework-free Java API behind the
[srcmorph](../README.md) project: a prompt-driven source-tree transformer built on local llama.cpp
(GGUF) models. It has **no dependency on the Maven Plugin API** (enforced by an ArchUnit rule —
see `CoreArchitectureTest#coreIsMavenFree`), so it can be embedded in any JVM tool: a Maven plugin (see
[`../srcmorph-maven-plugin/`](../srcmorph-maven-plugin/README.md)), a standalone CLI
(see [`../srcmorph-cli/`](../srcmorph-cli/README.md)), a Gradle plugin, a test harness, or plain code.

Package root: `net.ladenthin.srcmorph`.

## The shared configuration object: `SrcMorphConfiguration`

`net.ladenthin.srcmorph.config.SrcMorphConfiguration` is one mutable JavaBean holding everything a
run needs. It is the single object every binding surface populates the same way — Maven plexus
reflection (structural XML binding, no annotations needed), a Jackson `ObjectMapper`/`YAMLMapper`
(the CLI), or plain Java setter calls. **Field names intentionally mirror the Maven plugin's
`@Parameter` names**, so a JSON/YAML key reads identically to the matching `<configuration>` XML
element.

Major field groups:

| Group | Fields |
|---|---|
| Source selection | `baseDirectory`, `subtrees`, `excludes`, `fileExtensions`, `minFileSizeBytes`/`maxFileSizeBytes` |
| Output | `outputDirectory`, `force`, `planOnly` |
| Routing & prompts | `promptDefinitions` (`List<AiPromptDefinition>`), `aiDefinitions` (`List<AiModelDefinition>`), `fieldGenerations` (`List<AiFieldGenerationConfig>` — the routing rules), `factDefinitions` |
| AI backend | `generationProvider` (`"mock"` or `"llamacpp-jni"`), plus the `llama*` fallback params (`llamaModelPath`/`llamaContextSize`/`llamaMaxOutputTokens`/`llamaTemperature`/`llamaThreads`), used only when `fieldGenerations` is empty |
| Header metadata | `pluginVersion`, `aiVersion`, `projectName` |

Every other `config/` class is a plain, Maven-annotation-free JavaBean too: `AiModelDefinition` (a
named, complete set of GGUF sampling parameters), `AiPromptDefinition` (a named prompt template —
its `template` string is the system instructions, used verbatim; `AiPromptSupport` automatically
appends the file/package name and the source text as a separate user message, so a template needs no
placeholder), `AiFieldGenerationConfig` (one routing rule: a
`condition` tree, `priority`, and either a route, a `skip`, or the explicit `fallback`),
`AiCondition`/`AiConditionGroup`/`AiRangeCondition` (the composable and/or/not condition tree over
extensions/size/lines/modified-time/path-glob), `AiFactCounter`/`AiFactDefinition` (deterministic
regex-count "facts" prepended to a generated body), `AiOversizeStrategy` (`fail`/`sample`/
`mapReduce`/`deterministic` — what a rule does when a file is larger than its model's context
window), `AiCalibration` (per-machine measured throughput, pasted onto an `AiModelDefinition`).

## The four engines

Each engine is constructed from one `SrcMorphConfiguration` and owns its own AI provider lifecycle
(try-with-resources; one model resident in memory at a time). All four throw the checked
`net.ladenthin.srcmorph.engine.SrcMorphException` on misconfiguration (an invalid rule set, an
unmatched file with no fallback, an oversized file with `onOversize=fail`, a bad prompt/model
reference) and let a plain `java.io.IOException` propagate for genuine I/O failures — callers (the
Maven plugin's mojos, the CLI's `Main`) translate these into whatever their own surface expects.

| Engine | Purpose | Result |
|---|---|---|
| `GenerateEngine` | Phase 1: plans the whole run (which model + prompt each file gets, or skip/unmatched, and whether it fits its routed model's context window), fails fast on an unmatched file or a hard oversize failure, stops after the plan when `planOnly` is set, otherwise loads each distinct routed model once and indexes its files. | `GenerateResult` (`planOnly()`, `written()`, `unchanged()`, `skipped()`) |
| `AggregatePackagesEngine` | Phase 2: aggregates every package beneath the output directory into a `package.ai.md` per directory. | `int` — package index files written or refreshed |
| `AggregateProjectEngine` | Phase 3: harvests every package's one-line lead into a single `project.ai.md` table of contents. Fully deterministic by default (no model call); when `fieldGenerations` has at least one entry, its **first** entry is used for one extra AI call that writes a short `#### Overview` paragraph. | `int` — `1` if written/refreshed, `0` otherwise |
| `CalibrateEngine` | Loads each distinct model a `GenerateEngine` run would load, measures its prefill/decode throughput via a couple of representative generations, and returns a paste-ready report. | `CalibrationReport` (`measurements()`, `renderXml()` — a paste-ready `<calibration>` XML block per model) |

**A design nuance worth knowing if you drive more than one engine off the same
`SrcMorphConfiguration`** (as the CLI's `All`/`Calibrate` commands do): `GenerateEngine` *routes* each
file to exactly one matching `fieldGenerations` rule (by `condition` + `priority`, with the explicit
`fallback` catching the rest). `AggregatePackagesEngine` and the optional project overview do **not**
route by condition — `AggregatePackagesEngine` applies **every** entry in `fieldGenerations` to every
package in list order (the last entry's output wins), and `AggregateProjectEngine`'s optional overview
uses only `fieldGenerations.get(0)`. A single fallback-only rule (no condition needed) behaves
identically across all three phases; a multi-rule routing table is meaningful for `GenerateEngine`
but you should reason explicitly about what happens when the same list feeds package/project
aggregation. See `srcmorph-cli/README.md`'s config-file reference for the CLI's own take on this.

## Use as a library

```java
import java.io.File;
import java.util.Collections;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.engine.GenerateEngine;
import net.ladenthin.srcmorph.engine.GenerateResult;
import net.ladenthin.srcmorph.engine.SrcMorphException;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;

SrcMorphConfiguration config = new SrcMorphConfiguration();
config.setBaseDirectory(new File("."));
config.setOutputDirectory(new File("target/srcmorph-ai"));
config.setGenerationProvider("mock"); // or "llamacpp-jni" with a real GGUF model below

AiPromptDefinition prompt = new AiPromptDefinition();
prompt.setKey("file-body");
prompt.setTemplate("Summarize this file as structured markdown.");
config.setPromptDefinitions(Collections.singletonList(prompt));

AiModelDefinition model = new AiModelDefinition();
model.setKey("coder");
model.setModelPath("/path/to/model.gguf"); // unused by the mock provider
config.setAiDefinitions(Collections.singletonList(model));

AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
rule.setPromptKey("file-body");
rule.setAiDefinitionKey("coder");
rule.setFallback(true);
config.setFieldGenerations(Collections.singletonList(rule));

try {
    GenerateResult result = new GenerateEngine(config).execute();
    System.out.println("written=" + result.written() + " unchanged=" + result.unchanged());
} catch (SrcMorphException | java.io.IOException e) {
    // handle: misconfiguration vs. I/O failure
}
```

## Provider abstraction

`net.ladenthin.srcmorph.provider.AiGenerationProvider` (a `Closeable`) abstracts the AI backend.
`AiGenerationProviderFactory` looks up an implementation by name:

- `"mock"` (default) — `MockAiGenerationProvider`, deterministic canned responses; no model, no JNI,
  no native library required. Used throughout every module's test suite.
- `"llamacpp-jni"` — `LlamaCppJniAiGenerationProvider`, backed by
  [`net.ladenthin:llama`](https://central.sonatype.com/artifact/net.ladenthin/llama) (the llama.cpp
  JNI binding). This is the **only** package in this library that touches the JNI binding
  (`CoreArchitectureTest#jniConfinedToProvider`).

## Package layout

```
net.ladenthin.srcmorph/
├── config/     mutable JavaBeans; SrcMorphConfiguration is the shared root object
├── engine/     GenerateEngine, AggregatePackagesEngine, AggregateProjectEngine, CalibrateEngine,
│               SrcMorphException, GenerateResult, CalibrationReport, EngineSupport
├── indexer/    the walk/plan/write orchestration engines delegate to
├── document/   the .ai.md model + codecs (AiMdDocument, AiMdHeader, AiMdDocumentCodec, ...)
├── prompt/     prompt template lookup + preparation (trimming to a model's context window)
├── provider/   the AI backend abstraction (see above)
└── support/    foundation helpers with no dependency on anything above them
```

See [`../CLAUDE.md`](../CLAUDE.md) for the full architecture rules (`CoreArchitectureTest`), the
layered-package dependency direction, and the PIT mutation-testing scope (currently 47 classes at
100%).

## Testing

Every test in this module is model-free by default (`MockAiGenerationProvider`); a handful of
real-model tests are gated on `src/test/resources/SmolLM2-135M-Instruct-Q3_K_M.gguf` and self-skip
when the native library is unavailable. See the root [`TEST_WRITING_GUIDE.md`](../TEST_WRITING_GUIDE.md)
for conventions and [`../CLAUDE.md`](../CLAUDE.md) for how to run a single test class.
