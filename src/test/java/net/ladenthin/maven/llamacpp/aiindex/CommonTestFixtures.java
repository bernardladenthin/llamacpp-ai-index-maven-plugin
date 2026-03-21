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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Shared test fixture factory methods used across multiple test classes.
 *
 * <p>Methods in this class eliminate duplication of prompt definitions and field
 * generation configs that appear identically in multiple test classes.</p>
 */
public class CommonTestFixtures {

    /**
     * Prompt key for the file-level body prompt, used in file summarizer and source file indexer tests.
     */
    public static final String PROMPT_KEY_FILE_BODY = "file-body";

    /**
     * AI definition key used for the default model definition in tests.
     * Matches the prompt key convention so that a single definition suffices for most tests.
     */
    public static final String AI_DEFINITION_KEY_DEFAULT = "default";

    /**
     * Prompt key for the package-level body prompt, used in package indexer and summarizer tests.
     */
    public static final String PROMPT_KEY_PACKAGE_BODY = "package-body";

    /**
     * Creates the standard file-level prompt definitions used in file summarizer,
     * source file indexer, and llama JNI provider tests.
     *
     * <p>Provides a single {@code file-body} prompt template that produces the complete
     * body text (summary and any keywords naturally embedded in the text).</p>
     *
     * @return list with one prompt definition for file-level summarization
     */
    public static List<AiPromptDefinition> createFilePromptDefinitions() {
        final AiPromptDefinition bodyPrompt = new AiPromptDefinition();
        bodyPrompt.setKey(PROMPT_KEY_FILE_BODY);
        bodyPrompt.setTemplate("Summarize this Java file and include relevant keywords in your response.\n" +
                               "\n" +
                               "File: %s\n" +
                               "\n" +
                               "Source:\n" +
                               "%s\n");

        return Arrays.asList(bodyPrompt);
    }

    /**
     * Creates the standard file-level field generation configs.
     *
     * <p>Used in file summarizer and source file indexer tests.</p>
     *
     * @return list with one field generation config for the body
     */
    public static List<AiFieldGenerationConfig> createFileFieldGenerations() {
        return Arrays.asList(
                createFieldConfig(PROMPT_KEY_FILE_BODY)
        );
    }

    /**
     * Creates the standard package-level prompt definitions used in package indexer and summarizer tests.
     *
     * <p>Provides a single {@code package-body} prompt template that produces the complete
     * body text (summary and any keywords naturally embedded in the text).</p>
     *
     * @return list with one prompt definition for package-level summarization
     */
    public static List<AiPromptDefinition> createPackagePromptDefinitions() {
        final AiPromptDefinition bodyPrompt = new AiPromptDefinition();
        bodyPrompt.setKey(PROMPT_KEY_PACKAGE_BODY);
        bodyPrompt.setTemplate("Summarize this Java package and include relevant keywords in your response.\n" +
                               "\n" +
                               "File: %s\n" +
                               "\n" +
                               "Source:\n" +
                               "%s\n");

        return Arrays.asList(bodyPrompt);
    }

    /**
     * Creates the standard package-level field generation configs.
     *
     * <p>Used in package indexer and summarizer tests.</p>
     *
     * @return list with one field generation config for the body
     */
    public static List<AiFieldGenerationConfig> createPackageFieldGenerations() {
        return Arrays.asList(
                createFieldConfig(PROMPT_KEY_PACKAGE_BODY)
        );
    }

    /**
     * Creates the standard default {@link AiModelDefinition} list for tests.
     *
     * <p>Provides a single definition keyed {@link #AI_DEFINITION_KEY_DEFAULT} that uses
     * all {@link AiGenerationConfig} default values. Tests that need custom values
     * (e.g. different {@code maxRetries}) should build their own list.</p>
     *
     * @return list with one default model definition
     */
    public static List<AiModelDefinition> createDefaultAiModelDefinitions() {
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(AI_DEFINITION_KEY_DEFAULT);
        return Arrays.asList(definition);
    }

    /**
     * Creates an {@link AiModelDefinitionSupport} backed by the default definition list.
     *
     * @return model definition support using the default test definitions
     * @see #createDefaultAiModelDefinitions()
     */
    public static AiModelDefinitionSupport createDefaultAiModelDefinitionSupport() {
        return new AiModelDefinitionSupport(createDefaultAiModelDefinitions());
    }

    /**
     * Creates a single {@link AiFieldGenerationConfig} with the given prompt key and the
     * default AI definition key {@link #AI_DEFINITION_KEY_DEFAULT}.
     *
     * @param promptKey key that identifies the prompt template in the prompt support
     * @return a fully configured {@link AiFieldGenerationConfig}
     */
    private static AiFieldGenerationConfig createFieldConfig(final String promptKey) {
        final AiFieldGenerationConfig field = new AiFieldGenerationConfig();
        field.setPromptKey(promptKey);
        field.setAiDefinitionKey(AI_DEFINITION_KEY_DEFAULT);
        return field;
    }

    private CommonTestFixtures() {
        // utility class — not instantiable
    }
}
