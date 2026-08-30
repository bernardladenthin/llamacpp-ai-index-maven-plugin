// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import org.junit.jupiter.api.Test;

public class LlamaCppJniConfigFactoryTest {

    /**
     * Every field set to a distinct, distinguishable value so a mutant that swaps two getters (or drops
     * an argument) in {@link LlamaCppJniConfigFactory#fromGenerationConfig} is killed.
     */
    private static AiGenerationConfig fullConfig() {
        final AiGenerationConfig config = new AiGenerationConfig();
        config.setModelPath("model.gguf");
        config.setContextSize(1111);
        config.setMaxOutputTokens(222);
        config.setTemperature(0.33f);
        config.setThreads(4);
        config.setTopP(0.55f);
        config.setTopK(66);
        config.setMinP(0.07f);
        config.setTopNSigma(0.88f);
        config.setRepeatPenalty(1.09f);
        config.setChatTemplateEnableThinking(false);
        config.setCachePrompt(false);
        config.setSwaFull(false);
        config.setCacheReuse(101);
        config.setGpuLayers(12);
        config.setSeed(12345);
        config.setCpuMoeLayers(24);
        config.setCpuFfnLayers(16);
        config.setKvUnifiedPerSlot(4096);
        config.setTensorReadLazy("on");
        config.setMainGpu(3);
        config.setDevices("Vulkan1");
        config.setReasoningEffort("high");
        config.setReasoningBudgetTokens(512);
        config.setDryMultiplier(0.5f);
        config.setDryBase(1.3f);
        config.setDryAllowedLength(7);
        config.setDryPenaltyLastN(999);
        config.setDrySequenceBreakers(Arrays.asList("\n", "."));
        config.setStopStrings(Arrays.asList("<end>"));
        return config;
    }

    @Test
    public void fromGenerationConfig_threadsEveryFieldThrough() {
        final LlamaCppJniConfig result = LlamaCppJniConfigFactory.fromGenerationConfig("libpath", fullConfig());

        assertThat(result.libraryPath(), is("libpath"));
        assertThat(result.modelPath(), is("model.gguf"));
        assertThat(result.contextSize(), is(1111));
        assertThat(result.maxOutputTokens(), is(222));
        assertThat(result.temperature(), is(0.33f));
        assertThat(result.threads(), is(4));
        assertThat(result.topP(), is(0.55f));
        assertThat(result.topK(), is(66));
        assertThat(result.minP(), is(0.07f));
        assertThat(result.topNSigma(), is(0.88f));
        assertThat(result.repeatPenalty(), is(1.09f));
        assertThat(result.chatTemplateEnableThinking(), is(false));
        assertThat(result.cachePrompt(), is(false));
        assertThat(result.swaFull(), is(false));
        assertThat(result.cacheReuse(), is(101));
        assertThat(result.gpuLayers(), is(12));
        assertThat(result.seed(), is(12345));
        assertThat(result.cpuMoeLayers(), is(24));
        assertThat(result.cpuFfnLayers(), is(16));
        assertThat(result.kvUnifiedPerSlot(), is(4096));
        assertThat(result.tensorReadLazy(), is("on"));
        assertThat(result.mainGpu(), is(3));
        assertThat(result.devices(), is("Vulkan1"));
        assertThat(result.reasoningEffort(), is("high"));
        assertThat(result.reasoningBudgetTokens(), is(512));
        assertThat(result.dryMultiplier(), is(0.5f));
        assertThat(result.dryBase(), is(1.3f));
        assertThat(result.dryAllowedLength(), is(7));
        assertThat(result.dryPenaltyLastN(), is(999));
        assertThat(result.drySequenceBreakers(), hasItem("\n"));
        assertThat(result.drySequenceBreakers(), hasItem("."));
        assertThat(result.stopStrings(), hasItem("<end>"));
    }

    @Test
    public void fromGenerationConfig_libraryPathNullIsPreserved() {
        final AiGenerationConfig config = fullConfig();
        final LlamaCppJniConfig result = LlamaCppJniConfigFactory.fromGenerationConfig(null, config);
        assertThat(result.libraryPath(), is(nullValue()));
    }

