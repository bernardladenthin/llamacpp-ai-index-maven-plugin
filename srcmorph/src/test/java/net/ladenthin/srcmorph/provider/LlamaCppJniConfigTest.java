// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link LlamaCppJniConfig}'s own constructor contract.
 *
 * <p>{@link LlamaCppJniConfigFactoryTest} exercises this class only through the factory, which never
 * passes a {@code null} list (the {@link AiGenerationConfig} setters normalise that away first). The
 * constructor's own null guards and its {@code modelPath} requirement are therefore only reachable
 * from here. That matters beyond coverage bookkeeping: the class carries
 * {@link net.ladenthin.srcmorph.support.ConvertToRecord} for a future Java&nbsp;17+ migration, and a
 * canonical-record rewrite is exactly where a normalising constructor body gets silently dropped.
 */
public class LlamaCppJniConfigTest {

    // <editor-fold defaultstate="collapsed" desc="fixture">

    /** Builds a config carrying the given lists; every other value stays at its default. */
    private static LlamaCppJniConfig configWithLists(
            final @Nullable List<String> drySequenceBreakers, final @Nullable List<String> stopStrings) {
        return LlamaCppJniConfig.builder("model.gguf")
                .drySequenceBreakers(drySequenceBreakers)
                .stopStrings(stopStrings)
                .build();
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="null-list normalisation">

    @Test
    public void constructor_nullDrySequenceBreakers_normalisedToEmptyList() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(null, Arrays.asList("<end>"));

        // assert
        assertThat(config.drySequenceBreakers(), is(Collections.<String>emptyList()));
    }

    @Test
    public void constructor_nullStopStrings_normalisedToEmptyList() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n"), null);

