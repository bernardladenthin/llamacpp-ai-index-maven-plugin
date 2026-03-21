# Code Writing Guide — llamacpp-ai-index-maven-plugin

This guide is the authoritative reference for writing and improving production code in this project.

---

## 1. Named Constants — DRY, No Inline Literals

The primary motivation is **Don't Repeat Yourself (DRY)**. Every meaningful value must exist in exactly **one** authoritative place — a named constant — so that a future change to the value requires editing only one line. Inline literals scatter the same meaning across multiple call sites, making the code fragile and hard to maintain.

### Rules

- Every string, number, or flag literal that carries semantic meaning **must** be a named `public static final` or `private static final` constant. Inline magic values are **prohibited**.
- Constants must be placed at the top of the class, before constructors and methods.
- The name must describe the **meaning or role** of the value, not the value itself.
- Each constant must have a **Javadoc comment** that explains what the value represents, why it has that specific value, and any relevant cross-references (e.g. spec references, related constants, or affected classes).
- When a derived value (e.g., a string built from a constant prefix) is needed, define **both** the source constant and the derived constant, and compute the derived one from the source — never duplicate the raw literal.

```java
// BAD — magic literals inline, no single source of truth
if (name.endsWith(".ai.md")) { ... }
if ("package.ai.md".equals(name)) { ... }
header.put("h", "1.0");
```

```java
// GOOD — one authoritative constant with Javadoc
/**
 * File extension appended to every source file name to produce its AI index file name.
 * Example: "MyClass.java" -> "MyClass.java.ai.md"
 */
public static final String AI_MD_EXTENSION = ".ai.md";

/**
 * File name used for package-level AI index documents.
 * One {@value} file is created per indexed package directory.
 */
public static final String PACKAGE_AI_MD = "package.ai.md";

/**
 * Current metadata header format version written into every AI document.
 *
 * @see AiMdHeader#h()
 */
public static final String HEADER_VERSION_1_0 = "1.0";
```

### Header Field Key Constants

All YAML-like header field keys (e.g. `"h"`, `"title"`, `"c"`, `"s"`, `"k"`) must be defined as named constants in `AiMdHeaderCodec` and referenced everywhere — never written as bare string literals outside that class.

### Node Type Constants

`AiMdHeaderCodec.NODE_TYPE_FILE` and `AiMdHeaderCodec.NODE_TYPE_PACKAGE` are the single sources of truth for the `x` field values. Never write `"file"` or `"package"` as bare literals outside `AiMdHeaderCodec`.

### Provider Name Constants

Provider name strings (`"mock"`, `"llamacpp-jni"`) must be defined as constants in `AiGenerationProviderFactory` and referenced from both production code and tests.

---

## 2. Logger Injection — Constructor Over Setter

When a class accepts `org.apache.maven.plugin.logging.Log` and tests need to inject a mock or stub logger, prefer **constructor-based injection** over a setter method.

### Pattern

Provide two constructors:

1. **Mojo constructor** — Mojo subclasses obtain the logger via `AbstractMojo.getLog()` and pass it in. This is the constructor used by Maven at runtime.
2. **`@VisibleForTesting` constructor** — accepts a `Log` parameter directly. This is the constructor used by tests.

```java
public class SourceFileIndexer {

    private final Log log;

    // Production constructor — called from Mojo with getLog()
    public SourceFileIndexer(
            final Log log,
            final Path baseDirectory,
            final Path outputRoot,
            // ... other params
    ) {
        this.log = log;
        // ... assign fields
    }
}
```

Tests pass a `SystemStreamLog` or a mock `Log` implementation directly:

```java
// In tests — pass a real or mock Log
SourceFileIndexer indexer = new SourceFileIndexer(
        new SystemStreamLog(),
        baseDirectory,
        outputRoot,
        // ...
);
```

### Rules

