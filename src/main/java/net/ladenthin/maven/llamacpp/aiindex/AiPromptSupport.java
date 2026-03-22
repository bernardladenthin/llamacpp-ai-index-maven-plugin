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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiPromptSupport {

    private final Map<String, String> templates = new HashMap<>();
    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    public AiPromptSupport(final List<AiPromptDefinition> promptDefinitions) {
        if (promptDefinitions != null) {
            for (AiPromptDefinition definition : promptDefinitions) {
                if (definition.getKey() != null && definition.getTemplate() != null) {
                    templates.put(definition.getKey(), definition.getTemplate());
                }
            }
        }
    }

    public String buildPrompt(final AiGenerationRequest request) {
        return buildPrompt(request.promptKey(), request.sourceFile(), request.sourceText());
    }

    /**
     * Builds the prompt string for the given key, file, and source text without
     * requiring a full {@link AiGenerationRequest}. Useful when only template
     * length measurement is needed and no {@link AiMdHeader} is available.
     *
     * @param promptKey  the key identifying the prompt template
     * @param sourceFile the file path substituted as the filename argument
     * @param sourceText the source text substituted into the template
     * @return the rendered prompt string
     * @throws IllegalArgumentException if no template is registered for {@code promptKey}
     */
    public String buildPrompt(final String promptKey, final java.nio.file.Path sourceFile, final String sourceText) {
        final String template = templates.get(promptKey);
        if (template == null || compatibilityHelper.isBlank(template)) {
            throw new IllegalArgumentException("Missing prompt template for key: " + promptKey);
        }

        return compatibilityHelper.formatted(template,
                sourceFile.getFileName(),
                sourceText
        );
    }
}