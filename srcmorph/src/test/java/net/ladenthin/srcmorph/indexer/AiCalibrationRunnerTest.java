// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.AiGenerationProvider;
import net.ladenthin.srcmorph.provider.AiGenerationTimings;
import net.ladenthin.srcmorph.provider.MockAiGenerationProvider;
import org.junit.jupiter.api.Test;

public class AiCalibrationRunnerTest {

    private static AiPromptPreparationSupport prep() {
        return new AiPromptPreparationSupport(new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions()));
    }

    private static AiGenerationConfig config() {
        final AiGenerationConfig config = new AiGenerationConfig();
        config.setContextSize(2048);
        config.setCharsPerToken(4);
        config.setMaxOutputTokens(64);
        return config;
    }

    @Test
    public void measure_withMockProvider_reportsTheProvidersSyntheticThroughput() throws Exception {
        final AiCalibrationRunner runner = new AiCalibrationRunner();
        final AiCalibrationMeasurement m = runner.measure(
                new MockAiGenerationProvider(), config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // The mock reports 1000 prefill / 100 decode tok/s and ~4 chars/token (the synthetic source rounds
        // up to whole lines, so it is a hair under 4); the runner surfaces them. Its constant cached
        // prefix cancels out of the mid->near differential, so the ratio is unchanged by it.
        assertThat(m.prefillTokensPerSecond(), is(1000.0d));
        assertThat(m.decodeTokensPerSecond(), is(100.0d));
        assertThat(m.charsPerToken() > 3.9d && m.charsPerToken() <= 4.0d, is(true));
        assertThat(m.loadSeconds() >= 0.0d, is(true));
        // The mock's synthetic KV-cache hit count reaches the measurement instead of being dropped.
        assertThat(m.cachedPromptTokens() > 0, is(true));
    }

    @Test
    public void measure_zeroRateProvider_takesWallClockFallback() throws Exception {
        // A provider that reports zero rates (like the real JNI path) forces the wall-clock fallback; the
        // measured charsPerToken then comes from the config (4), proving the fallback branch ran.
        final AiGenerationProvider zeroRateProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "t";
            }

            @Override
            public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) {
                return new AiGenerationTimings("t", 0, 0, 0.0d, 0, 0.0d);
            }
        };
        final AiCalibrationMeasurement m = new AiCalibrationRunner()
                .measure(zeroRateProvider, config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());
        assertThat(m.charsPerToken(), is(4.0d));
        assertThat(m.prefillTokensPerSecond() >= 0.0d, is(true));
        assertThat(m.decodeTokensPerSecond() >= 0.0d, is(true));
    }

    @Test
    public void windowChars_isPositiveForANormalWindow() {
        final long window =
                new AiCalibrationRunner().windowChars(config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());
        assertThat(window > 0, is(true));
    }

    // <editor-fold defaultstate="collapsed" desc="wall-clock fallback divide-by-zero guards">

    /**
     * A provider returning empty text drives {@code outputTokens} to zero, which must yield a decode
     * rate of exactly zero rather than a division by it.
     *
     * <p>Scoped deliberately: most of {@code wallClockFallback}'s surviving mutants are arithmetic on
     * {@code System.nanoTime()} deltas and cannot be pinned without injecting a clock, so this class
     * stays off the PIT gate. These guards are the part that is both reachable and worth pinning --
     * they are what stands between a degenerate measurement and a {@code NaN}/{@code Infinity} leaking
     * into the calibration report.
     *
     * @throws Exception if the measurement fails
     */
    @Test
    public void measure_providerReturningEmptyText_yieldsZeroDecodeRateInsteadOfDividingByZero() throws Exception {
        // arrange
        final AiGenerationProvider emptyOutputProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "";
            }

            @Override
            public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) {
                return new AiGenerationTimings("", 0, 0, 0.0d, 0, 0.0d);
            }
        };

        // act
        final AiCalibrationMeasurement measurement = new AiCalibrationRunner()
                .measure(emptyOutputProvider, config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // assert
        assertThat(measurement.decodeTokensPerSecond(), is(0.0d));
        assertThat(Double.isFinite(measurement.decodeTokensPerSecond()), is(true));
        assertThat(Double.isFinite(measurement.prefillTokensPerSecond()), is(true));
    }

    /**
     * A non-positive configured {@code charsPerToken} must fall back to the built-in constant rather
     * than being used as a divisor.
     *
     * @throws Exception if the measurement fails
     */
    @Test
    public void measure_zeroCharsPerToken_fallsBackToTheBuiltInRatio() throws Exception {
        // arrange
        final AiGenerationConfig zeroRatio = config();
        zeroRatio.setCharsPerToken(0);
        final AiGenerationProvider zeroRateProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "t";
            }

            @Override
            public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) {
                return new AiGenerationTimings("t", 0, 0, 0.0d, 0, 0.0d);
            }
        };

        // act
        final AiCalibrationMeasurement measurement = new AiCalibrationRunner()
                .measure(zeroRateProvider, zeroRatio, CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // assert -- not the configured 0, and finite: the fallback ratio was used as the divisor
        assertThat(measurement.charsPerToken() > 0.0d, is(true));
        assertThat(Double.isFinite(measurement.charsPerToken()), is(true));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="chars-per-token is independent of prompt-cache reuse">

    /** Prompt tokens a synthetic provider charges for the base prompt, whatever the source size is. */
    private static final int BASE_PROMPT_TOKENS = 900;

    /** Synthetic chars-per-token the providers below tokenize with. */
    private static final int SYNTHETIC_CHARS_PER_TOKEN = 4;

    /**
     * A provider whose whole prompt is always {@link #BASE_PROMPT_TOKENS} plus one token per
     * {@link #SYNTHETIC_CHARS_PER_TOKEN} source characters, split into "evaluated" and "served from the
     * KV cache" exactly as asked. Two instances with different splits describe the <em>same</em> prompt.
     *
     * @param cachedPrefixTokens how many of the prompt's tokens the KV cache served this call
     * @return the provider
     */
    private static AiGenerationProvider tokenizingProvider(final int cachedPrefixTokens) {
        return new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "t";
            }

            @Override
            public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) {
                final int totalPromptTokens =
                        BASE_PROMPT_TOKENS + request.sourceText().length() / SYNTHETIC_CHARS_PER_TOKEN;
                return new AiGenerationTimings(
                        "t", totalPromptTokens - cachedPrefixTokens, cachedPrefixTokens, 1000.0d, 64, 100.0d);
            }
        };
    }

    /**
     * The measured chars-per-token must describe the source text, not the accident of whether the base
     * prompt happened to be a cache hit on that run.
     *
     * <p>Both arms describe the identical prompt; they differ only in how much of it the KV cache served.
     * Deriving from the mid&rarr;near differential over the <em>total</em> prompt tokens cancels the base
     * prompt, so both arms must agree exactly. The previous {@code nearChars / promptTokens()} form did
     * not: with no reuse it divided the source characters by base-plus-source tokens and silently
     * understated the ratio, and nothing in the run reported that the assumption had failed.</p>
     *
     * @throws Exception if a measurement fails
     */
    @Test
    public void measure_charsPerToken_isTheSameWhetherOrNotTheBasePromptWasACacheHit() throws Exception {
        // arrange -- same prompt both times; only the cached/evaluated split differs
        final AiGenerationProvider noReuse = tokenizingProvider(0);
        final AiGenerationProvider fullBaseReuse = tokenizingProvider(BASE_PROMPT_TOKENS);

        // act
        final AiCalibrationMeasurement withoutCache =
                new AiCalibrationRunner().measure(noReuse, config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());
        final AiCalibrationMeasurement withCache = new AiCalibrationRunner()
                .measure(fullBaseReuse, config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // assert -- identical ratio (the point of the test), and close to the provider's real tokenization
        // rate. Not exactly 4: syntheticSource(n) rounds n up to whole lines, so the differential's
        // numerator uses the requested sizes while its denominator counts the slightly longer real ones.
        assertThat(withoutCache.charsPerToken(), is(withCache.charsPerToken()));
        assertThat(Math.abs(withCache.charsPerToken() - SYNTHETIC_CHARS_PER_TOKEN) < 0.05d, is(true));
        // ...and the cache observation itself is reported rather than inferred
        assertThat(withoutCache.cachedPromptTokens(), is(0));
        assertThat(withCache.cachedPromptTokens(), is(BASE_PROMPT_TOKENS));
    }

    /**
     * With no size differential to read (a window so small that both calibration sources clamp to the same
     * floor) the derivation must still produce a finite, positive ratio instead of dividing by zero.
     *
     * @throws Exception if the measurement fails
     */
    @Test
    public void measure_windowTooSmallForASizeDifferential_stillYieldsAFiniteCharsPerToken() throws Exception {
        // arrange -- a one-token context leaves no room, so mid and near both clamp to the minimum
        final AiGenerationConfig tinyWindow = config();
        tinyWindow.setContextSize(1);

        // act
        final AiCalibrationMeasurement measurement = new AiCalibrationRunner()
                .measure(tokenizingProvider(0), tinyWindow, CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // assert
        assertThat(measurement.charsPerToken() > 0.0d, is(true));
        assertThat(Double.isFinite(measurement.charsPerToken()), is(true));
    }

    /**
     * A provider that reports throughput but no prompt tokens at all must yield {@code 0}, not a
     * {@code NaN} leaking into the calibration report.
     *
     * @throws Exception if the measurement fails
     */
    @Test
    public void measure_providerReportingNoPromptTokens_yieldsZeroCharsPerToken() throws Exception {
        // arrange -- positive rates take the preferred path, but every prompt-token count is zero
        final AiGenerationProvider noTokenCounts = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "t";
            }

            @Override
            public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) {
                return new AiGenerationTimings("t", 0, 0, 1000.0d, 64, 100.0d);
            }
        };

        // act
        final AiCalibrationMeasurement measurement = new AiCalibrationRunner()
                .measure(noTokenCounts, config(), CommonTestFixtures.PROMPT_KEY_FILE_BODY, prep());

        // assert
        assertThat(measurement.charsPerToken(), is(0.0d));
        assertThat(measurement.cachedPromptTokens(), is(0));
    }

    // </editor-fold>
}
