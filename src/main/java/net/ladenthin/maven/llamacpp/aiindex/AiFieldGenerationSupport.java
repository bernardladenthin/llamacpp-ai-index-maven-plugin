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
import java.util.List;

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

    private final Log log;
    private final AiGenerationProvider generationProvider;
    private final AiPromptPreparationSupport promptPreparationSupport;

    /**
     * Creates a new {@code AiFieldGenerationSupport}.
     *
     * @param log                      Maven logger for trim warnings and diagnostics
     * @param generationProvider       AI backend used to generate text for each field
     * @param promptPreparationSupport helper that resolves and prepares prompt templates
     */
    public AiFieldGenerationSupport(
            final Log log,
            final AiGenerationProvider generationProvider,
            final AiPromptPreparationSupport promptPreparationSupport
    ) {
        this.log = log;
        this.generationProvider = generationProvider;
        this.promptPreparationSupport = promptPreparationSupport;
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
     * @throws IllegalArgumentException if a field's {@link AiGenerationConfig} is {@code null}
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

            final AiGenerationConfig generationConfig = fieldGeneration.getGeneration();
            if (generationConfig == null) {
                throw new IllegalArgumentException("Missing generation config for body field");
            }

            final AiGenerationRequest request = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    contextFile,
                    sourceText,
                    baseHeader
            );

            final AiPreparedPrompt preparedPrompt = promptPreparationSupport.preparePrompt(
                    request,
                    generationConfig.getMaxInputChars()
            );

            if (preparedPrompt.trimmed() && generationConfig.isWarnOnTrim()) {
                log.warn("Trimmed AI input for " + contextType + TRIM_WARN_FIELD_LABEL + "body': " + contextFile
                        + " (source chars " + preparedPrompt.originalSourceLength()
                        + " -> " + preparedPrompt.trimmedSourceLength()
                        + ", available source chars " + preparedPrompt.availableSourceChars()
                        + ", max input chars " + generationConfig.getMaxInputChars() + ")");
            }

            final AiGenerationRequest generationRequest = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    contextFile,
                    preparedPrompt.sourceText(),
                    baseHeader
            );

            body = generationProvider.generate(generationRequest);

            if (body.isBlank()) {
                final int maxRetries = generationConfig.getMaxRetries();
                // DIAGNOSTIC: Log configuration values on first empty body
                if (maxRetries > 0) {
                    log.warn("DIAGNOSTIC - Temperature config: baseTemp=" + generationConfig.getTemperature()
                            + ", retryIncrement=" + generationConfig.getRetryTemperatureIncrement()
                            + ", maxRetries=" + maxRetries);
                }
                for (int attempt = 1; attempt <= maxRetries && body.isBlank(); attempt++) {
                    // Escalate temperature with each retry to break out of EOS-early failure modes.
                    // Formula: baseTemp + (attempt * increment)
                    // Example with baseTemp=0.4, increment=0.2:
                    // - Attempt 1: 0.4 + (1 × 0.2) = 0.6
                    // - Attempt 2: 0.4 + (2 × 0.2) = 0.8
                    // - Attempt 3: 0.4 + (3 × 0.2) = 1.0
                    final float retryTemperature = generationConfig.getTemperature()
                            + attempt * generationConfig.getRetryTemperatureIncrement();
                    log.info(RETRY_ATTEMPT_INFO_PREFIX + attempt + RETRY_OF_INFIX + maxRetries
                            + RETRY_FIELD_INFIX + fieldGeneration.getPromptKey()
                            + RETRY_TEMPERATURE_INFIX + retryTemperature
                            + " for " + contextFile);
                    body = generationProvider.generate(generationRequest, retryTemperature);
                }
                if (body.isBlank()) {
                    log.warn(EMPTY_OUTPUT_WARN_PREFIX + contextType + TRIM_WARN_FIELD_LABEL + fieldGeneration.getPromptKey() + "': " + contextFile);
                }
            }
        }

        return new AiGenerationResult(body);
    }
}
