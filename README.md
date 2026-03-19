# llamacpp-ai-index-maven-plugin
A Maven plugin for generating hierarchical, AI-readable documentation of source code projects using local llama.cpp-compatible models.
It creates structured `.ai.md` files per source file and aggregates them into package-level summaries for fast semantic navigation and retrieval.
## Features
- Generate AI summaries for Java source files
- Extract keyword metadata for search and indexing
- Aggregate summaries at package level
- Uses local models via llama.cpp (no cloud dependency)
- Incremental updates (skips unchanged files)
- Optimized for AI-assisted code understanding
## How It Works
The plugin runs in two phases.
### 1. File Generation (generate)
- Scans configured source directories
- Creates `.ai.md` files per source file
- Each file contains metadata header and markdown summary
### 2. Package Aggregation (aggregate-packages)
- Traverses generated `.ai.md` files
- Builds hierarchical package summaries
- Produces `package.ai.md` files
## Example Output
```
### AiMdDocument.java
- H: 1.0
- C: A48CED8C
- D: 2026-03-15T23:31:52Z
- T: 2026-03-19T18:13:31Z
- G: 0.1.0-SNAPSHOT
- X: file
- K: AiMdDocument, AiMdHeader, record, metadata, markdown
#### AiMdDocument.java
Represents a document consisting of a structured metadata header and a markdown body. Ensures non-null invariants and encapsulates AI-generated content.
```
## Requirements
- Java 21+
- Maven 3.6+
- Local GGUF model (llama.cpp compatible)
## Configuration
Minimal setup in POM:
```
<properties>
    <ai.index.model.path>/path/to/model.gguf</ai.index.model.path>
    <ai.index.output.directory>${project.basedir}/src/site/ai</ai.index.output.directory>
</properties>
```
## Usage
Run AI index generation:
```
mvn clean install -Pai-index-selftest
```
With native llama tests:
```
mvn clean install -Pai-index-selftest -DrunNativeLlamaTests=true
```
## Plugin Configuration
Key parameters:
- outputDirectory: target directory for `.ai.md` files
- subtrees: source directories to index
- summaryProvider: AI backend (`llamacpp-jni`)
- llamaModelPath: path to GGUF model
- llamaContextSize: context window
- llamaMaxTokens: output token limit
- llamaTemperature: sampling temperature
- llamaThreads: CPU threads
## Prompt System
The plugin uses configurable prompts:
- file-summary
- file-keywords
- package-summary
- package-keywords
Prompts are optimized to avoid code blocks, formatter artifacts, empty outputs, and produce structured markdown.
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
## Recommended Models
- Qwen2.5 Coder (balanced quality and speed)
- Smaller instruct models for faster indexing
## Development
Run full build:
```
mvn clean install
```
Skip AI generation:
```
mvn clean install -DaiIndex.skip=true
```
## License
Apache License 2.0