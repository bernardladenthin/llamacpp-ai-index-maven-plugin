// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CalibrateEngineTest {

    /**
     * A model definition with a (dummy, never loaded) {@code modelPath} set: even with the mock
     * provider, the engine always builds a
     * {@link net.ladenthin.srcmorph.provider.LlamaCppJniConfig} value object, which requires a
     * non-null model path.
     */
    private static AiModelDefinition mockModelDefinition() {
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        definition.setModelPath("mock.gguf");
        definition.setCharsPerToken(0);
        return definition;
    }

    /**
     * Per-test output directory. {@code execute()} writes the machine-readable report into
     * {@code outputDirectory}, whose default is the source-tree path {@code src/site/ai} -- without
     * this every calibration test would leave two files behind in the checkout.
     */
    @TempDir
    Path outputDirectory;

    private SrcMorphConfiguration baseConfig() {
        final SrcMorphConfiguration config = new SrcMorphConfiguration();
        config.setOutputDirectory(outputDirectory.toFile());
        config.setGenerationProvider("mock");
        config.setPromptDefinitions(CommonTestFixtures.createFilePromptDefinitions());
        config.setAiDefinitions(Collections.singletonList(mockModelDefinition()));
        return config;
    }

    @Test
    public void execute_missingFieldGenerationsThrowsSrcMorphException() {
        final SrcMorphConfiguration config = baseConfig();
        config.setFieldGenerations(null);

        final SrcMorphException e = assertThrows(SrcMorphException.class, () -> new CalibrateEngine(config).execute());
        assertThat(e.getMessage(), containsString("nothing to calibrate"));
    }

    @Test
    public void execute_noRoutableRuleThrowsSrcMorphException() {
        final SrcMorphConfiguration config = baseConfig();
        // A rule with neither promptKey nor aiDefinitionKey set is not routable.
        config.setFieldGenerations(Collections.singletonList(new AiFieldGenerationConfig()));

        final SrcMorphException e = assertThrows(SrcMorphException.class, () -> new CalibrateEngine(config).execute());
        assertThat(e.getMessage(), containsString("No routable"));
    }

    @Test
    public void execute_calibratesTheRoutedModelAndRendersXml() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        config.setFieldGenerations(CommonTestFixtures.createFileFieldGenerations());

        final CalibrationReport report = new CalibrateEngine(config).execute();

        assertThat(report.measurements().size(), is(1));
        final CalibrationReport.ModelMeasurement measurement =
                report.measurements().get(0);
        assertThat(measurement.modelKey(), is(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT));
        // The mock provider reports fixed synthetic throughput; the engine surfaces it unchanged.
        assertThat(measurement.measurement().prefillTokensPerSecond(), is(1000.0d));
        assertThat(measurement.measurement().decodeTokensPerSecond(), is(100.0d));

        final String xml = report.renderXml();
        assertThat(
                xml, containsString("calibration for aiDefinition '" + CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT));
        assertThat(xml, containsString("<prefillTokensPerSecond>1000.0</prefillTokensPerSecond>"));
    }

    @Test
    public void execute_dedupesMultipleRulesRoutedToTheSameModel() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        final AiFieldGenerationConfig ruleA = new AiFieldGenerationConfig();
        ruleA.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        ruleA.setAiDefinitionKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        final AiFieldGenerationConfig ruleB = new AiFieldGenerationConfig();
        ruleB.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        ruleB.setAiDefinitionKey(CommonTestFixtures.AI_DEFINITION_KEY_DEFAULT);
        config.setFieldGenerations(java.util.Arrays.asList(ruleA, ruleB));

        final CalibrationReport report = new CalibrateEngine(config).execute();

        assertThat(report.measurements().size(), is(1));
    }

    /**
     * The point of the feature: a calibration run's numbers must survive as something diffable and
     * committable, not only as log lines a human has to re-read. Asserted end to end through the real
     * engine -- rendering is covered separately in {@link CalibrationReportTest}, but only this proves
     * the files are actually written, to the documented names, with the documented content.
     */
    @Test
    public void execute_writesTheReportAsJsonAndYamlIntoTheOutputDirectory() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        config.setFieldGenerations(CommonTestFixtures.createFileFieldGenerations());

        final CalibrationReport report = new CalibrateEngine(config).execute();

        final Path json = outputDirectory.resolve(CalibrationReport.JSON_FILE_NAME);
        final Path yaml = outputDirectory.resolve(CalibrationReport.YAML_FILE_NAME);
        assertThat(Files.exists(json), is(true));
        assertThat(Files.exists(yaml), is(true));

        // Byte-for-byte what the report renders -- so the file cannot drift from the API.
        assertThat(new String(Files.readAllBytes(json), StandardCharsets.UTF_8), is(report.renderJson()));
        assertThat(new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8), is(report.renderYaml()));
        assertThat(
                new String(Files.readAllBytes(json), StandardCharsets.UTF_8),
                containsString("\"prefillTokensPerSecond\": 1000.0"));
    }

    /** A missing output directory is created rather than being a reason to fail the run. */
    @Test
    public void execute_createsTheOutputDirectoryWhenItDoesNotExist() throws Exception {
        final SrcMorphConfiguration config = baseConfig();
        final Path nested = outputDirectory.resolve("does/not/exist/yet");
        config.setOutputDirectory(nested.toFile());
        config.setFieldGenerations(CommonTestFixtures.createFileFieldGenerations());

        new CalibrateEngine(config).execute();

        assertThat(Files.exists(nested.resolve(CalibrationReport.JSON_FILE_NAME)), is(true));
    }
}