    @Test
    public void fromGenerationConfig_listsNullifiedThroughTheSetters_arriveAsEmptyLists() {
        // Note what this does and does not pin: the setters normalise null to an empty list, so the
        // factory never sees a null here. It proves the empty list survives the mapping, NOT that the
        // factory itself guards against null -- that guard lives in LlamaCppJniConfig's constructor
        // and is pinned by LlamaCppJniConfigTest.
        final AiGenerationConfig config = fullConfig();
        config.setStopStrings(null);
        config.setDrySequenceBreakers(null);

        final LlamaCppJniConfig result = LlamaCppJniConfigFactory.fromGenerationConfig("lib", config);

        assertThat(result.stopStrings(), is(Collections.<String>emptyList()));
        assertThat(result.drySequenceBreakers(), is(Collections.<String>emptyList()));
    }

    @Test
    public void fromFallbackParameters_threadsFallbackArgumentsThrough() {
        final LlamaCppJniConfig result =
                LlamaCppJniConfigFactory.fromFallbackParameters("lib", "fallback.gguf", 4096, 256, 0.42f, 6);

        assertThat(result.libraryPath(), is("lib"));
        assertThat(result.modelPath(), is("fallback.gguf"));
        assertThat(result.contextSize(), is(4096));
        assertThat(result.maxOutputTokens(), is(256));
        assertThat(result.temperature(), is(0.42f));
        assertThat(result.threads(), is(6));
    }

    @Test
    public void fromFallbackParameters_appliesEveryAiGenerationConfigDefault() {
        final LlamaCppJniConfig result =
                LlamaCppJniConfigFactory.fromFallbackParameters(null, "fallback.gguf", 4096, 256, 0.42f, 6);

        assertThat(result.topP(), is(AiGenerationConfig.DEFAULT_TOP_P));
        assertThat(result.topK(), is(AiGenerationConfig.DEFAULT_TOP_K));
        assertThat(result.minP(), is(AiGenerationConfig.DEFAULT_MIN_P));
        assertThat(result.topNSigma(), is(AiGenerationConfig.DEFAULT_TOP_N_SIGMA));
        assertThat(result.repeatPenalty(), is(AiGenerationConfig.DEFAULT_REPEAT_PENALTY));
        assertThat(result.chatTemplateEnableThinking(), is(AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING));
        assertThat(result.cachePrompt(), is(AiGenerationConfig.DEFAULT_CACHE_PROMPT));
        assertThat(result.swaFull(), is(AiGenerationConfig.DEFAULT_SWA_FULL));
        assertThat(result.cacheReuse(), is(AiGenerationConfig.DEFAULT_CACHE_REUSE));
        assertThat(result.gpuLayers(), is(AiGenerationConfig.DEFAULT_GPU_LAYERS));
        assertThat(result.seed(), is(AiGenerationConfig.DEFAULT_SEED));
        assertThat(result.cpuMoeLayers(), is(AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS));
        assertThat(result.cpuFfnLayers(), is(AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS));
        assertThat(result.kvUnifiedPerSlot(), is(AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT));
        assertThat(result.tensorReadLazy(), is(AiGenerationConfig.DEFAULT_TENSOR_READ_LAZY));
        assertThat(result.mainGpu(), is(AiGenerationConfig.DEFAULT_MAIN_GPU));
        assertThat(result.devices(), is(AiGenerationConfig.DEFAULT_DEVICES));
        assertThat(result.reasoningEffort(), is(AiGenerationConfig.DEFAULT_REASONING_EFFORT));
        assertThat(result.reasoningBudgetTokens(), is(AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS));
        assertThat(result.dryMultiplier(), is(AiGenerationConfig.DEFAULT_DRY_MULTIPLIER));
        assertThat(result.dryBase(), is(AiGenerationConfig.DEFAULT_DRY_BASE));
        assertThat(result.dryAllowedLength(), is(AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH));
        assertThat(result.dryPenaltyLastN(), is(AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N));
        assertThat(result.drySequenceBreakers(), is(Collections.<String>emptyList()));
        assertThat(result.stopStrings(), is(Collections.<String>emptyList()));
    }
}
