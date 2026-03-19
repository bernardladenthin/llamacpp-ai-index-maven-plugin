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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LlamaCppJniAiSummaryProviderTest {

    private static final String MODEL_PATH =
            Path.of("src", "test", "resources", "SmolLM2-135M-Instruct-Q3_K_M.gguf")
                    .toAbsolutePath()
                    .toString();

    private List<AiPromptDefinition> createPromptDefinitions() {
        final AiPromptDefinition summaryPrompt = new AiPromptDefinition();
        summaryPrompt.setKey("file-summary");
        summaryPrompt.setTemplate("""
                Summarize this Java file.

                File: %s

                Source:
                %s
                """);

        final AiPromptDefinition keywordsPrompt = new AiPromptDefinition();
        keywordsPrompt.setKey("file-keywords");
        keywordsPrompt.setTemplate("""
                Generate comma-separated keywords for this Java file.

                File: %s

                Source:
                %s
                """);

        return List.of(summaryPrompt, keywordsPrompt);
    }

    @Test
    public void shouldGenerateSummaryAndKeywordsForSimpleJavaFile() throws Exception {
        Assume.assumeTrue("Native llama test disabled. Enable with -DrunNativeLlamaTests=true",
                Boolean.getBoolean("runNativeLlamaTests"));
        Assume.assumeTrue("Model file missing: " + MODEL_PATH,
                Files.exists(Path.of(MODEL_PATH)));

        final LlamaCppJniConfig config = new LlamaCppJniConfig(
                null,
                MODEL_PATH,
                32768,
                128,
                0.15f,
                8
        );

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        try (LlamaCppJniAiSummaryProvider provider = new LlamaCppJniAiSummaryProvider(config, promptSupport)) {
            final AiMdHeader header = new AiMdHeader(
                    "Test.java",
                    AiMdHeaderCodec.HEADER_VERSION_1_0,
                    "00000000",
                    "2026-03-18T00:00:00Z",
                    "2026-03-18T00:00:00Z",
                    "0.1.0-SNAPSHOT",
                    "0.0.0",
                    AiMdHeaderCodec.NODE_TYPE_FILE,
                    "",
                    ""
            );

            final String source = """
                    package com.example;

                    public class Test {

                        public String hello(final String name) {
                            return "Hello " + name;
                        }
                    }
                    """;

            final AiGenerationRequest summaryRequest = new AiGenerationRequest(
                    "file-summary",
                    Path.of("Test.java"),
                    source,
                    header
            );

            final AiGenerationRequest keywordsRequest = new AiGenerationRequest(
                    "file-keywords",
                    Path.of("Test.java"),
                    source,
                    header
            );

            final String summary = provider.generate(summaryRequest);
            final String keywords = provider.generate(keywordsRequest);

            Assert.assertNotNull(summary);
            Assert.assertNotNull(keywords);
            Assert.assertFalse(summary.isBlank());
            Assert.assertFalse(keywords.isBlank());

            System.out.println("S: " + summary);
            System.out.println("K: " + keywords);
        }
    }
}