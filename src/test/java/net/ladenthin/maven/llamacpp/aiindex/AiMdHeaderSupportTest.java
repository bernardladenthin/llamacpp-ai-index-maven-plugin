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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class AiMdHeaderSupportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final AiMdHeaderSupport headerSupport = new AiMdHeaderSupport();
    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    /** Fixed title used across all shouldWrite tests to reduce duplication. */
    private static final String FIXED_TITLE = "Test.java";

    /** Fixed creation timestamp used across all shouldWrite tests. */
    private static final String FIXED_D = "2026-03-16T00:00:00Z";

    /** Fixed generation timestamp used across all shouldWrite tests. */
    private static final String FIXED_T = "2026-03-16T00:00:10Z";

    /** Fixed generator version used in tests that do not exercise the generator-version change. */
    private static final String FIXED_G = "1.0.0";

    /** Fixed AI version used across all shouldWrite tests. */
    private static final String FIXED_A = "0.0.0";

    /** Fixed checksum used in tests that do not exercise checksum-change detection. */
    private static final String FIXED_CHECKSUM = "12345678";

    // <editor-fold defaultstate="collapsed" desc="shouldWrite">
    @Test
    public void shouldWrite_fileDoesNotExist_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.getRoot().toPath().resolve("test.ai.md");
        final AiMdHeader header = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        // act
        final boolean result = headerSupport.shouldWrite(false, target, header);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_matchingExistingHeader_returnsFalse() throws IOException {
        // arrange
        final Path target = folder.getRoot().toPath().resolve("test.ai.md");
        final AiMdHeader header = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, "ABCDEF12",
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );
        Files.writeString(target, headerCodec.write(header));

        // act
        final boolean result = headerSupport.shouldWrite(false, target, header);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void shouldWrite_checksumChanged_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.getRoot().toPath().resolve("test.ai.md");
        final AiMdHeader original = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, "AAAAAAAA",
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );
        Files.writeString(target, headerCodec.write(original));

        final AiMdHeader changed = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, "BBBBBBBB",
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        // act
        final boolean result = headerSupport.shouldWrite(false, target, changed);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_generatorVersionChanged_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.getRoot().toPath().resolve("test.ai.md");
        final AiMdHeader original = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );
        Files.writeString(target, headerCodec.write(original));

        final AiMdHeader changed = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                FIXED_D, FIXED_T, "2.0.0", FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        // act
        final boolean result = headerSupport.shouldWrite(false, target, changed);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_forceEnabled_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.getRoot().toPath().resolve("test.ai.md");
        final AiMdHeader header = new AiMdHeader(
                FIXED_TITLE, AiMdHeaderCodec.HEADER_VERSION_1_0, FIXED_CHECKSUM,
                FIXED_D, FIXED_T, FIXED_G, FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY, AiMdHeaderCodec.DEFAULT_KEYWORDS
        );
        Files.writeString(target, headerCodec.write(header));

        // act
        final boolean result = headerSupport.shouldWrite(true, target, header);

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>
}
