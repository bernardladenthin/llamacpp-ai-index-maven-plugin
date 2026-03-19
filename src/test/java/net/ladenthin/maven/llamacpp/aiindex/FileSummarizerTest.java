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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class FileSummarizerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    /** Checksum written into .ai.md fixture files used in summarizeFiles tests. */
    private static final String FIXED_CHECKSUM = "AAAAAAAA";

    /** Source content of the test Java file used across multiple summarizeFiles tests. */
    private static final String FIXED_SOURCE_CONTENT = """
            package com.example;

            public class Test {
            }
            """;

    // <editor-fold defaultstate="collapsed" desc="summarizeFiles">
    @Test
    public void summarizeFiles_emptySummaryAndForceIsFalse_writesSummary() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path sourceFile = temp.resolve("src/main/java/com/example/Test.java");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/Test.java.ai.md");

        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(aiFile.getParent());
        Files.writeString(sourceFile, FIXED_SOURCE_CONTENT, StandardCharsets.UTF_8);

        final AiMdHeader header = new AiMdHeader(
                "Test.java", AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                "2026-03-16T00:00:00Z", "2026-03-16T00:00:10Z", "1.0.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE, "", ""
        );
        documentCodec.write(aiFile, new AiMdDocument(header, ""));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final FileSummarizer summarizer = new FileSummarizer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                List.of(), false, new MockAiGenerationProvider(),
                CommonTestFixtures.createFileFieldGenerations(), promptSupport
        );

        // pre-assert — verify the initial document has no summary yet
        assertThat(documentCodec.read(aiFile).header().s(), is(equalTo("")));

        // act
        final int summarized = summarizer.summarizeFiles();

        // assert
        assertThat(summarized, is(equalTo(1)));

        final AiMdDocument updated = documentCodec.read(aiFile);

        // pre-assert
        assertThat(updated, is(notNullValue()));

        // assert
        assertThat(updated.header().s(), is(equalTo("Mock summary for Test.java")));
        assertThat(updated.header().k(), is(equalTo("mock,keywords,Test.java")));
        assertThat(updated.header().title(), is(equalTo("Test.java")));
        assertThat(updated.header().c(), is(equalTo(FIXED_CHECKSUM)));
        assertThat(updated.header().d(), is(equalTo("2026-03-16T00:00:00Z")));
        assertThat(updated.header().t(), is(equalTo("2026-03-16T00:00:10Z")));
        assertThat(updated.header().g(), is(equalTo("1.0.0")));
        assertThat(updated.header().a(), is(equalTo("0.0.0")));
        assertThat(updated.header().x(), is(equalTo(AiMdHeaderCodec.NODE_TYPE_FILE)));
    }

    @Test
    public void summarizeFiles_existingSummaryAndForceIsFalse_skipsFile() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path sourceFile = temp.resolve("src/main/java/com/example/Test.java");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/Test.java.ai.md");

        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(aiFile.getParent());
        Files.writeString(sourceFile, FIXED_SOURCE_CONTENT, StandardCharsets.UTF_8);

        final AiMdHeader header = new AiMdHeader(
                "Test.java", AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                "2026-03-16T00:00:00Z", "2026-03-16T00:00:10Z", "1.0.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE, "Existing summary", "existing,keywords"
        );
        documentCodec.write(aiFile, new AiMdDocument(header, ""));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final FileSummarizer summarizer = new FileSummarizer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                List.of(), false, new MockAiGenerationProvider(),
                CommonTestFixtures.createFileFieldGenerations(), promptSupport
        );

        // act
        final int summarized = summarizer.summarizeFiles();

        // assert
        assertThat(summarized, is(equalTo(0)));

        final AiMdDocument updated = documentCodec.read(aiFile);
        assertThat(updated.header().s(), is(equalTo("Existing summary")));
        assertThat(updated.header().k(), is(equalTo("existing,keywords")));
    }

    @Test
    public void summarizeFiles_existingSummaryAndForceIsTrue_overwritesSummary() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path sourceFile = temp.resolve("src/main/java/com/example/Test.java");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/Test.java.ai.md");

        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(aiFile.getParent());
        Files.writeString(sourceFile, FIXED_SOURCE_CONTENT, StandardCharsets.UTF_8);

        final AiMdHeader header = new AiMdHeader(
                "Test.java", AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                "2026-03-16T00:00:00Z", "2026-03-16T00:00:10Z", "1.0.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE, "Existing summary", "existing,keywords"
        );
        documentCodec.write(aiFile, new AiMdDocument(header, ""));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final FileSummarizer summarizer = new FileSummarizer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                List.of(), true, new MockAiGenerationProvider(),
                CommonTestFixtures.createFileFieldGenerations(), promptSupport
        );

        // act
        final int summarized = summarizer.summarizeFiles();

        // assert
        assertThat(summarized, is(equalTo(1)));

        final AiMdDocument updated = documentCodec.read(aiFile);
        assertThat(updated.header().s(), is(equalTo("Mock summary for Test.java")));
        assertThat(updated.header().k(), is(equalTo("mock,keywords,Test.java")));
    }

    @Test
    public void summarizeFiles_packageAiFile_skipsPackageFile() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("src/site/ai");
        final Path aiFile = temp.resolve("src/site/ai/main/java/com/example/package.ai.md");

        Files.createDirectories(aiFile.getParent());

        final AiMdHeader header = new AiMdHeader(
                "main/java/com/example", AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                "2026-03-16T00:00:00Z", "2026-03-16T00:00:10Z", "1.0.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE, "", ""
        );
        documentCodec.write(aiFile, new AiMdDocument(header, "#### Contents\n- Test.java.ai.md\n"));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final FileSummarizer summarizer = new FileSummarizer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                List.of(), false, new MockAiGenerationProvider(),
                CommonTestFixtures.createFileFieldGenerations(), promptSupport
        );

        // act
        final int summarized = summarizer.summarizeFiles();

        // assert
        assertThat(summarized, is(equalTo(0)));

        final AiMdDocument updated = documentCodec.read(aiFile);
        assertThat(updated.header().s(), is(equalTo("")));
        assertThat(updated.header().k(), is(equalTo("")));
    }
    // </editor-fold>
}
