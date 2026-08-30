# srcmorph-maven-plugin

A Maven plugin for generating hierarchical, AI-readable documentation of source code projects
using local llama.cpp-compatible models. It creates structured `.ai.md` files per source file and
aggregates them into package-level (and project-level) summaries for fast semantic navigation and
retrieval.

> This module is one of three in the [srcmorph](../README.md) Maven reactor — see the root README
> for the product overview, badges, and build status. **This plugin was renamed** from
> `net.ladenthin:llamacpp-ai-index-maven-plugin` to `net.ladenthin:srcmorph-maven-plugin` (goal
> prefix `srcmorph`, properties `srcmorph.*`) as part of the reactor migration's final step.
> Consumers still declaring the old coordinates are transparently redirected: the old artifactId was
> published once (`1.0.4`, a tiny relocation-stub POM with only a
> `<distributionManagement><relocation>` pointing here) and the redirect verified working — see the
> root README's migration note. Internally, the plugin depends on
> [`net.ladenthin:srcmorph`](../srcmorph/README.md) (a sibling reactor module) for all of
> its engine/indexing logic — the 5 Mojo classes in this module are thin wrappers that map their
> `@Parameter` fields onto a shared `SrcMorphConfiguration` and delegate to one of `srcmorph`'s
> `engine.*` classes. That split is an internal implementation detail; nothing below changes for a
> user of this plugin.

## Features
- Generate AI summaries for source files
- Per-language prompts — Java, SQL schema, and a generic fallback — selected by file extension
- Weave searchable type, API and domain names into every summary
- Aggregate summaries at package level
- Build a single project index (one line + link per package) for top-down navigation, optionally with a one-call AI `#### Overview`
- Run any phase independently — toggle file / package / project on or off
- Exclude trivial or generated files with glob patterns
- Uses local models via llama.cpp (no cloud dependency)
- Incremental updates (skips unchanged files)
- Logs a rough per-file duration estimate before each generation (size, token count, expected time)
- Optimized for AI-assisted code understanding
## How It Works
The plugin runs in three phases, building a navigable index from fine to coarse.
### 1. File Generation (generate)
- Scans configured source directories
- Creates `.ai.md` files per source file
- Each file contains metadata header and markdown summary
### 2. Package Aggregation (aggregate-packages)
- Traverses generated `.ai.md` files
- Builds hierarchical package summaries
- Produces `package.ai.md` files; the header carries a deterministic `F` link list to each child (package → file navigation)
### 3. Project Index (aggregate-project)
- Harvests the one-line lead from every `package.ai.md` — the per-package listing is deterministic, no AI call
- Produces a single `project.ai.md`: one body line per package (its lead) with the clickable links in the header `F` list
- A compact, always-loadable table of contents an agent reads first to navigate down
- **Optional** (opt-in): configure a `<fieldGeneration>` on this goal and it makes *one* extra AI call to
  write a short `#### Overview` paragraph from the package leads. The deterministic per-package listing is
  unchanged; with no field generation the goal stays purely deterministic and calls no model.
## Example Output
```
### AiMdDocument.java
- H: 1.0
- C: A48CED8C
- D: 2026-03-15T23:31:52Z
- T: 2026-03-19T18:13:31Z
- G: 1.0.0
- A: 0.0.0
- X: file
---
> Immutable value type pairing a deterministic metadata header with the AI-generated markdown body of one .ai.md document.

#### Purpose
- Hold one parsed `.ai.md` document as an `AiMdHeader` plus its markdown body.

#### Type
- record-shaped value class (Java); marked `@ConvertToRecord`.

#### Public API
- `header() -> AiMdHeader` — the document's metadata header.
- `body() -> String` — the AI-generated markdown body.
```
## Requirements
- Java 8+ (production code targets Java 8; CI builds on Java 8 via temurin)
- Maven 3.6.3+
- Local GGUF model (llama.cpp compatible)

### Running under Java 8: override `checker-qual`

