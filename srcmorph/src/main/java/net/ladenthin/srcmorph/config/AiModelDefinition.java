// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Maven plugin configuration POJO that pairs a lookup key with a complete set of
 * AI model parameters.
 *
 * <p>Instances are registered in the {@code <aiDefinitions>} list of the plugin
 * configuration and referenced by their {@link #key} from
 * {@link AiFieldGenerationConfig#aiDefinitionKey}. This allows a single model configuration
 * to be defined once and reused across multiple field-generation entries and goals.</p>
 *
 * <p>All numeric fields default to the same values as {@link AiGenerationConfig} so that
 * a minimal definition only needs to supply a {@link #key} and a {@link #modelPath}.</p>
 *
 * <p><strong>Note:</strong> This class must remain a mutable JavaBean with setters because
 * Maven's plugin framework instantiates configuration objects via reflection and injects
 * values through setters.</p>
 *
 * @see AiModelDefinitionSupport
 * @see AiGenerationConfig
 */
@SuppressWarnings({"NullAway.Init", "initialization.fields.uninitialized"})
@ToString
public class AiModelDefinition {

    /** Creates a new {@link AiModelDefinition} with the defaults of {@link AiGenerationConfig} applied. */
    public AiModelDefinition() {
        // no-op
    }

    private String key;
    private String modelPath;
    private int contextSize = AiGenerationConfig.DEFAULT_CONTEXT_SIZE;
    private int maxOutputTokens = AiGenerationConfig.DEFAULT_MAX_OUTPUT_TOKENS;
    private float temperature = AiGenerationConfig.DEFAULT_TEMPERATURE;
    private int threads = AiGenerationConfig.DEFAULT_THREADS;
    private int charsPerToken = AiGenerationConfig.DEFAULT_CHARS_PER_TOKEN;
    private boolean warnOnTrim = AiGenerationConfig.DEFAULT_WARN_ON_TRIM;
    private float topP = AiGenerationConfig.DEFAULT_TOP_P;
    private int topK = AiGenerationConfig.DEFAULT_TOP_K;
    private float minP = AiGenerationConfig.DEFAULT_MIN_P;
    private float topNSigma = AiGenerationConfig.DEFAULT_TOP_N_SIGMA;
    private float repeatPenalty = AiGenerationConfig.DEFAULT_REPEAT_PENALTY;
    private @Nullable Boolean chatTemplateEnableThinking = AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING;
    private boolean cachePrompt = AiGenerationConfig.DEFAULT_CACHE_PROMPT;
    private boolean swaFull = AiGenerationConfig.DEFAULT_SWA_FULL;
    private int cacheReuse = AiGenerationConfig.DEFAULT_CACHE_REUSE;
    private int gpuLayers = AiGenerationConfig.DEFAULT_GPU_LAYERS;
    private int seed = AiGenerationConfig.DEFAULT_SEED;
    private int cpuMoeLayers = AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS;
    private int cpuFfnLayers = AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS;
    private int kvUnifiedPerSlot = AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT;
    private String lazyMode = AiGenerationConfig.DEFAULT_LAZY_MODE;
    private int repeatLastN = AiGenerationConfig.DEFAULT_REPEAT_LAST_N;
    private String cacheTypeK = AiGenerationConfig.DEFAULT_CACHE_TYPE_K;
    private String cacheTypeV = AiGenerationConfig.DEFAULT_CACHE_TYPE_V;
    private boolean flashAttn = AiGenerationConfig.DEFAULT_FLASH_ATTN;
    private int batchSize = AiGenerationConfig.DEFAULT_BATCH_SIZE;
    private int ubatchSize = AiGenerationConfig.DEFAULT_UBATCH_SIZE;
    private int threadsBatch = AiGenerationConfig.DEFAULT_THREADS_BATCH;
    private int mainGpu = AiGenerationConfig.DEFAULT_MAIN_GPU;
    private String devices = AiGenerationConfig.DEFAULT_DEVICES;
    private String reasoningEffort = AiGenerationConfig.DEFAULT_REASONING_EFFORT;
    private int reasoningBudgetTokens = AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS;
    private float dryMultiplier = AiGenerationConfig.DEFAULT_DRY_MULTIPLIER;
    private float dryBase = AiGenerationConfig.DEFAULT_DRY_BASE;
    private int dryAllowedLength = AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH;
    private int dryPenaltyLastN = AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N;
    private @Nullable List<String> drySequenceBreakers;
    private @Nullable List<String> stopStrings;

    /** Optional per-machine timing calibration ({@code <calibration>}), measured by {@code srcmorph:calibrate}. */
    private @Nullable AiCalibration calibration;

    /**
     * Returns the unique lookup key for this definition.
     *
     * @return the key, or {@code null} if not set
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique lookup key for this definition.
     *
     * @param key the key used to reference this definition from field-generation configs
     */
    public void setKey(final String key) {
        this.key = key;
    }

    /**
     * Returns the path to the GGUF model file.
     *
     * @return the model path, or {@code null} if not set
     */
    public String getModelPath() {
        return modelPath;
    }

    /**
     * Sets the path to the GGUF model file.
     *
     * @param modelPath absolute or relative path to the model file
     */
    public void setModelPath(final String modelPath) {
        this.modelPath = modelPath;
    }

    /**
     * Returns the context window size (in tokens).
     *
     * @return context size, defaults to {@link AiGenerationConfig#DEFAULT_CONTEXT_SIZE}
     */
    public int getContextSize() {
        return contextSize;
    }

    /**
     * Sets the context window size (in tokens).
     *
     * @param contextSize number of tokens in the model context window
     */
    public void setContextSize(final int contextSize) {
        this.contextSize = contextSize;
    }

    /**
     * Returns the maximum number of output tokens per inference call.
     *
     * @return max output tokens, defaults to {@link AiGenerationConfig#DEFAULT_MAX_OUTPUT_TOKENS}
     */
    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Sets the maximum number of output tokens per inference call.
     *
     * @param maxOutputTokens max tokens to generate per request
     */
    public void setMaxOutputTokens(final int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * Returns the base sampling temperature.
     *
     * @return temperature, defaults to {@link AiGenerationConfig#DEFAULT_TEMPERATURE}
     */
    public float getTemperature() {
        return temperature;
    }

    /**
     * Sets the base sampling temperature.
     *
     * @param temperature lower values are more deterministic; {@code 0.0} is fully greedy
     */
    public void setTemperature(final float temperature) {
        this.temperature = temperature;
    }

    /**
     * Returns the number of CPU threads for inference.
     *
     * @return thread count, defaults to {@link AiGenerationConfig#DEFAULT_THREADS}
     */
    public int getThreads() {
        return threads;
    }

    /**
     * Sets the number of CPU threads for inference.
     *
     * @param threads number of threads to use during llama.cpp inference
     */
    public void setThreads(final int threads) {
        this.threads = threads;
    }

    /**
     * Returns the number of characters per token used to automatically calculate the
     * maximum input characters for the source code.
     *
     * <p>Together with {@link #contextSize} and {@link #maxOutputTokens}, this value drives
     * the runtime calculation of how many source characters may be fed into the prompt before
     * trimming. Setting this to {@code 0} disables automatic calculation and falls back to
     * the internal default ({@link AiGenerationConfig#DEFAULT_MAX_INPUT_CHARS}).</p>
     *
     * @return chars-per-token ratio; defaults to {@link AiGenerationConfig#DEFAULT_CHARS_PER_TOKEN}
     */
    public int getCharsPerToken() {
        return charsPerToken;
    }

    /**
     * Sets the number of characters per token for automatic max-input-chars calculation.
     *
     * @param charsPerToken approximate characters per token (typically {@code 4} for
     *                      Latin-script source code); {@code 0} disables auto-calculation
     */
    public void setCharsPerToken(final int charsPerToken) {
        this.charsPerToken = charsPerToken;
    }

    /**
     * Returns whether a warning is emitted when source text is trimmed.
     *
     * @return {@code true} to emit a warning on trim; defaults to {@link AiGenerationConfig#DEFAULT_WARN_ON_TRIM}
     */
    public boolean isWarnOnTrim() {
        return warnOnTrim;
    }

    /**
     * Sets whether a warning is emitted when source text is trimmed.
     *
     * @param warnOnTrim {@code true} to log a warning whenever input is trimmed
     */
    public void setWarnOnTrim(final boolean warnOnTrim) {
        this.warnOnTrim = warnOnTrim;
    }

    /**
     * Returns the nucleus-sampling probability threshold.
     *
     * @return top-p value; defaults to {@link AiGenerationConfig#DEFAULT_TOP_P}
     */
    public float getTopP() {
        return topP;
    }

    /**
     * Sets the nucleus-sampling probability threshold.
     *
     * @param topP cumulative probability threshold; {@code 1.0} disables top-p filtering
     */
    public void setTopP(final float topP) {
        this.topP = topP;
    }

    /**
     * Returns the top-k sampling limit.
     *
     * @return top-k value; defaults to {@link AiGenerationConfig#DEFAULT_TOP_K}
     */
    public int getTopK() {
        return topK;
    }

    /**
     * Sets the top-k sampling limit.
     *
     * @param topK number of top tokens to sample from; {@code 0} disables top-k filtering
     */
    public void setTopK(final int topK) {
        this.topK = topK;
    }

    /**
     * Returns the repetition penalty.
     *
     * @return repeat-penalty value; defaults to {@link AiGenerationConfig#DEFAULT_REPEAT_PENALTY}
     */
    public float getRepeatPenalty() {
        return repeatPenalty;
    }

    /**
     * Sets the repetition penalty applied to already-generated tokens.
     *
     * @param repeatPenalty penalty factor; {@code 1.0} means no penalty
     */
    public void setRepeatPenalty(final float repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
    }

    /**
     * Returns the min-p sampling threshold.
     *
     * @return min-p threshold; defaults to {@link AiGenerationConfig#DEFAULT_MIN_P} ({@code 0.0} = disabled)
     */
    public float getMinP() {
        return minP;
    }

    /**
     * Sets the min-p sampling threshold (keep tokens with probability ≥ minP × top-token probability).
     *
     * @param minP min-p threshold; {@code 0.0} disables min-p truncation
     */
    public void setMinP(final float minP) {
        this.minP = minP;
    }

    /**
     * Returns the top-n-sigma sampling threshold.
     *
     * @return top-n-sigma threshold; defaults to {@link AiGenerationConfig#DEFAULT_TOP_N_SIGMA} ({@code -1.0} = disabled)
     */
    public float getTopNSigma() {
        return topNSigma;
    }

    /**
     * Sets the top-n-sigma sampling threshold (temperature-invariant truncation; -1.0 disables it).
     *
     * @param topNSigma top-n-sigma threshold; {@code -1.0} disables it
     */
    public void setTopNSigma(final float topNSigma) {
        this.topNSigma = topNSigma;
    }

    /**
     * Returns this definition's chat-template thinking mode, or {@code null} when it was never set.
     *
     * @return {@code true} or {@code false} when the knob was set, {@code null} when it is unset
     *         &#x2014; the kwarg is then not sent and the model's own chat-template default applies;
     *         defaults to {@link AiGenerationConfig#DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING}
     */
    public @Nullable Boolean getChatTemplateEnableThinking() {
        return chatTemplateEnableThinking;
    }

    /**
     * Sets this definition's chat-template thinking mode.
     *
     * @param chatTemplateEnableThinking {@code false} passes
     *        {@code enable_thinking=false} to
     *        {@link net.ladenthin.llama.parameters.ModelParameters#setChatTemplateKwargs} to suppress
     *        chain-of-thought reasoning at the Jinja template level, {@code true} passes it as
     *        {@code true}, and {@code null} omits the kwarg entirely
     */
    public void setChatTemplateEnableThinking(final @Nullable Boolean chatTemplateEnableThinking) {
        this.chatTemplateEnableThinking = chatTemplateEnableThinking;
    }

    /**
     * Returns whether llama.cpp prompt caching is enabled for this model.
     *
     * @return {@code true} to reuse the shared prompt-prefix KV across files;
     *         defaults to {@link AiGenerationConfig#DEFAULT_CACHE_PROMPT}
     */
    public boolean isCachePrompt() {
        return cachePrompt;
    }

    /**
     * Sets whether llama.cpp prompt caching ({@code cache_prompt}) is enabled for this model.
     *
     * @param cachePrompt {@code true} keeps the prompt-template prefix warm in the KV cache and
     *        reuses it per file (only the differing source is re-prefilled); output is unchanged
     */
    public void setCachePrompt(final boolean cachePrompt) {
        this.cachePrompt = cachePrompt;
    }

    /**
     * Returns whether the full-size SWA KV cache is kept ({@code --swa-full}) for this model.
     *
     * @return {@code true} to keep full SWA KV; defaults to {@link AiGenerationConfig#DEFAULT_SWA_FULL}
     */
    public boolean isSwaFull() {
        return swaFull;
    }

    /**
     * Sets whether the full-size SWA KV cache is kept ({@code --swa-full}) for this model.
     *
     * @param swaFull {@code true} keeps full SWA KV (enables cross-request prefix reuse, more RAM)
     */
    public void setSwaFull(final boolean swaFull) {
        this.swaFull = swaFull;
    }

    /**
     * Returns the KV prefix-reuse minimum chunk size ({@code --cache-reuse}) for this model.
     *
     * @return cache-reuse chunk size; defaults to {@link AiGenerationConfig#DEFAULT_CACHE_REUSE} (0 = off)
     */
    public int getCacheReuse() {
        return cacheReuse;
    }

    /**
     * Sets the KV prefix-reuse minimum chunk size ({@code --cache-reuse}) for this model.
     *
     * @param cacheReuse chunk size in tokens ({@code 0} = disabled)
     */
    public void setCacheReuse(final int cacheReuse) {
        this.cacheReuse = cacheReuse;
    }

    /**
     * Returns the GPU layer offload count ({@code --gpu-layers}) for this model.
     *
     * @return GPU layers; defaults to {@link AiGenerationConfig#DEFAULT_GPU_LAYERS} (-1 = leave default)
     */
    public int getGpuLayers() {
        return gpuLayers;
    }

    /**
     * Sets the GPU layer offload count ({@code --gpu-layers}) for this model.
     *
     * @param gpuLayers GPU layers ({@code -1} = leave default, {@code 0} = force CPU, {@code >0} = offload)
     */
    public void setGpuLayers(final int gpuLayers) {
        this.gpuLayers = gpuLayers;
    }

    /**
     * Returns the number of MoE layers kept on the CPU ({@code --n-cpu-moe}) for this model.
     *
     * @return CPU MoE layers; defaults to {@link AiGenerationConfig#DEFAULT_CPU_MOE_LAYERS} (-1 = leave default)
     */
    public int getCpuMoeLayers() {
        return cpuMoeLayers;
    }

    /**
     * Sets the number of MoE layers kept on the CPU ({@code --n-cpu-moe}) for this model.
     *
     * @param cpuMoeLayers CPU MoE layers ({@code -1} = leave default, {@code >= 0} = keep that many)
     */
    public void setCpuMoeLayers(final int cpuMoeLayers) {
        this.cpuMoeLayers = cpuMoeLayers;
    }

    /**
     * Returns the RNG seed for this model.
     *
     * @return the seed; defaults to {@link AiGenerationConfig#DEFAULT_SEED} (-1 = random per request)
     */
    public int getSeed() {
        return seed;
    }

    /**
     * Sets the RNG seed for this model.
     *
     * @param seed the seed ({@code -1} = leave upstream's random-per-request default)
     */
    public void setSeed(final int seed) {
        this.seed = seed;
    }

    /**
     * Returns the number of FFN layers kept on the CPU ({@code --n-cpu-ffn}) for this model.
     *
     * @return CPU FFN layers; defaults to {@link AiGenerationConfig#DEFAULT_CPU_FFN_LAYERS} (-1 = leave default)
     */
    public int getCpuFfnLayers() {
        return cpuFfnLayers;
    }

    /**
     * Sets the number of FFN layers kept on the CPU ({@code --n-cpu-ffn}) for this model.
     *
     * @param cpuFfnLayers CPU FFN layers ({@code -1} = leave default, {@code >= 0} = keep that many)
     */
    public void setCpuFfnLayers(final int cpuFfnLayers) {
        this.cpuFfnLayers = cpuFfnLayers;
    }

    /**
     * Returns the per-slot unified KV context cap ({@code --kv-unified-per-slot}) for this model.
     *
     * @return the cap; defaults to {@link AiGenerationConfig#DEFAULT_KV_UNIFIED_PER_SLOT} (-1 = leave default)
     */
    public int getKvUnifiedPerSlot() {
        return kvUnifiedPerSlot;
    }

    /**
     * Sets the per-slot unified KV context cap ({@code --kv-unified-per-slot}) for this model.
     *
     * @param kvUnifiedPerSlot the cap ({@code -1} = leave default, must be positive when set)
     */
    public void setKvUnifiedPerSlot(final int kvUnifiedPerSlot) {
        this.kvUnifiedPerSlot = kvUnifiedPerSlot;
    }

    /**
     * Returns the tensor-read laziness ({@code --tensor-read-lazy}) for this model.
     *
     * @return {@code off}, {@code auto}, {@code on}, or empty to leave the default
     */
    public String getLazyMode() {
        return lazyMode;
    }

    /**
     * Sets the tensor-read laziness ({@code --tensor-read-lazy}) for this model.
     *
     * @param lazyMode {@code off}, {@code auto}, {@code on}, or empty/{@code null} to leave the default
     */
    public void setLazyMode(final @Nullable String lazyMode) {
        this.lazyMode = lazyMode != null ? lazyMode : AiGenerationConfig.DEFAULT_LAZY_MODE;
    }

    /**
     * Returns the repeat-penalty window ({@code --repeat-last-n}) for this model.
     *
     * @return the window; defaults to {@link AiGenerationConfig#DEFAULT_REPEAT_LAST_N}
     */
    public int getRepeatLastN() {
        return repeatLastN;
    }

    /**
     * Sets the repeat-penalty window ({@code --repeat-last-n}) for this model. This is the range
     * {@code repeatPenalty} acts on.
     *
     * @param repeatLastN the window ({@code -1} = not sent, llama.cpp's own; {@code 0} = disable the
     *                    penalty)
     */
    public void setRepeatLastN(final int repeatLastN) {
        this.repeatLastN = repeatLastN;
    }

    /**
     * Returns the KV-cache data type for K ({@code --cache-type-k}) for this model.
     *
     * @return a llama.cpp cache type such as {@code q8_0}, or empty to leave the default
     */
    public String getCacheTypeK() {
        return cacheTypeK;
    }

    /**
     * Sets the KV-cache data type for K ({@code --cache-type-k}) for this model.
     *
     * @param cacheTypeK a llama.cpp cache type, or empty/{@code null} to leave the default
     */
    public void setCacheTypeK(final @Nullable String cacheTypeK) {
        this.cacheTypeK = cacheTypeK != null ? cacheTypeK : AiGenerationConfig.DEFAULT_CACHE_TYPE_K;
    }

    /**
     * Returns the KV-cache data type for V ({@code --cache-type-v}) for this model.
     *
     * @return a llama.cpp cache type such as {@code q8_0}, or empty to leave the default
     */
    public String getCacheTypeV() {
        return cacheTypeV;
    }

    /**
     * Sets the KV-cache data type for V ({@code --cache-type-v}) for this model. Usually requires
     * {@link #setFlashAttn(boolean)}.
     *
     * @param cacheTypeV a llama.cpp cache type, or empty/{@code null} to leave the default
     */
    public void setCacheTypeV(final @Nullable String cacheTypeV) {
        this.cacheTypeV = cacheTypeV != null ? cacheTypeV : AiGenerationConfig.DEFAULT_CACHE_TYPE_V;
    }

    /**
     * Returns whether Flash Attention is enabled ({@code --flash-attn}) for this model.
     *
     * @return {@code true} when Flash Attention is enabled
     */
    public boolean isFlashAttn() {
        return flashAttn;
    }

    /**
     * Sets whether Flash Attention is enabled ({@code --flash-attn}) for this model.
     *
     * @param flashAttn {@code true} to enable it (lower KV memory, and the precondition for a
     *                  quantized V cache)
     */
    public void setFlashAttn(final boolean flashAttn) {
        this.flashAttn = flashAttn;
    }

    /**
     * Returns the logical batch size ({@code --batch-size}) for this model.
     *
     * @return the logical batch size, or {@code -1} to leave the default
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the logical batch size ({@code --batch-size}) for this model.
     *
     * @param batchSize the logical batch size ({@code -1} = leave default, must be positive when set)
     */
    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Returns the physical batch size ({@code --ubatch-size}) for this model.
     *
     * @return the physical batch size, or {@code -1} to leave the default
     */
    public int getUbatchSize() {
        return ubatchSize;
    }

    /**
     * Sets the physical batch size ({@code --ubatch-size}) for this model.
     *
     * @param ubatchSize the physical batch size ({@code -1} = leave default, must be positive when set)
     */
    public void setUbatchSize(final int ubatchSize) {
        this.ubatchSize = ubatchSize;
    }

    /**
     * Returns the batch/prompt-processing thread count ({@code --threads-batch}) for this model.
     *
     * @return the batch thread count, or {@code -1} to reuse {@code threads}
     */
    public int getThreadsBatch() {
        return threadsBatch;
    }

    /**
     * Sets the batch/prompt-processing thread count ({@code --threads-batch}) for this model.
     *
     * @param threadsBatch the batch thread count ({@code -1} = reuse {@code threads}, must be positive
     *                     when set)
     */
    public void setThreadsBatch(final int threadsBatch) {
        this.threadsBatch = threadsBatch;
    }

    /**
     * Returns the primary GPU index ({@code --main-gpu}) for this model.
     *
     * @return the GPU index; defaults to {@link AiGenerationConfig#DEFAULT_MAIN_GPU} (-1 = leave default)
     */
    public int getMainGpu() {
        return mainGpu;
    }

    /**
     * Sets the primary GPU index ({@code --main-gpu}) for this model.
     *
     * @param mainGpu the GPU index ({@code -1} = leave default; a non-negative value selects that device)
     */
    public void setMainGpu(final int mainGpu) {
        this.mainGpu = mainGpu;
    }

    /**
     * Returns the device selection ({@code --device}) for this model.
     *
     * @return the comma-separated device list; defaults to {@link AiGenerationConfig#DEFAULT_DEVICES} (empty = leave default)
     */
    public String getDevices() {
        return devices;
    }

    /**
     * Sets the device selection ({@code --device}) for this model. A {@code null} argument resets to the empty default.
     *
     * @param devices the comma-separated backend device names (e.g. {@code Vulkan1}), or {@code null}/empty to leave default
     */
    public void setDevices(final String devices) {
        this.devices = devices == null ? AiGenerationConfig.DEFAULT_DEVICES : devices;
    }

    /**
     * Returns the gpt-oss reasoning-effort level for this model.
     *
     * @return reasoning effort ({@code "low"}/{@code "medium"}/{@code "high"}), or empty to omit it;
     *         defaults to {@link AiGenerationConfig#DEFAULT_REASONING_EFFORT}
     */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * Sets the gpt-oss reasoning-effort level for this model.
     *
     * @param reasoningEffort {@code "low"}/{@code "medium"}/{@code "high"} (passed as the
     *        {@code reasoning_effort} chat-template kwarg), or empty/blank to omit it
     */
    public void setReasoningEffort(final String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    /**
     * Returns the reasoning/think-token budget for this model.
     *
     * @return budget tokens; defaults to {@link AiGenerationConfig#DEFAULT_REASONING_BUDGET_TOKENS} (-1 = off)
     */
    public int getReasoningBudgetTokens() {
        return reasoningBudgetTokens;
    }

    /**
     * Sets the reasoning/think-token budget for this model (caps harmony analysis tokens).
     *
     * @param reasoningBudgetTokens budget in tokens ({@code -1} = unrestricted, {@code 0} = no thinking)
     */
    public void setReasoningBudgetTokens(final int reasoningBudgetTokens) {
        this.reasoningBudgetTokens = reasoningBudgetTokens;
    }

    /**
     * Returns the DRY sampling multiplier for this model.
     *
     * @return DRY multiplier; defaults to {@link AiGenerationConfig#DEFAULT_DRY_MULTIPLIER} (0.0 = off)
     */
    public float getDryMultiplier() {
        return dryMultiplier;
    }

    /**
     * Sets the DRY sampling multiplier for this model.
     *
     * @param dryMultiplier DRY multiplier ({@code 0.0} = disabled)
     */
    public void setDryMultiplier(final float dryMultiplier) {
        this.dryMultiplier = dryMultiplier;
    }

    /**
     * Returns the DRY base for this model.
     *
     * @return DRY base; defaults to {@link AiGenerationConfig#DEFAULT_DRY_BASE}
     */
    public float getDryBase() {
        return dryBase;
    }

    /**
     * Sets the DRY base for this model.
     *
     * @param dryBase DRY base
     */
    public void setDryBase(final float dryBase) {
        this.dryBase = dryBase;
    }

    /**
     * Returns the DRY allowed length for this model.
     *
     * @return DRY allowed length; defaults to {@link AiGenerationConfig#DEFAULT_DRY_ALLOWED_LENGTH}
     */
    public int getDryAllowedLength() {
        return dryAllowedLength;
    }

    /**
     * Sets the DRY allowed length for this model.
     *
     * @param dryAllowedLength DRY allowed length
     */
    public void setDryAllowedLength(final int dryAllowedLength) {
        this.dryAllowedLength = dryAllowedLength;
    }

    /**
     * Returns the DRY penalty look-back window for this model.
     *
     * @return DRY penalty last-n; defaults to {@link AiGenerationConfig#DEFAULT_DRY_PENALTY_LAST_N}
     */
    public int getDryPenaltyLastN() {
        return dryPenaltyLastN;
    }

    /**
     * Sets the DRY penalty look-back window for this model.
     *
     * @param dryPenaltyLastN DRY penalty last-n ({@code -1} = not sent, llama.cpp's own window;
     *                         {@code 0} = disabled)
     */
    public void setDryPenaltyLastN(final int dryPenaltyLastN) {
        this.dryPenaltyLastN = dryPenaltyLastN;
    }

    /**
     * Returns the DRY sequence breakers for this model.
     *
     * @return DRY sequence breakers, or {@code null} if not configured (use the model/binding default)
     */
    public @Nullable List<String> getDrySequenceBreakers() {
        return drySequenceBreakers != null ? Collections.unmodifiableList(drySequenceBreakers) : null;
    }

    /**
     * Sets the DRY sequence breakers (tokens that reset n-gram matching) for this model.
     *
     * @param drySequenceBreakers collection of sequence breakers, or {@code null} to clear
     */
    public void setDrySequenceBreakers(final @Nullable Collection<String> drySequenceBreakers) {
        this.drySequenceBreakers = drySequenceBreakers != null ? new ArrayList<>(drySequenceBreakers) : null;
    }

    /**
     * Returns the list of stop strings that terminate generation when encountered.
     *
     * @return stop strings, or {@code null} if not configured
     */
    public @Nullable List<String> getStopStrings() {
        return stopStrings != null ? Collections.unmodifiableList(stopStrings) : null;
    }

    /**
     * Sets the list of stop strings that terminate generation when encountered.
     *
     * @param stopStrings collection of strings; generation stops at the first match
     */
    public void setStopStrings(final @Nullable Collection<String> stopStrings) {
        this.stopStrings = stopStrings != null ? new ArrayList<>(stopStrings) : null;
    }

    /**
     * Returns the per-machine timing calibration, or {@code null} when not calibrated.
     *
     * @return the calibration, or {@code null}
     */
    public @Nullable AiCalibration getCalibration() {
        return calibration;
    }

    /**
     * Sets the per-machine timing calibration.
     *
     * @param calibration the calibration
     */
    public void setCalibration(final @Nullable AiCalibration calibration) {
        this.calibration = calibration;
    }
}
