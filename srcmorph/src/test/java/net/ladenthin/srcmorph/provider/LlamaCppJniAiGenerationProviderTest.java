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
import java.util.Collections;
import net.ladenthin.llama.args.TensorReadLazyMode;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
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
        return new LlamaCppJniConfig(
                null,
                MODEL_PATH,
                contextSize,
                maxOutputTokens,
                0.15f,
                8,
                AiGenerationConfig.DEFAULT_TOP_P,
                AiGenerationConfig.DEFAULT_TOP_K,
                AiGenerationConfig.DEFAULT_MIN_P,
                AiGenerationConfig.DEFAULT_TOP_N_SIGMA,
                AiGenerationConfig.DEFAULT_REPEAT_PENALTY,
                AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING,
                AiGenerationConfig.DEFAULT_CACHE_PROMPT,
                AiGenerationConfig.DEFAULT_SWA_FULL,
                AiGenerationConfig.DEFAULT_CACHE_REUSE,
                AiGenerationConfig.DEFAULT_GPU_LAYERS,
                AiGenerationConfig.DEFAULT_SEED,
                AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS,
                AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS,
                AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT,
                AiGenerationConfig.DEFAULT_TENSOR_READ_LAZY,
                AiGenerationConfig.DEFAULT_MAIN_GPU,
                AiGenerationConfig.DEFAULT_DEVICES,
                AiGenerationConfig.DEFAULT_REASONING_EFFORT,
                AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS,
                AiGenerationConfig.DEFAULT_DRY_MULTIPLIER,
                AiGenerationConfig.DEFAULT_DRY_BASE,
                AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH,
                AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N,
                Collections.emptyList(),
                Collections.emptyList());
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
}
