// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import java.util.Collections;
import net.ladenthin.srcmorph.config.AiGenerationConfig;

/**
 * Pure mapping from a resolved {@link AiGenerationConfig} (or a small set of fallback parameters) to an
 * immutable {@link LlamaCppJniConfig}.
 *
 * <p>Extracted from what was {@code AbstractAiIndexMojo.buildLlamaCppJniConfig} in the
 * {@code llamacpp-ai-index-maven-plugin} module: that method's model-lookup and
 * fieldGenerations-vs-fallback branching now lives in the {@code engine} package's
 * {@code EngineSupport}, which calls the two static methods here to do the actual field-by-field
 * translation. Both methods are pure — no I/O, no lookups, no thrown exceptions — so they are fully
 * unit-testable and are the project's PIT mutation-coverage target for this translation.</p>
 */
public final class LlamaCppJniConfigFactory {

    private LlamaCppJniConfigFactory() {
        // utility class — not instantiable
    }

    /**
     * Builds a {@link LlamaCppJniConfig} by copying every field from a resolved
     * {@link AiGenerationConfig} (an {@link net.ladenthin.srcmorph.config.AiModelDefinition} looked up by
     * key). Both list-valued getters already normalise a {@code null} to an empty list in their own
     * setters, so this method forwards them as-is; {@link LlamaCppJniConfig}'s constructor keeps its
     * own null guard for callers that build one directly.
     *
     * @param config      the resolved AI model generation config
     * @return the fully populated llama.cpp configuration
     */
    public static LlamaCppJniConfig fromGenerationConfig(final AiGenerationConfig config) {
        return new LlamaCppJniConfig(
                config.getModelPath(),
                config.getContextSize(),
                config.getMaxOutputTokens(),
                config.getTemperature(),
                config.getThreads(),
                config.getTopP(),
                config.getTopK(),
                config.getMinP(),
                config.getTopNSigma(),
                config.getRepeatPenalty(),
                config.isChatTemplateEnableThinking(),
                config.isCachePrompt(),
                config.isSwaFull(),
                config.getCacheReuse(),
                config.getGpuLayers(),
                config.getSeed(),
                config.getCpuMoeLayers(),
                config.getCpuFfnLayers(),
                config.getKvUnifiedPerSlot(),
                config.getTensorReadLazy(),
                config.getMainGpu(),
                config.getDevices(),
                config.getReasoningEffort(),
                config.getReasoningBudgetTokens(),
                config.getDryMultiplier(),
                config.getDryBase(),
                config.getDryAllowedLength(),
                config.getDryPenaltyLastN(),
                config.getDrySequenceBreakers(),
                config.getStopStrings());
    }

    /**
     * Builds a {@link LlamaCppJniConfig} from the small set of individual fallback parameters (used when
     * no {@code fieldGenerations}/routing rule is configured), applying every other
     * {@link AiGenerationConfig} default (sampling, DRY, GPU, …) unchanged.
     *
     * @param modelPath       path to the GGUF model file
     * @param contextSize     context window size in tokens
     * @param maxOutputTokens maximum number of output tokens per call
     * @param temperature     sampling temperature
     * @param threads         number of CPU threads
     * @return the fully populated llama.cpp configuration
     */
    public static LlamaCppJniConfig fromFallbackParameters(
            final String modelPath,
            final int contextSize,
            final int maxOutputTokens,
            final float temperature,
            final int threads) {
        return new LlamaCppJniConfig(
                modelPath,
                contextSize,
                maxOutputTokens,
                temperature,
                threads,
                AiGenerationConfig.DEFAULT_TOP_P,
                AiGenerationConfig.DEFAULT_TOP_K,
                AiGenerationConfig.DEFAULT_MIN_P,
                AiGenerationConfig.DEFAULT_TOP_N_SIGMA,
                AiGenerationConfig.DEFAULT_REPEAT_PENALTY,
                AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING,
                AiGenerationConfig.DEFAULT_CACHE_PROMPT,
                AiGenerationConfig.DEFAULT_SWA_FULL,
                AiGenerationConfig.DEFAULT_CACHE_REUSE,
                AiGenerationConfig.DEFAULT_GPU_LAYERS,
                AiGenerationConfig.DEFAULT_SEED,
                AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS,
                AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS,
                AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT,
                AiGenerationConfig.DEFAULT_TENSOR_READ_LAZY,
                AiGenerationConfig.DEFAULT_MAIN_GPU,
                AiGenerationConfig.DEFAULT_DEVICES,
                AiGenerationConfig.DEFAULT_REASONING_EFFORT,
                AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS,
                AiGenerationConfig.DEFAULT_DRY_MULTIPLIER,
                AiGenerationConfig.DEFAULT_DRY_BASE,
                AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH,
                AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N,
                Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }
}
