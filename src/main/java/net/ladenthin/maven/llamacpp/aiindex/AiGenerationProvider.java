// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.maven.llamacpp.aiindex;

import java.io.IOException;

public interface AiGenerationProvider extends AutoCloseable {

    String generate(AiGenerationRequest request) throws IOException;

    /**
     * Generates text for the given request using the specified temperature, overriding any
     * temperature configured in the provider itself.
     *
     * <p>This method is called during retry attempts to use a higher temperature than the
     * original request, which can break out of EOS-early failure modes that produce empty
     * responses. Implementations that support per-call temperature overrides should override
     * this method; the default implementation ignores {@code temperatureOverride} and
     * delegates to {@link #generate(AiGenerationRequest)}.</p>
     *
     * @param request           the generation request containing prompt key, source file,
     *                          source text, and current header
     * @param temperatureOverride the sampling temperature to use for this call, replacing
     *                          any temperature value held by the provider's own configuration
     * @return the generated text; never {@code null}, but may be blank if the model
     *         produced no tokens
     * @throws IOException if the underlying provider fails
     */
    default String generate(final AiGenerationRequest request, final float temperatureOverride) throws IOException {
        return generate(request);
    }

    @Override
    default void close() throws IOException {
    }
}