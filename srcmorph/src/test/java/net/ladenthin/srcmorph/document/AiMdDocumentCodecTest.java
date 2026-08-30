// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.document;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

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
                AiMdHeaderCodec.NODE_TYPE_FILE);
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
                AiMdHeaderCodec.NODE_TYPE_FILE);
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
                AiMdHeaderCodec.NODE_TYPE_FILE);
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
                AiMdHeaderCodec.NODE_TYPE_FILE);
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
                "This is the actual body content.");

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
                "More content");

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
                AiMdHeaderCodec.NODE_TYPE_PACKAGE);
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
                "- X: file");

        // act
        final AiMdDocument document = documentCodec.read(lines);

        // assert
        assertThat(document.body().trim().isEmpty(), is(true));
    }

    @Test
    public void read_bodyLineLookingLikeChildLink_isNotParsedAsChild() {
        // arrange: a "- F:" line in the BODY must not be mistaken for a header child link
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
                "- F: this looks like a child link but is body text");

        // act
        final AiMdDocument document = documentCodec.read(lines);

        // assert: the header has no children, and the line stayed in the body
        assertThat(document.header().children().isEmpty(), is(true));
        assertThat(document.body(), containsString("- F: this looks like a child link but is body text"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="header/body boundary and trailing newline">

    /**
     * A body that starts immediately after the header block, with no blank separator line, must keep
     * its first line.
     *
     * <p>This pins the blank-line check in {@code read}: negating it makes the parser treat the first
     * body line as the header terminator and drop it. That is silent data loss -- the document still
     * reads, just one line shorter -- so nothing else in the suite would catch it.
     */
    @Test
    public void read_bodyStartsWithoutBlankSeparatorLine_keepsFirstBodyLine() {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "- H: 1.0", "First body line.", "Second body line.");

        // act
        final AiMdDocument document = new AiMdDocumentCodec().read(lines);

        // assert
        assertThat(document.body(), containsString("First body line."));
        assertThat(document.body(), containsString("Second body line."));
    }

    /**
     * A blank line between header and body is consumed as the separator, not kept as body content.
     * The counterpart to {@link #read_bodyStartsWithoutBlankSeparatorLine_keepsFirstBodyLine}: without
     * it, a mutant that always treats a line as the terminator would survive.
     */
    @Test
    public void read_blankSeparatorLine_isNotPartOfTheBody() {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "- H: 1.0", "", "First body line.");

        // act
        final AiMdDocument document = new AiMdDocumentCodec().read(lines);

        // assert
        assertThat(document.body(), is(equalTo("First body line.\n")));
    }

    /** A body without a trailing newline gets exactly one appended, so files end cleanly. */
    @Test
    public void write_bodyWithoutTrailingNewline_appendsExactlyOne() {
        // arrange
        final AiMdDocument document = new AiMdDocument(headerFixture(), "Body without newline");

        // act
        final String rendered = new AiMdDocumentCodec().write(document);

        // assert
        assertThat(rendered.endsWith("Body without newline\n"), is(true));
        assertThat(rendered.endsWith("Body without newline\n\n"), is(false));
    }

    /** A body that already ends with a newline must not gain a second one. */
    @Test
    public void write_bodyAlreadyEndingWithNewline_doesNotAddASecond() {
        // arrange
        final AiMdDocument document = new AiMdDocument(headerFixture(), "Body with newline\n");

        // act
        final String rendered = new AiMdDocumentCodec().write(document);

        // assert
        assertThat(rendered.endsWith("Body with newline\n"), is(true));
        assertThat(rendered.endsWith("Body with newline\n\n"), is(false));
    }

    /**
     * Minimal valid header for the {@code write} round-trip tests above.
     *
     * @return a header whose exact field values are irrelevant to what those tests assert
     */
    private static AiMdHeader headerFixture() {
        return new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "12345678",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
    }

    // </editor-fold>
}
