# Unit Test Writing Guide — llamacpp-ai-index-maven-plugin

Derived by analysis of all test files in `src/test/java/net/ladenthin/maven/llamacpp/aiindex/`.
This guide is the authoritative reference for writing and improving tests in this project.

---

## 1. File Structure & Header

Every test file **must** start with the formatter-off block enclosing the Apache 2.0 license header, exactly as shown:

```java
// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.maven.llamacpp.aiindex;
```

- The `// @formatter:off` / `// @formatter:on` pair wraps **only** the license block.
- The year must match the file creation year (not the current year).

---

## 2. Test Framework

| Concern | Choice |
|---|---|
| Test runner | JUnit 4 (`@Test`, `@Before`, `@Rule`, etc.) |
| Parameterized tests | `com.tngtech.java.junit.dataprovider` (`@RunWith(DataProviderRunner.class)`, `@UseDataProvider`) |
| Assertions | Hamcrest only — `assertThat(actual, is(equalTo(expected)))` |
| Mocking | Mockito (`mock()`, `verify()`, `when()`, `ArgumentCaptor`) |
| Temp file system | `Files.createTempDirectory(...)` or JUnit `@Rule public TemporaryFolder folder = new TemporaryFolder()` |
| Maven logger in tests | `new SystemStreamLog()` or `mock(Log.class)` |

**Do NOT use:**
- `Assert.assertEquals` / `Assert.assertTrue` / `Assert.assertFalse` from `org.junit.Assert` — use Hamcrest equivalents.
- TestNG or JUnit 5.

---

## 3. Class-Level Setup

### Class Declaration

```java
@RunWith(DataProviderRunner.class)   // only when @UseDataProvider is used in this class
public class FooTest {
```

Omit `@RunWith` if no data providers are used.

### Shared Instance Fields

Declare shared utilities as `private final` instance fields:

```java
private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();
private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();
```

Mocks that need fresh state per test are declared at field level but initialized in `@Before`:

```java
private Log mockLog;

@Before
public void setUp() {
    mockLog = mock(Log.class);
}
```

An empty `@Before` method should be omitted entirely. Only keep it if it does meaningful work.

### TemporaryFolder (file-system tests)

Prefer `Files.createTempDirectory(...)` for one-off temp directories within a single test. Use `@Rule public TemporaryFolder` when multiple tests in the same class share the same temporary root.

```java
@Rule
public TemporaryFolder folder = new TemporaryFolder();
```

---

## 4. Code Folding — Grouping Tests by Method Under Test

Tests within a class **must** be grouped using NetBeans-style editor fold regions, one fold per method/feature under test:

```java
// <editor-fold defaultstate="collapsed" desc="methodName">
@Test
public void methodName_conditionA_expectedResultA() { ... }

@Test
public void methodName_conditionB_expectedResultB() { ... }
// </editor-fold>
```

Rules:
- The `desc` attribute equals the method name (or a short feature label for non-method groups).
- `defaultstate="collapsed"` is mandatory on every fold.
- All tests for the same method go inside a single fold.
- Tests that exercise different methods **must** be in different folds.
- The fold order in the file should match logical reading order (simple cases first, edge cases and exceptions last).

---

## 5. Test Method Naming

Pattern: **`methodUnderTest_inputOrCondition_expectedBehavior`**

```
indexSourceRoot_emptyDirectory_returnsZero
indexSourceRoot_existingSummaryForceIsFalse_skipsFile
indexSourceRoot_forceIsTrue_regeneratesSummary
read_validDocument_parsesHeaderAndBody
write_documentWithMetadata_roundtripsCorrectly
preparePrompt_sourceLongerThanMax_trimmedFlagIsTrue
```

Rules:
- All three segments are **required** and separated by underscores.
- Use camelCase within each segment.
- The `expected` segment describes the observable outcome, not the implementation step.
  - Good: `_returnsZero`, `_throwsException`, `_skipsFile`, `_roundtripsCorrectly`
  - Bad: `_works`, `_correct`, `_test`
