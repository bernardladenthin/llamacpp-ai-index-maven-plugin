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

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared field-generation logic used by both {@link SourceFileIndexer} and
 * {@link PackageIndexer}.
 *
 * <p>Iterates over a list of {@link AiFieldGenerationConfig} entries, prepares the
 * prompt for each, delegates generation to the configured {@link AiGenerationProvider},
 * and accumulates the generated text as the document body.
 * A trim warning is emitted via the supplied {@link Log} whenever the source text had
 * to be truncated to fit within the configured maximum input character budget.</p>
 */
public class AiFieldGenerationSupport {

    /**
     * Log message fragment inserted between the context type and the remainder of the
     * trim-warning message, ensuring a consistent sentence structure regardless of whether
     * the context is a file or a package.
     *
     * @see #processFieldGenerations
     */
    private static final String TRIM_WARN_FIELD_LABEL = " field '";

    /**
     * Log message prefix for warnings emitted when the AI provider returns an empty body
     * for a given field, ensuring a consistent sentence structure regardless of context type.
     * An empty response typically indicates that the model produced an EOS token immediately,
     * which can occur at low sampling temperatures for certain input patterns.
     *
     * @see #processFieldGenerations
     */
    private static final String EMPTY_OUTPUT_WARN_PREFIX = "AI provider returned empty body for ";

    /**
     * Log message prefix for info messages emitted at the start of each retry attempt,
     * showing the attempt number and the escalated sampling temperature.
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_ATTEMPT_INFO_PREFIX = "Retrying AI generation (attempt ";

    /**
     * Log message fragment inserted between the attempt index and the maximum retry count
     * in retry-attempt info messages.
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_OF_INFIX = "/";

    /**
     * Log message fragment inserted before the prompt key in retry-attempt info messages.
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_FIELD_INFIX = ") for field '";

    /**
     * Log message fragment inserted before the escalated temperature value in retry-attempt
     * info messages.
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_TEMPERATURE_INFIX = "' temperature=";

    /**
     * Log message suffix appended after temperature data in retry-attempt info messages.
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_CONTEXT_SUFFIX = " for ";

    /**
     * Log message fragment showing the temperature escalation calculation formula in
     * retry-attempt info messages (e.g., " (baseTemp=0.15 + attempt 1 × 0.15)").
     *
     * @see #processFieldGenerations
     */
    private static final String RETRY_TEMPERATURE_CALCULATION_TEMPLATE = " (baseTemp={0} + attempt {1} × {2})";

    /**
     * Separator used to construct the cache key for the computed {@code maxInputChars}
     * per {@code (aiDefinitionKey, promptKey)} pair.
     *
     * @see #computeMaxInputCharsKey
     */
    private static final String CACHE_KEY_SEPARATOR = ":";

    /**
     * Rounding granularity applied when computing the final {@code maxInputChars} value.
     * The available character count is rounded DOWN to the nearest multiple of this value
     * to produce a conservative, human-readable result.
     *
     * @see #calculateAndLogMaxInputChars
     */
    private static final int MAX_INPUT_CHARS_ROUNDING = 100;

    private final Log log;
    private final AiGenerationProvider generationProvider;
    private final AiPromptPreparationSupport promptPreparationSupport;
    private final AiModelDefinitionSupport modelDefinitionSupport;
    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    /**
     * Per-{@code (aiDefinitionKey, promptKey)} cache of the computed {@code maxInputChars}
     * value. Populated on first use so that the calculation and its detailed log output
     * are emitted exactly once per unique pair rather than once per processed file.
     */
    private final Map<String, Integer> maxInputCharsCache = new HashMap<>();

    /**
     * Creates a new {@code AiFieldGenerationSupport}.
     *
     * @param log                      Maven logger for trim warnings and diagnostics
     * @param generationProvider       AI backend used to generate text for each field
     * @param promptPreparationSupport helper that resolves and prepares prompt templates
     * @param modelDefinitionSupport   lookup for {@link AiGenerationConfig} by key
     */
    public AiFieldGenerationSupport(
            final Log log,
            final AiGenerationProvider generationProvider,
            final AiPromptPreparationSupport promptPreparationSupport,
            final AiModelDefinitionSupport modelDefinitionSupport
    ) {
        this.log = log;
        this.generationProvider = generationProvider;
        this.promptPreparationSupport = promptPreparationSupport;
        this.modelDefinitionSupport = modelDefinitionSupport;
    }