The plugin itself is compiled to Java 8 bytecode, but it pulls in
[`org.checkerframework:checker-qual`](https://central.sonatype.com/artifact/org.checkerframework/checker-qual)
transitively, and the version it builds against (`4.2.1`) ships its classes as **Java 11
bytecode** (class-file major version 55). On a **Java 8** JVM, loading those annotation
classes fails with `UnsupportedClassVersionError`.

If you run the plugin under a Java 8 Maven/JVM, **override `checker-qual` to `3.55.1`** — the
**last release whose runtime classes are Java 8 bytecode** (major 52). The `checker-qual` line
switched to Java 11 bytecode in `4.0.0`; every `3.x` release (`3.42.0` … `3.55.1`) is Java 8
loadable, and `3.55.1` is the newest of them. (`checker-qual` `3.43.0`–`3.55.1` additionally
carry a root `module-info.class`, which a Java 8 classpath simply ignores; if you want a jar
with no `module-info.class` at all, `3.42.0` is the last such release.)

Because this is a *plugin* dependency, the override goes inside the plugin's own
`<dependencies>` block (a project-level `<dependencyManagement>` does **not** affect a plugin's
classpath):

```xml
<plugin>
    <groupId>net.ladenthin</groupId>
    <artifactId>srcmorph-maven-plugin</artifactId>
    <!-- ... version + configuration ... -->
    <dependencies>
        <!-- Java 8 execution: pin the last Java-8-bytecode checker-qual.
             The plugin builds against 4.2.1, which is Java 11 bytecode and
             cannot be loaded by a Java 8 JVM. -->
        <dependency>
            <groupId>org.checkerframework</groupId>
            <artifactId>checker-qual</artifactId>
            <version>3.55.1</version>
        </dependency>
    </dependencies>
</plugin>
```

Running the plugin under Java 11 or newer needs no override.

## Dependency

The plugin depends on [`net.ladenthin:llama`](https://central.sonatype.com/artifact/net.ladenthin/llama), the Java/JNI binding for llama.cpp (via the `srcmorph` core library it wraps).
It is published on Maven Central and resolves automatically — no manual installation required.

```xml
<dependency>
    <groupId>net.ladenthin</groupId>
    <artifactId>llama</artifactId>
    <version>5.1.0</version>
</dependency>
```
## Configuration
The plugin is configured from three building blocks, declared on the plugin inside
`<build><plugins>`:

1. **`<aiDefinitions>`** — define each GGUF model once (path + sampling parameters), each with a `<key>`.
2. **`<promptDefinitions>`** — define each prompt template once, each with a `<key>`. A template takes
   two `%s` placeholders: the file/package name and the source (for packages, the child summaries; for
   the optional project overview, the per-package leads).
3. **`<fieldGenerations>`** — per goal, the routing **rules**. Each rule maps a `<promptKey>` to an
   `<aiDefinitionKey>` (model id, which carries the full parameter set) and selects files with a
   composable **`<condition>`** tree: `<and>`/`<or>`/`<not>` over the leaves `<extensions>`, `<size>`
   (`<min>`/`<max>` bytes), `<lines>` (`<min>`/`<max>`), `<modifiedAfter>`/`<modifiedBefore>` (ISO-8601
   instant vs the file's last-modified time), and `<pathGlob>` (base-relative glob). When several rules
   match, the highest `<priority>` wins (ties by declaration order). A rule may instead be
   `<skip>true</skip>` (ignore matching files — a high-priority skip beats routes and the fallback), and
   **exactly one** `<fallback>true</fallback>` (no condition) catches the rest. A file that
   matches no rule and no fallback **fails the build**. So one `generate` run can index different file
   kinds/sizes with **different models *and* prompts**; it loads each model once. Run with
   `-Dsrcmorph.planOnly=true` to print the routing plan (a copy-pasteable Markdown table: file → rule id →
   prompt → context-window fit → rough time estimate, summed per model and overall) and stop before
   loading any model. The plan also checks each file against its routed model's **context window**: a
   file too large for the window would lose content if trimmed. What happens then is **configuration
   only** — the plugin never picks a model for you — and is chosen per rule with `<onOversize>`:
   - **`fail`** *(default)* — abort the build (a hard fail). The fix is to add a `<fieldGeneration>` rule
     with a size `<condition>` that routes oversized files to a model with a large enough window (see the
     `granite-4.0-h-tiny-bigwindow` definition + the `big-window-java` / `big-window-sql` rules in the
     POM — IBM Granite 4.0-H-Tiny, Apache-2.0, a hybrid Mamba model whose KV cache grows only linearly,
     configured at a 384K window to cover files up to ~1 MB; verified summarizing a ~995 KB / ~268K-token
     file on an 8 GB GPU with no OOM).
   - **`sample`** — feed only the head of the file (trimmed to the window) in a single call. Fast and
     bounded; good for repetitive data where the head represents the whole.
   - **`mapReduce`** — split the file into window-sized chunks at line boundaries (with overlap so
     records are never torn mid-syntax), summarize each chunk, then combine the partial summaries in one
     final call. A `<maxChunks>` cap bounds the time (a representative subset — always head + tail,
     evenly spaced — is summarized when the file would exceed the cap), so an arbitrarily large file
     (e.g. 7 MB) stays within a chosen per-file budget. **Route oversized files to a *small*, fast model
     for this — not the big-window one.** Prefill is `O(n²)` in prompt length, so chunks should be small:
     many cheap small-window passes are far faster than a few giant ones (one 384K-token pass alone can
     dwarf a whole run). E.g. a 16K-window model with `maxChunks=6` is ~7 calls (~1 h order on a
     reference CPU; less on GPU) and samples a representative slice — the right trade-off for repetitive
     data. mapReduce *on* a big window is the slowest possible combination; avoid it.
   - **`deterministic`** — no model at all: emit a deterministic body (size, line count, head/tail
     sample). Instant; for pure data where no AI analysis is needed.

   Quality from a real model is best within Granite's validated 128K (~500 KB) and degrades gradually
   beyond it — a whole-file (or chunked) summary still beats a trimmed one.

   **Exact counts with `<facts>` (optional, every file — not just oversize).** A sampled/chunked AI
   summary can't reliably *count* things (no single call sees the whole file — it will guess "25 rows").
   Add a `<facts>` list to a rule and each `{label, pattern}` reports its regex match count over the
   **whole** source, prepended to the body of every file the rule matches as an exact facts line — so
   downstream agents get authoritative structural counts in every summary. It's fully generic — the
   meaning is in the regex, so the same mechanism counts SQL `INSERT` rows or Java `\bboolean\b` fields;
   multi-line matching is opt-in via the inline `(?m)` flag. Keep patterns robust (a fact that miscounts
   is worse than none). Example: `**Facts (exact, whole file):** INSERT rows: 36738; tables: 122; views: 4`.

```xml
<plugin>
    <groupId>net.ladenthin</groupId>
    <artifactId>srcmorph-maven-plugin</artifactId>
    <version>1.2.0</version>

    <configuration>
        <!-- outputDirectory defaults to ${project.basedir}/src/site/ai -->
        <subtrees>
            <subtree>src/main/java/com/example</subtree>
        </subtrees>
        <!-- provider defaults to "mock"; use "llamacpp-jni" to run a real GGUF model -->
        <generationProvider>llamacpp-jni</generationProvider>

        <!-- 1) Models: define once, reference by key. -->
        <aiDefinitions>
            <aiDefinition>
                <key>coder</key>
                <modelPath>/path/to/model.gguf</modelPath>
                <contextSize>32768</contextSize>
                <maxOutputTokens>1536</maxOutputTokens>
                <temperature>0.7</temperature>
                <threads>8</threads>
            </aiDefinition>
        </aiDefinitions>

        <!-- 2) Prompts (abbreviated): one prompt per language plus a fallback, a package
                prompt, and the optional project-overview prompt. See the srcmorph-selftest
                profile in this module's pom.xml for the full, tested file-body-java /
                file-body-sql / file-body-fallback / package-body / project-body templates. -->
        <promptDefinitions>
            <promptDefinition>
                <key>file-body-java</key>
                <template><![CDATA[Summarize ONE Java source file as structured markdown.

File: %s

Source:
%s]]></template>
            </promptDefinition>
            <promptDefinition>
                <key>file-body-fallback</key>
                <template><![CDATA[Summarize ONE source file as structured markdown.

File: %s

Source:
%s]]></template>
            </promptDefinition>
            <promptDefinition>
                <key>package-body</key>
                <template><![CDATA[Summarize ONE package from its already-generated file summaries.

Package: %s

File summaries:
%s]]></template>
            </promptDefinition>
            <!-- Only needed when the aggregate-project goal opts into the AI overview below. -->
            <promptDefinition>
                <key>project-body</key>
                <template><![CDATA[Write a short overview of the whole project from its per-package leads.

Index file: %s

%s]]></template>
            </promptDefinition>
        </promptDefinitions>
    </configuration>

    <!-- 3) Bind each goal to a phase and map prompt -> model per goal. -->
    <executions>
        <execution>
            <id>ai-generate</id>
            <phase>generate-resources</phase>
            <goals><goal>generate</goal></goals>
            <configuration>
                <!-- A .java rule (matched by its <condition>) and the explicit fallback for
                     everything else. See "Routing rules" below for size/lines/modified/glob
                     conditions, priority, and skip. -->
                <fieldGenerations>
                    <fieldGeneration>
                        <id>java</id>
                        <promptKey>file-body-java</promptKey>
                        <aiDefinitionKey>coder</aiDefinitionKey>
                        <condition>
                            <extensions><extension>.java</extension></extensions>
                        </condition>
                    </fieldGeneration>
                    <fieldGeneration>
                        <id>fallback</id>
                        <fallback>true</fallback>
                        <promptKey>file-body-fallback</promptKey>
                        <aiDefinitionKey>coder</aiDefinitionKey>
                    </fieldGeneration>
                </fieldGenerations>
            </configuration>
        </execution>
        <execution>
            <id>ai-aggregate-packages</id>
            <phase>process-resources</phase>
            <goals><goal>aggregate-packages</goal></goals>
            <configuration>
                <fieldGenerations>
                    <fieldGeneration>
                        <promptKey>package-body</promptKey>
                        <aiDefinitionKey>coder</aiDefinitionKey>
                    </fieldGeneration>
                </fieldGenerations>
            </configuration>
        </execution>
        <!-- Phase 3: project index. The per-package listing is deterministic (no model needed).
             Add the optional <configuration> below to also write a short #### Overview paragraph
             from the package leads (one extra AI call); omit it for a purely deterministic index. -->
        <execution>
            <id>ai-aggregate-project</id>
            <phase>prepare-package</phase>
            <goals><goal>aggregate-project</goal></goals>
            <configuration>
                <fieldGenerations>
                    <fieldGeneration>
                        <promptKey>project-body</promptKey>
                        <aiDefinitionKey>coder</aiDefinitionKey>
                    </fieldGeneration>
                </fieldGenerations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Routing rules (conditions, priority, skip, plan)
Within one `generate` run you can route files to different models **and** prompts by size, language,
age or path. Example — small/medium/large Java files to three context presets, skip generated sources,
and a fallback for everything else:

```xml
<fieldGenerations>
  <fieldGeneration>                                  <!-- skip wins by priority -->
    <id>skip-generated</id><skip>true</skip><priority>100</priority>
    <condition><pathGlob>**/generated/**</pathGlob></condition>
  </fieldGeneration>
  <fieldGeneration>
    <id>java-small</id><promptKey>file-body-java-terse</promptKey><aiDefinitionKey>gpt-oss-20B-c16k</aiDefinitionKey>
    <condition><and><conditions>
      <condition><extensions><extension>.java</extension></extensions></condition>
      <condition><size><max>16384</max></size></condition>
    </conditions></and></condition>
  </fieldGeneration>
  <fieldGeneration>
    <id>java-mid</id><promptKey>file-body-java</promptKey><aiDefinitionKey>gpt-oss-20B-c48k</aiDefinitionKey>
    <condition><and><conditions>
      <condition><extensions><extension>.java</extension></extensions></condition>
      <condition><size><min>16384</min><max>49152</max></size></condition>
    </conditions></and></condition>
  </fieldGeneration>
  <fieldGeneration>
    <id>java-large</id><promptKey>file-body-java-detailed</promptKey><aiDefinitionKey>gpt-oss-20B-c96k</aiDefinitionKey>
    <condition><and><conditions>
      <condition><extensions><extension>.java</extension></extensions></condition>
      <condition><size><min>49152</min></size></condition>
    </conditions></and></condition>
  </fieldGeneration>
  <fieldGeneration>                                  <!-- huge files: chunk over a SMALL fast window + combine -->
    <id>java-huge</id><promptKey>file-body-java</promptKey><aiDefinitionKey>gpt-oss-20B-c16k</aiDefinitionKey>
    <onOversize>mapReduce</onOversize><maxChunks>6</maxChunks>   <!-- ~7 cheap calls; bounds time to ~1 h/file -->
    <facts>                                          <!-- exact whole-file counts, prepended to the body -->
      <fact><label>boolean fields</label><pattern>\bboolean\b</pattern></fact>
      <fact><label>methods</label><pattern>(?m)^\s+(public|private|protected).*\(</pattern></fact>
    </facts>
    <condition><and><conditions>
      <condition><extensions><extension>.java</extension></extensions></condition>
      <condition><size><min>1048576</min></size></condition>
    </conditions></and></condition>
  </fieldGeneration>
  <fieldGeneration>
    <id>fallback</id><fallback>true</fallback>
    <promptKey>file-body-fallback</promptKey><aiDefinitionKey>gpt-oss-20B-c96k</aiDefinitionKey>
  </fieldGeneration>
</fieldGenerations>
```

Notes: size bounds are **min-exclusive / max-inclusive** so `band2.min == band1.max` is non-overlapping;
`<lines>` works the same way; `<modifiedAfter>2026-01-01T00:00:00Z</modifiedAfter>` only (re)indexes
recently changed files. `<onOversize>` (`fail` *(default)* / `sample` / `mapReduce` / `deterministic` —
see the context-window note above) chooses what a rule does when a file is still larger than its routed
model's window; `<maxChunks>` caps how many chunks `mapReduce` summarizes. Preview the mapping without
running a model:

```
mvn srcmorph:generate -Dsrcmorph.planOnly=true
```

## Usage
Run AI index generation:
```
mvn clean install -Psrcmorph-selftest
```
With native llama tests:
```
mvn clean install -Psrcmorph-selftest -DrunNativeLlamaTests=true
```
## Plugin Configuration
Run-level parameters (set in `<configuration>`):
- `outputDirectory` — target directory for `.ai.md` files (default: `${project.basedir}/src/site/ai`)
- `subtrees` — source directories to index, relative to the project base dir (default: `src/main/java`)
- `fileExtensions` — file extensions to index (default: `.java`)
- `excludes` — glob patterns for source files to skip, matched against each file's path relative to
  the base dir with `/` separators, e.g. `**/package-info.java`, `**/generated/**` (default: none).
  `*` stays within one path segment, `**` spans directories, `?` is a single character.
- `generationProvider` — AI backend: `mock` (default) or `llamacpp-jni`
- `force` — regenerate even when a body already exists (default: `false`)
- `skip` — global switch: skip **every** phase (default: `false`)
- Per-phase switches — turn any of the three phases on/off independently (each default `false`).
  Named after the three index levels (`file` / `package` / `project`, the `x` node types):
  - `srcmorph.file.skip` — skip the **file** phase (the `generate` goal)
  - `srcmorph.package.skip` — skip the **package** phase (the `aggregate-packages` goal)
  - `srcmorph.project.skip` — skip the **project** phase (the `aggregate-project` goal)

  So `-Dsrcmorph.package.skip=true` runs file + project only, `-Dsrcmorph.skip=true` runs
  nothing, and the defaults run all three.
- `aiDefinitions` / `promptDefinitions` — named models / prompt templates, referenced by key
- `fieldGenerations` — per goal: which `promptKey` runs with which `aiDefinitionKey` (**required**)

### Per-model `<aiDefinition>` parameters

Every model knob lives inside its `<aiDefinition>` (referenced by `aiDefinitionKey`), not as a top-level
plugin parameter. Only `key` and `modelPath` are required; everything else has a default. The defaults
below are the shipped values (`AiGenerationConfig.DEFAULT_*`).

| Element | Default | Description |
|---|---|---|
| `key` | *(required)* | Identifier referenced by a rule's `aiDefinitionKey` |
| `modelPath` | *(required)* | Path to the GGUF model file |
| `contextSize` | `32768` | Context window in tokens |
| `maxOutputTokens` | `128` | Max generated tokens per call |
| `threads` | `8` | CPU threads for inference |
| `temperature` | `0.15` | Sampling temperature |
| `topP` | `0.9` | Nucleus (top-p) sampling threshold |
| `topK` | `40` | Top-k sampling limit (`0` = disabled) |
| `minP` | `0.0` | Min-p sampling threshold (`0.0` = disabled) |
| `topNSigma` | `-1.0` | Top-n-sigma sampling threshold (`-1.0` = disabled) |
| `repeatPenalty` | `1.0` | Repetition penalty (`1.0` = disabled) |
| `charsPerToken` | `4` | Chars-per-token estimate; drives the automatic `maxInputChars` trim budget (`maxInputChars` itself is derived, not a field). Use a value at or below your model's real ratio so the budget stays conservative |
| `warnOnTrim` | `true` | Log a warning when the source is trimmed to fit the window |
| `cachePrompt` | `true` | Reuse the shared prompt-prefix KV across files (`cache_prompt`) |
| `swaFull` | `true` | Keep the full-size sliding-window-attention KV cache (`--swa-full`) |
| `cacheReuse` | `256` | KV prefix-reuse minimum chunk size in tokens (`--cache-reuse`; `0` = off) |
| `seed` | `-1` | RNG seed for generation; `-1` leaves upstream's random-seed-per-request default. Set it to make a re-run produce the same `.ai.md` body instead of a fresh sample, so re-indexing yields a reviewable diff. **Not bit-reproducibility** — llama.cpp is not identical across thread counts, batch sizes or backends, so this pins the sampling, not the arithmetic; same machine + same config is the guarantee |
| `gpuLayers` | `-1` | GPU layers to offload (`--gpu-layers`); `-1` = auto-fit to free VRAM, `0` = force CPU, `>0` = partial. GPU native only |
| `cpuMoeLayers` | `-1` | Keep the MoE expert weights of the first *n* layers on the CPU (`--n-cpu-moe` / `-ncmoe`); `-1` = leave default, `0` = keep none on the CPU. Usually the better trade than lowering `gpuLayers` on a MoE model: it moves only the expert weights, so a much larger model fits the same VRAM at a smaller speed cost. GPU native only |
| `cpuFfnLayers` | `-1` | Dense-model counterpart of `cpuMoeLayers`: keep the FFN weights of the first *n* layers on the CPU (`--n-cpu-ffn` / `-ncffn`); `-1` = leave default, `0` = keep none on the CPU. GPU native only |
| `kvUnifiedPerSlot` | `-1` | Per-slot unified KV context cap (`--kv-unified-per-slot`); `-1` = leave default. Must be positive when set |
| `tensorReadLazy` | *(empty)* | On-demand reading of lazy-loadable tensors (`--tensor-read-lazy`): `off`, `auto` or `on`; empty leaves the default. Trades resident memory for disk reads and shortens model load time; **requires mmap** |
| `mainGpu` | `-1` | Primary GPU index (`--main-gpu`); `-1` = leave default. Matters on multi-GPU hosts (e.g. a Vulkan build enumerates every GPU) |
| `devices` | *(empty)* | Explicit device selection (`--device`), comma-separated backend device names (e.g. `Vulkan1`); takes precedence over `mainGpu` |
| `chatTemplateEnableThinking` | `true` | Enable the chat template's thinking mode |
| `reasoningEffort` | `low` | gpt-oss harmony reasoning effort (`low`/`medium`/`high`); empty omits the kwarg (e.g. for non-gpt-oss models) |
| `reasoningBudgetTokens` | `-1` | Cap on harmony reasoning tokens (`-1` = unrestricted) |
| `dryMultiplier` | `0.0` | DRY repetition-penalty multiplier (`0.0` = disabled); the other `dry*` knobs only apply when this is `> 0` |
| `dryBase` | `1.75` | DRY exponential base |
| `dryAllowedLength` | `2` | Longest n-gram that may repeat without DRY penalty |
| `dryPenaltyLastN` | `-1` | DRY look-back window in tokens (`-1` = whole context, `0` = off) |
| `drySequenceBreakers` | *(empty)* | DRY sequence-breaker strings; empty = the binding defaults |
| `stopStrings` | *(empty)* | Extra stop strings that end generation |

## Prompt System
Prompts are defined in the plugin configuration (`<promptDefinitions>`) and referenced by key
from `<fieldGenerations>`. The self-test profile defines five:
- `file-body-java` — summarizes a single Java source file (types, public API, dependencies)
- `file-body-sql` — summarizes a single SQL file as schema (tables/views/procedures, columns,
  the tables it reads vs writes, and relationships)
- `file-body-fallback` — generic multi-language summary for any other source file
- `package-body` — synthesizes a package summary from the already-generated file summaries
- `project-body` — (optional) synthesizes the short project `#### Overview` paragraph from the package leads

For the `generate` goal the file-level prompt is selected per file by extension: the first field
generation whose `<fileExtensions>` matches the file name wins; otherwise the first entry without a
`<fileExtensions>` filter is the fallback. A single field generation with no filter (the historical
shape) keeps working — it is simply the fallback for every file.

Each summary begins with a one-sentence blockquote lead, followed by structured `####` sections.
Prompts are optimized to avoid code blocks, formatter artifacts, and empty outputs, and to produce structured markdown.
## Output Structure
```
src/site/ai/
└── main/
    └── java/
        └── com/
            └── example/
                ├── MyClass.java.ai.md
                ├── AnotherClass.java.ai.md
                └── package.ai.md
```
## Design Principles
- Deterministic metadata (hash-based change detection)
- Separation of concerns (header = metadata, body = summary)
- AI-friendly structure (predictable and hierarchical)
- Local-first (no external APIs required)
## Known Limitations
- Model output may require normalization (handled in code)
- Large models increase runtime
- Output quality depends on chosen model

## TODO
- **Expand PIT mutation-testing scope.** `srcmorph/pom.xml`'s `<targetClasses>` lists an explicit subset of classes verified at 100% mutation parity; this plugin module has no PIT gate of its own yet. Generic PIT setup and invocation: see the [PIT policy](../../workspace/policies/pit-mutation-testing.md).
## Recommended Models
Based on an 8-model × 2-prompt benchmark run against this codebase — full results, per-model
pros/cons, a source-faithfulness deep-dive, and reproduction steps in
[docs/ai-index-benchmark](../docs/ai-index-benchmark/COMPARISON.md):

> **On the `-Dai.*` flags below** (`ai.model`, `ai.gpuLayers`, `ai.mainGpu`, `ai.devices`, …): these are
> **this repo's own Maven build properties** (defined in `srcmorph-maven-plugin/pom.xml` and wired into
> the gpt-oss profile executions), overridable per run with `-D` when you build *this* repo. They are
> **not** plugin `@Parameter`s — a downstream build of the published plugin instead sets the same knobs as
> `<configuration>`/model-definition elements (`<gpuLayers>`, `<mainGpu>`, `<devices>`, `<aiDefinitionKey>`).
> The published mojo parameters are the separate `srcmorph.*` set.

- **`gpt-oss-20B-mxfp4` — the production default** (switch with `-Dai.model=<key>`). The native MXFP4
  quant at a 96K window; it inherits the benchmark's accuracy lead (gpt-oss-20b was most *accurate* per
  file, won 5/6 in the per-file matrix — measured on the `c96k`/UD-Q4_K_XL quant, but E5 shows quant
  choice is within noise so the native MXFP4 is the better-quality swap), run at `reasoningEffort=low`
  and a 96K window so it covers files up to ~250 KB untrimmed. Slowest of the set (~2× the 30B) — the
  accepted cost for accuracy. See the preset/timing details below.
- **Qwen3-Coder-30B-A3B-Instruct** — throughput alternative (and best of the non-reasoning models for
  large Java files): most complete/faithful of the fast models, code-specialized, Apache-2.0,
  ~3.3B-active MoE, 262K context. Pick it when throughput beats the last points of fidelity.
- **Granite-4.0-H-Tiny** — fastest on CPU (~4×, flat-KV hybrid, Apache-2.0); best for very large
  or many files when throughput beats the last points of fidelity.
- **Seed-Coder-8B-Instruct** — clean, permissive (MIT) small dense coder.
- Avoid `Qwen3.5-4B` (thinking tax, no quality gain).

### gpt-oss-20b presets, large files, and timing

`gpt-oss` is a *reasoning* model whose analysis tokens share the output budget, so use
`reasoningEffort=low` for code summaries (best quality here) and size the budget to the file. The pom
ships three ready presets, tiered by the largest file you must cover — full rationale and measurements
in [COMPARISON.md §11](../docs/ai-index-benchmark/COMPARISON.md):

| Preset | context | covers up to | ~ time (CPU) |
|---|---|---|---|
| `gpt-oss-20B-c16k` | 16K | ~40 KB | ~1–2 min |
| `gpt-oss-20B-c48k` | 48K | ~125 KB | ~25 min @ 100 KB |
| **`gpt-oss-20B-c96k` (default)** | 96K | ~260 KB | ~80 min @ 250 KB |

- **`c96k` is the default:** a measured A/B shows a wider context window costs only RAM, not per-file
  time, so it covers every file up to ~250 KB with no trimming while small files stay just as fast.
  Downshift only to save RAM. Hard ceiling is the 128K window (~480–500 KB of code).
- **Timing is quadratic, not linear:** prefill ≈ `24.4·n + 0.000674·n²` ms (n = prompt tokens),
  because attention is O(n) per token — which is why throughput drops as files grow. The plugin logs
  this estimate per file.

## GPU acceleration (opt-in)
The default native is CPU (Ninja build, bundled in the main `net.ladenthin:llama` jar). On an NVIDIA
RTX 3070 a CUDA build measured **~4.5× the CPU decode speed**; Vulkan also works (AMD + NVIDIA) but pays
a one-time shader-compilation cost on the first run. OpenCL is intentionally not offered (llama.cpp's
OpenCL backend does not support NVIDIA GPUs).

How the native is found: `net.ladenthin.llama.loader.LlamaLoader` tries `net.ladenthin.llama.lib.path`,
then `java.library.path`, then **extracts the native bundled in whatever `net.ladenthin:llama` jar is on
the classpath**. So there are two ways to enable a GPU when running the *published* plugin in your own
build.

**Recommended — add the GPU classifier to the plugin's own classpath.** Declare the matching
`net.ladenthin:llama` classifier as a dependency of the plugin in your POM; the loader then extracts the
GPU `jllama.dll` from it — no library path to manage:

```xml
<plugin>
  <groupId>net.ladenthin</groupId>
  <artifactId>srcmorph-maven-plugin</artifactId>
  <version>...</version>
  <dependencies>
    <dependency>
      <groupId>net.ladenthin</groupId>
      <artifactId>llama</artifactId>
      <version>5.1.0</version>
      <classifier>cuda13-windows-x86-64</classifier> <!-- NVIDIA; or vulkan-windows-x86-64 -->
    </dependency>
  </dependencies>
</plugin>
```

```
mvn srcmorph:generate -Dsrcmorph.generationProvider=llamacpp-jni      # + the GPU runtime on PATH (see below)
```

**Alternative — runtime library override (no POM change).** Point `net.ladenthin.llama.lib.path` at a
folder holding the GPU `jllama.dll` (extracted once from the classifier jar); it is tried before the
bundled native:

```
mvn srcmorph:generate -Dnet.ladenthin.llama.lib.path=C:\path\to\gpu-native -Dsrcmorph.generationProvider=llamacpp-jni
```

In both cases:

- **CUDA** needs a matching CUDA 13 toolkit + driver, and the toolkit's `bin\x64` (with `cudart64_13.dll`,
  `cublas64_13.dll`) on `PATH` — the classifier jar bundles only `jllama.dll`, not the CUDA runtime.
- **GPU layer offload** — set `<gpuLayers>` inside your `<aiDefinition>` (see "Per-model
  `<aiDefinition>` parameters" above): `-1` (default) does **not** pin a layer count, so llama.cpp
  **auto-fits** as many layers as fit the card's free VRAM — the robust "runs on any card" setting (it
  never over-commits, so no OOM on a 6 GB card, and uses more layers on a bigger one). Pin a positive
  number only to force a specific **partial** split (a fixed count disables auto-fit), or `0` to force
  CPU. Measured on an 8 GB RTX 3070, auto-fit gpt-oss-20b ≈ 29 decode t/s (vs ≈ 8 on CPU); a card with
  ≥ 16 GB fits all layers and is far faster. *(In this repo's own gpt-oss presets the same knob is wired
  to the `-Dai.gpuLayers` build property — see the note under "Recommended Models" above.)*
- **Picking a GPU on a multi-GPU host** — set `<mainGpu>` / `<devices>` inside your `<aiDefinition>`: a
  **CUDA** build only enumerates NVIDIA devices, so a single-NVIDIA host needs nothing. A **Vulkan**
  build enumerates *every* GPU (an integrated GPU is often device `0`), so the default may pick the
  slower one — set `<mainGpu>1</mainGpu>` to select the discrete GPU, or `<devices>Vulkan1</devices>`
  for explicit device names (these map to the binding's `--main-gpu` / `--device`). *(In this repo's own
  gpt-oss presets these are wired to the `-Dai.mainGpu` / `-Dai.devices` build properties.)*

**Profiles (this repo's own reactor build only — test/benchmark).** `-P gpu-cuda` / `-P gpu-vulkan`
swap the `net.ladenthin:llama` classifier (via the `llama.classifier` property) for `srcmorph`'s own
test/compile classpath — handy for the native test or benchmarking on GPU here. They do **not** change
the native used when the *published* plugin runs in another build (the POM is not flattened, so the
classifier stays a property that resolves to the CPU default downstream) — use one of the two methods
above for real indexing.

## Development

Run full build:
```
mvn clean install
```
Skip AI generation:
```
mvn clean install -Dsrcmorph.skip=true
```

### Contributors: do not upgrade jqwik past 1.9.3

> ⚠️ **DO NOT UPGRADE jqwik past 1.9.3.** jqwik 1.10.0 added an anti-AI prompt-injection string to test stdout; the 1.10.1 user guide states the library "is not meant to be used by any 'AI' coding agents at all." 1.9.3 is the last pre-disclosure release and is the pinned version (declared in `srcmorph/pom.xml`, the only module with a jqwik test dependency). See `../CLAUDE.md` section "jqwik prompt-injection in test output" for the full context. Dependabot is configured to ignore **all** `net.jqwik` updates (every version, including patches) — see the `ignore` rule in [`../.github/dependabot.yml`](../.github/dependabot.yml).

## A Note on History

Somewhere in early 2026, the same idea apparently occurred more than once. This repository
started with an implementation on March 15, 2026. Andrej Karpathy published his ["LLM wiki" gist](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)
on April 4, 2026. Google Cloud eventually formalized the very same pattern into the [Open Knowledge Format](https://cloud.google.com/blog/products/data-analytics/how-the-open-knowledge-format-can-improve-data-sharing/),
announced on June 12, 2026.

## License
Apache License 2.0

---

<details>
<summary>🍺 Beer-driven architectural review (2026-05-26)</summary>

> _Sometimes you spend two beers debating "architektonisch" vs "architekturiell" and the only conclusion is: ship the code anyway. Cheers!_

</details>
