// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.NativeLlamaAvailability;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.document.AiMdHeaderCodec;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives every model-definition knob at a non-default value against the committed model and asserts
 * that a real generation still completes.
 *
 * <p><b>The failure class this exists for.</b> A knob is not exercised by setting it — it is
 * exercised by the native binding <em>accepting</em> the value the provider derives from it. Those
 * two came apart twice: {@code dryPenaltyLastN} was forwarded at its own {@code -1} default and the
 * binding rejects any negative window, so every generation threw before producing a token; and
 * {@code flashAttn} maps onto a setter that cannot express the value llama.cpp now demands. Neither
 * is visible to a mapping unit test — the mapping was doing exactly what it was written to do — and
 * neither is visible to the rest of the suite, which runs on the {@code mock} provider and never
 * loads the native library. Only a real call finds them.</p>
 *
 * <p><b>Why one case per knob rather than one case with everything set.</b> An all-knobs-at-once
 * config would catch the same breakage but not say which knob caused it, and one rejected value
 * would mask every knob behind it. Per-knob cases cost one model load each (the committed model is
 * ~90&nbsp;MB and the budget here is a handful of output tokens, so a case is a few seconds) and
 * name the culprit in the test name.</p>
 *
 * <p><b>No silent gaps.</b> {@link #everyKnob_isSweptOrExplicitlyExcluded()} reflects over
 * {@link LlamaCppJniConfig}'s fields and fails when a knob is neither swept nor listed in
 * {@link #NOT_SWEPT} with a reason. A knob added later therefore reds this class until somebody
 * decides how it is covered, which is the part a hand-maintained list cannot promise.</p>
 */
public class LlamaCppJniKnobSweepTest {

    /** Context window for the sweep: large enough for the fixture prompt, small enough to load fast. */
    private static final int SWEEP_CONTEXT_SIZE = 1024;

    /** Output budget for the sweep. The assertion is "the binding accepted this", not "the model is good". */
    private static final int SWEEP_MAX_OUTPUT_TOKENS = 8;

    /** Threads for the sweep; a CI runner is not the place to spawn eight of them per case. */
    private static final int SWEEP_THREADS = 2;

    /** Source text handed to every case — deliberately tiny, the prompt content is not what is under test. */
    private static final String SWEEP_SOURCE = "package com.example;\npublic class Test {}\n";

    private static final AiMdHeader HEADER = new AiMdHeader(
            "Test.java",
            AiMdHeaderCodec.HEADER_VERSION_1_0,
            "00000000",
            "2026-03-18T00:00:00Z",
            "2026-03-18T00:00:00Z",
            "0.1.0-SNAPSHOT",
            "0.0.0",
            AiMdHeaderCodec.NODE_TYPE_FILE);

    /**
     * Knobs that are deliberately not swept, each with the reason it cannot be.
     *
     * <p>Keys are {@link LlamaCppJniConfig} field names; the completeness check below reads this map,
     * so an entry here is a documented decision rather than an omission.</p>
     */
    private static final Map<String, String> NOT_SWEPT = notSwept();

    private static Map<String, String> notSwept() {
        final Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("modelPath", "identifies the model under test; every case already sets it");
        reasons.put(
                "devices",
                "backend device names (e.g. \"Vulkan1\") exist only on the machine that enumerates them, "
                        + "so no value is valid on an arbitrary runner");
        reasons.put(
                "flashAttn",
                "cannot currently reach the binding at all -- the provider refuses it, which "
                        + "flashAttn_isRefusedRatherThanSilentlyDropped() pins instead");
        return Collections.unmodifiableMap(reasons);
    }

    /**
     * One case per knob: the knob at a value that differs from both its default and the sweep baseline.
     *
     * @return the sweep cases, as (knob name, builder mutation) pairs
     */
    static Stream<Arguments> knobs() {
        return Stream.of(
                knob("contextSize", b -> b.contextSize(2048)),
                knob("maxOutputTokens", b -> b.maxOutputTokens(16)),
                knob("temperature", b -> b.temperature(0.7f)),
                knob("threads", b -> b.threads(1)),
                knob("topP", b -> b.topP(0.5f)),
                knob("topK", b -> b.topK(10)),
                knob("minP", b -> b.minP(0.05f)),
                knob("topNSigma", b -> b.topNSigma(2.0f)),
                knob("repeatPenalty", b -> b.repeatPenalty(1.1f)),
                knob("chatTemplateEnableThinking", b -> b.chatTemplateEnableThinking(false)),
                knob("cachePrompt", b -> b.cachePrompt(false)),
                knob("swaFull", b -> b.swaFull(false)),
                knob("cacheReuse", b -> b.cacheReuse(128)),
                knob("gpuLayers", b -> b.gpuLayers(0)),
                knob("seed", b -> b.seed(42)),
                knob("cpuMoeLayers", b -> b.cpuMoeLayers(0)),
                knob("cpuFfnLayers", b -> b.cpuFfnLayers(0)),
                knob("kvUnifiedPerSlot", b -> b.kvUnifiedPerSlot(SWEEP_CONTEXT_SIZE)),
                knob("tensorReadLazy", b -> b.tensorReadLazy("on")),
                knob("repeatLastN", b -> b.repeatLastN(64)),
                // A quantized K cache works on its own; a quantized V cache generally needs Flash
                // Attention, which the provider currently refuses -- so V is swept at an explicit f16.
                knob("cacheTypeK", b -> b.cacheTypeK("q8_0")),
                knob("cacheTypeV", b -> b.cacheTypeV("f16")),
                knob("batchSize", b -> b.batchSize(256)),
                knob("ubatchSize", b -> b.ubatchSize(128)),
                knob("threadsBatch", b -> b.threadsBatch(1)),
                knob("mainGpu", b -> b.mainGpu(0)),
                knob("reasoningEffort", b -> b.reasoningEffort("high")),
                knob("reasoningBudgetTokens", b -> b.reasoningBudgetTokens(16)),
                knob("dryMultiplier", b -> b.dryMultiplier(0.8f)),
                knob("dryBase", b -> b.dryBase(1.5f)),
                knob("dryAllowedLength", b -> b.dryAllowedLength(3)),
                knob("dryPenaltyLastN", b -> b.dryPenaltyLastN(64)),
                knob("drySequenceBreakers", b -> b.drySequenceBreakers(Arrays.asList("\n", ":"))),
                // A stop string the model cannot emit: the knob must be accepted without also
                // truncating the answer, which would make the non-empty assertion meaningless.
                knob("stopStrings", b -> b.stopStrings(Collections.singletonList("</never-emitted>"))));
    }

    private static Arguments knob(final String name, final Consumer<LlamaCppJniConfig.Builder> mutation) {
        return Arguments.of(name, mutation);
    }

    /** The sweep baseline: everything at its default except the three knobs that bound the cost. */
    private static LlamaCppJniConfig.Builder baseline() {
        return LlamaCppJniConfig.builder(NativeLlamaAvailability.modelPath())
                .contextSize(SWEEP_CONTEXT_SIZE)
                .maxOutputTokens(SWEEP_MAX_OUTPUT_TOKENS)
                .threads(SWEEP_THREADS);
    }

    private static AiGenerationRequest request() {
        return new AiGenerationRequest(
                CommonTestFixtures.PROMPT_KEY_FILE_BODY, Paths.get("Test.java"), SWEEP_SOURCE, HEADER);
    }

    /**
     * Every knob, one at a time, at a non-default value, through a real generation.
     *
     * @param knobName the knob under test, used as the case name
     * @param mutation applies that knob's non-default value to the baseline builder
     * @throws Exception if the provider fails to close
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("knobs")
    public void knob_atNonDefaultValue_stillProducesOutput(
            final String knobName, final Consumer<LlamaCppJniConfig.Builder> mutation) throws Exception {
        NativeLlamaAvailability.assumeAvailable();

        // arrange
        final LlamaCppJniConfig.Builder builder = baseline();
        mutation.accept(builder);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());

        // act
        final String body;
        try (LlamaCppJniAiGenerationProvider provider =
                new LlamaCppJniAiGenerationProvider(builder.build(), promptSupport)) {
            body = provider.generate(request());
        }

        // assert -- the contract is that the binding accepted the value and inference ran, not that
        // the 135M test model said anything sensible with it.
        assertThat("knob " + knobName + " produced no response", body, is(notNullValue()));
        assertThat(
                "knob " + knobName + " produced an empty response", body.trim().isEmpty(), is(false));
    }

    /**
     * The one knob the provider refuses outright, pinned here so the sweep's coverage stays honest.
     *
     * <p>{@code ModelParameters.enableFlashAttn()} is a bare flag, while llama.cpp's
     * {@code --flash-attn} has taken a mandatory {@code on|off|auto} value since b10273 — a valueless
     * emission swallows the following argument. Refusing is the only correct behaviour until the
     * binding grows a value-taking setter; see the TODO on {@code LlamaCppJniAiGenerationProvider} and
     * {@code TODO.md}. When that lands, this test flips into a sweep case above.</p>
     */
    @Test
    public void flashAttn_isRefusedRatherThanSilentlyDropped() {
        NativeLlamaAvailability.assumeAvailable();
        final LlamaCppJniConfig config = baseline().flashAttn(true).build();
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());

        try (LlamaCppJniAiGenerationProvider provider = new LlamaCppJniAiGenerationProvider(config, promptSupport)) {
            final IllegalArgumentException thrown =
                    Assertions.assertThrows(IllegalArgumentException.class, () -> provider.generate(request()));
            assertThat(thrown.getMessage(), is(LlamaCppJniAiGenerationProvider.FLASH_ATTN_UNSUPPORTED_MESSAGE));
        }
    }

    /**
     * Fails when a knob exists that is neither swept nor listed in {@link #NOT_SWEPT}.
     *
     * <p>This is the part that survives the next person: a knob added to {@link LlamaCppJniConfig}
     * without a sweep case is exactly the shape of the two defects above, and a hand-maintained list
     * cannot notice it. Static and synthetic fields are skipped so an instrumented run (JaCoCo adds
     * {@code $jacocoData}) does not read as a missing knob.</p>
     */
    @Test
    public void everyKnob_isSweptOrExplicitlyExcluded() {
        final Set<String> declared = Arrays.stream(LlamaCppJniConfig.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        final Set<String> covered = new LinkedHashSet<>(NOT_SWEPT.keySet());
        knobs().map(a -> (String) a.get()[0]).forEach(covered::add);

        final Set<String> uncovered = new TreeSet<>(declared);
        uncovered.removeAll(covered);
        assertThat(
                "knob(s) with neither a sweep case nor an entry in NOT_SWEPT: " + uncovered,
                uncovered,
                is(Collections.emptySet()));

        // The reverse direction too: a knob removed from the config must not leave a stale case behind,
        // which would keep asserting against a setter nobody calls any more.
        final Set<String> stale = new TreeSet<>(covered);
        stale.removeAll(declared);
        assertThat("sweep case(s) for knob(s) that no longer exist: " + stale, stale, is(Collections.emptySet()));

        // Guards the guard: an empty knob list would satisfy both set comparisons above.
        final List<String> swept = knobs().map(a -> (String) a.get()[0]).collect(Collectors.toList());
        assertThat(swept.size(), is(declared.size() - NOT_SWEPT.size()));
    }
}
