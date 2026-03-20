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
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class AiFieldGenerationSupportTest {

    /**
     * Simple {@link Log} implementation that captures all messages passed to
     * {@link #warn(CharSequence)} for later assertion.
     */
    private static class WarnCapturingLog extends SystemStreamLog {

        private final List<String> capturedWarnings = new ArrayList<>();

        @Override
        public void warn(final CharSequence content) {
            capturedWarnings.add(content.toString());
            super.warn(content);
        }

        public List<String> getCapturedWarnings() {
            return capturedWarnings;
        }
    }

    private WarnCapturingLog capturingLog;

    @Before
    public void setUp() {
        capturingLog = new WarnCapturingLog();
    }

    // <editor-fold defaultstate="collapsed" desc="processFieldGenerations">
    @Test
    public void processFieldGenerations_providerReturnsNonEmpty_noWarningLogged() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiGenerationProvider nonEmptyProvider = request -> "A real summary.";
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, nonEmptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert
        assertThat(capturingLog.getCapturedWarnings().isEmpty(), is(true));
    }

    @Test
    public void processFieldGenerations_providerReturnsEmpty_warningContainsContextFile() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("AiGenerationConfig", ".java");
        final AiMdHeader header = new AiMdHeader("AiGenerationConfig.java", "1.0", "A8CBFAAA",
                "2026-03-20T21:32:55Z", "2026-03-20T21:58:26Z", "0.1.0-SNAPSHOT", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiGenerationProvider emptyProvider = request -> "";
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, emptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class AiGenerationConfig {}", header);

        // assert
        assertThat(capturingLog.getCapturedWarnings().size(), is(equalTo(1)));
        assertThat(capturingLog.getCapturedWarnings().get(0), containsString(contextFile.toString()));
    }

    @Test
    public void processFieldGenerations_providerReturnsEmpty_warningContainsPromptKey() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiGenerationProvider emptyProvider = request -> "";
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, emptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert
        assertThat(capturingLog.getCapturedWarnings().size(), is(equalTo(1)));
        assertThat(capturingLog.getCapturedWarnings().get(0),
                containsString(CommonTestFixtures.PROMPT_KEY_FILE_BODY));
    }

    @Test
    public void processFieldGenerations_providerReturnsEmpty_resultBodyIsEmpty() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiGenerationProvider emptyProvider = request -> "";
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, emptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        final AiGenerationResult result = support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert
        assertThat(result.body(), is(equalTo("")));
    }
    // </editor-fold>
}
