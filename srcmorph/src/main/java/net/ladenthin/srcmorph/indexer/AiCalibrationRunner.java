// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.provider.AiGenerationProvider;
import net.ladenthin.srcmorph.provider.AiGenerationTimings;

/**
 * Measures one model's per-machine timing for the {@code srcmorph:calibrate} goal: it triggers the load,
 * runs two representative generations (mid- and near-window sized), and reads the model's own measured
 * prefill/decode throughput. Lives in the indexer layer because it builds requests and drives the
 * provider; the mojo stays thin (orchestrate + format).
 */
public final class AiCalibrationRunner {

    /** Fraction of the window used for the first (mid-size) calibration generation. */
    private static final double MID_WINDOW_FRACTION = 0.5d;

    /** Fraction of the window used for the second (near-window) calibration generation, the headline. */
    private static final double NEAR_WINDOW_FRACTION = 0.9d;

    /** Character length of the warmup prompt that triggers model load. */
    private static final int WARMUP_SOURCE_CHARS = 200;

    /** Lower bound on a calibration source length, so a tiny/mock window still generates something. */
    private static final int MIN_CALIBRATION_SOURCE_CHARS = 200;

    /** Nanoseconds per second, for the load-time measurement. */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0d;

    /** Synthetic context file name used for the calibration requests. */
    private static final String CALIBRATION_CONTEXT_NAME = "calibration-sample";

    /** A representative code-like line repeated to synthesize a calibration source of a target length. */
    private static final String SYNTHETIC_SOURCE_LINE =
            "public final int value = compute(argument, offset) + 42; // calibration filler line\n";

    /** Chars/token assumed in the wall-clock fallback when the model config does not set one. */
    private static final double FALLBACK_CHARS_PER_TOKEN = 4.0d;

    /** Floor on the derived decode duration (seconds) so the wall-clock fallback never divides by ~zero. */
    private static final double MIN_DECODE_SECONDS = 0.001d;

    /** Creates a new {@link AiCalibrationRunner}. */
    public AiCalibrationRunner() {
        // no-op
    }

    /**
     * Runs the warmup + two sized generations against {@code provider} and returns the measurement.
     *
     * @param provider  the (already built) provider for the model being calibrated
     * @param config    the model's generation config (for the window size)
     * @param promptKey a representative prompt key routed to this model
     * @param prep      prompt preparation (for the base-prompt length / window)
     * @return the measurement (load time + near/mid throughput + chars-per-token)
     * @throws IOException if the provider throws during generation
     */
    public AiCalibrationMeasurement measure(
            final AiGenerationProvider provider,
            final AiGenerationConfig config,
            final String promptKey,
            final AiPromptPreparationSupport prep)
            throws IOException {
        final Path contextFile = Paths.get(CALIBRATION_CONTEXT_NAME);
        final int basePromptLength = prep.getBasePromptLength(promptKey, contextFile);
        final long availableSourceChars = AiInputWindowCalculator.availableSourceChars(config, basePromptLength);
        final int midChars = clampSourceChars((long) (availableSourceChars * MID_WINDOW_FRACTION));
        final int nearChars = clampSourceChars((long) (availableSourceChars * NEAR_WINDOW_FRACTION));
        final AiMdHeader header = new AiMdHeader(CALIBRATION_CONTEXT_NAME, "1.0", "", "", "", "", "", "file");
        // The warmup and the decode probe use the SAME tiny source, so the probe reuses the warmup's
        // prefill KV (prompt cache) and its wall-clock is close to pure decode.
        final String tinySource = syntheticSource(WARMUP_SOURCE_CHARS);

        final long loadStartNanos = System.nanoTime();
        provider.generateWithTimings(request(promptKey, contextFile, tinySource, header));
        final double loadSeconds = (System.nanoTime() - loadStartNanos) / NANOS_PER_SECOND;

        final long midStartNanos = System.nanoTime();
        final AiGenerationTimings mid =
                provider.generateWithTimings(request(promptKey, contextFile, syntheticSource(midChars), header));
        final double midWallSeconds = (System.nanoTime() - midStartNanos) / NANOS_PER_SECOND;

        final long nearStartNanos = System.nanoTime();
        final AiGenerationTimings near =
                provider.generateWithTimings(request(promptKey, contextFile, syntheticSource(nearChars), header));
        final double nearWallSeconds = (System.nanoTime() - nearStartNanos) / NANOS_PER_SECOND;

        // Preferred path: the provider reports the model's OWN measured throughput (exact tokens/sec).
        if (near.prefillTokensPerSecond() > 0.0d) {
            return new AiCalibrationMeasurement(
                    loadSeconds,
                    near.prefillTokensPerSecond(),
                    near.decodeTokensPerSecond(),
                    charsPerToken(midChars, mid, nearChars, near),
                    mid.prefillTokensPerSecond(),
                    near.cachedPromptTokens());
        }

        // Fallback (no reported timings, e.g. mock): estimate by wall-clock. Only now do the decode probe
        // (a TINY prompt whose prefill KV is reused from the warmup, so its wall-clock is ~pure decode; its
        // generated text gives the ACTUAL output size).
        final long probeStartNanos = System.nanoTime();
        final AiGenerationTimings probe =
                provider.generateWithTimings(request(promptKey, contextFile, tinySource, header));
        final double probeWallSeconds = (System.nanoTime() - probeStartNanos) / NANOS_PER_SECOND;
        return wallClockFallback(
                loadSeconds,
                midChars,
                nearChars,
                midWallSeconds,
                nearWallSeconds,
                probe,
                probeWallSeconds,
                config,
                near.cachedPromptTokens());
    }