- Exception tests: the segment ends with `_throwsException` or `_exceptionThrown`.
- No-op / smoke tests: use `_noExceptionThrown`.

---

## 6. Test Body — AAA Structure

Every test body **must** follow the Arrange / Act / Assert structure with explicit section comments:

```java
@Test
public void indexSourceRoot_emptyDirectory_returnsZero() throws Exception {
    // arrange
    final Path temp = Files.createTempDirectory("ai-test");
    final SourceFileIndexer indexer = new SourceFileIndexer(
            new SystemStreamLog(), temp, temp, List.of(".java"),
            "0.1.0", "0.0.0", List.of(), false,
            new MockAiGenerationProvider(), List.of(), new AiPromptSupport(List.of())
    );

    // act
    final int result = indexer.indexSourceRoot(temp);

    // assert
    assertThat(result, is(equalTo(0)));
}
```

### `// pre-assert` — two valid positions

`// pre-assert` is a named section that asserts a condition without it being the primary assertion of the test.

**1. Before `// act`** — to verify a precondition of the input:

```java
// arrange
final AiMdDocument document = documentCodec.read(aiFile);

// pre-assert
assertThat(document.header().s(), is(emptyOrNullString()));

// act
final int count = indexer.indexSourceRoot(sourceRoot);

// assert
assertThat(count, is(equalTo(1)));
```

**2. Between `// act` and `// assert`** — as a guard before accessing fields:

```java
// act
final AiMdDocument updated = documentCodec.read(aiFile);

// pre-assert
assertThat(updated, is(notNullValue()));

// assert
assertThat(updated.header().s(), is(equalTo("Mock summary for Test.java")));
```

Rules:
- `// arrange` may be omitted only when there is genuinely nothing to arrange.
- Keep the act to a **single method call** whenever possible.
- Do **not** use `Objects.requireNonNull(...)` as a guard in tests; use a `// pre-assert` with `assertThat(x, is(notNullValue()))`.

---

## 7. Assertions — Hamcrest Style

All assertions use the Hamcrest `assertThat` form:

```java
// equality
assertThat(result, is(equalTo(expected)));

// null / not null
assertThat(result, is(nullValue()));
assertThat(result, is(notNullValue()));

// boolean
assertThat(flag, is(true));
assertThat(flag, is(false));

// negation
assertThat(result, is(not(equalTo(unexpected))));

// strings
assertThat(message, containsString("substring"));
assertThat(output, not(emptyOrNullString()));

// collections
assertThat(list, hasSize(3));
assertThat(list, is(empty()));

// numbers
assertThat(count, is(greaterThan(0)));
assertThat(count, is(equalTo(0)));
```

**Imports to use:**
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;   // or import specific matchers
```

Do **not** use:
- `org.junit.Assert.assertEquals`
- `org.junit.Assert.assertTrue` / `assertFalse`
- `Assert.assertNotNull`

---

## 8. Exception Testing

### Simple expected exception (no message check needed)

```java
@Test(expected = IllegalArgumentException.class)
public void preparePrompt_unsupportedTarget_throwsException() {
    // act
    summarizer.someMethod(null);
}
```

### Exception with message verification

Use try/catch when the exception message must be asserted:

```java
@Test
public void create_unknownProvider_throwsWithMessage() {
    // arrange
    final AiGenerationProviderFactory factory = new AiGenerationProviderFactory();

    try {
        // act
        factory.create("unknown-provider", config, promptSupport);
        fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
        // assert
        assertThat(e.getMessage(), containsString("unknown-provider"));
    }
}
```

---

## 9. Data Providers (Parameterized Tests)

When multiple test cases share the same test logic with different inputs, use `junit-dataprovider`:

```java
// 1. Constant for the provider name
public static final String DATA_PROVIDER_NODE_TYPES = "nodeTypes";