        // assert
        assertThat(config.stopStrings(), is(Collections.<String>emptyList()));
    }

    @Test
    public void constructor_nonNullLists_areKept() {
        // arrange / act
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n", "."), Arrays.asList("<end>"));

        // assert
        assertThat(config.drySequenceBreakers(), is(Arrays.asList("\n", ".")));
        assertThat(config.stopStrings(), is(Arrays.asList("<end>")));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="accessor views and required arguments">

    @Test
    public void drySequenceBreakers_returnsUnmodifiableView() {
        // arrange
        final LlamaCppJniConfig config = configWithLists(Arrays.asList("\n"), Collections.<String>emptyList());

        // act / assert
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> config.drySequenceBreakers().add("x"));
    }

    @Test
    public void stopStrings_returnsUnmodifiableView() {
        // arrange
        final LlamaCppJniConfig config = configWithLists(Collections.<String>emptyList(), Arrays.asList("<end>"));

        // act / assert
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> config.stopStrings().add("x"));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="builder">

    /**
     * The builder's whole point: a value the caller never named is the matching
     * {@code AiGenerationConfig.DEFAULT_*}, not Java's zero. This is the single test standing between
     * {@code LlamaCppJniConfigFactory.fromFallbackParameters} -- which no longer restates any default --
     * and a knob silently arriving as {@code 0}/{@code false}/{@code null} instead of what
     * {@code AiGenerationConfig} says it should be.
     */
    @Test
    public void builder_leavesEveryUnsetValueAtItsAiGenerationConfigDefault() {
        // act -- name nothing but the one required value
        final LlamaCppJniConfig config = LlamaCppJniConfig.builder("model.gguf").build();

        // assert
        assertThat(config.modelPath(), is("model.gguf"));
        assertThat(config.contextSize(), is(AiGenerationConfig.DEFAULT_CONTEXT_SIZE));
        assertThat(config.maxOutputTokens(), is(AiGenerationConfig.DEFAULT_MAX_OUTPUT_TOKENS));
        assertThat(config.temperature(), is(AiGenerationConfig.DEFAULT_TEMPERATURE));
        assertThat(config.threads(), is(AiGenerationConfig.DEFAULT_THREADS));
        assertThat(config.topP(), is(AiGenerationConfig.DEFAULT_TOP_P));
        assertThat(config.topK(), is(AiGenerationConfig.DEFAULT_TOP_K));
        assertThat(config.minP(), is(AiGenerationConfig.DEFAULT_MIN_P));
        assertThat(config.topNSigma(), is(AiGenerationConfig.DEFAULT_TOP_N_SIGMA));
        assertThat(config.repeatPenalty(), is(AiGenerationConfig.DEFAULT_REPEAT_PENALTY));
        assertThat(config.chatTemplateEnableThinking(), is(AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING));
        assertThat(config.cachePrompt(), is(AiGenerationConfig.DEFAULT_CACHE_PROMPT));
        assertThat(config.swaFull(), is(AiGenerationConfig.DEFAULT_SWA_FULL));
        assertThat(config.cacheReuse(), is(AiGenerationConfig.DEFAULT_CACHE_REUSE));
        assertThat(config.gpuLayers(), is(AiGenerationConfig.DEFAULT_GPU_LAYERS));
        assertThat(config.seed(), is(AiGenerationConfig.DEFAULT_SEED));
        assertThat(config.cpuMoeLayers(), is(AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS));
        assertThat(config.cpuFfnLayers(), is(AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS));
        assertThat(config.kvUnifiedPerSlot(), is(AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT));
        assertThat(config.lazyMode(), is(AiGenerationConfig.DEFAULT_LAZY_MODE));
        assertThat(config.repeatLastN(), is(AiGenerationConfig.DEFAULT_REPEAT_LAST_N));
        assertThat(config.cacheTypeK(), is(AiGenerationConfig.DEFAULT_CACHE_TYPE_K));
        assertThat(config.cacheTypeV(), is(AiGenerationConfig.DEFAULT_CACHE_TYPE_V));
        assertThat(config.flashAttn(), is(AiGenerationConfig.DEFAULT_FLASH_ATTN));
        assertThat(config.batchSize(), is(AiGenerationConfig.DEFAULT_BATCH_SIZE));
        assertThat(config.ubatchSize(), is(AiGenerationConfig.DEFAULT_UBATCH_SIZE));
        assertThat(config.threadsBatch(), is(AiGenerationConfig.DEFAULT_THREADS_BATCH));
        assertThat(config.mainGpu(), is(AiGenerationConfig.DEFAULT_MAIN_GPU));
        assertThat(config.devices(), is(AiGenerationConfig.DEFAULT_DEVICES));
        assertThat(config.reasoningEffort(), is(AiGenerationConfig.DEFAULT_REASONING_EFFORT));
        assertThat(config.reasoningBudgetTokens(), is(AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS));
        assertThat(config.dryMultiplier(), is(AiGenerationConfig.DEFAULT_DRY_MULTIPLIER));
        assertThat(config.dryBase(), is(AiGenerationConfig.DEFAULT_DRY_BASE));
        assertThat(config.dryAllowedLength(), is(AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH));
        assertThat(config.dryPenaltyLastN(), is(AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N));
        assertThat(config.drySequenceBreakers(), is(Collections.<String>emptyList()));
        assertThat(config.stopStrings(), is(Collections.<String>emptyList()));
    }

    /**
     * Every setter must write its own field and no other. With thirty-six of them, several sharing a
     * type and sitting next to each other, a copy-paste slip is the realistic failure -- so each value
     * here is distinct within its type, and the two booleans that used to be adjacent in the old
     * positional constructor ({@code swaFull}, {@code flashAttn}) are given opposite values.
     */
    @Test
    public void builder_everySetterWritesItsOwnField() {
        // act
        final LlamaCppJniConfig config = LlamaCppJniConfig.builder("model.gguf")
                .contextSize(1111)
                .maxOutputTokens(222)
                .temperature(0.33f)
                .threads(4)
                .topP(0.55f)
                .topK(66)
                .minP(0.07f)
                .topNSigma(0.88f)
                .repeatPenalty(1.09f)
                .chatTemplateEnableThinking(false)
                .cachePrompt(false)
                .swaFull(false)
                .cacheReuse(101)
                .gpuLayers(12)
                .seed(12345)
                .cpuMoeLayers(24)
                .cpuFfnLayers(16)
                .kvUnifiedPerSlot(4096)
                .lazyMode("on")
                .repeatLastN(128)
                .cacheTypeK("q8_0")
                .cacheTypeV("q4_0")
                .flashAttn(true)
                .batchSize(512)
                .ubatchSize(256)
                .threadsBatch(6)
                .mainGpu(3)
                .devices("Vulkan1")
                .reasoningEffort("high")
                .reasoningBudgetTokens(512)
                .dryMultiplier(0.5f)
                .dryBase(1.3f)
                .dryAllowedLength(7)
                .dryPenaltyLastN(999)
                .drySequenceBreakers(Arrays.asList("\n", "."))
                .stopStrings(Arrays.asList("<end>"))
                .build();

        // assert
        assertThat(config.modelPath(), is("model.gguf"));
        assertThat(config.contextSize(), is(1111));
        assertThat(config.maxOutputTokens(), is(222));
        assertThat(config.temperature(), is(0.33f));
        assertThat(config.threads(), is(4));
        assertThat(config.topP(), is(0.55f));
        assertThat(config.topK(), is(66));
        assertThat(config.minP(), is(0.07f));
        assertThat(config.topNSigma(), is(0.88f));
        assertThat(config.repeatPenalty(), is(1.09f));
        assertThat(config.chatTemplateEnableThinking(), is(false));
        assertThat(config.cachePrompt(), is(false));
        assertThat(config.swaFull(), is(false));
        assertThat(config.cacheReuse(), is(101));
        assertThat(config.gpuLayers(), is(12));
        assertThat(config.seed(), is(12345));
        assertThat(config.cpuMoeLayers(), is(24));
        assertThat(config.cpuFfnLayers(), is(16));
        assertThat(config.kvUnifiedPerSlot(), is(4096));
        assertThat(config.lazyMode(), is("on"));
        assertThat(config.repeatLastN(), is(128));
        assertThat(config.cacheTypeK(), is("q8_0"));
        assertThat(config.cacheTypeV(), is("q4_0"));
        assertThat(config.flashAttn(), is(true));
        assertThat(config.batchSize(), is(512));
        assertThat(config.ubatchSize(), is(256));
        assertThat(config.threadsBatch(), is(6));
        assertThat(config.mainGpu(), is(3));
        assertThat(config.devices(), is("Vulkan1"));
        assertThat(config.reasoningEffort(), is("high"));
        assertThat(config.reasoningBudgetTokens(), is(512));
        assertThat(config.dryMultiplier(), is(0.5f));
        assertThat(config.dryBase(), is(1.3f));
        assertThat(config.dryAllowedLength(), is(7));
        assertThat(config.dryPenaltyLastN(), is(999));
        assertThat(config.drySequenceBreakers(), is(Arrays.asList("\n", ".")));
        assertThat(config.stopStrings(), is(Arrays.asList("<end>")));
    }

    /**
     * {@code null} on a String setter restores that knob's default rather than storing {@code null}, so
     * an accessor can never hand back {@code null}. Note the default is not always empty:
     * {@code reasoningEffort} defaults to {@code "low"}, which is what distinguishes "restores the
     * default" from "clears to empty".
     */
    @Test
    public void builder_nullOnAStringSetter_restoresThatKnobsDefault() {
        // arrange -- set them all first, so the null is genuinely undoing something
        final LlamaCppJniConfig config = LlamaCppJniConfig.builder("model.gguf")
                .lazyMode("on")
                .cacheTypeK("q8_0")
                .cacheTypeV("q4_0")
                .devices("Vulkan1")
                .reasoningEffort("high")
                .lazyMode(null)
                .cacheTypeK(null)
                .cacheTypeV(null)
                .devices(null)
                .reasoningEffort(null)
                .build();

        // assert
        assertThat(config.lazyMode(), is(""));
        assertThat(config.cacheTypeK(), is(""));
        assertThat(config.cacheTypeV(), is(""));
        assertThat(config.devices(), is(""));
        assertThat(config.reasoningEffort(), is(AiGenerationConfig.DEFAULT_REASONING_EFFORT));
        assertThat(config.reasoningEffort(), is("low"));
    }

    /** {@code modelPath} is the one value with no default, so it is rejected at the entry point. */
    @Test
    public void builder_nullModelPath_isRejectedAtTheEntryPoint() {
        Assertions.assertThrows(NullPointerException.class, () -> LlamaCppJniConfig.builder(null));
    }

    /** A builder is reusable: {@code build()} snapshots it, so a later setter cannot reach back. */
    @Test
    public void build_snapshotsTheBuilder_soLaterSettersDoNotMutateAnAlreadyBuiltConfig() {
        // arrange
        final LlamaCppJniConfig.Builder builder =
                LlamaCppJniConfig.builder("model.gguf").contextSize(1111);
        final LlamaCppJniConfig first = builder.build();

        // act
        final LlamaCppJniConfig second = builder.contextSize(2222).build();

        // assert
        assertThat(first.contextSize(), is(1111));
        assertThat(second.contextSize(), is(2222));
    }

    // </editor-fold>
}