    /**
     * Processes each entry in {@code fieldGenerations}, generates the requested AI text,
     * and accumulates the results into an {@link AiGenerationResult}.
     *
     * <p>For each non-null {@link AiFieldGenerationConfig}:</p>
     * <ol>
     *   <li>The prompt is prepared via {@link AiPromptPreparationSupport#preparePrompt}.</li>
     *   <li>A trim warning is logged if the source was truncated and
     *       {@link AiGenerationConfig#isWarnOnTrim()} is {@code true}.</li>
     *   <li>The AI provider generates a value for the trimmed source.</li>
     *   <li>If the provider returns a blank body, up to {@link AiGenerationConfig#getMaxRetries()}
     *       retry attempts are made, each using a temperature of
     *       {@code temperature + attempt * retryTemperatureIncrement} to escape
     *       EOS-early failure modes. Each retry is logged at INFO level.
     *       A warning is only emitted after all retries are exhausted.</li>
     *   <li>The generated value is stored as the document body.</li>
     * </ol>
     *
     * @param fieldGenerations per-field generation configuration list; {@code null} entries
     *                         are silently skipped
     * @param contextFile      path to the source or package file being processed; used in
     *                         prompt requests and log messages
     * @param contextType      human-readable label for the context (e.g. {@code "file"} or
     *                         {@code "package"}); embedded in trim-warning log messages
     * @param sourceText       full source text passed as input to the prompt preparation step
     * @param baseHeader       current header; passed through to each
     *                         {@link AiGenerationRequest}
     * @return an {@link AiGenerationResult} with the generated body; defaults to empty string
     * @throws IOException              if the AI provider throws during generation
     * @throws IllegalArgumentException if a field's {@link AiFieldGenerationConfig#getAiDefinitionKey()}
     *                                  does not match any registered {@link AiModelDefinition}
     */
    public AiGenerationResult processFieldGenerations(
            final List<AiFieldGenerationConfig> fieldGenerations,
            final Path contextFile,
            final String contextType,
            final String sourceText,
            final AiMdHeader baseHeader
    ) throws IOException {
        String body = "";

        for (AiFieldGenerationConfig fieldGeneration : fieldGenerations) {
            if (fieldGeneration == null) {
                continue;
            }

            final AiGenerationConfig generationConfig =
                    modelDefinitionSupport.getConfig(fieldGeneration.getAiDefinitionKey());

            final int effectiveMaxInputChars = resolveMaxInputChars(
                    fieldGeneration, generationConfig, contextFile);

            final AiGenerationRequest request = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    contextFile,
                    sourceText,
                    baseHeader
            );

            final AiPreparedPrompt preparedPrompt = promptPreparationSupport.preparePrompt(
                    request,
                    effectiveMaxInputChars
            );

            if (preparedPrompt.trimmed() && generationConfig.isWarnOnTrim()) {
                log.warn("Trimmed AI input for " + contextType + TRIM_WARN_FIELD_LABEL + "body': " + contextFile
                        + " (source chars " + preparedPrompt.originalSourceLength()
                        + " -> " + preparedPrompt.trimmedSourceLength()
                        + ", available source chars " + preparedPrompt.availableSourceChars()
                        + ", max input chars " + effectiveMaxInputChars + ")");
            }

            final AiGenerationRequest generationRequest = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    contextFile,
                    preparedPrompt.sourceText(),
                    baseHeader
            );

            log.info("Generating field '" + fieldGeneration.getPromptKey() +
                    "' with temperature=" + generationConfig.getTemperature() +
                    ", maxRetries=" + generationConfig.getMaxRetries() +
                    ", retryTemperatureIncrement=" + generationConfig.getRetryTemperatureIncrement() +
                    ", maxInputChars=" + effectiveMaxInputChars);
            body = generationProvider.generate(generationRequest);

