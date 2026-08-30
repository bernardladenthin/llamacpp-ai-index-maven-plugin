// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

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
        return LlamaCppJniConfig.builder(config.getModelPath())
                .contextSize(config.getContextSize())
                .maxOutputTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .threads(config.getThreads())
                .topP(config.getTopP())
                .topK(config.getTopK())
                .minP(config.getMinP())
                .topNSigma(config.getTopNSigma())
                .repeatPenalty(config.getRepeatPenalty())
                .chatTemplateEnableThinking(config.isChatTemplateEnableThinking())
                .cachePrompt(config.isCachePrompt())
                .swaFull(config.isSwaFull())
                .cacheReuse(config.getCacheReuse())
                .gpuLayers(config.getGpuLayers())
                .seed(config.getSeed())
                .cpuMoeLayers(config.getCpuMoeLayers())
                .cpuFfnLayers(config.getCpuFfnLayers())
                .kvUnifiedPerSlot(config.getKvUnifiedPerSlot())
                .tensorReadLazy(config.getTensorReadLazy())
                .repeatLastN(config.getRepeatLastN())
                .cacheTypeK(config.getCacheTypeK())
                .cacheTypeV(config.getCacheTypeV())
                .flashAttn(config.isFlashAttn())
                .batchSize(config.getBatchSize())
                .ubatchSize(config.getUbatchSize())
                .threadsBatch(config.getThreadsBatch())
                .mainGpu(config.getMainGpu())
                .devices(config.getDevices())
                .reasoningEffort(config.getReasoningEffort())
                .reasoningBudgetTokens(config.getReasoningBudgetTokens())
                .dryMultiplier(config.getDryMultiplier())
                .dryBase(config.getDryBase())
                .dryAllowedLength(config.getDryAllowedLength())
                .dryPenaltyLastN(config.getDryPenaltyLastN())
                .drySequenceBreakers(config.getDrySequenceBreakers())
                .stopStrings(config.getStopStrings())
                .build();
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
        // Every other value is left at the builder's default, which IS the matching
        // AiGenerationConfig.DEFAULT_* -- restating them here is what used to make this method thirty
        // lines long and let a new knob silently default differently than the config class says.
        return LlamaCppJniConfig.builder(modelPath)
                .contextSize(contextSize)
                .maxOutputTokens(maxOutputTokens)
                .temperature(temperature)
                .threads(threads)
                .build();
    }
}
