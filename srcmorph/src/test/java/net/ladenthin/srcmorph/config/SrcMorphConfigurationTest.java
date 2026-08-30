// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.Arrays;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import org.junit.jupiter.api.Test;

public class SrcMorphConfigurationTest {

    @Test
    public void defaults_matchDocumentedConstants() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();

        assertThat(c.getOutputDirectory(), is(new File(SrcMorphConfiguration.DEFAULT_OUTPUT_DIRECTORY)));
        assertThat(c.isForce(), is(false));
        assertThat(c.isPlanOnly(), is(false));
        assertThat(c.getGenerationProvider(), is(SrcMorphConfiguration.DEFAULT_GENERATION_PROVIDER));
        assertThat(c.getLlamaContextSize(), is(SrcMorphConfiguration.DEFAULT_LLAMA_CONTEXT_SIZE));
        assertThat(c.getLlamaMaxOutputTokens(), is(SrcMorphConfiguration.DEFAULT_LLAMA_MAX_OUTPUT_TOKENS));
        assertThat(c.getLlamaTemperature(), is(SrcMorphConfiguration.DEFAULT_LLAMA_TEMPERATURE));
        assertThat(c.getLlamaThreads(), is(SrcMorphConfiguration.DEFAULT_LLAMA_THREADS));
        assertThat(c.getAiVersion(), is(SrcMorphConfiguration.DEFAULT_AI_VERSION));
        assertThat(c.getMinFileSizeBytes(), is(0L));
        assertThat(c.getMaxFileSizeBytes(), is(0L));
        assertThat(c.getPluginVersion(), is(""));
    }

    @Test
    public void nullableCollectionsAndStringsAreNullByDefault() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();

        assertThat(c.getSubtrees(), is(nullValue()));
        assertThat(c.getExcludes(), is(nullValue()));
        assertThat(c.getFileExtensions(), is(nullValue()));
        assertThat(c.getPromptDefinitions(), is(nullValue()));
        assertThat(c.getAiDefinitions(), is(nullValue()));
        assertThat(c.getFieldGenerations(), is(nullValue()));
        assertThat(c.getFactDefinitions(), is(nullValue()));
        assertThat(c.getLlamaModelPath(), is(nullValue()));
        assertThat(c.getProjectName(), is(nullValue()));
    }

    @Test
    public void baseDirectoryRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final File dir = new File("/tmp/base");
        c.setBaseDirectory(dir);
        assertThat(c.getBaseDirectory(), is(sameInstance(dir)));
    }

    @Test
    public void outputDirectoryRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final File dir = new File("/tmp/out");
        c.setOutputDirectory(dir);
        assertThat(c.getOutputDirectory(), is(sameInstance(dir)));
    }

    @Test
    public void forceRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setForce(true);
        assertThat(c.isForce(), is(true));
    }

    @Test
    public void planOnlyRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setPlanOnly(true);
        assertThat(c.isPlanOnly(), is(true));
    }

    @Test
    public void subtreesRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setSubtrees(Arrays.asList("src/main/java/a"));
        assertThat(c.getSubtrees(), hasItem("src/main/java/a"));
    }

    @Test
    public void excludesRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setExcludes(Arrays.asList("**/generated/**"));
        assertThat(c.getExcludes(), hasItem("**/generated/**"));
    }

    @Test
    public void fileExtensionsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setFileExtensions(Arrays.asList(".kt"));
        assertThat(c.getFileExtensions(), hasItem(".kt"));
    }

    @Test
    public void minFileSizeBytesRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setMinFileSizeBytes(123L);
        assertThat(c.getMinFileSizeBytes(), is(123L));
    }

    @Test
    public void maxFileSizeBytesRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setMaxFileSizeBytes(456L);
        assertThat(c.getMaxFileSizeBytes(), is(456L));
    }

    @Test
    public void generationProviderRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setGenerationProvider("llamacpp-jni");
        assertThat(c.getGenerationProvider(), is("llamacpp-jni"));
    }

    @Test
    public void promptDefinitionsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final AiPromptDefinition p = new AiPromptDefinition();
        p.setKey("k");
        p.setTemplate("t");
        c.setPromptDefinitions(Arrays.asList(p));
        assertThat(c.getPromptDefinitions(), hasItem(p));
    }

    @Test
    public void aiDefinitionsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final AiModelDefinition m = new AiModelDefinition();
        m.setKey("m");
        c.setAiDefinitions(Arrays.asList(m));
        assertThat(c.getAiDefinitions(), hasItem(m));
    }

    @Test
    public void fieldGenerationsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final AiFieldGenerationConfig f = new AiFieldGenerationConfig();
        f.setPromptKey("p");
        c.setFieldGenerations(Arrays.asList(f));
        assertThat(c.getFieldGenerations(), hasItem(f));
    }

    @Test
    public void factDefinitionsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        final AiFactDefinition fd = new AiFactDefinition();
        fd.setKey("fd");
        c.setFactDefinitions(Arrays.asList(fd));
        assertThat(c.getFactDefinitions(), hasItem(fd));
    }

    @Test
    public void llamaModelPathRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setLlamaModelPath("model.gguf");
        assertThat(c.getLlamaModelPath(), is("model.gguf"));
    }

    @Test
    public void llamaContextSizeRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setLlamaContextSize(8192);
        assertThat(c.getLlamaContextSize(), is(8192));
    }

    @Test
    public void llamaMaxOutputTokensRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setLlamaMaxOutputTokens(4096);
        assertThat(c.getLlamaMaxOutputTokens(), is(4096));
    }

    @Test
    public void llamaTemperatureRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setLlamaTemperature(0.9f);
        assertThat(c.getLlamaTemperature(), is(0.9f));
    }

    @Test
    public void llamaThreadsRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setLlamaThreads(16);
        assertThat(c.getLlamaThreads(), is(16));
    }

    @Test
    public void pluginVersionRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setPluginVersion("1.2.3");
        assertThat(c.getPluginVersion(), is("1.2.3"));
    }

    @Test
    public void aiVersionRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setAiVersion("2.0.0");
        assertThat(c.getAiVersion(), is("2.0.0"));
    }

    @Test
    public void projectNameRoundTrips() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setProjectName("my-project");
        assertThat(c.getProjectName(), is("my-project"));
    }

    @Test
    public void toStringContainsDistinguishingField() {
        final SrcMorphConfiguration c = new SrcMorphConfiguration();
        c.setGenerationProvider("llamacpp-jni");
        assertThat(c.toString(), org.hamcrest.CoreMatchers.containsString("llamacpp-jni"));
    }
}
