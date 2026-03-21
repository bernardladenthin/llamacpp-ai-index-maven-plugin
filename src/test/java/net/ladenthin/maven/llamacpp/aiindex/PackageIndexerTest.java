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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class PackageIndexerTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    // <editor-fold defaultstate="collapsed" desc="aggregate">
    @Test
    public void aggregate_singleChildFile_createsPackageAiMdFile() throws Exception {
        // arrange
        final Path temp = Files.createTempDirectory("ai-index-test");
        final Path baseDirectory = temp;
        final Path outputRoot = temp.resolve("ai");
        final Path packageDirectory = outputRoot.resolve("main/java/com/example");
        final Path childAiFile = packageDirectory.resolve("Test.java.ai.md");
        final Path packageAiFile = packageDirectory.resolve("package.ai.md");

        Files.createDirectories(packageDirectory);

        final AiMdHeader childHeader = new AiMdHeader(
                "Test.java", AiMdHeaderCodec.HEADER_VERSION_1_0, "AAAAAAAA",
                "2026-03-16T00:00:00Z", "2026-03-16T00:00:10Z", "1.0.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        documentCodec.write(childAiFile, new AiMdDocument(childHeader, ""));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createPackagePromptDefinitions());
        final PackageIndexer indexer = new PackageIndexer(
                new SystemStreamLog(), baseDirectory, outputRoot,
                "1.0.0", "0.0.0", Collections.<Path>emptyList(), false, new MockAiGenerationProvider(),
                CommonTestFixtures.createPackageFieldGenerations(), promptSupport,
                CommonTestFixtures.createDefaultAiModelDefinitionSupport()
        );

        // act
        final int aggregated = indexer.aggregate(outputRoot);

        // pre-assert
        assertThat(Files.exists(packageAiFile), is(true));

        // assert
        assertThat(aggregated, is(equalTo(5)));

        final AiMdDocument document = documentCodec.read(packageAiFile);

        // pre-assert
        assertThat(document, is(notNullValue()));

        // assert
        assertThat(document.header().title(), is(equalTo("main/java/com/example")));
        assertThat(document.header().h(), is(equalTo(AiMdHeaderCodec.HEADER_VERSION_1_0)));
        assertThat(document.header().x(), is(equalTo(AiMdHeaderCodec.NODE_TYPE_PACKAGE)));
        assertThat(document.header().g(), is(equalTo("1.0.0")));
        assertThat(document.header().a(), is(equalTo("0.0.0")));
        assertThat(document.header().c().trim().isEmpty(), is(false));
        assertThat(document.header().d().trim().isEmpty(), is(false));
        assertThat(document.header().t().trim().isEmpty(), is(false));
        assertThat(document.body().trim().isEmpty(), is(false));
    }
    // </editor-fold>
}
