// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import org.junit.jupiter.api.Test;

public class AiGenerationProviderDefaultTimingsTest {

    private static final AiMdHeader HEADER = new AiMdHeader(
            "Foo.java", "1.0", "C", "2026-01-01T00:00:00Z", "2026-01-01T00:00:10Z", "0.1.0", "1.0.0", "file");

    @Test
    public void defaultGenerateWithTimings_delegatesToGenerateWithZeroRates() throws Exception {
        // A provider that implements only generate() inherits the interface default, which must return the
        // generated text with zero timings (no measurement available).
        final AiGenerationProvider provider = request -> "TEXT";
        final AiGenerationTimings timings =
                provider.generateWithTimings(new AiGenerationRequest("summary", Paths.get("Foo.java"), "src", HEADER));

        assertThat(timings.text(), is("TEXT"));
        assertThat(timings.promptTokens(), is(0));
        assertThat(timings.cachedPromptTokens(), is(0));
        assertThat(timings.totalPromptTokens(), is(0));
        assertThat(timings.prefillTokensPerSecond(), is(0.0d));
        assertThat(timings.predictedTokens(), is(0));
        assertThat(timings.decodeTokensPerSecond(), is(0.0d));
    }
}
