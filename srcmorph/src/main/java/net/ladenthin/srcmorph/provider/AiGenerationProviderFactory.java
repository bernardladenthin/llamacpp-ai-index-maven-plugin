// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import lombok.ToString;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.support.Java8CompatibilityHelper;

/** Selects and instantiates an {@link AiGenerationProvider} implementation by name. */
@ToString
public class AiGenerationProviderFactory {

    /** Creates a new {@link AiGenerationProviderFactory}. */
    public AiGenerationProviderFactory() {
        // no-op
    }

    /** Provider key selecting the deterministic, model-free {@link MockAiGenerationProvider}. */
    public static final String PROVIDER_MOCK = "mock";

    /** Provider key selecting the llama.cpp JNI provider, which loads a real GGUF model. */
    public static final String PROVIDER_LLAMACPP_JNI = "llamacpp-jni";

    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    /**
     * Creates an {@link AiGenerationProvider} for the given provider name.
     *
     * @param providerName  provider key; {@link #PROVIDER_MOCK} or {@link #PROVIDER_LLAMACPP_JNI}
     *                      (defaults to mock when blank or {@code null})
     * @param llamaConfig   configuration for the llama.cpp JNI provider
     * @param promptSupport prompt lookup support passed to providers that need it
     * @return a newly-created provider instance
     * @throws IllegalArgumentException if {@code providerName} is not recognised
     */
    public AiGenerationProvider create(
            final String providerName, final LlamaCppJniConfig llamaConfig, final AiPromptSupport promptSupport) {
        if (providerName == null || compatibilityHelper.isBlank(providerName)) {
            return new MockAiGenerationProvider();
        }

        switch (providerName) {
            case PROVIDER_MOCK:
                return new MockAiGenerationProvider();
            case PROVIDER_LLAMACPP_JNI:
                return new LlamaCppJniAiGenerationProvider(llamaConfig, promptSupport);
            default:
                throw new IllegalArgumentException("Unsupported AI provider: " + providerName);
        }
    }
}