// 2. Javadoc linking to which test it serves
/**
 * For {@link AiMdHeaderCodecTest}.
 */
@DataProvider
public static Object[][] nodeTypes() {
    return new Object[][] {
        { AiMdHeaderCodec.NODE_TYPE_FILE },
        { AiMdHeaderCodec.NODE_TYPE_PACKAGE },
    };
}
```

Consuming a provider:

```java
@RunWith(DataProviderRunner.class)
public class AiMdHeaderCodecTest {

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_NODE_TYPES, location = CommonDataProvider.class)
    public void roundtrip_validNodeType_preservesValue(final String nodeType) {
        // arrange
        final AiMdHeader header = buildHeader(nodeType);

        // act
        final String encoded = headerCodec.encode(header);
        final AiMdHeader decoded = headerCodec.decode(encoded);

        // assert
        assertThat(decoded.x(), is(equalTo(nodeType)));
    }
}
```

---

## 10. Mocking the Maven Logger

Inject a mock `Log` to verify that a class logs expected messages:

```java
@Before
public void setUp() {
    mockLog = mock(Log.class);
}

@Test
public void indexSourceRoot_missingSourceFile_logsWarning() throws Exception {
    // arrange
    final SourceFileIndexer indexer = new SourceFileIndexer(mockLog, ...);

    // act
    indexer.indexSourceRoot(sourceRoot);

    // assert
    verify(mockLog, atLeastOnce()).warn(contains("Skipping missing subtree"));
}
```

Use `ArgumentCaptor<String>` when the full message content must be asserted:

```java
final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
verify(mockLog).warn(captor.capture());
assertThat(captor.getValue(), containsString("expected fragment"));
```

---

## 11. LLM Integration Tests (JNI Provider)

Tests that exercise the real `LlamaCppJniAiSummaryProvider` must:

1. Be annotated with a marker (e.g., `@LlamaJniTest` if introduced) or clearly named with `_realProvider_` in the method name.
2. Guard with an availability check as the first statement, so they skip gracefully when the native library is absent.
3. Use the bundled `SmolLM2-135M-Instruct-Q3_K_M.gguf` test model from `src/test/resources/`.

```java
@Test
public void generate_realProvider_returnsNonEmptyResponse() {
    // arrange — skip if native lib is unavailable
    Assume.assumeTrue("llama JNI library not available",
            LlamaCppJniAvailability.isAvailable());

    final Path modelPath = Paths.get("src/test/resources/SmolLM2-135M-Instruct-Q3_K_M.gguf");
    Assume.assumeTrue("test model not found", Files.exists(modelPath));

    // arrange
    final LlamaCppJniConfig config = new LlamaCppJniConfig(null, modelPath.toString(), 512, 32, 0.0f, 2);

    // act / assert — omitted for brevity
}
```

All tests that do **not** need real inference must use `MockAiGenerationProvider`.

---

## 12. Import Style

Group imports in this order (no blank lines within groups, blank line between groups):

1. Standard Java (`java.*`, `javax.*`)
2. Third-party libraries (alphabetical)
3. Project classes (`net.ladenthin.*`)
4. Static imports (last, alphabetical)

Prefer specific static imports over wildcards when only one or two matchers are used:

```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
```

Use wildcard static import when many matchers are used:

```java
import static org.hamcrest.Matchers.*;
```

---

## 13. Constants — DRY Within a Fold

When the same literal appears in two or more tests within the same fold, extract it into a `private static final` constant at the class level.

```java
// GOOD — one definition; both tests derive from it
private static final String FIXED_CHECKSUM = "AAAAAAAA"; // arbitrary 8-char hex for header tests

