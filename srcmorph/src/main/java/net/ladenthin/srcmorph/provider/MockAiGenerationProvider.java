// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import java.io.IOException;
import java.nio.file.Path;
import lombok.ToString;
import net.ladenthin.srcmorph.document.AiGenerationRequest;

/** Deterministic {@link AiGenerationProvider} that returns a mock summary; used for testing. */
@ToString
public class MockAiGenerationProvider implements AiGenerationProvider {

    /** Synthetic prefill throughput reported by the mock, so calibrate runs deterministically without a model. */
    private static final double MOCK_PREFILL_TOKENS_PER_SECOND = 1000.0d;

    /** Synthetic decode throughput reported by the mock. */
    private static final double MOCK_DECODE_TOKENS_PER_SECOND = 100.0d;

    /** Synthetic characters per token used to derive the mock prompt-token count from the source length. */
    private static final int MOCK_CHARS_PER_TOKEN = 4;

    /** Synthetic decode token count reported by the mock. */
    private static final int MOCK_PREDICTED_TOKENS = 64;

    /**
     * Synthetic KV-cache hit count reported by the mock. A real run reuses the byte-identical system
     * prompt on every call, so the mock models that as a <em>constant</em> cached prefix: it is present
     * (so cache-aware callers see reuse) and it cancels out of any size differential, exactly as the real
     * one does.
     */
    private static final int MOCK_CACHED_PROMPT_TOKENS = 32;

    /** Creates a new {@link MockAiGenerationProvider}. */
    public MockAiGenerationProvider() {
        // no-op
    }

    @Override
    public String generate(final AiGenerationRequest request) throws IOException {
        final Path file = request.sourceFile();
        final Path fileNamePath = file.getFileName();
        final String fileName = fileNamePath != null ? fileNamePath.toString() : file.toString();
        return "Mock summary for " + fileName;
    }

    @Override
    public AiGenerationTimings generateWithTimings(final AiGenerationRequest request) throws IOException {
        final int promptTokens = request.sourceText().length() / MOCK_CHARS_PER_TOKEN;
        return new AiGenerationTimings(
                generate(request),
                promptTokens,
                MOCK_CACHED_PROMPT_TOKENS,
                MOCK_PREFILL_TOKENS_PER_SECOND,
                MOCK_PREDICTED_TOKENS,
                MOCK_DECODE_TOKENS_PER_SECOND);
    }
}
