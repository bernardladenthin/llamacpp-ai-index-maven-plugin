// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class AiGenerationTimingsTest {

    @Test
    public void accessorsReturnConstructorValues() {
        final AiGenerationTimings t = new AiGenerationTimings("summary", 1500, 512, 900.0d, 64, 45.0d);
        assertThat(t.text(), is("summary"));
        assertThat(t.promptTokens(), is(1500));
        assertThat(t.cachedPromptTokens(), is(512));
        assertThat(t.prefillTokensPerSecond(), is(900.0d));
        assertThat(t.predictedTokens(), is(64));
        assertThat(t.decodeTokensPerSecond(), is(45.0d));
    }

    /**
     * The whole prompt is the evaluated tokens <em>plus</em> the cache-served ones. The two operands are
     * deliberately distinct and non-zero so a swapped, dropped or sign-flipped term produces a different
     * number rather than the same one by coincidence.
     */
    @Test
    public void totalPromptTokens_addsTheCacheServedPrefixToTheEvaluatedTokens() {
        assertThat(new AiGenerationTimings("s", 1500, 512, 900.0d, 64, 45.0d).totalPromptTokens(), is(2012));
    }

    /** With nothing reused the total collapses onto the evaluated count -- the no-cache baseline. */
    @Test
    public void totalPromptTokens_withoutCacheReuse_equalsTheEvaluatedTokens() {
        assertThat(new AiGenerationTimings("s", 1500, 0, 900.0d, 64, 45.0d).totalPromptTokens(), is(1500));
    }

    /**
     * A fully reused prompt evaluates nothing yet still has a size; reading {@code promptTokens()} alone
     * would report an empty prompt, which is exactly the confusion the split invites.
     */
    @Test
    public void totalPromptTokens_withAFullyCachedPrompt_isTheCachedCount() {
        final AiGenerationTimings fullyCached = new AiGenerationTimings("s", 0, 777, 900.0d, 64, 45.0d);
        assertThat(fullyCached.promptTokens(), is(0));
        assertThat(fullyCached.totalPromptTokens(), is(777));
    }
}
