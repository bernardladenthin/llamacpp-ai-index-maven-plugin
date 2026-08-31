// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class GgufModelInfoTest {

    @Test
    public void accessorsReturnConstructorValues() {
        final GgufModelInfo info = new GgufModelInfo(true, "Qwen3-0.6B", 32768L, "");
        assertThat(info.readable(), is(true));
        assertThat(info.modelName(), is("Qwen3-0.6B"));
        assertThat(info.contextLength(), is(32768L));
        assertThat(info.failure(), is(""));
    }

    /**
     * The unreadable shape: a reason, and no claims about the model. Distinct from a readable file
     * that simply declares no context length, which is why {@code readable} is its own flag rather
     * than being inferred from {@link GgufModelInfo#UNKNOWN_CONTEXT_LENGTH}.
     */
    @Test
    public void unreadableFileCarriesItsReasonAndNoContextLength() {
        final GgufModelInfo info = new GgufModelInfo(false, "", GgufModelInfo.UNKNOWN_CONTEXT_LENGTH, "bad magic");
        assertThat(info.readable(), is(false));
        assertThat(info.failure(), is("bad magic"));
        assertThat(info.contextLength(), is(-1L));
    }

    /** A readable GGUF that declares no context length is still readable. */
    @Test
    public void readableFileWithoutADeclaredContextLength() {
        final GgufModelInfo info = new GgufModelInfo(true, "nameless", GgufModelInfo.UNKNOWN_CONTEXT_LENGTH, "");
        assertThat(info.readable(), is(true));
        assertThat(info.contextLength(), is(GgufModelInfo.UNKNOWN_CONTEXT_LENGTH));
    }

    /** Nulls are normalised, so no accessor can hand back {@code null}. */
    @Test
    public void nullStringsAreNormalisedToEmpty() {
        final GgufModelInfo info = new GgufModelInfo(false, null, 0L, null);
        assertThat(info.modelName(), is(""));
        assertThat(info.failure(), is(""));
    }
}