- The `log` field must be `private final`.
- Never expose a `setLog` method on non-Mojo classes. Constructor injection is the only approved mechanism for non-Mojo classes.
- Mojo classes obtain the logger through the inherited `getLog()` method and pass it down to collaborators at construction time.
- A `setLog` method is the **last resort** — only use it when the object is instantiated by a framework that controls construction and constructor injection is not feasible.

---

## 3. Records for Immutable Value Objects

Java `record` types are the preferred representation for immutable data carriers. Use records when:

- The class holds only final fields that are set at construction.
- There is no mutable state.
- The class has value semantics (equality based on field values).

Examples already using records: `AiMdDocument`, `AiMdHeader`, `AiPreparedPrompt`, `AiSummaryResponse`, `AiGenerationRequest`.

```java
// GOOD — immutable value object as a record
public record AiSummaryResponse(String text, int tokensGenerated) {}

// BAD — mutable class with getters/setters for a simple data carrier
public class AiSummaryResponse {
    private String text;
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
```

**Exception:** Classes that are instantiated by Maven's plugin framework via reflection (e.g., `@Parameter`-bearing configuration classes like `AiPromptDefinition`, `AiGenerationConfig`, `AiFieldGenerationConfig`) must remain regular classes with setters, because Maven cannot inject values into record components.

---

## 4. Prompt Target String Constants

The field target strings used by `SourceFileIndexer` and `PackageIndexer` (`"header.s"`, `"header.k"`, `"body"`) must be defined as named constants. Comparing target strings with bare inline literals is prohibited.

```java
// BAD
if ("header.s".equals(target)) { ... }

// GOOD
if (AiFieldGenerationConfig.TARGET_HEADER_SUMMARY.equals(target)) { ... }
```

---

## 5. Defensive Null and Empty Checks at Public Boundaries

- Validate `null` and empty inputs at the entry point of every public method that would propagate a `NullPointerException` deep into a call stack.
- Prefer `log.warn(...)` + early return over silent skips for cases that indicate a misconfiguration.
- Throw `IllegalArgumentException` with a descriptive message for programming errors (e.g., unsupported target name, missing required configuration).

```java
// GOOD — clear error for unsupported configuration
throw new IllegalArgumentException("Unsupported field target: " + target);

// GOOD — warn and skip rather than silently doing nothing
if (!Files.exists(sourceFile)) {
    log.warn("Skipping missing source file for AI summary: " + sourceFile);
    return false;
}
```

---

## 6. Helper Classes — Instance Methods Over Static Utilities

Helper classes should be designed for mockability and testability, not as static utility classes.

### Rules

- **Prefer instance methods over static methods.** Helper classes like `Java8CompatibilityHelper` must be regular classes (not `final`), with **instance methods** (not `static`).
- **No private constructor.** Do not enforce non-instantiation; allow normal object creation.
- **Dependency injection.** Store an instance as a field in classes that use the helper, making the dependency explicit and testable.
- **Easy to mock.** Instance methods can be overridden or mocked in tests, enabling better test isolation.

### Motivation

Static utility methods are hard to mock and don't reflect actual dependencies. Using instance fields makes the dependency graph explicit and enables test-time substitution.

### Example

**Before (static utility, not mockable):**
```java
public final class Java8CompatibilityHelper {
    private Java8CompatibilityHelper() { }

    public static boolean isBlank(final String str) {
        return str.isEmpty() || str.trim().isEmpty();
    }
}

// Hard to mock — uses static method directly
if (providerName == null || Java8CompatibilityHelper.isBlank(providerName)) { }
```

**After (instance method, mockable):**
```java
public class Java8CompatibilityHelper {

    public boolean isBlank(final String str) {
        return str.isEmpty() || str.trim().isEmpty();
    }
}

// In the class that uses it:
public class AiGenerationProviderFactory {
    private final Java8CompatibilityHelper compatibilityHelper =
        new Java8CompatibilityHelper();

    public AiGenerationProvider create(final String providerName, ...) {
        // Easy to test — can inject a mock
        if (providerName == null || compatibilityHelper.isBlank(providerName)) { }
    }
}

// In tests — inject a mock
AiGenerationProviderFactory factory = new AiGenerationProviderFactory(
    mockLog,
    mockLlamaCppConfig,
    mockPromptSupport,
    mockCompatibilityHelper  // Can now be mocked!
);
```

