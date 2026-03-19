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
     * Prompt key for the file-level summary prompt, used in file summarizer and source file indexer tests.
     */
    public static final String PROMPT_KEY_FILE_SUMMARY = "file-summary";

    /**
     * Prompt key for the file-level keywords prompt, used in file summarizer and source file indexer tests.
     */
    public static final String PROMPT_KEY_FILE_KEYWORDS = "file-keywords";

    /**
     * Prompt key for the package-level summary prompt, used in package indexer and summarizer tests.
     */
    public static final String PROMPT_KEY_PACKAGE_SUMMARY = "package-summary";

    /**
     * Prompt key for the package-level keywords prompt, used in package indexer and summarizer tests.
     */
    public static final String PROMPT_KEY_PACKAGE_KEYWORDS = "package-keywords";

    /**
     * Creates the standard file-level prompt definitions used in file summarizer,
     * source file indexer, and llama JNI provider tests.
     *
     * <p>Provides {@code file-summary} and {@code file-keywords} prompt templates.</p>
     *
     * @return list of two prompt definitions for file-level summarization
     */
    public static List<AiPromptDefinition> createFilePromptDefinitions() {
        final AiPromptDefinition summaryPrompt = new AiPromptDefinition();
        summaryPrompt.setKey(PROMPT_KEY_FILE_SUMMARY);
        summaryPrompt.setTemplate("""
                Summarize this Java file.

                File: %s

                Source:
                %s
                """);

        final AiPromptDefinition keywordsPrompt = new AiPromptDefinition();
        keywordsPrompt.setKey(PROMPT_KEY_FILE_KEYWORDS);
        keywordsPrompt.setTemplate("""
                Generate comma-separated keywords for this Java file.

                File: %s

                Source:
                %s
                """);

        return List.of(summaryPrompt, keywordsPrompt);
    }

    /**
     * Creates the standard file-level field generation configs targeting {@code header.s} and {@code header.k}.
     *
     * <p>Used in file summarizer and source file indexer tests.</p>
     *
     * @return list of two field generation configs for summary and keywords
     */
    public static List<AiFieldGenerationConfig> createFileFieldGenerations() {
        final AiGenerationConfig summaryGeneration = new AiGenerationConfig();
        summaryGeneration.setMaxInputChars(120000);
        summaryGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig summaryField = new AiFieldGenerationConfig();
        summaryField.setFieldName("summary");
        summaryField.setPromptKey(PROMPT_KEY_FILE_SUMMARY);
        summaryField.setTarget("header.s");
        summaryField.setGeneration(summaryGeneration);

        final AiGenerationConfig keywordsGeneration = new AiGenerationConfig();
        keywordsGeneration.setMaxInputChars(120000);
        keywordsGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig keywordsField = new AiFieldGenerationConfig();
        keywordsField.setFieldName("keywords");
        keywordsField.setPromptKey(PROMPT_KEY_FILE_KEYWORDS);
        keywordsField.setTarget("header.k");
        keywordsField.setGeneration(keywordsGeneration);

        return List.of(summaryField, keywordsField);
    }

    /**
     * Creates the standard package-level prompt definitions used in package indexer and summarizer tests.
     *
     * <p>Provides {@code package-summary} and {@code package-keywords} prompt templates.</p>
     *
     * @return list of two prompt definitions for package-level summarization
     */
    public static List<AiPromptDefinition> createPackagePromptDefinitions() {
        final AiPromptDefinition summaryPrompt = new AiPromptDefinition();
        summaryPrompt.setKey(PROMPT_KEY_PACKAGE_SUMMARY);
        summaryPrompt.setTemplate("""
                Summarize this Java package.

                File: %s

                Source:
                %s
                """);

        final AiPromptDefinition keywordsPrompt = new AiPromptDefinition();
        keywordsPrompt.setKey(PROMPT_KEY_PACKAGE_KEYWORDS);
        keywordsPrompt.setTemplate("""
                Generate comma-separated keywords for this Java package.

                File: %s

                Source:
                %s
                """);

        return List.of(summaryPrompt, keywordsPrompt);
    }

    /**
     * Creates the standard package-level field generation configs targeting {@code header.s} and {@code header.k}.
     *
     * <p>Used in package indexer and summarizer tests.</p>
     *
     * @return list of two field generation configs for summary and keywords
     */
    public static List<AiFieldGenerationConfig> createPackageFieldGenerations() {
        final AiGenerationConfig summaryGeneration = new AiGenerationConfig();
        summaryGeneration.setMaxInputChars(120000);
        summaryGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig summaryField = new AiFieldGenerationConfig();
        summaryField.setFieldName("summary");
        summaryField.setPromptKey(PROMPT_KEY_PACKAGE_SUMMARY);
        summaryField.setTarget("header.s");
        summaryField.setGeneration(summaryGeneration);

        final AiGenerationConfig keywordsGeneration = new AiGenerationConfig();
        keywordsGeneration.setMaxInputChars(120000);
        keywordsGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig keywordsField = new AiFieldGenerationConfig();
        keywordsField.setFieldName("keywords");
        keywordsField.setPromptKey(PROMPT_KEY_PACKAGE_KEYWORDS);
        keywordsField.setTarget("header.k");
        keywordsField.setGeneration(keywordsGeneration);

        return List.of(summaryField, keywordsField);
    }

    private CommonTestFixtures() {
        // utility class — not instantiable
    }
}
