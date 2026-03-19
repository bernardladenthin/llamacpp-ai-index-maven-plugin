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

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

public class AiPromptPreparationSupportTest {

    private AiPromptSupport createPromptSupport() {
        final AiPromptDefinition promptDefinition = new AiPromptDefinition();
        promptDefinition.setKey("file-summary");
        promptDefinition.setTemplate("""
                Prompt header

                File: %s

                Source:
                %s
                """);

        return new AiPromptSupport(List.of(promptDefinition));
    }

    @Test
    public void shouldNotTrimIfPromptFits() {
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "00000000",
                "2026-03-18T00:00:00Z",
                "2026-03-18T00:00:00Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY,
                AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        final String sourceText = """
                public class Test {
                }
                """;

        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                sourceText,
                header
        );

        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 10_000);

        Assert.assertFalse(preparedPrompt.trimmed());
        Assert.assertEquals(sourceText, preparedPrompt.sourceText());
        Assert.assertEquals(sourceText.length(), preparedPrompt.originalSourceLength());
        Assert.assertEquals(sourceText.length(), preparedPrompt.trimmedSourceLength());
        Assert.assertTrue(preparedPrompt.prompt().contains("Test.java"));
        Assert.assertTrue(preparedPrompt.prompt().contains(sourceText.strip()));
    }

    @Test
    public void shouldTrimIfPromptExceedsLimit() {
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "00000000",
                "2026-03-18T00:00:00Z",
                "2026-03-18T00:00:00Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY,
                AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        final String sourceText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                sourceText,
                header
        );

        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 40);

        Assert.assertTrue(preparedPrompt.trimmed());
        Assert.assertTrue(preparedPrompt.trimmedSourceLength() < preparedPrompt.originalSourceLength());
        Assert.assertEquals(preparedPrompt.sourceText().length(), preparedPrompt.trimmedSourceLength());
        Assert.assertTrue(preparedPrompt.availableSourceChars() >= 0);
    }

    @Test
    public void shouldTrimToEmptySourceIfPromptOverheadAlreadyExceedsLimit() {
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "00000000",
                "2026-03-18T00:00:00Z",
                "2026-03-18T00:00:00Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                AiMdHeaderCodec.DEFAULT_SUMMARY,
                AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                "abcdefghijklmnopqrstuvwxyz",
                header
        );

        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 5);

        Assert.assertTrue(preparedPrompt.trimmed());
        Assert.assertEquals("", preparedPrompt.sourceText());
        Assert.assertEquals(0, preparedPrompt.trimmedSourceLength());
        Assert.assertEquals(0, preparedPrompt.availableSourceChars());
    }
}