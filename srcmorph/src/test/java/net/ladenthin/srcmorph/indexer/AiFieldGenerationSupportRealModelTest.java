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
import java.util.Collections;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.NativeLlamaAvailability;
import net.ladenthin.srcmorph.config.AiFactCounter;
import net.ladenthin.srcmorph.config.AiFactExtractor;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.document.AiGenerationResult;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.LlamaCppJniAiGenerationProvider;
import net.ladenthin.srcmorph.provider.LlamaCppJniConfig;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke test of the {@code onOversize=mapReduce} pipeline (chunk &rarr; hierarchical reduce)
 * plus the exact {@code <facts>} block, driven by the <em>real</em> llama.cpp JNI provider and the small
 * bundled test model. Runs by default; skipped only when the native library or the model is missing
 * (see {@link net.ladenthin.srcmorph.NativeLlamaAvailability}).
 * Unit tests cover the orchestration with the mock provider; this proves the same path works against a
 * real model (real generation, real prompt-cache reuse, real trimming).
 */
public class AiFieldGenerationSupportRealModelTest {

    private static final String MODEL_PATH = NativeLlamaAvailability.modelPath();

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
        NativeLlamaAvailability.assumeAvailable();

        final AiModelDefinition def = new AiModelDefinition();
        def.setKey(MODEL_KEY);
        def.setModelPath(MODEL_PATH);
        def.setContextSize(SMALL_CONTEXT);
        def.setMaxOutputTokens(48);
        def.setCharsPerToken(3);
        final AiModelDefinitionSupport models = new AiModelDefinitionSupport(Collections.singletonList(def));

        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiPromptPreparationSupport prep = new AiPromptPreparationSupport(promptSupport);
        final LlamaCppJniConfig jniConfig = LlamaCppJniConfig.builder(MODEL_PATH)
                .contextSize(SMALL_CONTEXT)
                .maxOutputTokens(48)
                .temperature(0.15f)
                .threads(8)
                .build();

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
