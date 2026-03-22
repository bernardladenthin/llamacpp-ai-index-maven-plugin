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

public class AiGenerationConfig {

    /** Default context window size (in tokens) used when no explicit context size is configured. */
    public static final int DEFAULT_CONTEXT_SIZE = 32768;

    /** Default maximum number of output tokens to generate in a single inference call. */
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 128;

    /**
     * Default sampling temperature. Lower values make output more deterministic;
     * {@code 0.0} is fully greedy.
     */
    public static final float DEFAULT_TEMPERATURE = 0.15f;

    /** Default number of CPU threads used for llama.cpp inference. */
    public static final int DEFAULT_THREADS = 8;

    /**
     * Default maximum number of characters of source text fed into the prompt.
     * Used as a fallback when {@link #charsPerToken} is zero (e.g. in mock-based tests).
     * When {@link #charsPerToken} is greater than zero, {@code maxInputChars} is calculated
     * automatically from context size, output tokens, prompt overhead, and safety margin.
     */
    public static final int DEFAULT_MAX_INPUT_CHARS = 120000;

    /**
     * Default number of characters per token used in the automatic {@code maxInputChars}
     * calculation. A value of {@code 4} is a conventional conservative estimate that works
     * well across most Latin-script source code.
     *
     * <p>Set to {@code 0} to disable automatic calculation and fall back to
     * {@link #DEFAULT_MAX_INPUT_CHARS}.</p>
     */
    public static final int DEFAULT_CHARS_PER_TOKEN = 4;

    /**
     * Default safety margin in characters subtracted from the total available budget
     * during automatic {@code maxInputChars} calculation. Provides headroom for
     * tokenisation overhead and minor prompt expansions.
     */
    public static final int DEFAULT_SAFETY_MARGIN_CHARS = 500;

    /**
     * Default setting for whether to emit a warning when the prompt source text is trimmed
     * to fit within {@link #DEFAULT_MAX_INPUT_CHARS}.
     */
    public static final boolean DEFAULT_WARN_ON_TRIM = true;

    /**
     * Default maximum number of retry attempts when the AI provider returns an empty body.
     * A value of {@code 0} disables retries entirely.
     * Each retry uses a temperature incremented by {@link #DEFAULT_RETRY_TEMPERATURE_INCREMENT}.
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * Default temperature increment applied on each successive retry attempt.
     * Added to {@link #temperature} per attempt: attempt 1 uses
     * {@code temperature + retryTemperatureIncrement}, attempt 2 uses
     * {@code temperature + 2 * retryTemperatureIncrement}, and so on.
     * Higher temperatures make the model less deterministic and can break out of
     * EOS-early failure modes.
     */
    public static final float DEFAULT_RETRY_TEMPERATURE_INCREMENT = 0.1f;

    private String modelPath;
    private int contextSize = DEFAULT_CONTEXT_SIZE;
    private int maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
    private float temperature = DEFAULT_TEMPERATURE;
    private int threads = DEFAULT_THREADS;
    private int charsPerToken = DEFAULT_CHARS_PER_TOKEN;
    private int maxInputChars = DEFAULT_MAX_INPUT_CHARS;
    private boolean warnOnTrim = DEFAULT_WARN_ON_TRIM;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private float retryTemperatureIncrement = DEFAULT_RETRY_TEMPERATURE_INCREMENT;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(final String modelPath) {
        this.modelPath = modelPath;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(final int contextSize) {
        this.contextSize = contextSize;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(final int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(final float temperature) {
        this.temperature = temperature;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(final int threads) {
        this.threads = threads;
    }

    /**
     * Returns the number of characters per token used in automatic {@code maxInputChars}
     * calculation.
     *
     * @return chars-per-token ratio; {@code 0} disables automatic calculation
     */
    public int getCharsPerToken() {
        return charsPerToken;
    }

    /**
     * Sets the number of characters per token.
     *
     * @param charsPerToken approximate characters per token; use {@code 0} to disable
     *                      automatic calculation and fall back to {@link #getMaxInputChars()}
     */
    public void setCharsPerToken(final int charsPerToken) {
        this.charsPerToken = charsPerToken;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(final int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public boolean isWarnOnTrim() {
        return warnOnTrim;
    }

    public void setWarnOnTrim(final boolean warnOnTrim) {
        this.warnOnTrim = warnOnTrim;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(final int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public float getRetryTemperatureIncrement() {
        return retryTemperatureIncrement;
    }

    public void setRetryTemperatureIncrement(final float retryTemperatureIncrement) {
        this.retryTemperatureIncrement = retryTemperatureIncrement;
    }
}