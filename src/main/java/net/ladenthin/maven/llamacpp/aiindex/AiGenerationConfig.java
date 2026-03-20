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
     * Prompts exceeding this limit are trimmed before being sent to the AI provider.
     */
    public static final int DEFAULT_MAX_INPUT_CHARS = 120000;

    /**
     * Default setting for whether to emit a warning when the prompt source text is trimmed
     * to fit within {@link #DEFAULT_MAX_INPUT_CHARS}.
     */
    public static final boolean DEFAULT_WARN_ON_TRIM = true;

    private String modelPath;
    private int contextSize = DEFAULT_CONTEXT_SIZE;
    private int maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
    private float temperature = DEFAULT_TEMPERATURE;
    private int threads = DEFAULT_THREADS;
    private int maxInputChars = DEFAULT_MAX_INPUT_CHARS;
    private boolean warnOnTrim = DEFAULT_WARN_ON_TRIM;

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
}