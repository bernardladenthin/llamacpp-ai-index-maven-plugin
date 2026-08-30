// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.srcmorph.support.ConvertToRecord;

/**
 * The result of one model's calibration measurement (see {@link AiCalibrationRunner}): the load time and
 * the measured near-window throughput that becomes the model's {@code <calibration>}, plus the mid-window
 * prefill rate for a curvature hint and the near-window run's KV-cache hit count.
 *
 * <p>Record-shaped value type marked {@link ConvertToRecord} for the future Java&nbsp;17+ migration.</p>
 */
@ConvertToRecord
@ToString
@EqualsAndHashCode
public final class AiCalibrationMeasurement {

    private final double loadSeconds;
    private final double prefillTokensPerSecond;
    private final double decodeTokensPerSecond;
    private final double charsPerToken;
    private final double midPrefillTokensPerSecond;
    private final int cachedPromptTokens;

    /**
     * Creates a new {@link AiCalibrationMeasurement}.
     *
     * @param loadSeconds               wall-clock seconds for model load + first generation
     * @param prefillTokensPerSecond    near-window prefill throughput (the calibration headline)
     * @param decodeTokensPerSecond     near-window decode throughput
     * @param charsPerToken             measured characters per token at the near-window size
     * @param midPrefillTokensPerSecond mid-window prefill throughput (for a curvature hint)
     * @param cachedPromptTokens        prompt tokens the near-window run served from the KV cache
     *                                  ({@code cache_n}); {@code 0} means no prefix reuse was observed
     */
    public AiCalibrationMeasurement(
            final double loadSeconds,
            final double prefillTokensPerSecond,
            final double decodeTokensPerSecond,
            final double charsPerToken,
            final double midPrefillTokensPerSecond,
            final int cachedPromptTokens) {
        this.loadSeconds = loadSeconds;
        this.prefillTokensPerSecond = prefillTokensPerSecond;
        this.decodeTokensPerSecond = decodeTokensPerSecond;
        this.charsPerToken = charsPerToken;
        this.midPrefillTokensPerSecond = midPrefillTokensPerSecond;
        this.cachedPromptTokens = cachedPromptTokens;
    }

    /**
     * Returns the load + first-generation wall-clock seconds.
     *
     * @return the load seconds
     */
    public double loadSeconds() {
        return loadSeconds;
    }

    /**
     * Returns the near-window prefill throughput (tokens/second).
     *
     * @return the prefill tokens per second
     */
    public double prefillTokensPerSecond() {
        return prefillTokensPerSecond;
    }

    /**
     * Returns the near-window decode throughput (tokens/second).
     *
     * @return the decode tokens per second
     */
    public double decodeTokensPerSecond() {
        return decodeTokensPerSecond;
    }

    /**
     * Returns the measured characters per token.
     *
     * @return the characters per token
     */
    public double charsPerToken() {
        return charsPerToken;
    }

    /**
     * Returns the mid-window prefill throughput (tokens/second), for a curvature hint.
     *
     * @return the mid-window prefill tokens per second
     */
    public double midPrefillTokensPerSecond() {
        return midPrefillTokensPerSecond;
    }

    /**
     * Returns how many prompt tokens the near-window run was able to serve from the KV cache. This is the
     * one observable the prefix-reuse settings ({@code cachePrompt}, {@code cacheReuse}, {@code swaFull})
     * produce: a zero here on a real model means their KV-memory cost is buying nothing.
     *
     * @return the cache-served prompt token count of the near-window run
     */
    public int cachedPromptTokens() {
        return cachedPromptTokens;
    }
}
