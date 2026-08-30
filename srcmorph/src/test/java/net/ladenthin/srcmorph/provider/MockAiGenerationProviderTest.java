// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import org.junit.jupiter.api.Test;

public class MockAiGenerationProviderTest {

    private static final AiMdHeader HEADER = new AiMdHeader(
            "Foo.java", "1.0", "C", "2026-01-01T00:00:00Z", "2026-01-01T00:00:10Z", "0.1.0", "1.0.0", "file");

    private final MockAiGenerationProvider provider = new MockAiGenerationProvider();

    @Test
    public void generateUsesFileNameOnly() throws IOException {
        AiGenerationRequest request =
                new AiGenerationRequest("summary", Paths.get("a", "b", "Foo.java"), "src", HEADER);
        // Only the file name (not the full path) is used — kills the negate mutant on the
        // getFileName() null-guard, which would fall back to the full path string.
        assertThat(provider.generate(request), is("Mock summary for Foo.java"));
    }

    @Test
    public void generateFallsBackToFullPathWhenFileNameNull() throws IOException {
        // A root path has a null getFileName(); the provider must use file.toString(). Derive the
        // expected separator from the same Path so the assertion holds on Windows too (the root
        // renders with the platform separator: "/" on POSIX, "\" on Windows).
        Path root = Paths.get("/");
        AiGenerationRequest request = new AiGenerationRequest("summary", root, "src", HEADER);
        assertThat(provider.generate(request), is("Mock summary for " + root));
    }

    @Test
    public void generateWithTimings_reportsDeterministicSyntheticTimings() throws IOException {
        // 16 chars of source / 4 chars-per-token -> 4 mock prompt tokens; positive synthetic rates so a
        // calibrate run works without a real model.
        final AiGenerationRequest request =
                new AiGenerationRequest("summary", Paths.get("Foo.java"), "0123456789012345", HEADER);
        final AiGenerationTimings timings = provider.generateWithTimings(request);
        assertThat(timings.text(), is("Mock summary for Foo.java"));
        assertThat(timings.promptTokens(), is(4));
        assertThat(timings.prefillTokensPerSecond() > 0.0d, is(true));
        assertThat(timings.decodeTokensPerSecond() > 0.0d, is(true));
    }

    /**
     * The mock models the system prompt being served from the KV cache: a fixed, non-zero cached prefix on
     * top of the source-derived evaluated tokens. Pinned exactly, because "constant" is the property the
     * calibration differential relies on -- a cached count that varied with the source would not cancel.
     */
    @Test
    public void generateWithTimings_reportsAConstantCachedPrefixIndependentOfSourceSize() throws IOException {
        final AiGenerationTimings small =
                provider.generateWithTimings(new AiGenerationRequest("summary", Paths.get("Foo.java"), "0123", HEADER));
        final AiGenerationTimings large = provider.generateWithTimings(
                new AiGenerationRequest("summary", Paths.get("Foo.java"), "0123456789012345", HEADER));

        assertThat(small.cachedPromptTokens(), is(32));
        assertThat(large.cachedPromptTokens(), is(small.cachedPromptTokens()));
        // Only the evaluated half grows with the source, so the totals differ by the source delta alone.
        assertThat(small.totalPromptTokens(), is(33));
        assertThat(large.totalPromptTokens(), is(36));
    }
}
