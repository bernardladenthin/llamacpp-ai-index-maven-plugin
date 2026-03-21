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

import java.util.Objects;

@ConvertToRecord
public class LlamaCppJniConfig {
    private final String libraryPath;
    private final String modelPath;
    private final int contextSize;
    private final int maxOutputTokens;
    private final float temperature;
    private final int threads;

    public LlamaCppJniConfig(String libraryPath, String modelPath, int contextSize, int maxOutputTokens, float temperature, int threads) {
        Objects.requireNonNull(modelPath, "modelPath");
        this.libraryPath = libraryPath;
        this.modelPath = modelPath;
        this.contextSize = contextSize;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.threads = threads;
    }

    public String libraryPath() {
        return libraryPath;
    }

    public String modelPath() {
        return modelPath;
    }

    public int contextSize() {
        return contextSize;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public float temperature() {
        return temperature;
    }

    public int threads() {
        return threads;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        LlamaCppJniConfig that = (LlamaCppJniConfig) obj;
        return Objects.equals(this.libraryPath, that.libraryPath) &&
                Objects.equals(this.modelPath, that.modelPath) &&
                this.contextSize == that.contextSize &&
                this.maxOutputTokens == that.maxOutputTokens &&
                Float.floatToIntBits(this.temperature) == Float.floatToIntBits(that.temperature) &&
                this.threads == that.threads;
    }

    @Override
    public int hashCode() {
        return Objects.hash(libraryPath, modelPath, contextSize, maxOutputTokens, temperature, threads);
    }

    @Override
    public String toString() {
        return "LlamaCppJniConfig[" +
                "libraryPath=" + libraryPath + ", " +
                "modelPath=" + modelPath + ", " +
                "contextSize=" + contextSize + ", " +
                "maxOutputTokens=" + maxOutputTokens + ", " +
                "temperature=" + temperature + ", " +
                "threads=" + threads + ']';
    }

}