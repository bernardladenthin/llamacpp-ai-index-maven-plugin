// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import net.ladenthin.srcmorph.cli.configuration.CCommand;
import net.ladenthin.srcmorph.cli.configuration.CConfiguration;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.document.AiMdHeaderCodec;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end exercise of the {@link CCommand#All} command against the {@code mock} provider (no
 * model load, no forked {@code java -jar} process — {@link Main#run()} is invoked directly), proving
 * the CLI drives all three engines and the expected {@code .ai.md} tree lands on disk.
 */
public class CliEndToEndTest {

    @TempDir
    Path tempDir;

    private CConfiguration buildAllCommandConfiguration() throws IOException {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot.resolve("com/example"));
        Files.write(sourceRoot.resolve("com/example/Foo.java"), "class Foo {}\n".getBytes(StandardCharsets.UTF_8));

        final CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.All;
        configuration.srcMorph.setBaseDirectory(tempDir.toFile());
        configuration.srcMorph.setOutputDirectory(tempDir.resolve("out").toFile());
        configuration.srcMorph.setGenerationProvider("mock");
        configuration.srcMorph.setPluginVersion("1.0.0-test");
        configuration.srcMorph.setAiVersion("0.0.0");

        final AiPromptDefinition prompt = new AiPromptDefinition();
        prompt.setKey("file-body");
        prompt.setTemplate("Summarize:\n%s");
        configuration.srcMorph.setPromptDefinitions(Collections.singletonList(prompt));

        final AiModelDefinition model = new AiModelDefinition();
        model.setKey("mock-model");
        model.setModelPath("mock.gguf");
        // Disable automatic maxInputChars calculation so the tiny test source never trips the
        // oversize path (mirrors GenerateEngineTest's own mock model definition).
        model.setCharsPerToken(0);
        configuration.srcMorph.setAiDefinitions(Collections.singletonList(model));

        final AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
        rule.setPromptKey("file-body");
        rule.setAiDefinitionKey("mock-model");
        rule.setFallback(true);
        configuration.srcMorph.setFieldGenerations(Collections.singletonList(rule));

        return configuration;
    }

    @Test
    public void run_allCommand_writesFileAndPackageAndProjectIndex() throws Exception {
        final CConfiguration configuration = buildAllCommandConfiguration();

        new Main(configuration).run();

        final Path outputRoot = tempDir.resolve("out");
        // Phase 1: SourceFileIndexer relativises against the "src" root but keeps "main/java/...".
        assertThat(Files.exists(outputRoot.resolve("main/java/com/example/Foo.java.ai.md")), is(true));
        // Phase 2: one package.ai.md per directory from the output root down to the leaf package.
        assertThat(
                Files.exists(outputRoot.resolve("main/java/com/example/" + AiMdHeaderCodec.PACKAGE_AI_MD_FILENAME)),
                is(true));
        assertThat(Files.exists(outputRoot.resolve(AiMdHeaderCodec.PACKAGE_AI_MD_FILENAME)), is(true));
        // Phase 3: the single project-level index.
        assertThat(Files.exists(outputRoot.resolve(AiMdHeaderCodec.PROJECT_AI_MD_FILENAME)), is(true));
    }

    @Test
    public void run_planCommand_forcesPlanOnlyAndWritesNothing() throws Exception {
        final CConfiguration configuration = buildAllCommandConfiguration();
        // The file explicitly asks NOT to plan-only; Plan must force it anyway.
        configuration.command = CCommand.Plan;
        configuration.srcMorph.setPlanOnly(false);

        new Main(configuration).run();

        assertThat(Files.exists(tempDir.resolve("out")), is(false));
        // The original configuration object handed to Main must not have been mutated in place.
        assertThat(configuration.srcMorph.isPlanOnly(), is(false));
    }

    // <editor-fold defaultstate="collapsed" desc="each CCommand dispatch arm">

    /**
     * Runs one command against the shared mock fixture and returns the output root.
     *
     * @param command the command to dispatch
     * @return the output directory the run wrote into
     * @throws IOException if the fixture cannot be built
     */
    private Path runCommand(final CCommand command) throws IOException {
        final CConfiguration configuration = buildAllCommandConfiguration();
        configuration.command = command;
        new Main(configuration).run();
        return tempDir.resolve("out");
    }

    /**
     * Runs {@code GenerateFileIndex} and then {@code command} over the same fixture.
     *
     * <p>The aggregation commands summarise an existing {@code .ai.md} tree, so running one against an
     * empty output directory legitimately writes nothing -- each has to follow the phases it reads,
     * the way {@code All} sequences them. Verified, not assumed: both aggregation tests failed exactly
     * this way first, and {@code AggregateProject} needed the package phase too, not just the file
     * phase, because it lists the {@code package.ai.md} files.
     *
     * @param command   the aggregation command whose own output is under test
     * @param preceding the phases it depends on, dispatched in order first
     * @return the output directory the runs wrote into
     * @throws IOException if the fixture cannot be built
     */
    private Path runAfter(final CCommand command, final CCommand... preceding) throws IOException {
        final CConfiguration configuration = buildAllCommandConfiguration();
        for (final CCommand phase : preceding) {
            configuration.command = phase;
            new Main(configuration).run();
        }
        configuration.command = command;
        new Main(configuration).run();
        return tempDir.resolve("out");
    }

    /**
     * Only {@code Plan} and {@code All} were ever dispatched by a test, leaving four arms of the
     * command switch dead. A copy-paste slip -- {@code case AggregatePackages:} constructing
     * {@code AggregateProjectEngine}, say -- would produce the wrong artifacts with nothing failing.
     * Each of the four tests below therefore asserts both what its arm DOES write and what it does
     * NOT, so a swapped engine is caught in either direction.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void run_generateFileIndexCommand_writesTheFileIndexButNoProjectIndex() throws Exception {
        // act
        final Path outputRoot = runCommand(CCommand.GenerateFileIndex);

        // assert
        assertThat(Files.exists(outputRoot.resolve("main/java/com/example/Foo.java.ai.md")), is(true));
        assertThat(Files.exists(outputRoot.resolve(AiMdHeaderCodec.PROJECT_AI_MD_FILENAME)), is(false));
    }

    /**
     * Package aggregation writes the per-directory index, not the project one.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void run_aggregatePackagesCommand_writesPackageIndexButNoProjectIndex() throws Exception {
        // act
        final Path outputRoot = runAfter(CCommand.AggregatePackages, CCommand.GenerateFileIndex);

        // assert
        assertThat(
                Files.exists(outputRoot.resolve("main/java/com/example/" + AiMdHeaderCodec.PACKAGE_AI_MD_FILENAME)),
                is(true));
        assertThat(Files.exists(outputRoot.resolve(AiMdHeaderCodec.PROJECT_AI_MD_FILENAME)), is(false));
    }

    /**
     * Project aggregation writes the project index at the output root.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void run_aggregateProjectCommand_writesTheProjectIndex() throws Exception {
        // act
        final Path outputRoot =
                runAfter(CCommand.AggregateProject, CCommand.GenerateFileIndex, CCommand.AggregatePackages);

        // assert
        assertThat(Files.exists(outputRoot.resolve(AiMdHeaderCodec.PROJECT_AI_MD_FILENAME)), is(true));
    }

    /**
     * {@code Calibrate} is cheap and safe to dispatch here: the mock provider reports synthetic
     * throughput, so no GGUF and no native library are involved. Without this the arm is never
     * entered at all.
     *
     * @throws Exception if the run fails
     */
    @Test
    public void run_calibrateCommand_completesAgainstTheMockProvider() throws Exception {
        // arrange
        final CConfiguration configuration = buildAllCommandConfiguration();
        configuration.command = CCommand.Calibrate;

        // act / assert -- calibrate measures and reports; it writes no .ai.md tree
        new Main(configuration).run();
        assertThat(Files.exists(tempDir.resolve("out").resolve(AiMdHeaderCodec.PROJECT_AI_MD_FILENAME)), is(false));
    }

    // </editor-fold>
}
