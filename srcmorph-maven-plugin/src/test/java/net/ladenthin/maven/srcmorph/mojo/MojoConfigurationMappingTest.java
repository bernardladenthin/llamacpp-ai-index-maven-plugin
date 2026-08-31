// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.ladenthin.srcmorph.config.AiFactDefinition;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import org.junit.jupiter.api.Test;

/**
 * Covers each concrete goal's <em>own</em> {@code @Parameter} mapping.
 *
 * <p>{@code AbstractAiIndexMojoTest} pins the shared {@code buildConfiguration()}, but every goal
 * adds a second mapping step on top of it, and none of those had a single test: PIT reported the
 * whole of {@code buildGenerateConfiguration} / {@code buildAggregatePackagesConfiguration} /
 * {@code buildAggregateProjectConfiguration} plus every {@code getLlama*} accessor as
 * <em>no coverage</em>. The failure mode is silent — drop {@code setExcludes(...)} and the plugin
 * quietly indexes files the user excluded, with nothing failing.</p>
 *
 * <p>Values are distinct within their type so that transposing two same-typed fields fails rather
 * than cancelling out, mirroring the core's {@code fromGenerationConfig_threadsEveryFieldThrough}.</p>
 */
public class MojoConfigurationMappingTest {

    // <editor-fold defaultstate="collapsed" desc="fixture">