    /**
     * Derives characters per token from the mid&rarr;near <em>size differential</em>, so the result
     * describes the source text alone.
     *
     * <p>Both runs share the same base prompt, so subtracting them cancels it: the remaining characters
     * ({@code nearChars - midChars}) and the remaining tokens are pure source. The token side must be the
     * <em>whole</em> prompt ({@link AiGenerationTimings#totalPromptTokens()}, i.e. processed <b>plus</b>
     * KV-cache-served), never the processed count alone &mdash; the earlier
     * {@code nearChars / near.promptTokens()} form only happened to be right while the base prompt was a
     * cache hit, and nothing in the run enforced or even reported that. With the totals the derivation is
     * correct whether or not the prefix was reused.</p>
     *
     * @param midChars  the mid-window source length
     * @param mid       the mid-window run's timings
     * @param nearChars the near-window source length
     * @param near      the near-window run's timings
     * @return characters per token, or {@code 0} when the run reported no prompt tokens at all
     */
    private static double charsPerToken(
            final int midChars, final AiGenerationTimings mid, final int nearChars, final AiGenerationTimings near) {
        final int nearTokens = near.totalPromptTokens();
        final int deltaTokens = nearTokens - mid.totalPromptTokens();
        if (deltaTokens > 0) {
            return (double) (nearChars - midChars) / deltaTokens;
        }
        // Degenerate: both sizes clamped to the same floor (a tiny window), so there is no differential to
        // read. Fall back to the near run's own whole-prompt ratio, which overstates chars/token by the
        // base prompt's share -- still finite and far better than dividing by zero.
        return nearTokens > 0 ? (double) nearChars / nearTokens : 0.0d;
    }

