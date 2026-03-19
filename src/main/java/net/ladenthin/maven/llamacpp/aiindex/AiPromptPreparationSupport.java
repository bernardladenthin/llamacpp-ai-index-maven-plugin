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

public class AiPromptPreparationSupport {

    private final AiPromptSupport promptSupport;

    public AiPromptPreparationSupport(final AiPromptSupport promptSupport) {
        this.promptSupport = promptSupport;
    }

    public AiPreparedPrompt preparePrompt(
            final AiGenerationRequest request,
            final int maxInputChars
    ) {
        final String fullPrompt = buildPrompt(request);
        final int originalSourceLength = request.sourceText().length();

        if (fullPrompt.length() <= maxInputChars) {
            return new AiPreparedPrompt(
                    fullPrompt,
                    request.sourceText(),
                    false,
                    originalSourceLength,
                    originalSourceLength,
                    originalSourceLength
            );
        }

        final AiGenerationRequest emptySourceRequest = new AiGenerationRequest(
                request.promptKey(),
                request.sourceFile(),
                "",
                request.currentHeader()
        );

        final String promptWithoutSource = buildPrompt(emptySourceRequest);
        final int availableSourceChars = Math.max(0, maxInputChars - promptWithoutSource.length());

        final String trimmedSource = request.sourceText().substring(
                0,
                Math.min(request.sourceText().length(), availableSourceChars)
        );

        final AiGenerationRequest trimmedRequest = new AiGenerationRequest(
                request.promptKey(),
                request.sourceFile(),
                trimmedSource,
                request.currentHeader()
        );

        final String trimmedPrompt = buildPrompt(trimmedRequest);

        return new AiPreparedPrompt(
                trimmedPrompt,
                trimmedSource,
                true,
                originalSourceLength,
                trimmedSource.length(),
                availableSourceChars
        );
    }

    private String buildPrompt(final AiGenerationRequest request) {
        return promptSupport.buildPrompt(request);
    }
}