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
public class AiPreparedPrompt {
    private final String prompt;
    private final String sourceText;
    private final boolean trimmed;
    private final int originalSourceLength;
    private final int trimmedSourceLength;
    private final int availableSourceChars;


    public AiPreparedPrompt(String prompt, String sourceText, boolean trimmed, int originalSourceLength, int trimmedSourceLength, int availableSourceChars) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(sourceText, "sourceText");
        this.prompt = prompt;
        this.sourceText = sourceText;
        this.trimmed = trimmed;
        this.originalSourceLength = originalSourceLength;
        this.trimmedSourceLength = trimmedSourceLength;
        this.availableSourceChars = availableSourceChars;
    }

    public String prompt() {
        return prompt;
    }

    public String sourceText() {
        return sourceText;
    }

    public boolean trimmed() {
        return trimmed;
    }

    public int originalSourceLength() {
        return originalSourceLength;
    }

    public int trimmedSourceLength() {
        return trimmedSourceLength;
    }

    public int availableSourceChars() {
        return availableSourceChars;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        AiPreparedPrompt that = (AiPreparedPrompt) obj;
        return Objects.equals(this.prompt, that.prompt) &&
                Objects.equals(this.sourceText, that.sourceText) &&
                this.trimmed == that.trimmed &&
                this.originalSourceLength == that.originalSourceLength &&
                this.trimmedSourceLength == that.trimmedSourceLength &&
                this.availableSourceChars == that.availableSourceChars;
    }

    @Override
    public int hashCode() {
        return Objects.hash(prompt, sourceText, trimmed, originalSourceLength, trimmedSourceLength, availableSourceChars);
    }

    @Override
    public String toString() {
        return "AiPreparedPrompt[" +
                "prompt=" + prompt + ", " +
                "sourceText=" + sourceText + ", " +
                "trimmed=" + trimmed + ", " +
                "originalSourceLength=" + originalSourceLength + ", " +
                "trimmedSourceLength=" + trimmedSourceLength + ", " +
                "availableSourceChars=" + availableSourceChars + ']';
    }

}