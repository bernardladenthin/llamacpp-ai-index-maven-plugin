// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.srcmorph.support.ConvertToRecord;

/**
 * A generated text plus the model's measured timing for that generation, used by the
 * {@code srcmorph:calibrate} goal to derive per-machine throughput. Prefill = prompt processing; decode =
 * answer generation. Rates of {@code 0} mean the provider did not report timings (e.g. the mock provider
 * or a binding that omits them).
 *
 * <p><b>Prompt tokens are split in two.</b> {@link #promptTokens()} counts only the tokens the model
 * actually <em>evaluated</em> ({@code prompt_n}); {@link #cachedPromptTokens()} counts the leading tokens
 * it could serve straight from the KV cache without re-evaluating them ({@code cache_n}), which is what
 * the prefix-reuse settings ({@code cachePrompt}, {@code cacheReuse}, {@code swaFull}) buy. Only their sum
 * &mdash; {@link #totalPromptTokens()} &mdash; describes the whole prompt, so any arithmetic over prompt
 * size must use that sum unless it deliberately means "work done this call".</p>
 *
 * <p>Record-shaped value type marked {@link ConvertToRecord} for the future Java&nbsp;17+ migration; the
 * accessors follow record style ({@code text()}, not {@code getText()}).</p>
 */
@ConvertToRecord
@ToString
@EqualsAndHashCode
public final class AiGenerationTimings {

    private final String text;
    private final int promptTokens;
    private final int cachedPromptTokens;
    private final double prefillTokensPerSecond;
    private final int predictedTokens;
    private final double decodeTokensPerSecond;

    /**
     * Creates a new {@link AiGenerationTimings}.
     *
     * @param text                   the generated (parsed) text
     * @param promptTokens           number of prompt tokens the model processed (prefill)
     * @param cachedPromptTokens     number of leading prompt tokens served from the KV cache instead of
     *                               being processed ({@code cache_n}); {@code 0} when nothing was reused
     * @param prefillTokensPerSecond measured prefill throughput (tokens/second)
     * @param predictedTokens        number of tokens the model generated (decode)
     * @param decodeTokensPerSecond  measured decode throughput (tokens/second)
     */
    public AiGenerationTimings(
            final String text,
            final int promptTokens,
            final int cachedPromptTokens,
            final double prefillTokensPerSecond,
            final int predictedTokens,
            final double decodeTokensPerSecond) {
        this.text = text;
        this.promptTokens = promptTokens;
        this.cachedPromptTokens = cachedPromptTokens;
        this.prefillTokensPerSecond = prefillTokensPerSecond;
        this.predictedTokens = predictedTokens;
        this.decodeTokensPerSecond = decodeTokensPerSecond;
    }

    /**
     * Returns the generated (parsed) text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the number of prompt tokens the model processed.
     *
     * @return the prompt token count
     */
    public int promptTokens() {
        return promptTokens;
    }

    /**
     * Returns the number of leading prompt tokens the model served from the KV cache instead of
     * evaluating them ({@code cache_n}). A value greater than zero is the direct evidence that the
     * prefix-reuse settings are actually paying off on this run.
     *
     * @return the cache-served prompt token count, or {@code 0} when nothing was reused
     */
    public int cachedPromptTokens() {
        return cachedPromptTokens;
    }

    /**
     * Returns the size of the whole prompt in tokens: the tokens evaluated this call plus the ones
     * served from the KV cache.
     *
     * @return the total prompt token count
     */
    public int totalPromptTokens() {
        return promptTokens + cachedPromptTokens;
    }

    /**
     * Returns the measured prefill throughput (tokens/second).
     *
     * @return the prefill tokens per second
     */
    public double prefillTokensPerSecond() {
        return prefillTokensPerSecond;
    }

    /**
     * Returns the number of tokens the model generated.
     *
     * @return the predicted token count
     */
    public int predictedTokens() {
        return predictedTokens;
    }

    /**
     * Returns the measured decode throughput (tokens/second).
     *
     * @return the decode tokens per second
     */
    public double decodeTokensPerSecond() {
        return decodeTokensPerSecond;
    }
}
