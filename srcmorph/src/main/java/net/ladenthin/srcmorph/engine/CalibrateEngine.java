// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.ToString;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.indexer.AiCalibrationMeasurement;
import net.ladenthin.srcmorph.indexer.AiCalibrationRunner;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.AiGenerationProvider;
import net.ladenthin.srcmorph.provider.AiGenerationProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preflight + per-machine timing pass that sits between {@code planOnly} (no model loaded) and a real
 * {@link GenerateEngine} run.
 *
 * <p>Extracted from what was {@code CalibrateMojo.execute()} in the {@code llamacpp-ai-index-maven-plugin}
 * module. For each distinct model a {@link GenerateEngine} run would load (the {@code aiDefinitionKey}s
 * referenced by {@link SrcMorphConfiguration#getFieldGenerations()}), loads the model once (catching a
 * bad path / OOM / wrong native early), runs a couple of representative generations via
 * {@link AiCalibrationRunner}, and reads the model's own measured prefill/decode throughput. The caller
 * pastes {@link CalibrationReport#renderXml()} onto the matching {@code <aiDefinition>} so the plan's time
 * estimate reflects <em>this</em> machine instead of the built-in reference-CPU coefficients.</p>
 */
@ToString
public final class CalibrateEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrateEngine.class);

    private final SrcMorphConfiguration config;
    private final AiCalibrationRunner calibrationRunner = new AiCalibrationRunner();

    /**
     * Creates a new {@link CalibrateEngine} for the given run configuration.
     *
     * @param config the run configuration
     */
    public CalibrateEngine(final SrcMorphConfiguration config) {
        this.config = config;
    }

    /**
     * Calibrates every distinct model referenced by the configured routing rules.
     *
     * @return the calibration report, one measurement per distinct routed model
     * @throws SrcMorphException if no routable rule is configured, or a model fails to load/generate (any
     *                            {@link IOException} from the provider is caught per-model and rewrapped
     *                            with the failing model's key)
     */
    public CalibrationReport execute() throws SrcMorphException {
        final List<AiFieldGenerationConfig> fieldGenerations = config.getFieldGenerations();
        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            throw new SrcMorphException("No <fieldGenerations> configured; nothing to calibrate.");
        }

        final AiPromptSupport promptSupport = EngineSupport.buildPromptSupport(config.getPromptDefinitions());
        final AiModelDefinitionSupport modelDefinitionSupport =
                EngineSupport.buildAiModelDefinitionSupport(config.getAiDefinitions());
        final AiPromptPreparationSupport promptPreparationSupport = new AiPromptPreparationSupport(promptSupport);
        final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();

        // Distinct routed models, each mapped to a representative prompt key (skip rules have none).
        final Map<String, String> modelToPrompt = new LinkedHashMap<>();
        for (final AiFieldGenerationConfig rule : fieldGenerations) {
            if (rule == null || rule.getAiDefinitionKey() == null || rule.getPromptKey() == null) {
                continue;
            }
            modelToPrompt.putIfAbsent(rule.getAiDefinitionKey(), rule.getPromptKey());
        }
        if (modelToPrompt.isEmpty()) {
            throw new SrcMorphException("No routable (model + prompt) rule found to calibrate.");
        }

        LOGGER.info(
                "AI index calibration: {} model(s). Provider: {}",
                modelToPrompt.size(),
                config.getGenerationProvider());
        final List<CalibrationReport.ModelMeasurement> measurements = new ArrayList<>(modelToPrompt.size());
        for (final Map.Entry<String, String> entry : modelToPrompt.entrySet()) {
            measurements.add(calibrateModel(
                    entry.getKey(),
                    entry.getValue(),
                    modelDefinitionSupport,
                    promptSupport,
                    promptPreparationSupport,
                    providerFactory));
        }

        return new CalibrationReport(measurements);
    }

    /**
     * Loads one model, measures it via {@link AiCalibrationRunner}, and logs the result.
     *
     * @param modelKey                 the aiDefinitionKey to calibrate
     * @param promptKey                a representative prompt key routed to this model
     * @param modelDefinitionSupport   model lookup
     * @param promptSupport            prompt lookup (for the provider)
     * @param promptPreparationSupport prompt preparation (for the window calculation)
     * @param providerFactory          provider factory
     * @return the model's key paired with its measurement
     * @throws SrcMorphException if the model fails to load or generate
     */
    private CalibrationReport.ModelMeasurement calibrateModel(
            final String modelKey,
            final String promptKey,
            final AiModelDefinitionSupport modelDefinitionSupport,
            final AiPromptSupport promptSupport,
            final AiPromptPreparationSupport promptPreparationSupport,
            final AiGenerationProviderFactory providerFactory)
            throws SrcMorphException {
        final AiGenerationConfig modelConfig = modelDefinitionSupport.getConfig(modelKey);
        final long windowChars = calibrationRunner.windowChars(modelConfig, promptKey, promptPreparationSupport);

        LOGGER.info("");
        LOGGER.info("Model '{}': loading (window ~{} source chars)...", modelKey, windowChars);
        try (AiGenerationProvider provider = providerFactory.create(
                config.getGenerationProvider(),
                EngineSupport.resolveLlamaCppJniConfig(modelDefinitionSupport, modelKey),
                promptSupport)) {
            final AiCalibrationMeasurement m =
                    calibrationRunner.measure(provider, modelConfig, promptKey, promptPreparationSupport);

            LOGGER.info(String.format(
                    Locale.ROOT,
                    "Model '%s': loaded+first-gen ~%.1fs | prefill %.0f tok/s | decode %.0f tok/s | ~%.2f chars/token",
                    modelKey,
                    m.loadSeconds(),
                    m.prefillTokensPerSecond(),
                    m.decodeTokensPerSecond(),
                    m.charsPerToken()));
            if (m.prefillTokensPerSecond() > 0 && m.midPrefillTokensPerSecond() > 0) {
                LOGGER.info(String.format(
                        Locale.ROOT,
                        "  (mid-window prefill %.0f tok/s vs near-window %.0f tok/s - larger gap => more curvature)",
                        m.midPrefillTokensPerSecond(),
                        m.prefillTokensPerSecond()));
            }
            return new CalibrationReport.ModelMeasurement(modelKey, m);
        } catch (final IOException e) {
            throw new SrcMorphException("Calibration failed for model '" + modelKey + "': " + e.getMessage(), e);
        }
    }
}
