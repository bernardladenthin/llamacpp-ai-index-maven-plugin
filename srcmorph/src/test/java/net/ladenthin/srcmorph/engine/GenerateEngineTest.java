// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.config.AiCondition;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.provider.AiGenerationProviderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

public class GenerateEngineTest {

    @TempDir
    Path tempDir;

    /**
     * A routable rule matching {@code .java} files, with the {@code default} AI definition key. Unlike
     * {@link CommonTestFixtures#createFileFieldGenerations()} (whose rule carries no condition — fine for
     * the indexer-level tests that never call {@link net.ladenthin.srcmorph.config.AiFieldGenerationSelector#validate}),
     * {@link GenerateEngine#execute()} does call {@code validate}, so the rule here needs a real condition.
     */
    private static AiFieldGenerationConfig javaFileRule() {
        final AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
        rule.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        rule.setAiDefinitionKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        final AiCondition condition = new AiCondition();
        condition.setExtensions(Arrays.asList(".java"));
        rule.setCondition(condition);
        return rule;
    }

    /**
     * A model definition with a (dummy, never loaded) {@code modelPath} set: even with the mock provider,
     * the engine always builds a {@link net.ladenthin.srcmorph.provider.LlamaCppJniConfig} value object
     * before dispatching to {@link net.ladenthin.srcmorph.provider.AiGenerationProviderFactory}, and that
     * value object requires a non-null model path.
     */
    private static AiModelDefinition mockModelDefinition() {
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        definition.setModelPath("mock.gguf");
        // Disable automatic maxInputChars calculation so the 13-byte test source never trips the
        // oversize path in the "happy path" tests below.
        definition.setCharsPerToken(0);
        return definition;
    }

    private SrcMorphConfiguration baseConfig() throws IOException {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot.resolve("com/example"));
        Files.write(sourceRoot.resolve("com/example/Foo.java"), "class Foo {}\n".getBytes(StandardCharsets.UTF_8));

