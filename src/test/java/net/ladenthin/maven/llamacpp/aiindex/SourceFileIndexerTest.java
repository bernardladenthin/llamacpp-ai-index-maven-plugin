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

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SourceFileIndexerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

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

    private List<AiFieldGenerationConfig> createFieldGenerations() {
        final AiGenerationConfig summaryGeneration = new AiGenerationConfig();
        summaryGeneration.setMaxInputChars(120000);
        summaryGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig summaryField = new AiFieldGenerationConfig();
        summaryField.setFieldName("summary");
        summaryField.setPromptKey("file-summary");
        summaryField.setTarget("header.s");
        summaryField.setGeneration(summaryGeneration);

        final AiGenerationConfig keywordsGeneration = new AiGenerationConfig();
        keywordsGeneration.setMaxInputChars(120000);
        keywordsGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig keywordsField = new AiFieldGenerationConfig();
        keywordsField.setFieldName("keywords");
        keywordsField.setPromptKey("file-keywords");
        keywordsField.setTarget("header.k");
        keywordsField.setGeneration(keywordsGeneration);

        return List.of(summaryField, keywordsField);
    }

    @Test
    public void shouldCreateAiFileDirectlyWithGeneratedHeaderValues() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path sourceRoot = temp.resolve("src/main/java");
        final Path sourceFile = sourceRoot.resolve("com/example/Test.java");
        final Path aiFile = outputRoot.resolve("main/java/com/example/Test.java.ai.md");

        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package com.example;

                public class Test {
                    public String hello(final String name) {
                        return "Hello " + name;
                    }
                }
                """, StandardCharsets.UTF_8);

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final SourceFileIndexer indexer = new SourceFileIndexer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                List.of(".java"),
                "1.0.0",
                "0.0.0",
                List.of(),
                false,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int indexed = indexer.indexSourceRoot(sourceRoot);

        Assert.assertEquals(1, indexed);
        Assert.assertTrue(Files.exists(aiFile));

        final AiMdDocument document = documentCodec.read(aiFile);

        Assert.assertEquals("Test.java", document.header().title());
        Assert.assertEquals(AiMdHeaderCodec.HEADER_VERSION_1_0, document.header().h());
        Assert.assertEquals(AiMdHeaderCodec.NODE_TYPE_FILE, document.header().x());
        Assert.assertEquals("1.0.0", document.header().g());
        Assert.assertEquals("0.0.0", document.header().a());
        Assert.assertEquals("Mock summary for Test.java", document.header().s());
        Assert.assertEquals("mock,keywords,Test.java", document.header().k());
        Assert.assertFalse(document.header().c().isBlank());
        Assert.assertFalse(document.header().d().isBlank());
        Assert.assertFalse(document.header().t().isBlank());
    }
}