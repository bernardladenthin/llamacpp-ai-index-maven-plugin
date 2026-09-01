// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AiModelDefinitionSupportTest {

    // <editor-fold defaultstate="collapsed" desc="getConfig">
    @Test
    public void getConfig_knownKey_returnsConfig() {
        // arrange
        final String key = "my-model";
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(key);
        definition.setModelPath("/path/to/model.gguf");
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Arrays.asList(definition));

        // act
        final AiGenerationConfig config = support.getConfig(key);

        // assert
        assertThat(config, is(notNullValue()));
        assertThat(config.getModelPath(), is(equalTo("/path/to/model.gguf")));
    }

    @Test
    public void getConfig_knownKey_propagatesAllFields() {
        // arrange
        final String key = "full-model";
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(key);
        definition.setModelPath("/models/test.gguf");
        definition.setContextSize(16384);
        definition.setMaxOutputTokens(512);
        definition.setTemperature(0.8f);
        definition.setThreads(4);
        definition.setCharsPerToken(3);
        definition.setWarnOnTrim(false);
        definition.setChatTemplateEnableThinking(false);
        definition.setCachePrompt(false);
        definition.setSwaFull(false);
        definition.setCacheReuse(128);
        definition.setGpuLayers(20);
        definition.setSeed(12345);
        definition.setCpuMoeLayers(24);
        definition.setCpuFfnLayers(16);
        definition.setKvUnifiedPerSlot(4096);
        definition.setLazyMode("on");
        definition.setRepeatLastN(128);
        definition.setCacheTypeK("q8_0");
        definition.setCacheTypeV("q4_0");
        definition.setFlashAttn(true);
        definition.setBatchSize(512);
        definition.setUbatchSize(256);
        definition.setThreadsBatch(6);
        definition.setMainGpu(1);
        definition.setDevices("Vulkan1");
        definition.setReasoningEffort("high");
        definition.setReasoningBudgetTokens(512);
        definition.setDryMultiplier(0.7f);
        definition.setDryBase(1.5f);
        definition.setDryAllowedLength(4);
        definition.setDryPenaltyLastN(128);
        definition.setDrySequenceBreakers(Arrays.asList("\n", ":"));
        definition.setTopP(0.55f);
        definition.setTopK(21);
        definition.setMinP(0.09f);
        definition.setTopNSigma(1.3f);
        definition.setRepeatPenalty(1.15f);
        definition.setStopStrings(Arrays.asList("</s>", "STOP"));
        final AiCalibration calibration = new AiCalibration();
        calibration.setPrefillTokensPerSecond(900.0d);
        calibration.setDecodeTokensPerSecond(45.0d);
        calibration.setCharsPerToken(4.2d);
        definition.setCalibration(calibration);
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Arrays.asList(definition));

        // act
        final AiGenerationConfig config = support.getConfig(key);

        // assert
        assertThat(config.getModelPath(), is(equalTo("/models/test.gguf")));
        assertThat(config.getContextSize(), is(equalTo(16384)));
        assertThat(config.getMaxOutputTokens(), is(equalTo(512)));
        assertThat(config.getTemperature(), is(equalTo(0.8f)));
        assertThat(config.getThreads(), is(equalTo(4)));
        assertThat(config.getCharsPerToken(), is(equalTo(3)));
        assertThat(config.isWarnOnTrim(), is(false));
        assertThat(config.getChatTemplateEnableThinking(), is(false));
        // topP/topK/repeatPenalty/stopStrings are propagated too — kills the void-call mutants
        // that would drop those setter calls from toConfig().
        assertThat(config.getTopP(), is(equalTo(0.55f)));
        assertThat(config.getTopK(), is(equalTo(21)));
        assertThat(config.getMinP(), is(equalTo(0.09f)));
        assertThat(config.getTopNSigma(), is(equalTo(1.3f)));
        assertThat(config.getRepeatPenalty(), is(equalTo(1.15f)));
        assertThat(config.getStopStrings(), is(equalTo(Arrays.asList("</s>", "STOP"))));
        // The calibration is propagated — kills the void-call mutant that would drop the
        // setCalibration(...) copy from toConfig().
        assertThat(config.getCalibration(), is(sameInstance(calibration)));
        // Non-default cachePrompt propagates — kills the void-call mutant that would drop the
        // setCachePrompt(...) copy from toConfig().
        assertThat(config.isCachePrompt(), is(false));
        // Non-default swaFull (false; the shipped default is now true) propagates.
        assertThat(config.isSwaFull(), is(false));
        assertThat(config.getCacheReuse(), is(equalTo(128)));
        assertThat(config.getGpuLayers(), is(equalTo(20)));
        // The CPU-offload / KV / lazy-read knobs propagate — kill the dropped-setter void-call
        // mutants in toConfig().
        assertThat(config.getSeed(), is(equalTo(12345)));
        assertThat(config.getCpuMoeLayers(), is(equalTo(24)));
        assertThat(config.getCpuFfnLayers(), is(equalTo(16)));
        assertThat(config.getKvUnifiedPerSlot(), is(equalTo(4096)));
        assertThat(config.getLazyMode(), is(equalTo("on")));
        assertThat(config.getRepeatLastN(), is(equalTo(128)));
        assertThat(config.getCacheTypeK(), is(equalTo("q8_0")));
        assertThat(config.getCacheTypeV(), is(equalTo("q4_0")));
        assertThat(config.isFlashAttn(), is(equalTo(true)));
        assertThat(config.getBatchSize(), is(equalTo(512)));
        assertThat(config.getUbatchSize(), is(equalTo(256)));
        assertThat(config.getThreadsBatch(), is(equalTo(6)));
        // mainGpu/devices propagate — kill the dropped-setter void-call mutants in toConfig().
        assertThat(config.getMainGpu(), is(equalTo(1)));
        assertThat(config.getDevices(), is(equalTo("Vulkan1")));
        // Non-default reasoningEffort propagates — kills the dropped-setReasoningEffort void-call mutant.
        assertThat(config.getReasoningEffort(), is(equalTo("high")));
        assertThat(config.getReasoningBudgetTokens(), is(equalTo(512)));
        // DRY knobs propagate — kill the dropped-setter void-call mutants in toConfig().
        assertThat(config.getDryMultiplier(), is(equalTo(0.7f)));
        assertThat(config.getDryBase(), is(equalTo(1.5f)));
        assertThat(config.getDryAllowedLength(), is(equalTo(4)));
        assertThat(config.getDryPenaltyLastN(), is(equalTo(128)));
        assertThat(config.getDrySequenceBreakers(), is(equalTo(Arrays.asList("\n", ":"))));
    }

    @Test
    public void getConfig_defaultValues_matchAiGenerationConfigDefaults() {
        // arrange
        final String key = "defaults-model";
        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey(key);
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Arrays.asList(definition));

        // act
        final AiGenerationConfig config = support.getConfig(key);

        // assert
        assertThat(config.getContextSize(), is(equalTo(AiGenerationConfig.DEFAULT_CONTEXT_SIZE)));
        assertThat(config.getMaxOutputTokens(), is(equalTo(AiGenerationConfig.DEFAULT_MAX_OUTPUT_TOKENS)));
        assertThat(config.getTemperature(), is(equalTo(AiGenerationConfig.DEFAULT_TEMPERATURE)));
        assertThat(config.getThreads(), is(equalTo(AiGenerationConfig.DEFAULT_THREADS)));
        assertThat(config.getCharsPerToken(), is(equalTo(AiGenerationConfig.DEFAULT_CHARS_PER_TOKEN)));
        assertThat(config.getMaxInputChars(), is(equalTo(AiGenerationConfig.DEFAULT_MAX_INPUT_CHARS)));
        assertThat(config.isWarnOnTrim(), is(AiGenerationConfig.DEFAULT_WARN_ON_TRIM));
        assertThat(config.getChatTemplateEnableThinking(), is(AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING));
        assertThat(config.isCachePrompt(), is(AiGenerationConfig.DEFAULT_CACHE_PROMPT));
        assertThat(config.isSwaFull(), is(AiGenerationConfig.DEFAULT_SWA_FULL));
        assertThat(config.getCacheReuse(), is(equalTo(AiGenerationConfig.DEFAULT_CACHE_REUSE)));
        assertThat(config.getGpuLayers(), is(equalTo(AiGenerationConfig.DEFAULT_GPU_LAYERS)));
        assertThat(config.getSeed(), is(equalTo(AiGenerationConfig.DEFAULT_SEED)));
        assertThat(config.getCpuMoeLayers(), is(equalTo(AiGenerationConfig.DEFAULT_CPU_MOE_LAYERS)));
        assertThat(config.getCpuFfnLayers(), is(equalTo(AiGenerationConfig.DEFAULT_CPU_FFN_LAYERS)));
        assertThat(config.getKvUnifiedPerSlot(), is(equalTo(AiGenerationConfig.DEFAULT_KV_UNIFIED_PER_SLOT)));
        assertThat(config.getLazyMode(), is(equalTo(AiGenerationConfig.DEFAULT_LAZY_MODE)));
        assertThat(config.getRepeatLastN(), is(equalTo(AiGenerationConfig.DEFAULT_REPEAT_LAST_N)));
        assertThat(config.getCacheTypeK(), is(equalTo(AiGenerationConfig.DEFAULT_CACHE_TYPE_K)));
        assertThat(config.getCacheTypeV(), is(equalTo(AiGenerationConfig.DEFAULT_CACHE_TYPE_V)));
        assertThat(config.isFlashAttn(), is(equalTo(AiGenerationConfig.DEFAULT_FLASH_ATTN)));
        assertThat(config.getBatchSize(), is(equalTo(AiGenerationConfig.DEFAULT_BATCH_SIZE)));
        assertThat(config.getUbatchSize(), is(equalTo(AiGenerationConfig.DEFAULT_UBATCH_SIZE)));
        assertThat(config.getThreadsBatch(), is(equalTo(AiGenerationConfig.DEFAULT_THREADS_BATCH)));
        assertThat(config.getMainGpu(), is(equalTo(AiGenerationConfig.DEFAULT_MAIN_GPU)));
        assertThat(config.getDevices(), is(equalTo(AiGenerationConfig.DEFAULT_DEVICES)));
        assertThat(config.getReasoningEffort(), is(equalTo(AiGenerationConfig.DEFAULT_REASONING_EFFORT)));
        assertThat(config.getReasoningBudgetTokens(), is(equalTo(AiGenerationConfig.DEFAULT_REASONING_BUDGET_TOKENS)));
        assertThat(config.getMinP(), is(equalTo(AiGenerationConfig.DEFAULT_MIN_P)));
        assertThat(config.getTopNSigma(), is(equalTo(AiGenerationConfig.DEFAULT_TOP_N_SIGMA)));
        assertThat(config.getDryMultiplier(), is(equalTo(AiGenerationConfig.DEFAULT_DRY_MULTIPLIER)));
        assertThat(config.getDryBase(), is(equalTo(AiGenerationConfig.DEFAULT_DRY_BASE)));
        assertThat(config.getDryAllowedLength(), is(equalTo(AiGenerationConfig.DEFAULT_DRY_ALLOWED_LENGTH)));
        assertThat(config.getDryPenaltyLastN(), is(equalTo(AiGenerationConfig.DEFAULT_DRY_PENALTY_LAST_N)));
        assertThat(config.getDrySequenceBreakers(), is(Collections.<String>emptyList()));
    }

    @Test
    public void getConfig_unknownKey_throwsException() {
        // arrange
        final AiModelDefinitionSupport support =
                new AiModelDefinitionSupport(Collections.<AiModelDefinition>emptyList());

        // act / assert
        assertThrows(IllegalArgumentException.class, () -> support.getConfig("unknown-key"));
    }

    @Test
    public void getConfig_unknownKey_exceptionMessageContainsKey() {
        // arrange
        final String missingKey = "missing-model-key";
        final AiModelDefinitionSupport support =
                new AiModelDefinitionSupport(Collections.<AiModelDefinition>emptyList());

        try {
            // act
            support.getConfig(missingKey);
        } catch (IllegalArgumentException e) {
            // assert
            assertThat(e.getMessage(), containsString(missingKey));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException was not thrown");
    }

    @Test
    public void getConfig_multipleDefinitions_returnsCorrectOne() {
        // arrange
        final AiModelDefinition defA = new AiModelDefinition();
        defA.setKey("model-a");
        defA.setMaxOutputTokens(1);

        final AiModelDefinition defB = new AiModelDefinition();
        defB.setKey("model-b");
        defB.setMaxOutputTokens(7);

        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Arrays.asList(defA, defB));

        // act
        final AiGenerationConfig configA = support.getConfig("model-a");
        final AiGenerationConfig configB = support.getConfig("model-b");

        // assert
        assertThat(configA.getMaxOutputTokens(), is(equalTo(1)));
        assertThat(configB.getMaxOutputTokens(), is(equalTo(7)));
    }

    @Test
    public void constructor_definitionWithNullKey_throwsWithIndexAndBadEntry() {
        // arrange — first entry well-formed, second missing the required key
        final AiModelDefinition validDef = new AiModelDefinition();
        validDef.setKey("valid");
        final AiModelDefinition nullKeyDef = new AiModelDefinition();
        // key not set — remains null

        // act + assert — construction now fails fast at the bad entry rather than
        // silently dropping it (which would surface as "Missing AI model definition"
        // deeper in the goal). Message must name the list index and dump the bad
        // entry so the user can locate the misconfiguration in their POM.
        final NullPointerException npe = assertThrows(
                NullPointerException.class, () -> new AiModelDefinitionSupport(Arrays.asList(validDef, nullKeyDef)));
        assertThat(npe.getMessage(), containsString("aiDefinitions[1].key"));
        assertThat(npe.getMessage(), containsString("AiModelDefinition"));
    }

    @Test
    public void getConfig_nullDefinitionsList_throwsForAnyKey() {
        // arrange
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(null);

        try {
            // act
            support.getConfig("any-key");
        } catch (IllegalArgumentException e) {
            // assert
            assertThat(e.getMessage(), containsString("any-key"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException was not thrown");
    }
    // </editor-fold>
}
