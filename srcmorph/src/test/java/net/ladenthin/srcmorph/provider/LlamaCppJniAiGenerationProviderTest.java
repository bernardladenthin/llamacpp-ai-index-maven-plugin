// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.ladenthin.llama.args.CacheType;
import net.ladenthin.llama.args.TensorReadLazyMode;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.document.AiMdHeaderCodec;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class LlamaCppJniAiGenerationProviderTest {

    private static final String MODEL_PATH = Paths.get("src", "test", "resources", "SmolLM2-135M-Instruct-Q3_K_M.gguf")
            .toAbsolutePath()
            .toString();

    private static final AiMdHeader HEADER = new AiMdHeader(
            "Test.java",
            AiMdHeaderCodec.HEADER_VERSION_1_0,
            "00000000",
            "2026-03-18T00:00:00Z",
            "2026-03-18T00:00:00Z",
            "0.1.0-SNAPSHOT",
            "0.0.0",
            AiMdHeaderCodec.NODE_TYPE_FILE);

    private static void assumeNativeAvailable() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("runNativeLlamaTests"),
                "Native llama test disabled. Enable with -DrunNativeLlamaTests=true");
        Assumptions.assumeTrue(Files.exists(Paths.get(MODEL_PATH)), "Model file missing: " + MODEL_PATH);
    }

    private static LlamaCppJniConfig config(final int contextSize, final int maxOutputTokens) {
        return LlamaCppJniConfig.builder(MODEL_PATH)
                .contextSize(contextSize)
                .maxOutputTokens(maxOutputTokens)
                .temperature(0.15f)
                .threads(8)
                .build();
    }

    private static AiGenerationRequest request(final String source) {
        return new AiGenerationRequest(CommonTestFixtures.PROMPT_KEY_FILE_BODY, Paths.get("Test.java"), source, HEADER);
    }

    // <editor-fold defaultstate="collapsed" desc="generate">
    @Test
    public void generate_realProvider_returnsNonEmptyResponse() throws Exception {
        assumeNativeAvailable();
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final String source = "package com.example;\n" + "\n"
                + "public class Test {\n"
                + "\n"
                + "    public String hello(final String name) {\n"
                + "        return \"Hello \" + name;\n"
                + "    }\n"
                + "}\n";

        try (LlamaCppJniAiGenerationProvider provider =
                new LlamaCppJniAiGenerationProvider(config(32768, 128), promptSupport)) {
            final String body = provider.generate(request(source));
            assertThat(body, is(notNullValue()));
            assertThat(body.trim().isEmpty(), is(false));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="generateWithTimings">
    @Test
    public void generateWithTimings_realProvider_reportsEngineTimingsThatScaleWithPromptSize() throws Exception {
        assumeNativeAvailable();
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final StringBuilder large = new StringBuilder("package com.example;\npublic class Big {\n");
        for (int i = 0; i < 120; i++) {
            large.append("    public int field")
                    .append(i)
                    .append(" = compute(")
                    .append(i)
                    .append(");\n");
        }
        large.append("}\n");

        try (LlamaCppJniAiGenerationProvider provider =
                new LlamaCppJniAiGenerationProvider(config(4096, 16), promptSupport)) {
            final AiGenerationTimings small = provider.generateWithTimings(request("class A {}"));
            final AiGenerationTimings big = provider.generateWithTimings(request(large.toString()));

            // Real engine timings (not zero-rate default), so the plan gets exact throughput.
            assertThat(small.prefillTokensPerSecond() > 0.0d, is(true));
            assertThat(small.decodeTokensPerSecond() > 0.0d, is(true));
            assertThat(small.promptTokens() > 0, is(true));
            // A clearly larger, distinct prompt processes more prompt tokens -> proves it is the real
            // generation path (not a discarded/zero-timings one).
            assertThat(big.promptTokens() > small.promptTokens(), is(true));
            // Both requests share the same system prompt, so the second one must find that prefix already
            // in the KV cache. This is the end-to-end proof that the prefix-reuse settings (cachePrompt /
            // cacheReuse / swaFull) actually engage -- without cache_n surfaced, their KV-memory cost was
            // paid on faith alone.
            assertThat(big.cachedPromptTokens() > 0, is(true));
            assertThat(big.totalPromptTokens() > big.promptTokens(), is(true));
        }
    }
    // </editor-fold>

    @Test
    public void tensorReadLazyMode_mapsEveryDeclaredCliString() {
        // Every mode the binding declares must resolve; a new upstream mode is then covered for free.
        for (final TensorReadLazyMode mode : TensorReadLazyMode.values()) {
            assertThat(LlamaCppJniAiGenerationProvider.tensorReadLazyMode(mode.getArgValue()), is(mode));
        }
    }

    @Test
    public void tensorReadLazyMode_isCaseInsensitive() {
        assertThat(LlamaCppJniAiGenerationProvider.tensorReadLazyMode("ON"), is(TensorReadLazyMode.ON));
        assertThat(LlamaCppJniAiGenerationProvider.tensorReadLazyMode("Auto"), is(TensorReadLazyMode.AUTO));
    }

    @Test
    public void tensorReadLazyMode_rejectsUnknownValueAndNamesTheAcceptedOnes() {
        // A typo must fail loud rather than be dropped, and the message must name what is accepted.
        final IllegalArgumentException thrown = Assertions.assertThrows(
                IllegalArgumentException.class, () -> LlamaCppJniAiGenerationProvider.tensorReadLazyMode("lazy"));
        assertThat(thrown.getMessage(), containsString("lazy"));
        assertThat(thrown.getMessage(), containsString("off, auto, on"));
    }

    @Test
    public void cacheType_mapsEveryDeclaredCliString() {
        // Every cache type the binding declares must resolve; a type upstream adds is covered for free.
        for (final CacheType type : CacheType.values()) {
            assertThat(LlamaCppJniAiGenerationProvider.cacheType("cacheTypeK", type.getArgValue()), is(type));
        }
    }

    @Test
    public void cacheType_isCaseInsensitive() {
        assertThat(LlamaCppJniAiGenerationProvider.cacheType("cacheTypeK", "Q8_0"), is(CacheType.Q8_0));
        assertThat(LlamaCppJniAiGenerationProvider.cacheType("cacheTypeV", "F16"), is(CacheType.F16));
    }

    @Test
    public void cacheType_rejectsUnknownValueAndNamesBothTheKnobAndTheAcceptedOnes() {
        // A typo must fail loud rather than be dropped. The knob name is in the message because the same
        // resolver serves cacheTypeK and cacheTypeV -- without it the user cannot tell which one is wrong.
        final IllegalArgumentException thrown = Assertions.assertThrows(
                IllegalArgumentException.class, () -> LlamaCppJniAiGenerationProvider.cacheType("cacheTypeV", "q3_k"));
        assertThat(thrown.getMessage(), containsString("cacheTypeV"));
        assertThat(thrown.getMessage(), containsString("q3_k"));
        assertThat(thrown.getMessage(), containsString("f32, f16, bf16, q8_0"));
    }
}
