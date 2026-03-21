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

import java.nio.file.Path;
import java.util.Objects;

@ConvertToRecord
public class AiGenerationRequest {
    private final String promptKey;
    private final Path sourceFile;
    private final String sourceText;
    private final AiMdHeader currentHeader;


    public AiGenerationRequest(String promptKey, Path sourceFile, String sourceText, AiMdHeader currentHeader) {
        Objects.requireNonNull(promptKey, "promptKey");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(currentHeader, "currentHeader");
        this.promptKey = promptKey;
        this.sourceFile = sourceFile;
        this.sourceText = sourceText;
        this.currentHeader = currentHeader;
    }

    public String promptKey() {
        return promptKey;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public String sourceText() {
        return sourceText;
    }

    public AiMdHeader currentHeader() {
        return currentHeader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        AiGenerationRequest that = (AiGenerationRequest) obj;
        return Objects.equals(this.promptKey, that.promptKey) &&
                Objects.equals(this.sourceFile, that.sourceFile) &&
                Objects.equals(this.sourceText, that.sourceText) &&
                Objects.equals(this.currentHeader, that.currentHeader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptKey, sourceFile, sourceText, currentHeader);
    }

    @Override
    public String toString() {
        return "AiGenerationRequest[" +
                "promptKey=" + promptKey + ", " +
                "sourceFile=" + sourceFile + ", " +
                "sourceText=" + sourceText + ", " +
                "currentHeader=" + currentHeader + ']';
    }

}