assertThat(updated.header().c(), is(equalTo(FIXED_CHECKSUM)));
```

```java
// BAD — same literal repeated across tests
assertThat(updated.header().c(), is(equalTo("AAAAAAAA")));
// ... in another test ...
assertThat(updated.header().c(), is(equalTo("AAAAAAAA")));
```

Constants belong to their logical fold. Do not share a constant between different folds even when the underlying value is identical — different folds test independent methods and should not be coupled.

---

## 14. What NOT To Do

| Anti-pattern | Correct alternative |
|---|---|
| `Assert.assertEquals(expected, actual)` | `assertThat(actual, is(equalTo(expected)))` |
| `Assert.assertTrue(condition)` | `assertThat(condition, is(true))` |
| `Assert.assertNotNull(x)` | `assertThat(x, is(notNullValue()))` |
| `Objects.requireNonNull(x)` as guard in tests | `// pre-assert` with `assertThat(x, is(notNullValue()))` |
| `System.out.println(...)` in tests | Remove; use logger assertions instead |
| Missing `// arrange / act / assert` comments | Add the section comments always |
| Missing editor fold | Wrap each method group in `<editor-fold>` |
| Non-conforming test name like `shouldSummarizeFile()` | Rename to `summarizeFiles_condition_expectation()` |
| Empty `@Before` method | Remove it |
| `@RunWith(DataProviderRunner.class)` without `@UseDataProvider` | Remove the `@RunWith` |
| Hard-coded path strings like `"/tmp/test"` | Use `Files.createTempDirectory(...)` |
| Real llama JNI without availability guard | Add `Assume.assumeTrue(...)` as first statement |
| Removing existing correct inline comments during a fix | Preserve all correct comments; only remove factually wrong ones |

---

## 15. Preserving Existing Comments

When modifying existing test code:

- **Keep all existing inline comments** that are correct and descriptive.
- **Only remove a comment** if it is factually wrong, misleading, or describes code that no longer exists.
- **Add new comments** where added code is not self-explanatory.
- When adding AAA section comments, place them **around** existing inline comments — do not replace them.

The goal is to **minimize the diff** to only lines that actually need changing.

---

## 16. Test Anatomy — Complete Reference Example

```java
// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.maven.llamacpp.aiindex;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;

public class SourceFileIndexerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    // shared constant used across multiple tests in the same fold
    private static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";
    private static final String AI_VERSION = "0.0.0";

    // <editor-fold defaultstate="collapsed" desc="indexSourceRoot">
    @Test
    public void indexSourceRoot_emptyDirectory_returnsZero() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-test");
        final SourceFileIndexer indexer = new SourceFileIndexer(
                new SystemStreamLog(), temp, temp, List.of(".java"),
                PLUGIN_VERSION, AI_VERSION, List.of(), false,
                new MockAiGenerationProvider(), List.of(), new AiPromptSupport(List.of())
        );

        // act
        final int result = indexer.indexSourceRoot(temp);

        // assert
        assertThat(result, is(equalTo(0)));
    }

    @Test
    public void indexSourceRoot_existingSummaryForceIsFalse_skipsFile() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-test");
        // ... set up files ...
        final SourceFileIndexer indexer = buildIndexer(temp, false);

        // act
        final int result = indexer.indexSourceRoot(temp);

        // assert
        assertThat(result, is(equalTo(0)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="indexSourceRoot — round-trip">
    @Test
    public void indexSourceRoot_newFile_writesExpectedSummary() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-test");
        final Path sourceFile = setupSourceFile(temp, "Test.java");
        final SourceFileIndexer indexer = buildIndexer(temp, false);

        // act
        final int result = indexer.indexSourceRoot(temp);

        // pre-assert
        assertThat(result, is(equalTo(1)));

        // assert
        final Path aiFile = temp.resolve("Test.java" + AiMdHeaderCodec.AI_MD_EXTENSION);
        final AiMdDocument written = documentCodec.read(aiFile);
        assertThat(written, is(notNullValue()));
        assertThat(written.header().s(), is(not(emptyOrNullString())));
    }
    // </editor-fold>
}
```
