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

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

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
        definition.setMaxRetries(5);
        definition.setRetryTemperatureIncrement(0.2f);
        definition.setChatTemplateEnableThinking(false);
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
        assertThat(config.getMaxRetries(), is(equalTo(5)));
        assertThat(config.getRetryTemperatureIncrement(), is(equalTo(0.2f)));
        assertThat(config.isChatTemplateEnableThinking(), is(false));
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
        assertThat(config.getMaxRetries(), is(equalTo(AiGenerationConfig.DEFAULT_MAX_RETRIES)));
        assertThat(config.getRetryTemperatureIncrement(), is(equalTo(AiGenerationConfig.DEFAULT_RETRY_TEMPERATURE_INCREMENT)));
        assertThat(config.isChatTemplateEnableThinking(), is(AiGenerationConfig.DEFAULT_CHAT_TEMPLATE_ENABLE_THINKING));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getConfig_unknownKey_throwsException() {
        // arrange
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Collections.<AiModelDefinition>emptyList());

        // act
        support.getConfig("unknown-key");
    }

    @Test
    public void getConfig_unknownKey_exceptionMessageContainsKey() {
        // arrange
        final String missingKey = "missing-model-key";
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Collections.<AiModelDefinition>emptyList());

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
        defA.setMaxRetries(1);

        final AiModelDefinition defB = new AiModelDefinition();
        defB.setKey("model-b");
        defB.setMaxRetries(7);

        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(Arrays.asList(defA, defB));

        // act
        final AiGenerationConfig configA = support.getConfig("model-a");
        final AiGenerationConfig configB = support.getConfig("model-b");

        // assert
        assertThat(configA.getMaxRetries(), is(equalTo(1)));
        assertThat(configB.getMaxRetries(), is(equalTo(7)));
    }

    @Test
    public void getConfig_definitionWithNullKey_ignoredDuringConstruction() {
        // arrange
        final AiModelDefinition nullKeyDef = new AiModelDefinition();
        // key not set — remains null
        final AiModelDefinition validDef = new AiModelDefinition();
        validDef.setKey("valid");
        final AiModelDefinitionSupport support = new AiModelDefinitionSupport(
                Arrays.asList(nullKeyDef, validDef));

        // act
        final AiGenerationConfig config = support.getConfig("valid");

        // assert — null-key definition was silently ignored; valid one is accessible
        assertThat(config, is(notNullValue()));
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
