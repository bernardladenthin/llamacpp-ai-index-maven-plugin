// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.LlamaCppJniConfig;
import net.ladenthin.srcmorph.provider.LlamaCppJniConfigFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package-private helpers shared by at least two of the {@code engine} package's per-phase engines.
 * Anything used by only one engine stays local to that engine class instead.
 */
final class EngineSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineSupport.class);

    private EngineSupport() {
        // utility class — not instantiable
    }

    /**
     * Resolves the configured subtree strings against {@code basePath}, filtering out any paths that do
     * not exist on disk. Used by {@link GenerateEngine} and {@link AggregatePackagesEngine}.
     *
     * @param basePath absolute, normalised project base directory
     * @param subtrees configured subtree strings, or {@code null}/empty for none
     * @return list of resolved, existing subtree paths; empty if none configured or none exist
     */
    static List<Path> resolveSubtrees(final Path basePath, final @Nullable List<String> subtrees) {
        final List<Path> resolved = new ArrayList<>();

        if (subtrees == null || subtrees.isEmpty()) {
            return resolved;
        }

        for (final String subtree : subtrees) {
            final Path path = basePath.resolve(subtree).normalize();
            if (path.toFile().exists()) {
                resolved.add(path);
            } else {
                LOGGER.warn("Skipping missing subtree: {}", path);
            }
        }

        return resolved;
    }

    /**
     * Builds an {@link AiPromptSupport} from the configured prompt definitions, translating a
     * misconfiguration ({@link NullPointerException} from a missing {@code key}/{@code template}) into
     * a {@link SrcMorphException} so the caller reports it as a configuration error.
     *
     * @param promptDefinitions the configured prompt definitions, or {@code null} for none
     * @return prompt support instance backed by the configured definitions
     * @throws SrcMorphException if any prompt definition is missing a required field
     */
    static AiPromptSupport buildPromptSupport(final @Nullable List<AiPromptDefinition> promptDefinitions)
            throws SrcMorphException {
        try {
            return new AiPromptSupport(promptDefinitions);
        } catch (final NullPointerException e) {
            throw new SrcMorphException("Invalid plugin configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Builds an {@link AiModelDefinitionSupport} from the configured AI model definitions, translating a
     * misconfiguration ({@link NullPointerException} from a missing {@code key}) into a
     * {@link SrcMorphException} so the caller reports it as a configuration error.
     *
     * @param aiDefinitions the configured AI model definitions, or {@code null} for none
     * @return model definition support instance backed by the configured definitions
     * @throws SrcMorphException if any AI definition is missing a required field
     */
    static AiModelDefinitionSupport buildAiModelDefinitionSupport(final @Nullable List<AiModelDefinition> aiDefinitions)
            throws SrcMorphException {
        try {
            return new AiModelDefinitionSupport(aiDefinitions);
        } catch (final NullPointerException e) {
            throw new SrcMorphException("Invalid plugin configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the {@link LlamaCppJniConfig} for a run with no specific routed model in mind: when
     * {@link SrcMorphConfiguration#getFieldGenerations()} is non-empty, all model parameters come from the
     * {@link AiModelDefinition} referenced by the <em>first</em> entry's
     * {@link AiFieldGenerationConfig#getAiDefinitionKey()}; otherwise the configuration's individual
     * {@code llama*} fallback fields are used. Used by {@link AggregatePackagesEngine} and
     * {@link AggregateProjectEngine}, which drive a single provider for the whole run.
     *
     * @param config                 the run configuration
     * @param modelDefinitionSupport model lookup built from {@link SrcMorphConfiguration#getAiDefinitions()}
     * @return the fully populated llama.cpp configuration
     * @throws IllegalArgumentException if the first field generation's {@code aiDefinitionKey} matches no
     *                                   registered definition
     */
    static LlamaCppJniConfig resolveLlamaCppJniConfig(
            final SrcMorphConfiguration config, final AiModelDefinitionSupport modelDefinitionSupport) {
        final List<AiFieldGenerationConfig> fieldGenerations = config.getFieldGenerations();
        if (fieldGenerations != null && !fieldGenerations.isEmpty()) {
            return resolveLlamaCppJniConfig(
                    modelDefinitionSupport, fieldGenerations.get(0).getAiDefinitionKey());
        }
        final String modelPath = config.getLlamaModelPath();
        if (modelPath == null) {
            throw new NullPointerException("llamaModelPath");
        }
        return LlamaCppJniConfigFactory.fromFallbackParameters(
                modelPath,
                config.getLlamaContextSize(),
                config.getLlamaMaxOutputTokens(),
                config.getLlamaTemperature(),
                config.getLlamaThreads());
    }

    /**
     * Resolves the {@link LlamaCppJniConfig} for one specific {@link AiModelDefinition}, identified by its
     * key. Used by {@link GenerateEngine} (one provider per routing group) and {@link CalibrateEngine}
     * (one provider per calibrated model).
     *
     * <p>Takes no {@link SrcMorphConfiguration}: everything this needs comes from the named model
     * definition. The run configuration used to contribute the (never-read) native library path; with
     * that gone, passing it here would be an unused parameter.</p>
     *
     * @param modelDefinitionSupport model lookup built from {@link SrcMorphConfiguration#getAiDefinitions()}
     * @param aiDefinitionKey        the {@link AiModelDefinition} key
     * @return the fully populated llama.cpp configuration for that definition
     * @throws IllegalArgumentException if {@code aiDefinitionKey} matches no registered definition
     */
    static LlamaCppJniConfig resolveLlamaCppJniConfig(
            final AiModelDefinitionSupport modelDefinitionSupport, final String aiDefinitionKey) {
        final AiGenerationConfig modelConfig = modelDefinitionSupport.getConfig(aiDefinitionKey);
        return LlamaCppJniConfigFactory.fromGenerationConfig(modelConfig);
    }
}
