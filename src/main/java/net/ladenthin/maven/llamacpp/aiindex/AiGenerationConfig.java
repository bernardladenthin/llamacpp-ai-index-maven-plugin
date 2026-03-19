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

    private String modelPath;
    private int contextSize = 32768;
    private int maxTokens = 128;
    private float temperature = 0.15f;
    private int threads = 8;
    private int maxInputChars = 120000;
    private boolean warnOnTrim = true;

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

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(final int maxTokens) {
        this.maxTokens = maxTokens;
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