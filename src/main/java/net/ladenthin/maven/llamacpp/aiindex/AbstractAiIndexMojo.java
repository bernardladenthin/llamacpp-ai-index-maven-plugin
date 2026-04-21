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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all AI index mojos. Centralises the parameters shared by every goal
 * ({@code generate}, {@code summarize-files}, {@code summarize-packages},
 * {@code aggregate-packages}) and provides the utility methods {@link #resolveSubtrees},
 * {@link #sizeOf}, {@link #buildLlamaCppJniConfig}, and {@link #buildPromptSupport}.
 *
 * <p>Concrete subclasses must implement {@link #getLlamaContextSize()} and
 * {@link #getLlamaThreads()} so that each goal can declare its own
 * {@code @Parameter}-annotated field with the appropriate default value.</p>
 */
public abstract class AbstractAiIndexMojo extends AbstractMojo {

    /** The Maven project base directory, injected by Maven. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    protected File baseDirectory;

    /**
     * Directory into which all {@code .ai.md} files are written.
     * Defaults to {@code ${project.basedir}/src/site/ai}.
     */
    @Parameter(
            property = "aiIndex.outputDirectory",
            defaultValue = "${project.basedir}/src/site/ai"
    )
    protected File outputDirectory;

    /** When {@code true}, the goal skips all processing and returns immediately. */
    @Parameter(property = "aiIndex.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * When {@code true}, regenerates AI fields even when they already have a value.
     * When {@code false}, only missing or changed entries are processed.
     */
    @Parameter(property = "aiIndex.force", defaultValue = "false")
    protected boolean force;

    /**
     * Source subdirectory paths (relative to {@code basedir}) to restrict processing.
     * When empty, all discovered source roots are used.
     */
    @Parameter(property = "aiIndex.subtrees")
    protected List<String> subtrees;

    /**
     * Name of the AI generation provider to use.
     * Supported values: {@code mock}, {@code llamacpp-jni}.
     *
     * @see AiGenerationProviderFactory
     */
    @Parameter(property = "aiIndex.summaryProvider", defaultValue = "mock")
    protected String summaryProvider;

    /** Prompt template definitions referenced by field generation configurations. */
    @Parameter
    protected List<AiPromptDefinition> promptDefinitions;

    /**
     * AI model definitions that pair a lookup key with a complete set of model parameters.
     * Field-generation entries and the provider configuration reference these definitions
     * by key rather than embedding the full parameter set inline.
     *
     * @see AiModelDefinition
     * @see AiModelDefinitionSupport
     */
    @Parameter
    protected List<AiModelDefinition> aiDefinitions;

    /** Per-field AI generation configurations controlling which prompt and AI definition to use. */
    @Parameter
    protected List<AiFieldGenerationConfig> fieldGenerations;

    /**
     * Optional native library path passed to the llama.cpp JNI provider.
     * Leave unset to use the bundled native library.
     */
    @Parameter(property = "aiIndex.llama.libraryPath")
    protected String llamaLibraryPath;

    /** Path to the GGUF model file used by the llama.cpp JNI provider. */
    @Parameter(property = "aiIndex.llama.modelPath")
    protected String llamaModelPath;

    /** Maximum number of output tokens the model may generate per request. */
    @Parameter(property = "aiIndex.llama.maxOutputTokens", defaultValue = "128")
    protected int llamaMaxOutputTokens;

    /** Sampling temperature for the llama.cpp model (lower = more deterministic). */
    @Parameter(property = "aiIndex.llama.temperature", defaultValue = "0.15")
    protected float llamaTemperature;

    // -------------------------------------------------------------------------
    // Abstract methods — subclasses declare @Parameter fields with their defaults
    // -------------------------------------------------------------------------

    /**
     * Returns the llama.cpp context window size for this goal.
     * Each concrete mojo declares its own {@code @Parameter}-annotated field and
     * implements this method to return it.
     */
    protected abstract int getLlamaContextSize();

    /**
     * Returns the number of CPU threads for llama.cpp inference for this goal.
     * Each concrete mojo declares its own {@code @Parameter}-annotated field and
     * implements this method to return it.
     */
    protected abstract int getLlamaThreads();

    // -------------------------------------------------------------------------
    // Shared utility methods
    // -------------------------------------------------------------------------

    /**
     * Resolves the configured {@link #subtrees} strings against {@code basePath},
     * filtering out any paths that do not exist on disk.
     *
     * @param basePath absolute, normalised project base directory
     * @return list of resolved, existing subtree paths; empty if none configured
     */
    protected List<Path> resolveSubtrees(final Path basePath) {
        final List<Path> resolved = new ArrayList<>();

        if (subtrees == null || subtrees.isEmpty()) {
            return resolved;
        }

        for (String subtree : subtrees) {
            final Path path = basePath.resolve(subtree).normalize();
            if (path.toFile().exists()) {
                resolved.add(path);
            } else {
                getLog().warn("Skipping missing subtree: " + path);
            }
        }

        return resolved;
    }

    /**
     * Returns the size of {@code list}, or {@code 0} when {@code list} is {@code null}.
     *
     * @param list any list, or {@code null}
     * @return number of elements, or {@code 0}
     */
    protected int sizeOf(final List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * Builds a {@link LlamaCppJniConfig} for the AI generation provider.
     *
     * <p>When {@link #fieldGenerations} is non-empty, all model parameters
     * (model path, context size, max output tokens, temperature, threads) are taken from
     * the {@link AiModelDefinition} referenced by the first entry's
     * {@link AiFieldGenerationConfig#getAiDefinitionKey()}. This ensures the provider is
     * always configured from the same definition that drives field generation.</p>
     *
     * <p>When {@link #fieldGenerations} is {@code null} or empty, the individual
     * {@code llamaModelPath}, {@code llamaContextSize}, {@code llamaMaxOutputTokens},
     * {@code llamaTemperature}, and {@code llamaThreads} parameters are used as a fallback.</p>
     *
     * @return fully populated llama.cpp configuration
     * @throws IllegalArgumentException if the first field generation's
     *                                  {@link AiFieldGenerationConfig#getAiDefinitionKey()}
     *                                  does not match any registered definition
     */
    protected LlamaCppJniConfig buildLlamaCppJniConfig() {
        if (fieldGenerations != null && !fieldGenerations.isEmpty()) {
            final AiFieldGenerationConfig first = fieldGenerations.get(0);
            final AiGenerationConfig config = buildAiModelDefinitionSupport().getConfig(first.getAiDefinitionKey());
            return new LlamaCppJniConfig(
                    llamaLibraryPath,
                    config.getModelPath(),
                    config.getContextSize(),
                    config.getMaxOutputTokens(),
                    config.getTemperature(),
                    config.getThreads(),
                    config.getTopP(),
                    config.getTopK(),
                    config.getRepeatPenalty(),
                    config.getStopStrings()
            );
        }
        return new LlamaCppJniConfig(
                llamaLibraryPath,
                llamaModelPath,
                getLlamaContextSize(),
                llamaMaxOutputTokens,
                llamaTemperature,
                getLlamaThreads(),
                AiGenerationConfig.DEFAULT_TOP_P,
                AiGenerationConfig.DEFAULT_TOP_K,
                AiGenerationConfig.DEFAULT_REPEAT_PENALTY,
                Collections.emptyList()
        );
    }

    /**
     * Builds an {@link AiPromptSupport} from the configured {@link #promptDefinitions}.
     *
     * @return prompt support instance backed by the configured definitions
     */
    protected AiPromptSupport buildPromptSupport() {
        return new AiPromptSupport(promptDefinitions);
    }

    /**
     * Builds an {@link AiModelDefinitionSupport} from the configured {@link #aiDefinitions}.
     *
     * @return model definition support instance backed by the configured definitions
     */
    protected AiModelDefinitionSupport buildAiModelDefinitionSupport() {
        return new AiModelDefinitionSupport(aiDefinitions);
    }

    /**
     * Logs the standard set of execution parameters that are common to every goal.
     *
     * <p>Always logs: the start message, base directory, output directory, subtrees,
     * force flag, and provider name. When {@code resolvedExtensions} is non-{@code null}
     * it is also logged; goals that do not use file-extension filtering (e.g.
     * {@code aggregate-packages}) pass {@code null} to suppress that line.</p>
     *
     * @param startMessage       first log line that identifies which goal is starting
     * @param basePath           resolved, absolute project base directory
     * @param outputPath         resolved, absolute output directory
     * @param resolvedSubtrees   resolved subtree paths; may be empty but not {@code null}
     * @param resolvedExtensions file extensions in scope, or {@code null} if not applicable
     */
    protected void logExecutionParameters(
            final String startMessage,
            final Path basePath,
            final Path outputPath,
            final List<Path> resolvedSubtrees,
            final List<String> resolvedExtensions
    ) {
        getLog().info(startMessage);
        getLog().info("Base directory  : " + basePath);
        getLog().info("Output directory: " + outputPath);
        getLog().info("Subtrees        : " + resolvedSubtrees);
        if (resolvedExtensions != null) {
            getLog().info("Extensions      : " + resolvedExtensions);
        }
        getLog().info("Force           : " + force);
        getLog().info("Provider        : " + summaryProvider);
        getLog().info("LlamaCpp Temperature: " + llamaTemperature);
        getLog().info("LlamaCpp Max Output Tokens: " + llamaMaxOutputTokens);
    }
}
