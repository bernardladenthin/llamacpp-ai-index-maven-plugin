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
        // up to whole lines, so it is a hair under 4); the runner surfaces them.
        assertThat(m.prefillTokensPerSecond(), is(1000.0d));
        assertThat(m.decodeTokensPerSecond(), is(100.0d));
        assertThat(m.charsPerToken() > 3.9d && m.charsPerToken() <= 4.0d, is(true));
        assertThat(m.loadSeconds() >= 0.0d, is(true));
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
                return new AiGenerationTimings("t", 0, 0.0d, 0, 0.0d);
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
                return new AiGenerationTimings("", 0, 0.0d, 0, 0.0d);
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
                return new AiGenerationTimings("t", 0, 0.0d, 0, 0.0d);
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
}
