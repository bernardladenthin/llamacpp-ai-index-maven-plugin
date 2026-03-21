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
        final String template = templates.get(request.promptKey());
        if (template == null || template.isEmpty() || template.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing prompt template for key: " + request.promptKey());
        }

        return String.format(template,
                request.sourceFile().getFileName(),
                request.sourceText()
        );
    }
}