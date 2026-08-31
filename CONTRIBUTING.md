# Contributing to srcmorph

Thank you for your interest in contributing to **srcmorph**, a prompt-driven source-tree transformer for generating AI-readable hierarchical documentation of Java source projects using local llama.cpp-compatible models.

---

## How to build and run

### Prerequisites

- Java 8+ (production code targets Java 8; CI builds on Java 8 via temurin)
- Maven 3.6.3+

### Common commands

```bash
# Compile production sources
mvn compile

# Run all tests
mvn test

# Build the plugin JAR (with sources and Javadoc)
mvn package

# Build without running tests
mvn package -DskipTests

# Install to your local Maven repository (~/.m2)
mvn install

# Run the plugin against itself (self-test; requires a local GGUF model)
mvn clean install -Psrcmorph-selftest

# Skip AI generation during build
mvn clean install -Dsrcmorph.skip=true
```

### Offline / restricted-network environments

If Maven cannot reach the internet, use the offline flag after a warm `~/.m2` cache:

```bash
mvn test -o
mvn package -o -DskipTests
```

---

## Reporting issues

Please use the GitHub issue tracker:

https://github.com/bernardladenthin/srcmorph/issues

When filing a bug report, include:
- Java version (`java -version`) and Maven version (`mvn -version`)
- The full command you ran
- Relevant stack trace or log output
- A minimal reproduction case if possible

For feature requests, describe the use case and why the current behavior does not satisfy it.

Security-sensitive issues must **not** be filed in the public tracker. See [SECURITY.md](./SECURITY.md) for the private reporting channel.

---

## Pull request workflow

1. **Fork** the repository on GitHub.
2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/my-feature
   ```
3. **Make your changes** and commit with a clear message (see Commit message convention below).
4. **Push** your branch to your fork:
   ```bash
   git push -u origin feat/my-feature
   ```
5. **Open a Pull Request** against the `main` branch on GitHub.
6. Respond to any review feedback. Maintainers may request changes before merging.

Pull requests are expected to pass all CI checks (build, test, CodeQL) before merge.

---

## Coding standards

- **Java version:** Production code targets Java 8 (source/target = 1.8); do not use APIs unavailable on Java 8 in `src/main`.
- **Encoding:** UTF-8 throughout.
- **License headers:** All `.java` files must include the Apache 2.0 license header wrapped in `// @formatter:off` / `// @formatter:on`. Copy the header from any existing source file.
- **Named constants:** Every meaningful string literal, header field name, and node type must be declared as a `public static final` or `private static final` constant with Javadoc.
- **Null safety:** Mark nullable parameters and return types explicitly; prefer early null/empty guards with a log warning over silent skips.
- **Records:** Use Java `record` types for immutable value carriers (note: records are a development-environment convenience; ensure source/target = 1.8 compatibility for emitted bytecode).
- **Logging:** Use `org.apache.maven.plugin.logging.Log` (not SLF4J), obtained via `getLog()` or constructor injection.
- **Javadoc:** Use HTML entities for operators (`&lt;`, `&gt;`, `&#x2264;`, etc.); never use bare Unicode symbols in Javadoc.

Consult [CLAUDE.md](./CLAUDE.md) and [CODE_WRITING_GUIDE.md](./CODE_WRITING_GUIDE.md) at the root of the repository for the full set of conventions enforced on every production file.

---

## Test policy

> Every new feature or behavior change MUST include automated tests. Pull requests that add or change functionality without corresponding tests will be asked to add tests before merge. Bug fixes SHOULD include a regression test.

Tests are written with **JUnit 4** and **Hamcrest**. Use `MockAiGenerationProvider` for all tests that do not explicitly exercise the llama.cpp JNI backend. Tests that call the real JNI must guard with a JNI-availability check so the suite remains runnable on machines without the native library.

See [TEST_WRITING_GUIDE.md](./TEST_WRITING_GUIDE.md) at the root of the repository for the full test conventions.

---

## Commit message convention

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<optional scope>): <short summary>

<optional body>

<optional footer(s)>
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`.

Examples:

```
feat(generate): add force flag to bypass checksum cache
fix(codec): close reader on IOException
docs(security): publish maintainer fallback email
```

---

## Communication channels

- **GitHub Issues** — bug reports, feature requests, and questions:
  https://github.com/bernardladenthin/srcmorph/issues
- **GitHub Security Advisories** — private vulnerability reports:
  https://github.com/bernardladenthin/srcmorph/security/advisories/new

---

## Code of Conduct

This project adheres to a Contributor Covenant–based [Code of Conduct](./CODE_OF_CONDUCT.md). By participating, you agree to uphold it. Report unacceptable behavior to the maintainer contact listed in that document.

---

## Releasing

The release procedure (prompt template, version-bump steps, snapshot bump) lives in [docs/RELEASE.md](./docs/RELEASE.md). Only maintainers cut releases.

---

## License of contributions

By submitting a contribution (pull request, patch, or other submission) to this repository, you agree that your contribution will be licensed under the **Apache License, Version 2.0**, the same license that covers the project. See [LICENSE](./LICENSE) for the full text.
