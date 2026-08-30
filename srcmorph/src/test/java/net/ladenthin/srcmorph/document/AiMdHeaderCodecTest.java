// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.document;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AiMdHeaderCodecTest {

    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    // <editor-fold defaultstate="collapsed" desc="write">
    @Test
    public void write_fileNodeHeader_roundtripsToEqualHeader() {
        // arrange
        final AiMdHeader original = new AiMdHeader(
                "GenerateMojo.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "D56BA12A",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);

        // act
        final String encoded = headerCodec.write(original);
        final AiMdHeader decoded = headerCodec.read(Arrays.asList(encoded.split("\\R")));

        // assert
        assertThat(decoded, is(equalTo(original)));
    }

    @Test
    public void write_packageNodeHeader_decodedFieldsMatchOriginal() {
        // arrange
        final AiMdHeader original = new AiMdHeader(
                "main/java/net/ladenthin/maven/llamacpp/aiindex",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "9863444A",
                "2026-03-15T18:33:50Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE);

        // act
        final String encoded = headerCodec.write(original);
        final List<String> lines = Arrays.asList(encoded.split("\\R"));
        final AiMdHeader decoded = headerCodec.read(lines);

        // assert
        assertThat(decoded.title(), is(equalTo("main/java/net/ladenthin/maven/llamacpp/aiindex")));
        assertThat(decoded.h(), is(equalTo(AiMdHeaderCodec.HEADER_VERSION_1_0)));
        assertThat(decoded.c(), is(equalTo("9863444A")));
        assertThat(decoded.d(), is(equalTo("2026-03-15T18:33:50Z")));
        assertThat(decoded.t(), is(equalTo("2026-03-15T18:34:26Z")));
        assertThat(decoded.g(), is(equalTo("0.1.0-SNAPSHOT")));
        assertThat(decoded.a(), is(equalTo("0.0.0")));
        assertThat(decoded.x(), is(equalTo(AiMdHeaderCodec.NODE_TYPE_PACKAGE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="child links (F)">
    @Test
    public void write_headerWithChildren_emitsOneChildLinkLinePerChild() {
        // arrange
        final AiMdHeader header = new AiMdHeader(
                "main/java/com/example",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "9863444A",
                "2026-03-15T18:33:50Z",
                "2026-03-15T18:34:26Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                Arrays.asList("[Foo.java](Foo.java.ai.md)", "[sub/](sub/package.ai.md)"));

        // act
        final String encoded = headerCodec.write(header);

        // assert
        assertThat(encoded, containsString("- F: [Foo.java](Foo.java.ai.md)\n"));
        assertThat(encoded, containsString("- F: [sub/](sub/package.ai.md)\n"));
    }

    @Test
    public void read_childLinkLines_collectedIntoChildrenInOrder() {
        // arrange
        final List<String> lines = Arrays.asList(
                "### main/java/com/example",
                "- H: 1.0",
                "- C: 9863444A",
                "- D: 2026-03-15T18:33:50Z",
                "- T: 2026-03-15T18:34:26Z",
                "- G: 1.0.0",
                "- A: 0.0.0",
                "- X: package",
                "- F: [Foo.java](Foo.java.ai.md)",
                "- F: [sub/](sub/package.ai.md)");

        // act
        final AiMdHeader decoded = headerCodec.read(lines);

        // assert
        assertThat(
                decoded.children(),
                is(equalTo(Arrays.asList("[Foo.java](Foo.java.ai.md)", "[sub/](sub/package.ai.md)"))));
    }

    @Test
    public void write_read_headerWithChildren_roundtripsToEqualHeader() {
        // arrange
        final AiMdHeader original = new AiMdHeader(
                "main/java/com/example",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "9863444A",
                "2026-03-15T18:33:50Z",
                "2026-03-15T18:34:26Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                Arrays.asList("[Foo.java](Foo.java.ai.md)", "[sub/](sub/package.ai.md)"));

        // act
        final String encoded = headerCodec.write(original);
        final AiMdHeader decoded = headerCodec.read(Arrays.asList(encoded.split("\\R")));

        // assert
        assertThat(decoded, is(equalTo(original)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="file overload and malformed field lines">

    @TempDir
    Path tempDir;

    /**
     * The public {@code read(Path)} overload has no test of its own -- every other case goes through
     * the {@code List<String>} form -- so nothing pins that it reads the file at all, let alone with
     * the right charset. It must agree with the line-based form for the same content.
     *
     * @throws IOException if the fixture cannot be written or read
     */
    @Test
    public void read_existingFile_parsesSameAsLineForm() throws IOException {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "- H: 1.0", "- C: 12345678", "- X: file");
        final Path file = tempDir.resolve("test.ai.md");
        Files.write(file, lines, StandardCharsets.UTF_8);

        // act
        final AiMdHeader fromFile = new AiMdHeaderCodec().read(file);
        final AiMdHeader fromLines = new AiMdHeaderCodec().read(lines);

        // assert
        assertThat(fromFile.title(), is(equalTo(fromLines.title())));
        assertThat(fromFile.h(), is(equalTo(fromLines.h())));
        assertThat(fromFile.c(), is(equalTo(fromLines.c())));
        assertThat(fromFile.x(), is(equalTo(fromLines.x())));
    }

    /**
     * A field line whose colon sits at the prefix boundary carries an empty key and must be ignored.
     *
     * <p>Pins the {@code colonIndex < HEADER_FIELD_PREFIX.length() + 1} guard: both the boundary
     * mutant and the {@code +1 -> -1} arithmetic mutant survive without a case at exactly this
     * position, and either would admit an empty-keyed entry into the parsed field map.
     */
    @Test
    public void read_fieldLineWithColonAtPrefixBoundary_isIgnored() {
        // arrange -- "- :value" has its colon at index 2, i.e. exactly HEADER_FIELD_PREFIX.length()
        final List<String> lines = Arrays.asList("### Test.java", "- :value", "- H: 1.0");

        // act
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        // assert -- the malformed line contributed nothing; the well-formed one still parsed
        assertThat(header.h(), is(equalTo("1.0")));
        assertThat(header.title(), is(equalTo("Test.java")));
    }

    /** A field line with no colon at all is ignored rather than throwing. */
    @Test
    public void read_fieldLineWithoutColon_isIgnored() {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "- no colon here", "- H: 1.0");

        // act
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        // assert
        assertThat(header.h(), is(equalTo("1.0")));
    }

    /** Input with no title line yields an empty title rather than failing. */
    @Test
    public void read_noTitleLine_yieldsEmptyTitle() {
        // arrange
        final List<String> lines = Arrays.asList("- H: 1.0", "- C: 12345678");

        // act
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        // assert
        assertThat(header.title(), is(equalTo("")));
    }

    /** A line that is neither a title nor a field is skipped without disturbing the parse. */
    @Test
    public void read_unrelatedLine_isSkipped() {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "some prose", "- H: 1.0");

        // act
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        // assert
        assertThat(header.h(), is(equalTo("1.0")));
        assertThat(header.title(), is(equalTo("Test.java")));
    }

    /** A header missing a field yields the empty string for it, not null. */
    @Test
    public void read_missingField_yieldsEmptyString() {
        // arrange
        final List<String> lines = Arrays.asList("### Test.java", "- H: 1.0");

        // act
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        // assert
        assertThat(header.c(), is(equalTo("")));
        assertThat(header.g(), is(equalTo("")));
    }

    // </editor-fold>
}
