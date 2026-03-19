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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PackageIndexerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    private List<AiPromptDefinition> createPromptDefinitions() {
        final AiPromptDefinition summaryPrompt = new AiPromptDefinition();
        summaryPrompt.setKey("package-summary");
        summaryPrompt.setTemplate("""
                Summarize this Java package.

                File: %s

                Source:
                %s
                """);

        final AiPromptDefinition keywordsPrompt = new AiPromptDefinition();
        keywordsPrompt.setKey("package-keywords");
        keywordsPrompt.setTemplate("""
                Generate comma-separated keywords for this Java package.

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
        summaryField.setPromptKey("package-summary");
        summaryField.setTarget("header.s");
        summaryField.setGeneration(summaryGeneration);

        final AiGenerationConfig keywordsGeneration = new AiGenerationConfig();
        keywordsGeneration.setMaxInputChars(120000);
        keywordsGeneration.setWarnOnTrim(true);

        final AiFieldGenerationConfig keywordsField = new AiFieldGenerationConfig();
        keywordsField.setFieldName("keywords");
        keywordsField.setPromptKey("package-keywords");
        keywordsField.setTarget("header.k");
        keywordsField.setGeneration(keywordsGeneration);

        return List.of(summaryField, keywordsField);
    }

    @Test
    public void shouldCreatePackageAiFileDirectlyWithGeneratedHeaderValues() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("ai");
        final Path packageDirectory = outputRoot.resolve("main/java/com/example");
        final Path childAiFile = packageDirectory.resolve("Test.java.ai.md");
        final Path packageAiFile = packageDirectory.resolve("package.ai.md");

        Files.createDirectories(packageDirectory);

        final AiMdHeader childHeader = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "Child summary",
                "child,keywords"
        );

        documentCodec.write(childAiFile, new AiMdDocument(childHeader, ""));

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final PackageIndexer indexer = new PackageIndexer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                "1.0.0",
                "0.0.0",
                List.of(),
                false,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int aggregated = indexer.aggregate(outputRoot);

        Assert.assertEquals(5, aggregated);
        Assert.assertTrue(Files.exists(packageAiFile));

        final AiMdDocument document = documentCodec.read(packageAiFile);

        Assert.assertEquals("main/java/com/example", document.header().title());
        Assert.assertEquals(AiMdHeaderCodec.HEADER_VERSION_1_0, document.header().h());
        Assert.assertEquals(AiMdHeaderCodec.NODE_TYPE_PACKAGE, document.header().x());
        Assert.assertEquals("1.0.0", document.header().g());
        Assert.assertEquals("0.0.0", document.header().a());
        Assert.assertEquals("Mock summary for package.ai.md", document.header().s());
        Assert.assertEquals("mock,keywords,package.ai.md", document.header().k());
        Assert.assertFalse(document.header().c().isBlank());
        Assert.assertFalse(document.header().d().isBlank());
        Assert.assertFalse(document.header().t().isBlank());
        Assert.assertTrue(document.body().contains("#### Contents"));
        Assert.assertTrue(document.body().contains("- Test.java.ai.md"));
    }
}