    /**
     * Derives throughput from wall-clock timing when the binding reports none.
     *
     * <p><b>Prefill</b> comes from the mid&rarr;near difference (decode cancels because both runs use the
     * same output budget). <b>Decode</b> comes from the tiny probe run: its wall-clock minus the tiny
     * prompt's (negligible) prefill, divided by the <em>actual</em> number of generated tokens (from the
     * probe's returned text length &divide; chars-per-token) &mdash; not an assumed output budget.</p>
     *
     * @param loadSeconds     the measured load + first-generation seconds
     * @param midChars        the mid-window source length
     * @param nearChars       the near-window source length
     * @param midWallSeconds  wall-clock seconds for the mid-window generation
     * @param nearWallSeconds wall-clock seconds for the near-window generation
     * @param probe           the tiny decode-probe result (its text is the actual generated output)
     * @param probeWallSeconds wall-clock seconds for the decode probe
     * @param config          the model config (chars/token)
     * @param cachedPromptTokens the near-window run's KV-cache hit count, carried through unchanged
     * @return the measurement derived from wall-clock timing
     */
    private static AiCalibrationMeasurement wallClockFallback(
            final double loadSeconds,
            final int midChars,
            final int nearChars,
            final double midWallSeconds,
            final double nearWallSeconds,
            final AiGenerationTimings probe,
            final double probeWallSeconds,
            final AiGenerationConfig config,
            final int cachedPromptTokens) {
        final double charsPerToken =
                config.getCharsPerToken() > 0 ? config.getCharsPerToken() : FALLBACK_CHARS_PER_TOKEN;
        final double midTokens = midChars / charsPerToken;
        final double nearTokens = nearChars / charsPerToken;
        final double deltaTokens = nearTokens - midTokens;
        final double deltaWall = nearWallSeconds - midWallSeconds;
        final double prefillTps = deltaTokens > 0.0d && deltaWall > 0.0d ? deltaTokens / deltaWall : 0.0d;

        // Decode from the tiny probe: subtract its (tiny) prefill, divide by the ACTUAL generated tokens.
        final double probePromptTokens = WARMUP_SOURCE_CHARS / charsPerToken;
        final double probePrefillSeconds = prefillTps > 0.0d ? probePromptTokens / prefillTps : 0.0d;
        final double decodeSeconds = Math.max(MIN_DECODE_SECONDS, probeWallSeconds - probePrefillSeconds);
        final double outputTokens = probe.text().length() / charsPerToken;
        final double decodeTps = outputTokens > 0.0d ? outputTokens / decodeSeconds : 0.0d;

        return new AiCalibrationMeasurement(
                loadSeconds, prefillTps, decodeTps, charsPerToken, prefillTps, cachedPromptTokens);
    }

    /**
     * Returns the window size (source chars) used for calibration, for the caller's log line.
     *
     * @param config    the model config
     * @param promptKey a representative prompt key
     * @param prep      prompt preparation
     * @return the available source-character window
     */
    public long windowChars(
            final AiGenerationConfig config, final String promptKey, final AiPromptPreparationSupport prep) {
        final int basePromptLength = prep.getBasePromptLength(promptKey, Paths.get(CALIBRATION_CONTEXT_NAME));
        return AiInputWindowCalculator.availableSourceChars(config, basePromptLength);
    }

    private static int clampSourceChars(final long chars) {
        return (int) Math.max(MIN_CALIBRATION_SOURCE_CHARS, Math.min(chars, Integer.MAX_VALUE));
    }

    private static String syntheticSource(final int targetChars) {
        final StringBuilder sb = new StringBuilder(targetChars + SYNTHETIC_SOURCE_LINE.length());
        // A size-specific first line so the mid- and near-window sources DIFFER from the start; otherwise
        // the shared repeated-line prefix lets the prompt cache reuse the earlier run's KV and the larger
        // run finishes faster, breaking the prefill differential.
        sb.append("// calibration sample of size ").append(targetChars).append('\n');
        while (sb.length() < targetChars) {
            sb.append(SYNTHETIC_SOURCE_LINE);
        }
        return sb.toString();
    }

    private static AiGenerationRequest request(
            final String promptKey, final Path contextFile, final String source, final AiMdHeader header) {
        return new AiGenerationRequest(promptKey, contextFile, source, header);
    }
}
