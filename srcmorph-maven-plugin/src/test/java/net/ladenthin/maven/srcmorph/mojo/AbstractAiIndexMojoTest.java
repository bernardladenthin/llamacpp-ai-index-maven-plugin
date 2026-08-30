// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.engine.SrcMorphException;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AbstractAiIndexMojo}'s two shared, non-abstract members.
 *
 * <p>Neither had any coverage: {@code buildConfiguration()} is this module's analogue of the core's
 * {@code LlamaCppJniConfigFactory} (which is PIT-gated at 100%), and its failure mode is silent --
 * a new {@code @Parameter} added but forgotten in the mapping, or two fields transposed, breaks
 * nothing visible and no test would notice. Both members are {@code protected} and this test lives
 * in the same package, so no reflection is needed.
 */
public class AbstractAiIndexMojoTest {

    // <editor-fold defaultstate="collapsed" desc="fixture">

    /** Concrete subclass supplying the abstract members; {@code execute()} is never called here. */
    private static final class TestMojo extends AbstractAiIndexMojo {

        private final int contextSize;
        private final int threads;

        TestMojo(final int contextSize, final int threads) {
            this.contextSize = contextSize;
            this.threads = threads;
        }

        @Override
        protected int getLlamaContextSize() {
            return contextSize;
        }

        @Override
        protected int getLlamaThreads() {
            return threads;
        }

        @Override
        protected boolean isPhaseSkipped() {
            return false;
        }

        @Override
        public void execute() throws MojoExecutionException {
            // never invoked by this test class
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="buildConfiguration()">

    /**
     * Every shared {@code @Parameter} field must arrive on the configuration, each with a distinct,
     * distinguishable value so that a transposition of two same-typed fields is caught rather than
     * cancelling out. This mirrors the core's {@code fromGenerationConfig_threadsEveryFieldThrough}.
     */
    @Test
    public void buildConfiguration_everyParameterField_threadsThroughToTheConfiguration() {
        // arrange
        final TestMojo mojo = new TestMojo(4096, 7);
        final File base = new File("base-dir");
        final File out = new File("out-dir");
        final List<String> subtrees = Arrays.asList("src/main/java", "src/test/java");
        final List<AiPromptDefinition> prompts = new ArrayList<>();
        final List<AiModelDefinition> models = new ArrayList<>();
        final List<AiFieldGenerationConfig> rules = new ArrayList<>();
        mojo.baseDirectory = base;
        mojo.outputDirectory = out;
        mojo.force = true;
        mojo.subtrees = subtrees;
        mojo.generationProvider = "mock";
        mojo.promptDefinitions = prompts;
        mojo.aiDefinitions = models;
        mojo.fieldGenerations = rules;
        mojo.llamaLibraryPath = "lib-path";
        mojo.llamaModelPath = "model.gguf";
        mojo.llamaMaxOutputTokens = 222;
        mojo.llamaTemperature = 0.33f;

        // act
        final SrcMorphConfiguration config = mojo.buildConfiguration();

        // assert
        assertThat(config.getBaseDirectory(), is(equalTo(base)));
        assertThat(config.getOutputDirectory(), is(equalTo(out)));
        assertThat(config.isForce(), is(true));
        assertThat(config.getSubtrees(), is(equalTo(subtrees)));
        assertThat(config.getGenerationProvider(), is(equalTo("mock")));
        assertThat(config.getPromptDefinitions(), is(sameInstance(prompts)));
        assertThat(config.getAiDefinitions(), is(sameInstance(models)));
        assertThat(config.getFieldGenerations(), is(sameInstance(rules)));
        assertThat(config.getLlamaLibraryPath(), is(equalTo("lib-path")));
        assertThat(config.getLlamaModelPath(), is(equalTo("model.gguf")));
        assertThat(config.getLlamaMaxOutputTokens(), is(equalTo(222)));
        assertThat(config.getLlamaTemperature(), is(equalTo(0.33f)));
        // These two come from the abstract getters, not fields -- a subclass declares its own
        // @Parameter and returns it, so the mapping must read the getter and not some field.
        assertThat(config.getLlamaContextSize(), is(equalTo(4096)));
        assertThat(config.getLlamaThreads(), is(equalTo(7)));
    }

    /** {@code force} defaults to false and must not be inverted on the way through. */
    @Test
    public void buildConfiguration_forceLeftUnset_staysFalse() {
        // arrange
        final TestMojo mojo = new TestMojo(1024, 1);

        // act
        final SrcMorphConfiguration config = mojo.buildConfiguration();

        // assert
        assertThat(config.isForce(), is(false));
    }

    /** Each call builds a fresh configuration; goals must not share mutable state. */
    @Test
    public void buildConfiguration_calledTwice_returnsDistinctInstances() {
        // arrange
        final TestMojo mojo = new TestMojo(1024, 1);

        // act
        final SrcMorphConfiguration first = mojo.buildConfiguration();
        final SrcMorphConfiguration second = mojo.buildConfiguration();

        // assert
        assertThat(first == second, is(false));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="messageOf()">

    /** A normal engine exception contributes its own message. */
    @Test
    public void messageOf_exceptionWithMessage_returnsThatMessage() {
        // arrange
        final SrcMorphException cause = new SrcMorphException("something specific went wrong");

        // act / assert
        assertThat(AbstractAiIndexMojo.messageOf(cause), is(equalTo("something specific went wrong")));
    }

    /**
     * A {@code null} message must fall back to {@code toString()} rather than propagating null into
     * {@code MojoExecutionException}'s constructor -- the whole reason this helper exists.
     */
    @Test
    public void messageOf_exceptionWithNullMessage_fallsBackToToString() {
        // arrange
        final SrcMorphException cause = new SrcMorphException(null);

        // act
        final String message = AbstractAiIndexMojo.messageOf(cause);

        // assert
        assertThat(message, is(equalTo(cause.toString())));
        assertThat(message.contains("SrcMorphException"), is(true));
    }

    // </editor-fold>
}
