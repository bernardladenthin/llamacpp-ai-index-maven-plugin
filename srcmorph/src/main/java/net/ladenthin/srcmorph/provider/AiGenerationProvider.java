// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import java.io.IOException;
import net.ladenthin.srcmorph.document.AiGenerationRequest;

/**
 * Pluggable AI backend that produces text for an {@link net.ladenthin.srcmorph.document.AiGenerationRequest}.
 * Implementations may run locally (llama.cpp) or be mock providers for tests.
 */
public interface AiGenerationProvider extends AutoCloseable {

    /**
     * Generates text for the given request using the provider's default sampling parameters.
     *
     * @param request the generation request containing prompt key, source file, source text, and current header
     * @return the generated text; never {@code null}, but may be blank if the model produced no tokens
     * @throws IOException if the underlying provider fails
     */
    String generate(AiGenerationRequest request) throws IOException;

    /**
     * Generates text and returns it together with the model's measured timing, for the
     * {@code srcmorph:calibrate} goal. The default implementation delegates to {@link #generate} and
     * reports no timings (rates {@code 0}); providers that expose timings override this.
     *
     * @param request the generation request
     * @return the generated text plus timing (rates {@code 0} when unavailable)
     * @throws IOException if the underlying provider fails
     */
    default AiGenerationTimings generateWithTimings(final AiGenerationRequest request) throws IOException {
        return new AiGenerationTimings(generate(request), 0, 0, 0.0d, 0, 0.0d);
    }

    @Override
    default void close() throws IOException {}
}
