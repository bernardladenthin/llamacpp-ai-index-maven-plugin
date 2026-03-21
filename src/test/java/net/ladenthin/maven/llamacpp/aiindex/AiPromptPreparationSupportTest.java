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

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

public class AiPromptPreparationSupportTest {

    private AiPromptSupport createPromptSupport() {
        final AiPromptDefinition promptDefinition = new AiPromptDefinition();
        promptDefinition.setKey("file-summary");
        promptDefinition.setTemplate("Prompt header\n" +
                                     "\n" +
                                     "File: %s\n" +
                                     "\n" +
                                     "Source:\n" +
                                     "%s\n");

        return new AiPromptSupport(List.of(promptDefinition));
    }

    private AiMdHeader buildHeader() {
        return new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "00000000",
                "2026-03-18T00:00:00Z",
                "2026-03-18T00:00:00Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE
        );
    }

    // <editor-fold defaultstate="collapsed" desc="preparePrompt">
    @Test
    public void preparePrompt_promptFitsWithinLimit_notTrimmed() {
        // arrange
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());
        final String sourceText = "public class Test {\n" +
                                  "}\n";
        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                sourceText,
                buildHeader()
        );

        // act
        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 10_000);

        // assert
        assertThat(preparedPrompt.trimmed(), is(false));
        assertThat(preparedPrompt.sourceText(), is(equalTo(sourceText)));
        assertThat(preparedPrompt.originalSourceLength(), is(equalTo(sourceText.length())));
        assertThat(preparedPrompt.trimmedSourceLength(), is(equalTo(sourceText.length())));
        assertThat(preparedPrompt.prompt(), containsString("Test.java"));
        assertThat(preparedPrompt.prompt(), containsString(sourceText.strip()));
    }

    @Test
    public void preparePrompt_sourceLongerThanLimit_trimmedFlagIsTrue() {
        // arrange
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());
        final String sourceText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                sourceText,
                buildHeader()
        );

        // act
        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 40);

        // assert
        assertThat(preparedPrompt.trimmed(), is(true));
        assertThat(preparedPrompt.trimmedSourceLength() < preparedPrompt.originalSourceLength(), is(true));
        // sourceText includes EOF marker (35 chars), so it's longer than trimmedSourceLength
        assertThat(preparedPrompt.sourceText().length(), is(equalTo(preparedPrompt.trimmedSourceLength() + 35)));
        assertThat(preparedPrompt.availableSourceChars() >= 0, is(true));
    }

    @Test
    public void preparePrompt_overheadAlreadyExceedsLimit_sourceTrimmedToEmpty() {
        // arrange
        final AiPromptPreparationSupport support = new AiPromptPreparationSupport(createPromptSupport());
        final AiGenerationRequest request = new AiGenerationRequest(
                "file-summary",
                Path.of("Test.java"),
                "abcdefghijklmnopqrstuvwxyz",
                buildHeader()
        );

        // act
        final AiPreparedPrompt preparedPrompt = support.preparePrompt(request, 5);

        // assert
        assertThat(preparedPrompt.trimmed(), is(true));
        assertThat(preparedPrompt.sourceText(), is(equalTo("\n/* [EOF - source was truncated] */")));
        assertThat(preparedPrompt.trimmedSourceLength(), is(equalTo(0)));
        assertThat(preparedPrompt.availableSourceChars(), is(equalTo(0)));
    }
    // </editor-fold>
}
