// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.config.AiFactCounter;
import net.ladenthin.srcmorph.config.AiFactExtractor;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.document.AiGenerationResult;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.LlamaCppJniAiGenerationProvider;
import net.ladenthin.srcmorph.provider.LlamaCppJniConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke test of the {@code onOversize=mapReduce} pipeline (chunk &rarr; hierarchical reduce)
 * plus the exact {@code <facts>} block, driven by the <em>real</em> llama.cpp JNI provider and the small
 * bundled test model. Skipped unless the native lib is available and {@code -DrunNativeLlamaTests=true}.
 * Unit tests cover the orchestration with the mock provider; this proves the same path works against a
 * real model (real generation, real prompt-cache reuse, real trimming).
 */
public class AiFieldGenerationSupportRealModelTest {

    private static final String MODEL_PATH = Paths.get("src", "test", "resources", "SmolLM2-135M-Instruct-Q3_K_M.gguf")
            .toAbsolutePath()
            .toString();

    /** Small context so a modest synthetic source is over-window and triggers map-reduce with the tiny model. */
    private static final int SMALL_CONTEXT = 512;

    private static final String MODEL_KEY = "smol";

    private static AiMdHeader header() {
        return new AiMdHeader(
                "Data.java", "1.0", "0", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", "0", "0", "file");
    }

    private static String largeSource(final int lines) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            sb.append("line ").append(i).append(" = value;\n");
        }
        return sb.toString();
    }

    @Test
    public void mapReduceWithFacts_realModel_producesFactsPlusSummary() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("runNativeLlamaTests"),
                "Native llama test disabled. Enable with -DrunNativeLlamaTests=true");
        Assumptions.assumeTrue(Files.exists(Paths.get(MODEL_PATH)), "Model file missing: " + MODEL_PATH);

        final AiModelDefinition def = new AiModelDefinition();
        def.setKey(MODEL_KEY);
        def.setModelPath(MODEL_PATH);
        def.setContextSize(SMALL_CONTEXT);
        def.setMaxOutputTokens(48);
        def.setCharsPerToken(3);
        final AiModelDefinitionSupport models = new AiModelDefinitionSupport(Collections.singletonList(def));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiPromptPreparationSupport prep = new AiPromptPreparationSupport(promptSupport);
        final LlamaCppJniConfig jniConfig = new LlamaCppJniConfig(
                null,
                MODEL_PATH,
                SMALL_CONTEXT,
                48,
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

        final AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
        rule.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        rule.setAiDefinitionKey(MODEL_KEY);
        rule.setOnOversize("mapReduce");
        rule.setMaxChunks(2);
        final AiFactCounter counter = new AiFactCounter();
        counter.setLabel("lines");
        counter.setPattern("(?m)^line ");
        rule.setFacts(Collections.singletonList(counter));

        final int lineCount = 400;
        final String source = largeSource(lineCount);
        final Path contextFile = Files.createTempFile("Data", ".java");

        try (LlamaCppJniAiGenerationProvider provider = new LlamaCppJniAiGenerationProvider(jniConfig, promptSupport)) {
            final AiFieldGenerationSupport support = new AiFieldGenerationSupport(provider, prep, models);
            final AiGenerationResult result = support.processFieldGenerations(
                    Collections.singletonList(rule), contextFile, "file", source, header());

            // The exact facts (counted over the whole source) lead the body; the map-reduced AI summary
            // follows. This proves the real-model chunk -> hierarchical-reduce path completes end-to-end.
            assertThat(result.body(), startsWith(AiFactExtractor.FACTS_HEADER));
            assertThat(result.body(), containsString("lines: " + lineCount));
            assertThat(
                    result.body().length()
                            > AiFactExtractor.factsBlock(rule.getFacts(), source)
                                    .length(),
                    is(true));
        }
    }
}
