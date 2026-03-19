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

public class PackageSummarizerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    private List<AiPromptDefinition> createPromptDefinitions() {
        final AiPromptDefinition summaryPrompt = new AiPromptDefinition();
        summaryPrompt.setKey("package-summary");
        summaryPrompt.setTemplate("""
                Summarize this Java package in one line.

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
    public void shouldSummarizePackageHeaderWithMockProvider() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/package.ai.md");

        Files.createDirectories(aiFile.getParent());

        final AiMdHeader header = new AiMdHeader(
                "main/java/com/example",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                "",
                ""
        );

        final AiMdDocument document = new AiMdDocument(
                header,
                "#### Contents\n- Test.java.ai.md\n"
        );
        documentCodec.write(aiFile, document);

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final PackageSummarizer summarizer = new PackageSummarizer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                List.of(),
                false,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int summarized = summarizer.summarizePackages();

        Assert.assertEquals(1, summarized);

        final AiMdDocument updated = documentCodec.read(aiFile);

        Assert.assertEquals("Mock summary for package.ai.md", updated.header().s());
        Assert.assertEquals("mock,keywords,package.ai.md", updated.header().k());
        Assert.assertEquals("main/java/com/example", updated.header().title());
        Assert.assertEquals("AAAAAAAA", updated.header().c());
        Assert.assertEquals("2026-03-16T00:00:00Z", updated.header().d());
        Assert.assertEquals("2026-03-16T00:00:10Z", updated.header().t());
        Assert.assertEquals("1.0.0", updated.header().g());
        Assert.assertEquals("0.0.0", updated.header().a());
        Assert.assertEquals(AiMdHeaderCodec.NODE_TYPE_PACKAGE, updated.header().x());
        Assert.assertEquals("#### Contents\n- Test.java.ai.md\n", updated.body());
    }

    @Test
    public void shouldNotSummarizePackageIfSummaryAlreadyExistsAndForceIsFalse() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/package.ai.md");

        Files.createDirectories(aiFile.getParent());

        final AiMdHeader header = new AiMdHeader(
                "main/java/com/example",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                "Existing package summary",
                "existing,package"
        );

        final AiMdDocument document = new AiMdDocument(
                header,
                "#### Contents\n- Test.java.ai.md\n"
        );
        documentCodec.write(aiFile, document);

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final PackageSummarizer summarizer = new PackageSummarizer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                List.of(),
                false,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int summarized = summarizer.summarizePackages();

        Assert.assertEquals(0, summarized);

        final AiMdDocument updated = documentCodec.read(aiFile);
        Assert.assertEquals("Existing package summary", updated.header().s());
        Assert.assertEquals("existing,package", updated.header().k());
        Assert.assertEquals("#### Contents\n- Test.java.ai.md\n", updated.body());
    }

    @Test
    public void shouldSummarizePackageIfForceIsTrue() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/package.ai.md");

        Files.createDirectories(aiFile.getParent());

        final AiMdHeader header = new AiMdHeader(
                "main/java/com/example",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                "Existing package summary",
                "existing,package"
        );

        final AiMdDocument document = new AiMdDocument(
                header,
                "#### Contents\n- Test.java.ai.md\n"
        );
        documentCodec.write(aiFile, document);

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final PackageSummarizer summarizer = new PackageSummarizer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                List.of(),
                true,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int summarized = summarizer.summarizePackages();

        Assert.assertEquals(1, summarized);

        final AiMdDocument updated = documentCodec.read(aiFile);
        Assert.assertEquals("Mock summary for package.ai.md", updated.header().s());
        Assert.assertEquals("mock,keywords,package.ai.md", updated.header().k());
        Assert.assertEquals("#### Contents\n- Test.java.ai.md\n", updated.body());
    }

    @Test
    public void shouldSkipNonPackageAiFiles() throws Exception {
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/Test.java.ai.md");

        Files.createDirectories(aiFile.getParent());

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "",
                ""
        );

        final AiMdDocument document = new AiMdDocument(header, "");
        documentCodec.write(aiFile, document);

        final AiPromptSupport promptSupport = new AiPromptSupport(createPromptDefinitions());

        final PackageSummarizer summarizer = new PackageSummarizer(
                new SystemStreamLog(),
                baseDirectory,
                outputRoot,
                List.of(),
                false,
                new MockAiGenerationProvider(),
                createFieldGenerations(),
                promptSupport
        );

        final int summarized = summarizer.summarizePackages();

        Assert.assertEquals(0, summarized);

        final AiMdDocument updated = documentCodec.read(aiFile);
        Assert.assertEquals("", updated.header().s());
        Assert.assertEquals("", updated.header().k());
    }
}