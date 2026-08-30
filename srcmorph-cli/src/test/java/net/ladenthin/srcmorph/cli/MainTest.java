// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ladenthin.srcmorph.cli.configuration.CCommand;
import net.ladenthin.srcmorph.cli.configuration.CConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Argument validation and JSON/YAML parsing behaviour of {@link Main}, all against the {@code mock}
 * provider (never loads a real model), plus one dedicated ad-hoc test fixture pair (kept private to
 * this module's {@code src/test/resources} — the public, documented {@code examples/} fixture set is
 * a later migration step).
 */
public class MainTest {

    @TempDir
    Path tempDir;

    private static final Path MINIMAL_GENERATE_JSON = Path.of("src/test/resources/test-fixtures/minimal-generate.json");
    private static final Path MINIMAL_GENERATE_YAML = Path.of("src/test/resources/test-fixtures/minimal-generate.yaml");

    private static final String MINIMAL_JSON_STRING = "{\"command\":\"Plan\"}";
    private static final String MINIMAL_YAML_STRING = "command: Plan\n";

    @Test
    public void main_noArgumentGiven_returnsWithoutException() {
        assertDoesNotThrow(() -> Main.main(new String[0]));
    }

    @Test
    public void main_tooManyArgumentsGiven_returnsWithoutException() {
        assertDoesNotThrow(() -> Main.main(new String[] {"one", "two"}));
    }

    @Test
    public void main_unknownExtensionPath_throwsIllegalArgumentException() throws IOException {
        final Path configFile = tempDir.resolve("config.txt");
        Files.write(configFile, MINIMAL_JSON_STRING.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {configFile.toString()}));
    }

    @Test
    public void loadConfiguration_jsonExtension_parsesFixture() throws IOException {
        final CConfiguration configuration = Main.loadConfiguration(MINIMAL_GENERATE_JSON);

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.GenerateFileIndex)));
        assertThat(configuration.srcMorph.getGenerationProvider(), is(equalTo("mock")));
    }

    @Test
    public void loadConfiguration_yamlExtension_parsesFixture() throws IOException {
        final CConfiguration configuration = Main.loadConfiguration(MINIMAL_GENERATE_YAML);

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.GenerateFileIndex)));
        assertThat(configuration.srcMorph.getGenerationProvider(), is(equalTo("mock")));
    }

    @Test
    public void fromJson_validJsonString_returnsExpectedConfiguration() throws IOException {
        final CConfiguration configuration = Main.fromJson(MINIMAL_JSON_STRING);

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.Plan)));
    }

    @Test
    public void fromYaml_validYamlString_returnsExpectedConfiguration() throws IOException {
        final CConfiguration configuration = Main.fromYaml(MINIMAL_YAML_STRING);

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.Plan)));
    }

    @Test
    public void fromJson_unknownTopLevelProperty_throwsIOException() {
        assertThrows(IOException.class, () -> Main.fromJson("{\"thisFieldDoesNotExist\":true}"));
    }

    @Test
    public void fromYaml_unknownTopLevelProperty_throwsIOException() {
        assertThrows(IOException.class, () -> Main.fromYaml("thisFieldDoesNotExist: true\n"));
    }

    @Test
    public void configurationToJsonThenFromJson_roundTripsCommand() throws IOException {
        final CConfiguration original = new CConfiguration();
        original.command = CCommand.AggregateProject;

        final String json = Main.configurationToJson(original);
        final CConfiguration roundTripped = Main.fromJson(json);

        assertThat(roundTripped.command, is(equalTo(CCommand.AggregateProject)));
    }

    @Test
    public void configurationToYAMLThenFromYaml_roundTripsCommand() throws IOException {
        final CConfiguration original = new CConfiguration();
        original.command = CCommand.Calibrate;

        final String yaml = Main.configurationToYAML(original);
        final CConfiguration roundTripped = Main.fromYaml(yaml);

        assertThat(roundTripped.command, is(equalTo(CCommand.Calibrate)));
    }

    @Test
    public void logConfigurationTransformation_defaultConfiguration_doesNotThrow() {
        final Main main = new Main(new CConfiguration());

        assertDoesNotThrow(main::logConfigurationTransformation);
    }

    @Test
    public void run_planCommandWithNoFieldGenerations_throwsIllegalStateException() {
        final CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Plan;
        configuration.srcMorph.setBaseDirectory(tempDir.toFile());
        configuration.srcMorph.setOutputDirectory(tempDir.resolve("out").toFile());
        // No <fieldGenerations> configured -> GenerateEngine.execute() throws SrcMorphException,
        // which Main.run() wraps as an unchecked IllegalStateException (no System.exit anywhere).
        final Main main = new Main(configuration);

        assertThrows(IllegalStateException.class, main::run);
    }

    // <editor-fold defaultstate="collapsed" desc="extension aliases and the safe default">

    /**
     * The {@code .js} alias is documented in {@code loadConfiguration}'s Javadoc as a JSON extension,
     * but no shipped {@code examples/} fixture uses it, so nothing else can reach that branch. If the
     * {@code ||} were reordered or the constant dropped, half the documented extensions would start
     * failing with "Unknown file ending" and no test would notice.
     *
     * @throws IOException if the fixture cannot be written or read
     */
    @Test
    public void loadConfiguration_jsExtension_parsesAsJson() throws IOException {
        // arrange
        final Path configFile = tempDir.resolve("config.js");
        Files.write(configFile, MINIMAL_JSON_STRING.getBytes(StandardCharsets.UTF_8));

        // act
        final CConfiguration configuration = Main.loadConfiguration(configFile);

        // assert
        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.Plan)));
    }

    /**
     * The {@code .yml} alias, for the same reason as {@link #loadConfiguration_jsExtension_parsesAsJson}.
     *
     * @throws IOException if the fixture cannot be written or read
     */
    @Test
    public void loadConfiguration_ymlExtension_parsesAsYaml() throws IOException {
        // arrange
        final Path configFile = tempDir.resolve("config.yml");
        Files.write(configFile, MINIMAL_YAML_STRING.getBytes(StandardCharsets.UTF_8));

        // act
        final CConfiguration configuration = Main.loadConfiguration(configFile);

        // assert
        assertThat(configuration, is(notNullValue()));
        assertThat(configuration.command, is(equalTo(CCommand.Plan)));
    }

    /**
     * Extension matching is case-insensitive by construction ({@code toLowerCase(Locale.ROOT)}); a
     * mutant dropping that call would break an upper-cased path with no other test noticing.
     *
     * @throws IOException if the fixture cannot be written or read
     */
    @Test
    public void loadConfiguration_upperCaseExtension_stillParses() throws IOException {
        // arrange
        final Path configFile = tempDir.resolve("config.JSON");
        Files.write(configFile, MINIMAL_JSON_STRING.getBytes(StandardCharsets.UTF_8));

        // act
        final CConfiguration configuration = Main.loadConfiguration(configFile);

        // assert
        assertThat(configuration, is(notNullValue()));
    }

    /**
     * Pins the documented safety default: a configuration that names no command must plan, never run.
     * {@code Plan} loads no model and writes nothing, so it is the only safe value to fall back to --
     * the contract is documented on {@code CConfiguration.command} but was not asserted anywhere.
     *
     * @throws IOException if the empty document cannot be deserialised
     */
    @Test
    public void fromJson_documentWithoutCommand_defaultsToPlan() throws IOException {
        // act
        final CConfiguration configuration = Main.fromJson("{}");

        // assert
        assertThat(configuration.command, is(equalTo(CCommand.Plan)));
    }

    // </editor-fold>
}