    /** The goal parameters are private, so the same reflective assignment the sibling skip test uses. */
    private static void setField(final Object target, final String name, final Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException("cannot set " + name, e);
            }
        }
        throw new IllegalStateException("no such field: " + name);
    }

    /** Fills the shared base parameters a {@code buildConfiguration()} call needs. */
    private static void fillSharedParameters(final AbstractAiIndexMojo mojo) {
        mojo.baseDirectory = new File("base-dir");
        mojo.outputDirectory = new File("out-dir");
        mojo.generationProvider = "mock";
    }

    /** Gives a goal one mock-provider model and one fallback rule, so an engine can actually run. */
    private static void wireMockModelAndRule(final AbstractAiIndexMojo mojo) {
        final AiPromptDefinition prompt = new AiPromptDefinition();
        prompt.setKey("file-body");
        prompt.setTemplate("Summarize:\n%s");
        mojo.promptDefinitions = Collections.singletonList(prompt);

        final AiModelDefinition model = new AiModelDefinition();
        model.setKey("mock-model");
        model.setModelPath("mock.gguf");
        mojo.aiDefinitions = Collections.singletonList(model);

        final AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
        rule.setPromptKey("file-body");
        rule.setAiDefinitionKey("mock-model");
        rule.setFallback(true);
        mojo.fieldGenerations = Collections.singletonList(rule);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="per-goal parameter mapping">

    @Test
    public void generateMojo_mapsEveryOwnParameterOntoTheConfiguration() {
        // arrange
        final GenerateMojo mojo = new GenerateMojo();
        fillSharedParameters(mojo);
        final List<String> extensions = Arrays.asList(".java", ".kt");
        final List<String> excludes = Collections.singletonList("**/generated/**");
        final List<AiFactDefinition> facts = Collections.singletonList(new AiFactDefinition());
        setField(mojo, "pluginVersion", "9.9.9");
        setField(mojo, "aiVersion", "8.8.8");
        setField(mojo, "fileExtensions", extensions);
        setField(mojo, "excludes", excludes);
        setField(mojo, "factDefinitions", facts);
        setField(mojo, "minFileSizeBytes", 111L);
        setField(mojo, "maxFileSizeBytes", 222L);
        setField(mojo, "planOnly", true);

        // act
        final SrcMorphConfiguration config = mojo.buildGenerateConfiguration();

        // assert
        assertThat(config.getPluginVersion(), is(equalTo("9.9.9")));
        assertThat(config.getAiVersion(), is(equalTo("8.8.8")));
        assertThat(config.getFileExtensions(), is(sameInstance(extensions)));
        assertThat(config.getExcludes(), is(sameInstance(excludes)));
        assertThat(config.getFactDefinitions(), is(sameInstance(facts)));
        assertThat(config.getMinFileSizeBytes(), is(111L));
        assertThat(config.getMaxFileSizeBytes(), is(222L));
        assertThat(config.isPlanOnly(), is(true));
        // ...and the shared step still ran, so this is a superset of buildConfiguration(), not a replacement
        assertThat(config.getGenerationProvider(), is(equalTo("mock")));
    }

    @Test
    public void aggregatePackagesMojo_mapsEveryOwnParameterOntoTheConfiguration() {
        final AggregatePackagesMojo mojo = new AggregatePackagesMojo();
        fillSharedParameters(mojo);
        setField(mojo, "pluginVersion", "9.9.9");
        setField(mojo, "aiVersion", "8.8.8");

        final SrcMorphConfiguration config = mojo.buildAggregatePackagesConfiguration();

        assertThat(config.getPluginVersion(), is(equalTo("9.9.9")));
        assertThat(config.getAiVersion(), is(equalTo("8.8.8")));
        assertThat(config.getGenerationProvider(), is(equalTo("mock")));
    }

    @Test
    public void aggregateProjectMojo_mapsEveryOwnParameterOntoTheConfiguration() {
        final AggregateProjectMojo mojo = new AggregateProjectMojo();
        fillSharedParameters(mojo);
        setField(mojo, "pluginVersion", "9.9.9");
        setField(mojo, "aiVersion", "8.8.8");
        setField(mojo, "projectName", "my-project");

        final SrcMorphConfiguration config = mojo.buildAggregateProjectConfiguration();

        assertThat(config.getPluginVersion(), is(equalTo("9.9.9")));
        assertThat(config.getAiVersion(), is(equalTo("8.8.8")));
        assertThat(config.getProjectName(), is(equalTo("my-project")));
        assertThat(config.getGenerationProvider(), is(equalTo("mock")));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="llama accessors">

    /**
     * Every goal supplies the two llama parameters to the shared base through these accessors, and
     * none was covered. The two values differ so returning the wrong field is caught, and neither is
     * {@code 0} so PIT's "replaced int return with 0" cannot pass by coincidence.
     */
    @Test
    public void everyGoal_reportsItsOwnLlamaContextSizeAndThreads() {
        assertThat(llamaContextSizeOf(new GenerateMojo()), is(4096));
        assertThat(llamaThreadsOf(new GenerateMojo()), is(7));
        assertThat(llamaContextSizeOf(new AggregatePackagesMojo()), is(4096));
        assertThat(llamaThreadsOf(new AggregatePackagesMojo()), is(7));
        assertThat(llamaContextSizeOf(new AggregateProjectMojo()), is(4096));
        assertThat(llamaThreadsOf(new AggregateProjectMojo()), is(7));
        assertThat(llamaContextSizeOf(new CalibrateMojo()), is(4096));
        assertThat(llamaThreadsOf(new CalibrateMojo()), is(7));
    }

    private static int llamaContextSizeOf(final AbstractAiIndexMojo mojo) {
        setField(mojo, "llamaContextSize", 4096);
        return mojo.getLlamaContextSize();
    }

    private static int llamaThreadsOf(final AbstractAiIndexMojo mojo) {
        setField(mojo, "llamaThreads", 7);
        return mojo.getLlamaThreads();
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="calibrate output">

    /**
     * {@code CalibrateMojo.execute()} exists to hand the user a paste-ready {@code <calibration>}
     * block; the surrounding {@code getLog().info(...)} calls are that output, not decoration. None
     * was covered, so removing any of them — including the whole rendered report — left the suite
     * green while the goal printed nothing usable.
     *
     * <p>Runs against the {@code mock} provider, so no GGUF and no model load are involved.</p>
     *
     * @throws Exception if the goal fails
     */
    @Test
    public void calibrateMojo_execute_logsTheInstructionsAndTheRenderedReport() throws Exception {
        // arrange
        final CalibrateMojo mojo = new CalibrateMojo();
        fillSharedParameters(mojo);
        wireMockModelAndRule(mojo);
        final CapturingLog log = new CapturingLog();
        mojo.setLog(log);

        // act
        mojo.execute();

        // assert -- both the guidance lines and the report itself reach the log
        final String all = String.join("\n", log.infoMessages());
        // The deliberate blank line that separates the block from whatever preceded it. Asserted by
        // position, because a `contains` check cannot see an empty string.
        assertThat(log.infoMessages().get(0), is(equalTo("")));
        assertThat(all.contains("Paste each <calibration>"), is(true));
        assertThat(all.contains("measured prefill/decode throughput"), is(true));
        assertThat(all.contains("<calibration>"), is(true));
        assertThat(all.contains("</calibration>"), is(true));
        assertThat(all.contains("mock-model"), is(true));
    }

    // </editor-fold>
}