            if (compatibilityHelper.isBlank(body)) {
                final int maxRetries = generationConfig.getMaxRetries();
                for (int attempt = 1; attempt <= maxRetries && compatibilityHelper.isBlank(body); attempt++) {
                    // Escalate temperature with each retry to break out of EOS-early failure modes.
                    // Formula: baseTemp + (attempt * increment)
                    // Example with baseTemp=0.4, increment=0.2:
                    // - Attempt 1: 0.4 + (1 × 0.2) = 0.6
                    // - Attempt 2: 0.4 + (2 × 0.2) = 0.8
                    // - Attempt 3: 0.4 + (3 × 0.2) = 1.0
                    final float retryTemperature = generationConfig.getTemperature()
                            + attempt * generationConfig.getRetryTemperatureIncrement();
                    final String temperatureCalculation = RETRY_TEMPERATURE_CALCULATION_TEMPLATE
                            .replace("{0}", String.valueOf(generationConfig.getTemperature()))
                            .replace("{1}", String.valueOf(attempt))
                            .replace("{2}", String.valueOf(generationConfig.getRetryTemperatureIncrement()));
                    log.info(RETRY_ATTEMPT_INFO_PREFIX + attempt + RETRY_OF_INFIX + maxRetries
                            + RETRY_FIELD_INFIX + fieldGeneration.getPromptKey()
                            + RETRY_TEMPERATURE_INFIX + retryTemperature
                            + temperatureCalculation
                            + RETRY_CONTEXT_SUFFIX + contextFile);
                    body = generationProvider.generate(generationRequest, retryTemperature);
                }
                if (compatibilityHelper.isBlank(body)) {
                    log.warn(EMPTY_OUTPUT_WARN_PREFIX + contextType + TRIM_WARN_FIELD_LABEL + fieldGeneration.getPromptKey() + "': " + contextFile);
                }
            }
        }

        return new AiGenerationResult(body);
    }

    /**
     * Returns the effective {@code maxInputChars} for the given field generation entry.
     *
     * <p>When the {@link AiGenerationConfig#getCharsPerToken()} value is greater than zero,
     * the maximum is computed once per unique {@code (aiDefinitionKey, promptKey)} pair,
     * logged in detail, cached, and reused for all subsequent files. When
     * {@code charsPerToken} is zero, the static {@link AiGenerationConfig#getMaxInputChars()}
     * fallback is returned instead.</p>
     *
     * @param fieldGeneration  the field configuration identifying the definition and prompt keys
     * @param generationConfig the resolved generation config carrying {@code charsPerToken} etc.
     * @param contextFile      a representative file path used to render the base prompt length
     * @return effective maximum input characters for this field generation step
     */
    private int resolveMaxInputChars(
            final AiFieldGenerationConfig fieldGeneration,
            final AiGenerationConfig generationConfig,
            final Path contextFile
    ) {
        if (generationConfig.getCharsPerToken() <= 0) {
            return generationConfig.getMaxInputChars();
        }

        final String cacheKey = computeMaxInputCharsKey(
                fieldGeneration.getAiDefinitionKey(), fieldGeneration.getPromptKey());
        final Integer cached = maxInputCharsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        final int basePromptLength = promptPreparationSupport.getBasePromptLength(
                fieldGeneration.getPromptKey(), contextFile);
        final int computed = calculateAndLogMaxInputChars(generationConfig, basePromptLength);
        maxInputCharsCache.put(cacheKey, computed);
        return computed;
    }

    /**
     * Computes and logs the detailed {@code maxInputChars} calculation for a given
     * generation config and base prompt length.
     *
     * <p>The formula is:</p>
     * <pre>
     *   totalChars     = contextSize × charsPerToken
     *   overheadTotal  = promptChars + eofChars + (maxOutputTokens × charsPerToken) + safetyChars
     *   availableChars = totalChars - overheadTotal
     *   finalChars     = floor(availableChars / 100) × 100   (rounded down conservatively)
     * </pre>
     *
     * <p>All intermediate values are emitted at INFO level so that the calculation is
     * transparent and reproducible from the build log alone.</p>
     *
     * @param config          the resolved generation config supplying {@code contextSize},
     *                        {@code maxOutputTokens}, and {@code charsPerToken}
     * @param basePromptLength character length of the prompt template rendered with empty source
     * @return final (rounded) maximum input character count
     */
    private int calculateAndLogMaxInputChars(
            final AiGenerationConfig config,
            final int basePromptLength
    ) {
        final int contextSize = config.getContextSize();
        final int charsPerToken = config.getCharsPerToken();
        final int maxOutputTokens = config.getMaxOutputTokens();

        final int totalChars = contextSize * charsPerToken;
        final int promptChars = basePromptLength;
        final int eofChars = AiPromptPreparationSupport.EOF_MARKER_LENGTH;
        final int outputChars = maxOutputTokens * charsPerToken;
        final int safetyChars = AiGenerationConfig.DEFAULT_SAFETY_MARGIN_CHARS;
        final int overheadTotal = promptChars + eofChars + outputChars + safetyChars;
        final int availableChars = totalChars - overheadTotal;
        final int finalChars = Math.max(0, (availableChars / MAX_INPUT_CHARS_ROUNDING) * MAX_INPUT_CHARS_ROUNDING);

        log.info("Maximum input characters for source code before trimming. Calculated as: (context_size x " + charsPerToken + ") - overhead");
        log.info("  Context: " + contextSize + " tokens");
        log.info("  Chars per token: ~" + charsPerToken);
        log.info("  [Total available]: " + contextSize + " x " + charsPerToken + " = " + totalChars + " chars");
        log.info("  Overhead (conservative estimate):");
        log.info("  - Prompt template: ~" + promptChars + " chars");
        log.info("  - EOF marker: ~" + eofChars + " chars");
        log.info("  - Max output (" + maxOutputTokens + " tokens x " + charsPerToken + "): ~" + outputChars + " chars");
        log.info("  - Safety margin: ~" + safetyChars + " chars");
        log.info("  - [Subtotal overhead]: ~" + overheadTotal + " chars");
        log.info("  Available for source: [Total available] - [Subtotal overhead]");
        log.info("  Available for source: " + totalChars + " - " + overheadTotal + " = " + availableChars + " chars");
        log.info("  Set to: " + finalChars + " (rounded, conservative)");

        return finalChars;
    }

    /**
     * Builds the cache key for the computed {@code maxInputChars} per
     * {@code (aiDefinitionKey, promptKey)} pair.
     *
     * @param aiDefinitionKey the AI model definition key
     * @param promptKey       the prompt template key
     * @return a unique composite cache key
     */
    private String computeMaxInputCharsKey(final String aiDefinitionKey, final String promptKey) {
        return aiDefinitionKey + CACHE_KEY_SEPARATOR + promptKey;
    }
}
