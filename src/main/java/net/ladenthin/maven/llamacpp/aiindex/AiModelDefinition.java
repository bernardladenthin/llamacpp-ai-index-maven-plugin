// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.maven.llamacpp.aiindex;

import java.util.List;

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
public class AiModelDefinition {

    private String key;
    private String modelPath;
    private int contextSize = AiGenerationConfig.DEFAULT_CONTEXT_SIZE;
    private int maxOutputTokens = AiGenerationConfig.DEFAULT_MAX_OUTPUT_TOKENS;
    private float temperature = AiGenerationConfig.DEFAULT_TEMPERATURE;
    private int threads = AiGenerationConfig.DEFAULT_THREADS;
    private int charsPerToken = AiGenerationConfig.DEFAULT_CHARS_PER_TOKEN;
    private boolean warnOnTrim = AiGenerationConfig.DEFAULT_WARN_ON_TRIM;
    private int maxRetries = AiGenerationConfig.DEFAULT_MAX_RETRIES;
    private float retryTemperatureIncrement = AiGenerationConfig.DEFAULT_RETRY_TEMPERATURE_INCREMENT;
    private float topP = AiGenerationConfig.DEFAULT_TOP_P;
    private int topK = AiGenerationConfig.DEFAULT_TOP_K;
    private float repeatPenalty = AiGenerationConfig.DEFAULT_REPEAT_PENALTY;
    private boolean chatTemplateEnableThinking = AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING;
    private List<String> stopStrings;

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
     * Returns the maximum number of retry attempts when the provider returns an empty body.
     *
     * @return max retries, defaults to {@link AiGenerationConfig#DEFAULT_MAX_RETRIES}
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retry attempts when the provider returns an empty body.
     *
     * @param maxRetries {@code 0} disables retries entirely
     */
    public void setMaxRetries(final int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Returns the temperature increment applied on each successive retry attempt.
     *
     * @return retry temperature increment, defaults to {@link AiGenerationConfig#DEFAULT_RETRY_TEMPERATURE_INCREMENT}
     */
    public float getRetryTemperatureIncrement() {
        return retryTemperatureIncrement;
    }

    /**
     * Sets the temperature increment applied on each successive retry attempt.
     *
     * @param retryTemperatureIncrement added to the base temperature on each retry
     */
    public void setRetryTemperatureIncrement(final float retryTemperatureIncrement) {
        this.retryTemperatureIncrement = retryTemperatureIncrement;
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
     * Returns whether the model's chat-template thinking mode is enabled.
     *
     * <p>When {@code true} (the default), no {@code setChatTemplateKwargs} call is made.
     * When {@code false}, {@code setChatTemplateKwargs({"enable_thinking": "false"})} is
     * passed to disable chain-of-thought reasoning at the Jinja template level.</p>
     *
     * @return {@code true} to keep thinking enabled; defaults to
     *         {@link AiGenerationConfig#DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING}
     */
    public boolean isChatTemplateEnableThinking() {
        return chatTemplateEnableThinking;
    }

    /**
     * Sets whether the model's chat-template thinking mode is enabled.
     *
     * @param chatTemplateEnableThinking {@code false} disables thinking by passing
     *        {@code enable_thinking=false} to the Jinja chat template via
     *        {@code InferenceParameters.setChatTemplateKwargs}
     */
    public void setChatTemplateEnableThinking(final boolean chatTemplateEnableThinking) {
        this.chatTemplateEnableThinking = chatTemplateEnableThinking;
    }

    /**
     * Returns the list of stop strings that terminate generation when encountered.
     *
     * @return stop strings, or {@code null} if not configured
     */
    public List<String> getStopStrings() {
        return stopStrings;
    }

    /**
     * Sets the list of stop strings that terminate generation when encountered.
     *
     * @param stopStrings list of strings; generation stops at the first match
     */
    public void setStopStrings(final List<String> stopStrings) {
        this.stopStrings = stopStrings;
    }
}
