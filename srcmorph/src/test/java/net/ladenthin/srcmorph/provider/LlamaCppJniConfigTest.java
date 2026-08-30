// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link LlamaCppJniConfig}'s own constructor contract.
 *
 * <p>{@link LlamaCppJniConfigFactoryTest} exercises this class only through the factory, which never
 * passes a {@code null} list (the {@link AiGenerationConfig} setters normalise that away first). The
 * constructor's own null guards and its {@code modelPath} requirement are therefore only reachable
 * from here. That matters beyond coverage bookkeeping: the class carries
 * {@link net.ladenthin.srcmorph.support.ConvertToRecord} for a future Java&nbsp;17+ migration, and a
 * canonical-record rewrite is exactly where a normalising constructor body gets silently dropped.
 */
public class LlamaCppJniConfigTest {

    // <editor-fold defaultstate="collapsed" desc="fixture">

    /** Builds a config with the given lists and otherwise irrelevant, fixed scalar values. */
    private static LlamaCppJniConfig configWithLists(
            final @Nullable List<String> drySequenceBreakers, final @Nullable List<String> stopStrings) {
        return new LlamaCppJniConfig(
                null,
                "model.gguf",
                2048,
                128,
                0.15f,
                2,
                0.9f,
                40,
                0.0f,
                -1.0f,
                1.1f,
                false,
                true,
                false,
                0,
                -1,
                -1,
                -1,
                -1,
                -1,
                "",
                -1,
                "",
                "",
                -1,
                0.0f,
                1.75f,
                2,
                -1,
                drySequenceBreakers,
                stopStrings);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="null-list normalisation">

    @Test
    public void constructor_nullDrySequenceBreakers_normalisedToEmptyList() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(null, Arrays.asList("<end>"));

        // assert
        assertThat(config.drySequenceBreakers(), is(Collections.<String>emptyList()));
    }

    @Test
    public void constructor_nullStopStrings_normalisedToEmptyList() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n"), null);

        // assert
        assertThat(config.stopStrings(), is(Collections.<String>emptyList()));
    }

    @Test
    public void constructor_nonNullLists_areKept() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n", "."), Arrays.asList("<end>"));

        // assert
        assertThat(config.drySequenceBreakers(), is(Arrays.asList("\n", ".")));
        assertThat(config.stopStrings(), is(Arrays.asList("<end>")));
    }

    @Test
    public void constructor_nullTensorReadLazy_normalisedToEmptyString() {
        // arrange / act
        final LlamaCppJniConfig config = new LlamaCppJniConfig(
                null,
                "model.gguf",
                2048,
                128,
                0.15f,
                2,
                0.9f,
                40,
                0.0f,
                -1.0f,
                1.1f,
                false,
                true,
                false,
                0,
                -1,
                -1,
                -1,
                -1,
                -1,
                null,
                -1,
                "",
                "",
                -1,
                0.0f,
                1.75f,
                2,
                -1,
                Collections.<String>emptyList(),
                Collections.<String>emptyList());

        // assert
        assertThat(config.tensorReadLazy(), is(""));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="accessor views and required arguments">

    @Test
    public void drySequenceBreakers_returnsUnmodifiableView() {
        // arrange
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n"), Collections.<String>emptyList());

        // act / assert
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> config.drySequenceBreakers().add("x"));
    }

    @Test
    public void stopStrings_returnsUnmodifiableView() {
        // arrange
        final LlamaCppJniConfig config = configWithLists(Collections.<String>emptyList(), Arrays.asList("<end>"));

        // act / assert
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> config.stopStrings().add("x"));
    }

    @Test
    public void constructor_nullModelPath_isRejected() {
        // act / assert -- modelPath is the one argument the constructor requires outright
        final NullPointerException thrown = Assertions.assertThrows(
                NullPointerException.class,
                () -> new LlamaCppJniConfig(
                        null,
                        null,
                        2048,
                        128,
                        0.15f,
                        2,
                        0.9f,
                        40,
                        0.0f,
                        -1.0f,
                        1.1f,
                        false,
                        true,
                        false,
                        0,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        "",
                        -1,
                        "",
                        "",
                        -1,
                        0.0f,
                        1.75f,
                        2,
                        -1,
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList()));
        assertThat(thrown.getMessage(), is("modelPath"));
    }

    @Test
    public void libraryPath_nullIsPreserved() {
        // arrange / act
        final LlamaCppJniConfig config =
                configWithLists(Collections.<String>emptyList(), Collections.<String>emptyList());

        // assert -- null means "use the bundled native library", it is not normalised away
        assertThat(config.libraryPath(), is(nullValue()));
    }

    // </editor-fold>
}
