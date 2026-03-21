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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.is;

public class LlamaCppJniAiSummaryProviderTest {

    private static final String MODEL_PATH =
            Paths.get("src", "test", "resources", "SmolLM2-135M-Instruct-Q3_K_M.gguf")
                    .toAbsolutePath()
                    .toString();

    // <editor-fold defaultstate="collapsed" desc="generate">
    @Test
    public void generate_realProvider_returnsNonEmptyResponse() throws Exception {
        // arrange — skip if native lib is unavailable or model is missing
        Assume.assumeTrue("Native llama test disabled. Enable with -DrunNativeLlamaTests=true",
                Boolean.getBoolean("runNativeLlamaTests"));
        Assume.assumeTrue("Model file missing: " + MODEL_PATH,
                Files.exists(Paths.get(MODEL_PATH)));

        final LlamaCppJniConfig config = new LlamaCppJniConfig(
                null, MODEL_PATH, 32768, 128, 0.15f, 8
        );
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());

        final AiMdHeader header = new AiMdHeader(
                "Test.java", AiMdHeaderCodec.HEADER_VERSION_1_0, "00000000",
                "2026-03-18T00:00:00Z", "2026-03-18T00:00:00Z",
                "0.1.0-SNAPSHOT", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
        final String source = "package com.example;\n" +
                              "\n" +
                              "public class Test {\n" +
                              "\n" +
                              "    public String hello(final String name) {\n" +
                              "        return \"Hello \" + name;\n" +
                              "    }\n" +
                              "}\n";

        final AiGenerationRequest bodyRequest = new AiGenerationRequest(
                CommonTestFixtures.PROMPT_KEY_FILE_BODY, Paths.get("Test.java"), source, header
        );

        // act
        try (LlamaCppJniAiSummaryProvider provider = new LlamaCppJniAiSummaryProvider(config, promptSupport)) {
            final String body = provider.generate(bodyRequest);

            // assert
            assertThat(body, is(notNullValue()));
            assertThat(body.trim().isEmpty(), is(false));
        }
    }
    // </editor-fold>
}
