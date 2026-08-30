// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.document;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AiMdHeaderSupportTest {

    @TempDir
    public Path folder;

    private final AiMdHeaderSupport headerSupport = new AiMdHeaderSupport();
    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();
    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

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

    /**
     * Non-blank body written into every stored document under test.
     *
     * <p>{@code shouldWrite} returns early on a blank body (a previously failed generation), so a
     * test that writes only the header can never reach the header-field comparison it means to
     * exercise -- it passes for the wrong reason.
     */
    private static final String EXISTING_BODY = "Existing body content.\n";

    /**
     * Builds an {@link AiMdHeader} for {@link #FIXED_TITLE} / {@link AiMdHeaderCodec#NODE_TYPE_FILE}
     * using the supplied checksum and generator version. All other structural fields are
     * taken from the class-level {@code FIXED_*} constants.
     *
     * @param checksum         value for the {@code c} field
     * @param generatorVersion value for the {@code g} field
     * @return a fully populated header suitable for use in shouldWrite tests
     */
    private AiMdHeader buildHeader(final String checksum, final String generatorVersion) {
        return new AiMdHeader(
                FIXED_TITLE,
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                checksum,
                FIXED_D,
                FIXED_T,
                generatorVersion,
                FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE);
    }

    // <editor-fold defaultstate="collapsed" desc="shouldWrite">
    @Test
    public void shouldWrite_fileDoesNotExist_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader header = buildHeader(FIXED_CHECKSUM, FIXED_G);

        // act
        final boolean result = headerSupport.shouldWrite(false, target, header);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_matchingExistingHeaderWithBody_returnsFalse() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader header = buildHeader("ABCDEF12", FIXED_G);
        final AiMdDocument document = new AiMdDocument(header, "Existing body content.\n");
        documentCodec.write(target, document);

        // act
        final boolean result = headerSupport.shouldWrite(false, target, header);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void shouldWrite_existingHeaderVersionMismatch_returnsTrue() throws IOException {
        // arrange: an existing doc whose header format version is NOT 1.0, with a non-blank body.
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader oldVersionHeader = new AiMdHeader(
                FIXED_TITLE, "0.9", "ABCDEF12", FIXED_D, FIXED_T, FIXED_G, FIXED_A, AiMdHeaderCodec.NODE_TYPE_FILE);
        documentCodec.write(target, new AiMdDocument(oldVersionHeader, "Existing body content.\n"));
        final AiMdHeader expected = buildHeader("ABCDEF12", FIXED_G);

        // act: the stored 0.9 header forces a rewrite regardless of field equality.
        final boolean result = headerSupport.shouldWrite(false, target, expected);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_matchingExistingHeaderEmptyBody_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader header = buildHeader("ABCDEF12", FIXED_G);
        // write document with blank body to simulate a previously failed AI generation
        final AiMdDocument document = new AiMdDocument(header, "");
        documentCodec.write(target, document);

        // act
        final boolean result = headerSupport.shouldWrite(false, target, header);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_checksumChanged_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader original = buildHeader("AAAAAAAA", FIXED_G);
        // The body matters: a blank one short-circuits shouldWrite before the field comparison runs,
        // so writing header-only here would make this test pass without exercising the checksum check.
        documentCodec.write(target, new AiMdDocument(original, EXISTING_BODY));

        final AiMdHeader changed = buildHeader("BBBBBBBB", FIXED_G);

        // act
        final boolean result = headerSupport.shouldWrite(false, target, changed);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_generatorVersionChanged_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader original = buildHeader(FIXED_CHECKSUM, FIXED_G);
        // Body required for the same reason as in the checksum test above.
        documentCodec.write(target, new AiMdDocument(original, EXISTING_BODY));

        final AiMdHeader changed = buildHeader(FIXED_CHECKSUM, "2.0.0");

        // act
        final boolean result = headerSupport.shouldWrite(false, target, changed);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void shouldWrite_forceEnabled_returnsTrue() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        final AiMdHeader header = buildHeader(FIXED_CHECKSUM, FIXED_G);
        Files.write(target, headerCodec.write(header).getBytes(StandardCharsets.UTF_8));

        // act
        final boolean result = headerSupport.shouldWrite(true, target, header);

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="shouldWrite -- every compared header field">

    /**
     * The header stored on disk in {@link #shouldWrite_singleHeaderFieldChanged_returnsTrue}. Every
     * case writes this one and then passes an expected header differing in exactly one field.
     */
    private static AiMdHeader storedHeader() {
        return new AiMdHeader(
                FIXED_TITLE,
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                FIXED_CHECKSUM,
                FIXED_D,
                FIXED_T,
                FIXED_G,
                FIXED_A,
                AiMdHeaderCodec.NODE_TYPE_FILE);
    }

    /**
     * One case per field {@code shouldWrite} compares: {@code h}, {@code x}, {@code title},
     * {@code c}, {@code d}, {@code g}, {@code a}. Each argument pair is the field name (for the
     * failure message) and a header differing from {@link #storedHeader()} in that field alone.
     *
     * <p>Note {@code t} (the generation timestamp) is deliberately absent: it changes on every run
     * by design and comparing it would force a rewrite of every file, every time.
     *
     * @return the seven single-field-changed cases
     */
    private static Stream<Arguments> changedHeaderFields() {
        return Stream.of(
                Arguments.of(
                        "h",
                        new AiMdHeader(
                                FIXED_TITLE,
                                "2.0",
                                FIXED_CHECKSUM,
                                FIXED_D,
                                FIXED_T,
                                FIXED_G,
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_FILE)),
                Arguments.of(
                        "x",
                        new AiMdHeader(
                                FIXED_TITLE,
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                FIXED_CHECKSUM,
                                FIXED_D,
                                FIXED_T,
                                FIXED_G,
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_PACKAGE)),
                Arguments.of(
                        "title",
                        new AiMdHeader(
                                "Other.java",
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                FIXED_CHECKSUM,
                                FIXED_D,
                                FIXED_T,
                                FIXED_G,
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_FILE)),
                Arguments.of(
                        "c",
                        new AiMdHeader(
                                FIXED_TITLE,
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                "DEADBEEF",
                                FIXED_D,
                                FIXED_T,
                                FIXED_G,
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_FILE)),
                Arguments.of(
                        "d",
                        new AiMdHeader(
                                FIXED_TITLE,
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                FIXED_CHECKSUM,
                                "2020-01-01T00:00:00Z",
                                FIXED_T,
                                FIXED_G,
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_FILE)),
                Arguments.of(
                        "g",
                        new AiMdHeader(
                                FIXED_TITLE,
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                FIXED_CHECKSUM,
                                FIXED_D,
                                FIXED_T,
                                "9.9.9",
                                FIXED_A,
                                AiMdHeaderCodec.NODE_TYPE_FILE)),
                Arguments.of(
                        "a",
                        new AiMdHeader(
                                FIXED_TITLE,
                                AiMdHeaderCodec.HEADER_VERSION_1_0,
                                FIXED_CHECKSUM,
                                FIXED_D,
                                FIXED_T,
                                FIXED_G,
                                "9.9.9",
                                AiMdHeaderCodec.NODE_TYPE_FILE)));
    }

    /**
     * Pins every disjunct of {@code shouldWrite}'s field-comparison chain individually.
     *
     * <p>This exists because the chain was provably unreachable from this test class: replacing the
     * whole seven-way condition with {@code return false;} left all tests green, since the two
     * change-detection tests wrote a header with no body and returned at the blank-body guard first.
     * Dropping a disjunct means a stale {@code .ai.md} silently never regenerates, which contradicts
     * the project's incremental-update principle -- and nothing would have failed.
     *
     * @param fieldName      the single header field that differs (used in the failure message)
     * @param expectedHeader the header the caller expects, differing from the stored one in that field
     * @throws IOException if the fixture document cannot be written
     */
    @ParameterizedTest(name = "field {0} changed forces a rewrite")
    @MethodSource("changedHeaderFields")
    public void shouldWrite_singleHeaderFieldChanged_returnsTrue(
            final String fieldName, final AiMdHeader expectedHeader) throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        documentCodec.write(target, new AiMdDocument(storedHeader(), EXISTING_BODY));

        // act
        final boolean result = headerSupport.shouldWrite(false, target, expectedHeader);

        // assert
        assertThat("a changed " + fieldName + " must force a rewrite", result, is(true));
    }

    /**
     * The negative counterpart: an unchanged header must NOT force a rewrite. Without this, a mutant
     * turning the chain into a constant {@code true} would survive every case above.
     *
     * @throws IOException if the fixture document cannot be written
     */
    @Test
    public void shouldWrite_noHeaderFieldChanged_returnsFalse() throws IOException {
        // arrange
        final Path target = folder.resolve("test.ai.md");
        documentCodec.write(target, new AiMdDocument(storedHeader(), EXISTING_BODY));

        // act
        final boolean result = headerSupport.shouldWrite(false, target, storedHeader());

        // assert
        assertThat(result, is(false));
    }

    // </editor-fold>
}
