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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

public class AiMdDocumentCodecTest {

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();
    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    // <editor-fold defaultstate="collapsed" desc="separator write">
    @Test
    public void write_documentWithBody_includesSeparator() {
        // arrange
        final AiMdHeader header = new AiMdHeader(
                "Example.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "ABC123",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        final AiMdDocument document = new AiMdDocument(header, "This is the body content.");

        // act
        final String output = documentCodec.write(document);

        // assert
        assertThat(output, containsString(AiMdDocumentCodec.HEADER_BODY_SEPARATOR));
    }

    @Test
    public void write_documentWithBody_separatorPlacedCorrectly() {
        // arrange
        final AiMdHeader header = new AiMdHeader(
                "Example.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "ABC123",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        final AiMdDocument document = new AiMdDocument(header, "Body content");

        // act
        final String output = documentCodec.write(document);
        final String[] lines = output.split("\\R");

        // assert - separator should appear after the header fields
        boolean foundSeparator = false;
        for (int i = 0; i < lines.length; i++) {
            if (AiMdDocumentCodec.HEADER_BODY_SEPARATOR.equals(lines[i])) {
                foundSeparator = true;
                // separator should have body content after it
                assertThat(i < lines.length - 1, is(true));
                break;
            }
        }
        assertThat(foundSeparator, is(true));
    }

    @Test
    public void write_documentWithoutBody_noSeparatorIncluded() {
        // arrange
        final AiMdHeader header = new AiMdHeader(
                "Example.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "ABC123",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        final AiMdDocument document = new AiMdDocument(header, "   ");

        // act
        final String output = documentCodec.write(document);

        // assert
        assertThat(output.contains(AiMdDocumentCodec.HEADER_BODY_SEPARATOR), is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="roundtrip">
    @Test
    public void write_read_documentRoundtripsCorrectly() {
        // arrange
        final AiMdHeader originalHeader = new AiMdHeader(
                "MyClass.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "DEF456",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        final String originalBody = "This is some body content.\nWith multiple lines.";
        final AiMdDocument original = new AiMdDocument(originalHeader, originalBody);

        // act
        final String encoded = documentCodec.write(original);
        final List<String> lines = Arrays.asList(encoded.split("\\R"));
        final AiMdDocument decoded = documentCodec.read(lines);

        // assert
        assertThat(decoded.header(), is(equalTo(original.header())));
        // Note: read adds trailing newline to body
        assertThat(decoded.body(), is(equalTo(original.body() + "\n")));
    }

    @Test
    public void read_documentWithSeparator_separatorNotIncludedInBody() {
        // arrange
        final List<String> lines = Arrays.asList(
                "### Example.java",
                "- H: 1.0",
                "- C: ABC123",
                "- D: 2026-03-15T18:33:40Z",
                "- T: 2026-03-15T18:34:26Z",
                "- G: 0.1.0-SNAPSHOT",
                "- A: 0.0.0",
                "- X: file",
                "",
                "---",
                "This is the actual body content."
        );

        // act
        final AiMdDocument document = documentCodec.read(lines);

        // assert
        assertThat(document.body(), is(equalTo("This is the actual body content.\n")));
        assertThat(document.body().contains("---"), is(false));
    }

    @Test
    public void read_documentWithMultipleLineSeparator_onlyExactSeparatorSkipped() {
        // arrange
        final List<String> lines = Arrays.asList(
                "### Example.java",
                "- H: 1.0",
                "- C: ABC123",
                "- D: 2026-03-15T18:33:40Z",
                "- T: 2026-03-15T18:34:26Z",
                "- G: 0.1.0-SNAPSHOT",
                "- A: 0.0.0",
                "- X: file",
                "",
                "---",
                "Body with --- in the middle",
                "More content"
        );

        // act
        final AiMdDocument document = documentCodec.read(lines);

        // assert
        assertThat(document.body(), is(equalTo("Body with --- in the middle\nMore content\n")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="edge cases">
    @Test
    public void write_read_documentWithBlankLines_preservesContent() {
        // arrange
        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "XYZ789",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE
        );
        final String bodyWithBlankLines = "First paragraph.\n\nSecond paragraph.";
        final AiMdDocument original = new AiMdDocument(header, bodyWithBlankLines);

        // act
        final String encoded = documentCodec.write(original);
        final AiMdDocument decoded = documentCodec.read(Arrays.asList(encoded.split("\\R")));

        // assert
        // Note: read adds trailing newline to body
        assertThat(decoded.body(), is(equalTo(original.body() + "\n")));
    }

    @Test
    public void read_documentWithEmptyBody_parsesCorrectly() {
        // arrange
        final List<String> lines = Arrays.asList(
                "### Empty.java",
                "- H: 1.0",
                "- C: ABC123",
                "- D: 2026-03-15T18:33:40Z",
                "- T: 2026-03-15T18:34:26Z",
                "- G: 0.1.0-SNAPSHOT",
                "- A: 0.0.0",
                "- X: file"
        );

        // act
        final AiMdDocument document = documentCodec.read(lines);

        // assert
        assertThat(document.body().trim().isEmpty(), is(true));
    }
    // </editor-fold>
}