        final SrcMorphConfiguration config = new SrcMorphConfiguration();
        config.setBaseDirectory(tempDir.toFile());
        config.setOutputDirectory(tempDir.resolve("out").toFile());
        config.setGenerationProvider("mock");
        config.setPluginVersion("1.0.0");
        config.setAiVersion("0.0.0");
        config.setPromptDefinitions(CommonTestFixtures.createFilePromptDefinitions());
        config.setAiDefinitions(Collections.singletonList(mockModelDefinition()));
        config.setFieldGenerations(Collections.singletonList(javaFileRule()));
        return config;
    }

    @Test
    public void execute_missingFieldGenerationsThrowsSrcMorphException() throws IOException {
        final SrcMorphConfiguration config = baseConfig();
        config.setFieldGenerations(null);

        final SrcMorphException e = assertThrows(SrcMorphException.class, () -> new GenerateEngine(config).execute());
        assertThat(e.getMessage(), containsString("No <fieldGenerations> configured"));
    }

    @Test
    public void execute_planOnlyStopsBeforeGenerating() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        config.setPlanOnly(true);

        final GenerateResult result = new GenerateEngine(config).execute();

        assertThat(result.planOnly(), is(true));
        assertThat(result.written(), is(0));
        assertThat(Files.exists(tempDir.resolve("out")), is(false));
    }

    @Test
    public void execute_writesMatchedFileAndReportsCounts() throws Exception {
        final SrcMorphConfiguration config = baseConfig();

        final GenerateResult result = new GenerateEngine(config).execute();

        assertThat(result.planOnly(), is(false));
        assertThat(result.written(), is(1));
        assertThat(result.unchanged(), is(0));
        assertThat(result.skipped(), is(0));
        // SourceFileIndexer relativises against the "src" root but keeps the "main/java/..." tail.
        assertThat(Files.exists(tempDir.resolve("out/main/java/com/example/Foo.java.ai.md")), is(true));
    }

    @Test
    public void execute_secondRunWithoutForceReportsUnchanged() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        new GenerateEngine(config).execute();

        final GenerateResult second = new GenerateEngine(config).execute();

        assertThat(second.written(), is(0));
        assertThat(second.unchanged(), is(1));
    }

    @Test
    public void execute_unmatchedFileWithNoFallbackThrowsSrcMorphException() throws IOException {
        final SrcMorphConfiguration config = baseConfig();
        final AiFieldGenerationConfig onlyMatchesTxt = new AiFieldGenerationConfig();
        onlyMatchesTxt.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        onlyMatchesTxt.setAiDefinitionKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        final AiCondition condition = new AiCondition();
        condition.setExtensions(Arrays.asList(".txt"));
        onlyMatchesTxt.setCondition(condition);
        config.setFieldGenerations(Arrays.asList(onlyMatchesTxt));

        final SrcMorphException e = assertThrows(SrcMorphException.class, () -> new GenerateEngine(config).execute());
        assertThat(e.getMessage(), containsString("matched no rule and no fallback"));
    }

    @Test
    public void execute_oversizeFailThrowsSrcMorphException() throws Exception {
        final SrcMorphConfiguration config = baseConfig();

        // Force the routed model's window to be smaller than the source, with the default
        // onOversize=fail strategy, so the file is a hard failure. The window check happens entirely
        // during planning, before any LlamaCppJniConfig/provider is built, so modelPath is irrelevant here.
        final AiModelDefinition tinyWindow = new AiModelDefinition();
        tinyWindow.setKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        tinyWindow.setCharsPerToken(1);
        tinyWindow.setContextSize(1);
        tinyWindow.setMaxOutputTokens(0);
        config.setAiDefinitions(Collections.singletonList(tinyWindow));

        final SrcMorphException e = assertThrows(SrcMorphException.class, () -> new GenerateEngine(config).execute());
        assertThat(e.getMessage(), containsString("exceed their routed model's context window"));
    }

    // <editor-fold defaultstate="collapsed" desc="user-facing failure paths">

    private ListAppender<ILoggingEvent> logAppender;

    /**
     * Both loggers that can report a missing subtree. The warning a user actually sees comes from
     * {@code EngineSupport.resolveSubtrees}, not from {@link GenerateEngine} -- see the test below.
     */
    private static List<Logger> subtreeLoggers() {
        return Arrays.asList((Logger) LoggerFactory.getLogger(GenerateEngine.class), (Logger)
                LoggerFactory.getLogger(EngineSupport.class));
    }

    @BeforeEach
    public void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        for (final Logger logger : subtreeLoggers()) {
            logger.setLevel(Level.DEBUG);
            logger.addAppender(logAppender);
        }
    }

    @AfterEach
    public void detachLogAppender() {
        for (final Logger logger : subtreeLoggers()) {
            logger.detachAppender(logAppender);
        }
    }

    private List<String> capturedMessages(final Level level) {
        final List<String> messages = new ArrayList<>();
        for (final ILoggingEvent event : logAppender.list) {
            if (event.getLevel() == level) {
                messages.add(event.getFormattedMessage());
            }
        }
        return messages;
    }

    /**
     * A configured subtree that does not exist must be reported, and this test pins what the run then
     * actually does -- which is not what one would guess.
     *
     * <p>{@code EngineSupport.resolveSubtrees} filters missing entries out (logging the warning) before
     * {@link GenerateEngine} ever looks at the list. Every configured subtree being wrong therefore
     * leaves the resolved list EMPTY, which the engine treats as "none configured" and falls back to
     * the default {@code src/main/java}. So a typo'd {@code <subtree>} does not index nothing -- it
     * silently indexes the default tree instead, and the warning is the only signal that anything was
     * off. That behaviour is asserted here rather than assumed: the first version of this test expected
     * zero files written and failed against one.
     *
     * <p>A consequence worth knowing when touching this code: the second "Skipping missing subtree"
     * warning inside {@code GenerateEngine.execute()} itself is unreachable in practice, since the list
     * it iterates has already been filtered. Only a directory deleted between the two checks reaches it.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void execute_configuredSubtreeDoesNotExist_warnsAndFallsBackToTheDefaultTree() throws Exception {
        // arrange
        final SrcMorphConfiguration config = baseConfig();
        config.setSubtrees(Collections.singletonList("does/not/exist"));

        // act
        final GenerateResult result = new GenerateEngine(config).execute();

        // assert
        boolean warned = false;
        for (final String message : capturedMessages(Level.WARN)) {
            if (message.contains("Skipping missing subtree")) {
                warned = true;
            }
        }
        assertThat("a missing subtree must be reported, not silently dropped", warned, is(true));
        // The fallback ran: the default src/main/java (created by baseConfig) was indexed.
        assertThat(result.written(), is(1));
    }

    /**
     * The counterpart: a subtree that DOES exist is used, and no warning is emitted. Without it, a
     * mutant that warns unconditionally -- or that never resolves a subtree at all -- survives.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void execute_configuredSubtreeExists_isUsedWithoutWarning() throws Exception {
        // arrange
        final SrcMorphConfiguration config = baseConfig();
        config.setSubtrees(Collections.singletonList("src/main/java"));

        // act
        final GenerateResult result = new GenerateEngine(config).execute();

        // assert
        assertThat(result.written(), is(1));
        for (final String message : capturedMessages(Level.WARN)) {
            assertThat("no subtree warning expected", message.contains("Skipping missing subtree"), is(false));
        }
    }

    /**
     * A {@code factsKey} that matches no {@code factDefinitions} group is a configuration error and
     * must surface as a {@link SrcMorphException} naming the offending setting -- the message is what
     * the user sees, so it is part of the contract.
     *
     * @throws IOException if the fixture cannot be built
     */
    @Test
    public void execute_unknownFactsKey_throwsWithTheConfigurationHint() throws IOException {
        // arrange
        final SrcMorphConfiguration config = baseConfig();
        final AiFieldGenerationConfig rule = javaFileRule();
        rule.setFactsKey("no-such-group");
        config.setFieldGenerations(Collections.singletonList(rule));

        // act
        final SrcMorphException thrown =
                assertThrows(SrcMorphException.class, () -> new GenerateEngine(config).execute());

        // assert
        assertThat(thrown.getMessage(), containsString("Invalid factDefinitions/factsKey configuration"));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="planOnly stays model-free">

    /**
     * A plan-only run must not require the model file to exist.
     *
     * <p>{@code planOnly} is documented to "stop before loading any model" (plugin README) and the
     * CLI's {@code Plan} command promises "no model is loaded" — and it is the CLI's default. The
     * workflow it exists for is configuring routing on a machine where the GGUFs are not present and
     * running for real on the box that has them. Stat'ing the file is not loading it, but failing the
     * plan on a missing model breaks that workflow all the same.
     *
     * <p>This is the test the suite did not have: every other fixture here sets the {@code mock}
     * provider, so the routed-model-path check never ran through {@link GenerateEngine} at all. It
     * was briefly ordered before the plan-only return, which made a plan on a model-free machine
     * throw.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void execute_planOnlyWithRealProviderAndMissingModel_stillPlans() throws Exception {
        // arrange -- the real provider name, and a model path that deliberately does not exist
        final SrcMorphConfiguration config = baseConfig();
        config.setGenerationProvider(AiGenerationProviderFactory.PROVIDER_LLAMACPP_JNI);
        config.setPlanOnly(true);

        // act
        final GenerateResult result = new GenerateEngine(config).execute();

        // assert -- planned, not thrown, and nothing written
        assertThat(result.planOnly(), is(true));
        assertThat(result.written(), is(0));
        assertThat(Files.exists(tempDir.resolve("out")), is(false));
    }

    /**
     * The counterpart: the same configuration WITHOUT {@code planOnly} must still fail fast on the
     * missing model, so moving the check below the plan-only return did not disable it.
     *
     * @throws IOException if the fixture cannot be built
     */
    @Test
    public void execute_realProviderAndMissingModel_failsFastBeforeLoading() throws IOException {
        // arrange
        final SrcMorphConfiguration config = baseConfig();
        config.setGenerationProvider(AiGenerationProviderFactory.PROVIDER_LLAMACPP_JNI);

        // act
        final SrcMorphException thrown =
                assertThrows(SrcMorphException.class, () -> new GenerateEngine(config).execute());

        // assert
        assertThat(thrown.getMessage(), containsString("does not exist"));
        assertThat(thrown.getMessage(), containsString("mock.gguf"));
    }

    // </editor-fold>
}
