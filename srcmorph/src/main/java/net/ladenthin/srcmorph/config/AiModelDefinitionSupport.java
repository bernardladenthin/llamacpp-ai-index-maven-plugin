// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import net.ladenthin.srcmorph.support.Java8CompatibilityHelper;
import org.jspecify.annotations.Nullable;

/**
 * Resolves {@link AiModelDefinition} entries by their key, returning the corresponding
 * {@link AiGenerationConfig}.
 *
 * <p>Built from the {@code <aiDefinitions>} list in the plugin configuration, this
 * support class acts as a lookup table that converts each {@link AiModelDefinition} into
 * a ready-to-use {@link AiGenerationConfig} object. Field-generation entries and mojos
 * reference definitions by their string key rather than embedding the full parameter
 * set inline.</p>
 *
 * <p>Every entry in the supplied list must have a non-null {@code key}; a null key
 * throws {@link NullPointerException} naming the list index and dumping the offending
 * entry. This is the contract enforcement boundary that makes misconfigured POM
 * {@code <aiDefinitions>} fail at build configuration time rather than silently
 * dropping the entry and surfacing later as a "Missing AI model definition" failure
 * deeper in the goal. Callers (e.g. the llamacpp-ai-index-maven-plugin module's Mojos) wrap
 * construction in {@link NullPointerException}
 * &rarr; {@code org.apache.maven.plugin.MojoExecutionException} so the Maven
 * framework reports it as a user configuration error rather than a plugin bug. This library
 * itself has no Maven dependency, so that type is referenced here only in prose.</p>
 *
 * <p>Lookups for missing keys throw {@link IllegalArgumentException} so that
 * configuration errors are detected eagerly at runtime.</p>
 *
 * @see AiModelDefinition
 * @see AiFieldGenerationConfig
 */
@ToString
public final class AiModelDefinitionSupport {

    /**
     * Error message prefix used when a requested key is not found in the definition map.
     * The missing key is appended after this prefix to produce the full exception message.
     */
    private static final String MISSING_DEFINITION_MESSAGE_PREFIX = "Missing AI model definition for key: ";

    private final Map<String, AiGenerationConfig> configs;
    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    /**
     * Builds a new {@code AiModelDefinitionSupport} from the supplied definitions list.
     *
     * <p>Every entry must have a non-null {@code key}; a null key throws
     * {@link NullPointerException} naming the list index and the offending entry.
     * See the class Javadoc for the full contract.</p>
     *
     * @param definitions list of AI model definitions; may be {@code null} or empty
     *                    (treated as no definitions); individual entries must be
     *                    well-formed
     * @throws NullPointerException if any entry has a {@code null} {@code key}
     */
    public AiModelDefinitionSupport(final @Nullable List<AiModelDefinition> definitions) {
        if (definitions == null) {
            this.configs = new HashMap<>(compatibilityHelper.hashMapCapacityFor(0));
            return;
        }
        final int count = definitions.size();
        // Presize so the loop's put() calls never trigger a rehash
        // (fb-contrib PSC_PRESIZE_COLLECTIONS).
        this.configs = new HashMap<>(compatibilityHelper.hashMapCapacityFor(count));
        for (int i = 0; i < count; i++) {
            final AiModelDefinition definition = definitions.get(i);
            final int index = i;
            Objects.requireNonNull(
                    definition.getKey(),
                    () -> "aiDefinitions[" + index + "].key is required (bad entry: " + definition + ")");
            configs.put(definition.getKey(), toConfig(definition));
        }
    }

    /**
     * Returns the {@link AiGenerationConfig} associated with the given key.
     *
     * @param key the definition key to look up; must not be {@code null}
     * @return the corresponding generation config
     * @throws IllegalArgumentException if no definition is registered for {@code key}
     */
    public AiGenerationConfig getConfig(final String key) {
        final AiGenerationConfig config = configs.get(key);
        if (config == null) {
            throw new IllegalArgumentException(MISSING_DEFINITION_MESSAGE_PREFIX + key);
        }
        return config;
    }

    /**
     * Converts an {@link AiModelDefinition} into an {@link AiGenerationConfig} by copying
     * all field values.
     *
     * @param definition the definition to convert; must not be {@code null}
     * @return a fully populated {@link AiGenerationConfig}
     */
    private static AiGenerationConfig toConfig(final AiModelDefinition definition) {
        final AiGenerationConfig config = new AiGenerationConfig();
        config.setModelPath(definition.getModelPath());
        config.setContextSize(definition.getContextSize());
        config.setMaxOutputTokens(definition.getMaxOutputTokens());
        config.setTemperature(definition.getTemperature());
        config.setThreads(definition.getThreads());
        config.setCharsPerToken(definition.getCharsPerToken());
        config.setWarnOnTrim(definition.isWarnOnTrim());
        config.setTopP(definition.getTopP());
        config.setTopK(definition.getTopK());
        config.setMinP(definition.getMinP());
        config.setTopNSigma(definition.getTopNSigma());
        config.setRepeatPenalty(definition.getRepeatPenalty());
        config.setStopStrings(definition.getStopStrings());
        config.setChatTemplateEnableThinking(definition.getChatTemplateEnableThinking());
        config.setCachePrompt(definition.isCachePrompt());
        config.setSwaFull(definition.isSwaFull());
        config.setCacheReuse(definition.getCacheReuse());
        config.setGpuLayers(definition.getGpuLayers());
        config.setSeed(definition.getSeed());
        config.setCpuMoeLayers(definition.getCpuMoeLayers());
        config.setCpuFfnLayers(definition.getCpuFfnLayers());
        config.setKvUnifiedPerSlot(definition.getKvUnifiedPerSlot());
        config.setLazyMode(definition.getLazyMode());
        config.setRepeatLastN(definition.getRepeatLastN());
        config.setCacheTypeK(definition.getCacheTypeK());
        config.setCacheTypeV(definition.getCacheTypeV());
        config.setFlashAttn(definition.isFlashAttn());
        config.setBatchSize(definition.getBatchSize());
        config.setUbatchSize(definition.getUbatchSize());
        config.setThreadsBatch(definition.getThreadsBatch());
        config.setMainGpu(definition.getMainGpu());
        config.setDevices(definition.getDevices());
        config.setReasoningEffort(definition.getReasoningEffort());
        config.setReasoningBudgetTokens(definition.getReasoningBudgetTokens());
        config.setDryMultiplier(definition.getDryMultiplier());
        config.setDryBase(definition.getDryBase());
        config.setDryAllowedLength(definition.getDryAllowedLength());
        config.setDryPenaltyLastN(definition.getDryPenaltyLastN());
        config.setDrySequenceBreakers(definition.getDrySequenceBreakers());
        config.setCalibration(definition.getCalibration());
        return config;
    }
}
