// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The inspector's contract is "never throws": it turns every failure into a result the caller can
 * act on. These cover the failure side, which is the side that matters — the plan-time check exists
 * precisely because a file can exist and still not be a model.
 */
public class GgufModelInspectorTest {

    @TempDir
    Path tempDir;

    private final GgufModelInspector inspector = new GgufModelInspector();

    /** A Git LFS pointer: exists, is a plain text file, passes any existence check. */
    @Test
    public void inspect_gitLfsPointerInsteadOfAModel_isUnreadableWithAReason() throws Exception {
        final Path pointer = tempDir.resolve("model.gguf");
        Files.write(
                pointer,
                ("version https://git-lfs.github.com/spec/v1\noid sha256:0000\nsize 123\n")
                        .getBytes(StandardCharsets.UTF_8));

        final GgufModelInfo info = inspector.inspect(pointer);

        assertThat(info.readable(), is(false));
        assertThat(info.failure().isEmpty(), is(false));
        assertThat(info.contextLength(), is(GgufModelInfo.UNKNOWN_CONTEXT_LENGTH));
    }

    /** A truncated download: the right magic bytes, then nothing. */
    @Test
    public void inspect_truncatedFile_isUnreadableRatherThanThrowing() throws Exception {
        final Path truncated = tempDir.resolve("truncated.gguf");
        Files.write(truncated, new byte[] {'G', 'G', 'U', 'F'});

        final GgufModelInfo info = inspector.inspect(truncated);

        assertThat(info.readable(), is(false));
    }

    /** A path that does not exist at all must also come back as a result, not an exception. */
    @Test
    public void inspect_missingFile_isUnreadableRatherThanThrowing() {
        final GgufModelInfo info = inspector.inspect(tempDir.resolve("nope.gguf"));

        assertThat(info.readable(), is(false));
    }
}