### When to Use Instance Methods

- **Compatibility adapters** (e.g., `Java8CompatibilityHelper`) that wrap external APIs
- **Support utilities** that collaborators need to substitute or mock in tests
- **Cross-cutting utilities** used by many classes and likely to change
- Any utility that might be enhanced, extended, or replaced with a mock

### When Static Methods Are Acceptable

Static methods are acceptable **only** for:
- Pure mathematical functions with no side effects
- Trivial string/number formatting that never needs to be mocked
- Constant lookup functions that have no external dependencies

---

## 7. Key-Indexed Definition Pattern

When a plugin `<configuration>` block contains a list of named definitions (e.g. prompt templates, AI model configs) that other parts of the configuration reference by a string key, apply the **key-indexed definition pattern**:

1. **Definition POJO** — a regular JavaBean class (not a record, because Maven injects via reflection) that holds the key and all configuration fields. Fields default to the corresponding `*Config.DEFAULT_*` constants.
2. **Support class** — converts the list of definition POJOs into a `Map<String, ConfigType>` at construction time, then exposes `getConfig(String key)` which throws `IllegalArgumentException` (with the missing key in the message) for unknown keys.
3. **Reference by key** — any configuration class that previously held an inline nested config object is refactored to hold only a `String aiDefinitionKey` (or similar) that is resolved at runtime via the support class.

### Why this pattern?

- Eliminates duplication when the same model parameters are needed in multiple places.
- Removes the need for Maven profiles to vary model configuration; all definitions live inline in the plugin's `<configuration>` block.
- The support class is constructed once per Mojo execution and passed into collaborators, making the dependency explicit and testable.

### Example

**Definition POJO (Maven @Parameter):**
```java
public class AiModelDefinition {
    private String key;
    private int contextSize = AiGenerationConfig.DEFAULT_CONTEXT_SIZE;
    private float temperature = AiGenerationConfig.DEFAULT_TEMPERATURE;
    // ... other fields with defaults, plus getters/setters
}
```

**Support class:**
```java
public class AiModelDefinitionSupport {
    private static final String MISSING_DEFINITION_MESSAGE_PREFIX =
            "Missing AI model definition for key: ";

    private final Map<String, AiGenerationConfig> configs = new HashMap<>();

    public AiModelDefinitionSupport(final List<AiModelDefinition> definitions) {
        if (definitions != null) {
            for (final AiModelDefinition definition : definitions) {
                if (definition.getKey() != null) {
                    configs.put(definition.getKey(), toConfig(definition));
                }
            }
        }
    }

    public AiGenerationConfig getConfig(final String key) {
        final AiGenerationConfig config = configs.get(key);
        if (config == null) {
            throw new IllegalArgumentException(MISSING_DEFINITION_MESSAGE_PREFIX + key);
        }
        return config;
    }
}
```

**Plugin configuration (pom.xml):**
```xml
<aiDefinitions>
    <aiDefinition>
        <key>my-model</key>
        <modelPath>/path/to/model.gguf</modelPath>
        <contextSize>16384</contextSize>
    </aiDefinition>
</aiDefinitions>

<fieldGenerations>
    <fieldGeneration>
        <promptKey>file-body</promptKey>
        <aiDefinitionKey>my-model</aiDefinitionKey>   <!-- reference by key -->
    </fieldGeneration>
</fieldGenerations>
```

### Existing examples in this codebase

| Definition POJO | Support class | Referenced by |
|---|---|---|
| `AiPromptDefinition` | `AiPromptSupport` | `AiFieldGenerationConfig.promptKey` |
| `AiModelDefinition` | `AiModelDefinitionSupport` | `AiFieldGenerationConfig.aiDefinitionKey` |

---
