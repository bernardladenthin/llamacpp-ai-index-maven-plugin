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

        return List.of(bodyPrompt);
    }

    /**
     * Creates the standard file-level field generation configs.
     *
     * <p>Used in file summarizer and source file indexer tests.</p>
     *
     * @return list with one field generation config for the body
     */
    public static List<AiFieldGenerationConfig> createFileFieldGenerations() {
        return List.of(
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

        return List.of(bodyPrompt);
    }

    /**
     * Creates the standard package-level field generation configs.
     *
     * <p>Used in package indexer and summarizer tests.</p>
     *
     * @return list with one field generation config for the body
     */
    public static List<AiFieldGenerationConfig> createPackageFieldGenerations() {
        return List.of(
                createFieldConfig(PROMPT_KEY_PACKAGE_BODY)
        );
    }

    /**
     * Creates a single {@link AiFieldGenerationConfig} with the given prompt key.
     * The {@link AiGenerationConfig} uses its default values ({@link AiGenerationConfig#DEFAULT_MAX_INPUT_CHARS}
     * characters max input and {@link AiGenerationConfig#DEFAULT_WARN_ON_TRIM} for trim warnings).
     *
     * @param promptKey key that identifies the prompt template in the prompt support
     * @return a fully configured {@link AiFieldGenerationConfig}
     */
    private static AiFieldGenerationConfig createFieldConfig(final String promptKey) {
        final AiFieldGenerationConfig field = new AiFieldGenerationConfig();
        field.setPromptKey(promptKey);
        return field;
    }

    private CommonTestFixtures() {
        // utility class — not instantiable
    }
}
