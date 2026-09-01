// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import net.ladenthin.llama.args.CacheType;
import net.ladenthin.llama.args.LazyMode;
import net.ladenthin.llama.value.ChatChoice;
import net.ladenthin.llama.value.ChatMessage;
import net.ladenthin.llama.value.ChatResponse;
import net.ladenthin.llama.value.Usage;
import net.ladenthin.srcmorph.CommonTestFixtures;
import net.ladenthin.srcmorph.NativeLlamaAvailability;
import net.ladenthin.srcmorph.document.AiGenerationRequest;
import net.ladenthin.srcmorph.document.AiMdHeader;
import net.ladenthin.srcmorph.document.AiMdHeaderCodec;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class LlamaCppJniAiGenerationProviderTest {

    private static final String MODEL_PATH = NativeLlamaAvailability.modelPath();

    /**
     * JSON key of the DRY penalty window as the binding renders it. Quoted so the assertions cannot
     * accidentally match a different key that merely contains this one as a substring.
     */
    private static final String PARAM_DRY_PENALTY_LAST_N = "\"dry_penalty_last_n\"";

    /** JSON key of the repeat-penalty window, quoted for the same reason. */
    private static final String PARAM_REPEAT_LAST_N = "\"repeat_last_n\"";

    /** Chat-template kwarg key for Qwen-style thinking, as the provider spells it. */
    private static final String KWARG_ENABLE_THINKING = "enable_thinking";

    /** Chat-template kwarg key for the gpt-oss reasoning-effort level. */
    private static final String KWARG_REASONING_EFFORT = "reasoning_effort";

    private static final AiMdHeader HEADER = new AiMdHeader(
            "Test.java",
            AiMdHeaderCodec.HEADER_VERSION_1_0,
            "00000000",
            "2026-03-18T00:00:00Z",
            "2026-03-18T00:00:00Z",
            "0.1.0-SNAPSHOT",
            "0.0.0",
            AiMdHeaderCodec.NODE_TYPE_FILE);

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
        NativeLlamaAvailability.assumeAvailable();
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
        NativeLlamaAvailability.assumeAvailable();
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
    public void lazyMode_mapsEveryDeclaredCliString() {
        // Every mode the binding declares must resolve; a new upstream mode is then covered for free.
        for (final LazyMode mode : LazyMode.values()) {
            assertThat(LlamaCppJniAiGenerationProvider.lazyMode(mode.getArgValue()), is(mode));
        }
    }

    @Test
    public void lazyMode_isCaseInsensitive() {
        assertThat(LlamaCppJniAiGenerationProvider.lazyMode("ON"), is(LazyMode.ON));
        assertThat(LlamaCppJniAiGenerationProvider.lazyMode("Auto"), is(LazyMode.AUTO));
    }

    @Test
    public void lazyMode_rejectsUnknownValueAndNamesTheAcceptedOnes() {
        // A typo must fail loud rather than be dropped, and the message must name what is accepted.
        final IllegalArgumentException thrown = Assertions.assertThrows(
                IllegalArgumentException.class, () -> LlamaCppJniAiGenerationProvider.lazyMode("lazy"));
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

    // <editor-fold defaultstate="collapsed" desc="buildInferenceParameters: negative-sentinel guards">

    /**
     * Builds a provider over a path that does not exist. The model is loaded lazily on the first
     * generate, and {@code buildInferenceParameters} never touches it, so these tests need no GGUF and
     * no native library -- which is the point: the regression they guard shipped twice while every
     * model-free gate stayed green.
     */
    private static LlamaCppJniAiGenerationProvider providerWith(final LlamaCppJniConfig config) {
        return new LlamaCppJniAiGenerationProvider(
                config, new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions()));
    }

    /**
     * The regression itself. Both penalty windows default to {@code -1}, and the binding rejects any
     * negative window outright ({@code IllegalArgumentException}) because llama.cpp b10273 dropped
     * "{@code -1} = context size". Forwarding one unguarded therefore throws before a single token is
     * produced -- which is exactly what {@code dry_penalty_last_n} did in 1.1.0 and 1.1.1.
     */
    @Test
    public void buildInferenceParameters_defaultConfig_doesNotThrowOnTheNegativeSentinels() {
        // arrange
        final LlamaCppJniConfig defaults =
                LlamaCppJniConfig.builder("/does/not/exist.gguf").build();

        // act / assert
        Assertions.assertDoesNotThrow(() -> providerWith(defaults).buildInferenceParameters(request("class A {}")));
    }

    /** A sentinel means "say nothing", so llama.cpp keeps its own window -- it must not be sent as -1. */
    @Test
    public void buildInferenceParameters_defaultConfig_sendsNeitherPenaltyWindow() {
        // arrange
        final LlamaCppJniConfig defaults =
                LlamaCppJniConfig.builder("/does/not/exist.gguf").build();

        // act
        final String json = providerWith(defaults)
                .buildInferenceParameters(request("class A {}"))
                .toString();

        // assert
        assertThat(json, not(containsString(PARAM_DRY_PENALTY_LAST_N)));
        assertThat(json, not(containsString(PARAM_REPEAT_LAST_N)));
    }

    /**
     * {@code 0} is a real value, not a second sentinel: it disables the penalty. A guard written as
     * {@code > 0} would swallow it, so this pins the boundary from the other side.
     */
    @Test
    public void buildInferenceParameters_zeroWindows_sendsBothAsZero() {
        // arrange
        final LlamaCppJniConfig zeroed = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .dryPenaltyLastN(0)
                .repeatLastN(0)
                .build();

        // act
        final String json = providerWith(zeroed)
                .buildInferenceParameters(request("class A {}"))
                .toString();

        // assert
        assertThat(json, containsString(PARAM_DRY_PENALTY_LAST_N));
        assertThat(json, containsString(PARAM_REPEAT_LAST_N));
    }

    /** A configured positive window reaches the request unchanged. */
    @Test
    public void buildInferenceParameters_configuredWindows_arePassedThrough() {
        // arrange
        final LlamaCppJniConfig configured = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .dryPenaltyLastN(64)
                .repeatLastN(128)
                .build();

        // act
        final String json = providerWith(configured)
                .buildInferenceParameters(request("class A {}"))
                .toString();

        // assert -- distinct values, so a transposition of the two guards fails rather than cancelling out
        assertThat(json, containsString(PARAM_DRY_PENALTY_LAST_N + ": 64"));
        assertThat(json, containsString(PARAM_REPEAT_LAST_N + ": 128"));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="warnOnTruncatedAnswer / logPromptCacheReuse">

    /** Builds a response carrying just the one field each of these two helpers reads. */
    private static ChatResponse responseWith(final String finishReason, final Usage usage) {
        final ChatChoice choice = new ChatChoice(0, new ChatMessage("assistant", "body"), finishReason);
        return new ChatResponse("id", Collections.singletonList(choice), usage, null, "{}");
    }

    private static ListAppender<ILoggingEvent> attachAppender(final Level level) {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        final Logger logger = (Logger) LoggerFactory.getLogger(LlamaCppJniAiGenerationProvider.class);
        logger.setLevel(level);
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(final ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(LlamaCppJniAiGenerationProvider.class)).detachAppender(appender);
        appender.stop();
    }

    /**
     * The whole point of the feature: {@code length} means the model hit the output budget and stopped
     * mid-sentence, so the {@code .ai.md} it produced is incomplete and the user has to be told.
     */
    @Test
    public void warnOnTruncatedAnswer_finishReasonLength_warnsAndNamesTheFileAndTheBudget() {
        // arrange
        final ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        try {
            final LlamaCppJniAiGenerationProvider provider =
                    providerWith(LlamaCppJniConfig.builder("/does/not/exist.gguf")
                            .maxOutputTokens(128)
                            .build());

            // act
            provider.warnOnTruncatedAnswer(request("class A {}"), responseWith("length", new Usage(1, 1)));

            // assert
            assertThat(appender.list.size(), is(1));
            final String message = appender.list.get(0).getFormattedMessage();
            assertThat(message, containsString("Test.java"));
            assertThat(message, containsString("128"));
        } finally {
            detachAppender(appender);
        }
    }

    /**
     * A normal completion must stay silent. This is the assertion that pins the {@code "length"}
     * literal: {@code StopReason.fromStopType("length")} is {@code NONE}, so comparing against the
     * wrong vocabulary would make the warning fire never or always -- silently, either way.
     */
    @Test
    public void warnOnTruncatedAnswer_finishReasonStop_saysNothing() {
        // arrange
        final ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        try {
            final LlamaCppJniAiGenerationProvider provider = providerWith(
                    LlamaCppJniConfig.builder("/does/not/exist.gguf").build());

            // act
            provider.warnOnTruncatedAnswer(request("class A {}"), responseWith("stop", new Usage(1, 1)));

            // assert
            assertThat(appender.list.isEmpty(), is(true));
        } finally {
            detachAppender(appender);
        }
    }

    /** No choices means nothing to judge; reading {@code get(0)} anyway would throw. */
    @Test
    public void warnOnTruncatedAnswer_noChoices_saysNothing() {
        // arrange
        final ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        try {
            final LlamaCppJniAiGenerationProvider provider = providerWith(
                    LlamaCppJniConfig.builder("/does/not/exist.gguf").build());
            final ChatResponse empty = new ChatResponse("id", Collections.emptyList(), new Usage(1, 1), null, "{}");

            // act
            provider.warnOnTruncatedAnswer(request("class A {}"), empty);

            // assert
            assertThat(appender.list.isEmpty(), is(true));
        } finally {
            detachAppender(appender);
        }
    }

    /** The cache line reports all three counts, so a transposition of two of them is visible. */
    @Test
    public void logPromptCacheReuse_debugEnabled_reportsCachedTotalAndGenerated() {
        // arrange
        final ListAppender<ILoggingEvent> appender = attachAppender(Level.DEBUG);
        try {
            final LlamaCppJniAiGenerationProvider provider = providerWith(
                    LlamaCppJniConfig.builder("/does/not/exist.gguf").build());

            // act -- distinct values so each lands in its own placeholder
            provider.logPromptCacheReuse(request("class A {}"), responseWith("stop", new Usage(70, 11, 33)));

            // assert
            assertThat(appender.list.size(), is(1));
            final String message = appender.list.get(0).getFormattedMessage();
            assertThat(message, containsString("33 of 70 prompt token(s)"));
            assertThat(message, containsString("11 generated"));
        } finally {
            detachAppender(appender);
        }
    }

    /** At INFO the line must not be built at all -- it is one log line per indexed file. */
    @Test
    public void logPromptCacheReuse_debugDisabled_saysNothing() {
        // arrange
        final ListAppender<ILoggingEvent> appender = attachAppender(Level.INFO);
        try {
            final LlamaCppJniAiGenerationProvider provider = providerWith(
                    LlamaCppJniConfig.builder("/does/not/exist.gguf").build());

            // act
            provider.logPromptCacheReuse(request("class A {}"), responseWith("stop", new Usage(70, 11, 33)));

            // assert
            assertThat(appender.list.isEmpty(), is(true));
        } finally {
            detachAppender(appender);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="buildChatTemplateKwargs: opt-in kwargs">

    /**
     * The point of the tri-state. While {@code chatTemplateEnableThinking} was a plain
     * {@code boolean} defaulting to {@code true}, every run handed {@code enable_thinking} to the
     * chat template -- including templates that have never heard of it, which llama.cpp's Jinja
     * layer has been moving from "silently ignored" toward "warned about". Unset must now mean
     * "say nothing", so the template's own default applies.
     *
     * <p>The sibling kwarg is deliberately not asserted away here: {@code reasoningEffort} does
     * default to {@code "low"} and is therefore still sent by a default run, but unlike
     * {@code enable_thinking} it has an escape that means exactly "unset" -- the empty string -- so a
     * non-gpt-oss user can switch it off without picking a value that says something else. That is a
     * documented choice, not the same defect; the next test pins that escape.</p>
     */
    @Test
    public void buildChatTemplateKwargs_defaultConfig_doesNotSendEnableThinking() {
        // arrange
        final LlamaCppJniConfig defaults =
                LlamaCppJniConfig.builder("/does/not/exist.gguf").build();

        // act
        final Map<String, String> kwargs = providerWith(defaults).buildChatTemplateKwargs();

        // assert
        assertThat(kwargs.containsKey(KWARG_ENABLE_THINKING), is(false));
    }

    /** The other half of "unset means unset": a blank reasoning effort omits its kwarg too. */
    @Test
    public void buildChatTemplateKwargs_blankReasoningEffort_sendsNoKwargAtAll() {
        // arrange
        final LlamaCppJniConfig blank = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .reasoningEffort("")
                .build();

        // act
        final Map<String, String> kwargs = providerWith(blank).buildChatTemplateKwargs();

        // assert
        assertThat(kwargs.isEmpty(), is(true));
    }

    /**
     * {@code true} is a real configured value, not a second spelling of "unset". A guard written as
     * "send it only when false" would swallow it, so this pins the boundary from the other side --
     * the same shape as the {@code 0}-versus-{@code -1} penalty-window pair above.
     */
    @Test
    public void buildChatTemplateKwargs_thinkingSetToTrue_isStillSent() {
        // arrange
        final LlamaCppJniConfig enabled = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .chatTemplateEnableThinking(Boolean.TRUE)
                .build();

        // act
        final Map<String, String> kwargs = providerWith(enabled).buildChatTemplateKwargs();

        // assert
        assertThat(kwargs.get(KWARG_ENABLE_THINKING), is("true"));
    }

    /** The Gemma-4 case the knob exists for: suppress the thinking block at the Jinja level. */
    @Test
    public void buildChatTemplateKwargs_thinkingSetToFalse_isSentAsFalse() {
        // arrange
        final LlamaCppJniConfig disabled = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .chatTemplateEnableThinking(Boolean.FALSE)
                .build();

        // act
        final Map<String, String> kwargs = providerWith(disabled).buildChatTemplateKwargs();

        // assert
        assertThat(kwargs.get(KWARG_ENABLE_THINKING), is("false"));
    }

    /**
     * The second kwarg was already opt-in (blank omits it); pinned here so the extraction of
     * {@code buildChatTemplateKwargs} out of {@code model()} cannot drop it unnoticed.
     */
    @Test
    public void buildChatTemplateKwargs_reasoningEffortConfigured_isSentAlongside() {
        // arrange
        final LlamaCppJniConfig configured = LlamaCppJniConfig.builder("/does/not/exist.gguf")
                .reasoningEffort("high")
                .chatTemplateEnableThinking(Boolean.FALSE)
                .build();

        // act
        final Map<String, String> kwargs = providerWith(configured).buildChatTemplateKwargs();

        // assert
        assertThat(kwargs.get(KWARG_REASONING_EFFORT), is("high"));
        assertThat(kwargs.get(KWARG_ENABLE_THINKING), is("false"));
    }

    // </editor-fold>

    // </editor-fold>
}
