// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.AiGenerationProviderFactory;
import net.ladenthin.srcmorph.provider.GgufModelInfo;
import net.ladenthin.srcmorph.provider.GgufModelInspector;
import net.ladenthin.srcmorph.provider.LlamaCppJniAiGenerationProvider;
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

    /**
     * Fails the run when a routed {@code <aiDefinition>}'s model file does not exist.
     *
     * <p>Without this, a typo in a {@code <modelPath>} survives the entire plan phase — the walk, the
     * classification, the rendered plan — and only dies inside the native loader. With several model
     * groups that happens <em>after</em> the earlier groups have already generated, so the failure can
     * arrive an hour into a run for a mistake that was visible before it started. This joins the other
     * fail-fast checks the engines already run (missing {@code <fieldGenerations>}, rule-set
     * validation, unmatched files, over-window files).
     *
     * <p><strong>Gated on the provider on purpose.</strong> Only {@code llamacpp-jni} loads a GGUF;
     * the {@code mock} provider ignores {@code modelPath} entirely, and every shipped example config
     * sets a deliberately non-existent {@code "unused-with-mock-provider.gguf"}. An ungated check
     * would red those examples, their binding tests, the CLI end-to-end test and the fat-jar release
     * smoke.
     *
     * @param generationProvider     the configured provider key
     * @param modelDefinitionSupport model lookup built from the configured {@code <aiDefinitions>}
     * @param fieldGenerations       the routing rules, already validated
     * @throws SrcMorphException if a routed model's file is missing or its path is unset
     */
    static void validateRoutedModelPaths(
            final String generationProvider,
            final AiModelDefinitionSupport modelDefinitionSupport,
            final List<AiFieldGenerationConfig> fieldGenerations)
            throws SrcMorphException {
        if (!AiGenerationProviderFactory.PROVIDER_LLAMACPP_JNI.equals(generationProvider)) {
            return;
        }
        for (final AiFieldGenerationConfig rule : fieldGenerations) {
            if (rule == null || rule.isSkip()) {
                continue;
            }
            final String aiDefinitionKey = rule.getAiDefinitionKey();
            if (aiDefinitionKey == null) {
                continue;
            }
            final AiGenerationConfig modelConfig = modelDefinitionSupport.getConfig(aiDefinitionKey);
            final String modelPath = modelConfig.getModelPath();
            if (modelPath == null || !new File(modelPath).isFile()) {
                throw new SrcMorphException("Model file for aiDefinition '" + aiDefinitionKey
                        + "' does not exist: " + modelPath
                        + " (checked before any model is loaded; fix the modelPath, or set"
                        + " generationProvider to mock for a model-free run)");
            }
            validateFlashAttnIsNotRequested(aiDefinitionKey, modelConfig);
            validateAgainstTheModelItself(aiDefinitionKey, modelConfig, Paths.get(modelPath));
        }
    }

    /**
     * Refuses {@code flashAttn} at plan time, before any model is loaded or any file written.
     *
     * <p>The knob is documented and settable but cannot work with the pinned binding: {@code -fa} takes a
     * mandatory {@code [on|off|auto]} value and {@code ModelParameters.enableFlashAttn()} emits the flag
     * without one, so llama.cpp swallows the following argv token and the load dies naming a flag the
     * user never set. Refusing here rather than in the provider means a multi-model run fails before its
     * first group generates, instead of after.</p>
     *
     * @param aiDefinitionKey the model definition being validated, for the message
     * @param modelConfig     its resolved configuration
     * @throws SrcMorphException when {@code flashAttn} is enabled
     */
    private static void validateFlashAttnIsNotRequested(
            final String aiDefinitionKey, final AiGenerationConfig modelConfig) throws SrcMorphException {
        // TODO: remove together with the provider-side guard once net.ladenthin:llama 5.2.0 exposes a
        // value-taking Flash Attention setter -- see the TODO in LlamaCppJniAiGenerationProvider.model().
        if (modelConfig.isFlashAttn()) {
            throw new SrcMorphException("aiDefinition '" + aiDefinitionKey + "': "
                    + LlamaCppJniAiGenerationProvider.FLASH_ATTN_UNSUPPORTED_MESSAGE);
        }
    }

    /**
     * Checks the configuration against what the GGUF file itself declares, still without loading it.
     *
     * <p>Existing on disk is a weak check. The two things it misses are both silent:</p>
     *
     * <ul>
     *   <li><b>The file is not a GGUF.</b> A truncated download, a Git LFS pointer, or the wrong file
     *       entirely passes {@code isFile()} and dies much later inside the native loader — after the
     *       earlier model groups of a multi-model run have already generated. Fails here instead.</li>
     *   <li><b>{@code contextSize} exceeds what the model declares.</b> This one is worse than it looks,
     *       because <em>every</em> number the plan phase produces is derived from {@code contextSize}:
     *       {@code maxInputChars}, the oversize/chunking decision, and the time estimate. Point the
     *       default 32768 at a 4096-context model and the plan is wrong by 8&times; before a single
     *       token is generated. A warning rather than an error because the run still proceeds &mdash;
     *       but note what actually happens: llama.cpp <em>caps the slot</em> to the model's trained
     *       context, so a file the plan calls "fits" can still die mid-run once earlier files have
     *       generated. (An earlier version of this text offered RoPE scaling as the escape hatch. It is
     *       not one: srcmorph exposes no knob for it, so the only fix is to lower {@code contextSize}.)
     *       </li>
     * </ul>
     *
     * @param aiDefinitionKey the model key, for the message
     * @param modelConfig     the resolved model configuration
     * @param modelFile       the model file, already known to exist
     * @throws SrcMorphException if the file is not a readable GGUF
     */
    private static void validateAgainstTheModelItself(
            final String aiDefinitionKey, final AiGenerationConfig modelConfig, final Path modelFile)
            throws SrcMorphException {
        final GgufModelInfo info = new GgufModelInspector().inspect(modelFile);
        if (!info.readable()) {
            throw new SrcMorphException("Model file for aiDefinition '" + aiDefinitionKey + "' is not a readable"
                    + " GGUF: " + modelFile + " (" + info.failure() + "). A truncated download or a Git LFS"
                    + " pointer looks like a perfectly good file to an existence check.");
        }
        final long declared = info.contextLength();
        if (declared != GgufModelInfo.UNKNOWN_CONTEXT_LENGTH && modelConfig.getContextSize() > declared) {
            LOGGER.warn(
                    "aiDefinition '{}' sets contextSize {} but {} declares only {}. llama.cpp caps the"
                            + " slot to the model's trained context, so the runtime window really is {} --"
                            + " while the plan's maxInputChars, oversize handling and time estimate are all"
                            + " derived from the configured {}. A file the plan calls 'fits' can therefore"
                            + " still die mid-run. Lower contextSize to match.",
                    aiDefinitionKey,
                    modelConfig.getContextSize(),
                    info.modelName().isEmpty() ? modelFile.toString() : info.modelName(),
                    declared,
                    declared,
                    modelConfig.getContextSize());
        }
    }
}
