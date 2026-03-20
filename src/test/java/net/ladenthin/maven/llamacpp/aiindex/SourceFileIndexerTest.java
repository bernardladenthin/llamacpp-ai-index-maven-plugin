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

public class SourceFileIndexerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    // <editor-fold defaultstate="collapsed" desc="indexSourceRoot">
    @Test
    public void indexSourceRoot_singleJavaFile_createsAiMdFile() throws Exception {
        // arrange
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

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final SourceFileIndexer indexer = new SourceFileIndexer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                List.of(".java"), "1.0.0", "0.0.0", List.of(), false,
                new MockAiGenerationProvider(),
                CommonTestFixtures.createFileFieldGenerations(), promptSupport
        );

        // act
        final int indexed = indexer.indexSourceRoot(sourceRoot);

        // pre-assert
        assertThat(Files.exists(aiFile), is(true));

        // assert
        assertThat(indexed, is(equalTo(1)));

        final AiMdDocument document = documentCodec.read(aiFile);

        // pre-assert
        assertThat(document, is(notNullValue()));

        // assert
        assertThat(document.header().title(), is(equalTo("Test.java")));
        assertThat(document.header().h(), is(equalTo(AiMdHeaderCodec.HEADER_VERSION_1_0)));
        assertThat(document.header().x(), is(equalTo(AiMdHeaderCodec.NODE_TYPE_FILE)));
        assertThat(document.header().g(), is(equalTo("1.0.0")));
        assertThat(document.header().a(), is(equalTo("0.0.0")));
        assertThat(document.header().c().isBlank(), is(false));
        assertThat(document.header().d().isBlank(), is(false));
        assertThat(document.header().t().isBlank(), is(false));
        assertThat(document.body().isBlank(), is(false));
    }
    // </editor-fold>
}
