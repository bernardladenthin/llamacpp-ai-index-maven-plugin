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
import java.util.concurrent.atomic.AtomicInteger;
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
     * {@link #warn(CharSequence)} and {@link #info(CharSequence)} for later assertion.
     */
    private static class WarnCapturingLog extends SystemStreamLog {

        private final List<String> capturedWarnings = new ArrayList<>();
        private final List<String> capturedInfos = new ArrayList<>();

        @Override
        public void warn(final CharSequence content) {
            capturedWarnings.add(content.toString());
            super.warn(content);
        }

        @Override
        public void info(final CharSequence content) {
            capturedInfos.add(content.toString());
            super.info(content);
        }

        public List<String> getCapturedWarnings() {
            return capturedWarnings;
        }

        public List<String> getCapturedInfos() {
            return capturedInfos;
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

    @Test
    public void processFieldGenerations_providerReturnEmptyThenNonEmpty_noWarningLoggedAndResultBodyIsNonEmpty() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AtomicInteger callCount = new AtomicInteger(0);
        // Returns empty on the first call, a real summary on the first retry
        final AiGenerationProvider eventualProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                callCount.incrementAndGet();
                return "";
            }

            @Override
            public String generate(final AiGenerationRequest request, final float temperatureOverride) {
                callCount.incrementAndGet();
                return "Summary on retry.";
            }
        };
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, eventualProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        final AiGenerationResult result = support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert — warning suppressed because retry succeeded
        assertThat(capturingLog.getCapturedWarnings().isEmpty(), is(true));
        assertThat(result.body(), is(equalTo("Summary on retry.")));
    }

    @Test
    public void processFieldGenerations_providerAlwaysEmpty_retriesDefaultMaxRetriesTimes() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AtomicInteger retryCallCount = new AtomicInteger(0);
        final AiGenerationProvider alwaysEmptyProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "";
            }

            @Override
            public String generate(final AiGenerationRequest request, final float temperatureOverride) {
                retryCallCount.incrementAndGet();
                return "";
            }
        };
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, alwaysEmptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert — retry was invoked exactly DEFAULT_MAX_RETRIES times
        assertThat(retryCallCount.get(), is(equalTo(AiGenerationConfig.DEFAULT_MAX_RETRIES)));
    }

    @Test
    public void processFieldGenerations_providerAlwaysEmpty_logsRetryInfoMessages() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AiGenerationProvider alwaysEmptyProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "";
            }

            @Override
            public String generate(final AiGenerationRequest request, final float temperatureOverride) {
                return "";
            }
        };
        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, alwaysEmptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                CommonTestFixtures.createFileFieldGenerations(),
                contextFile, "file", "public class Test {}", header);

        // assert — one INFO log per retry attempt
        assertThat(capturingLog.getCapturedInfos().size(), is(equalTo(AiGenerationConfig.DEFAULT_MAX_RETRIES)));
        for (final String infoMsg : capturingLog.getCapturedInfos()) {
            assertThat(infoMsg, containsString(CommonTestFixtures.PROMPT_KEY_FILE_BODY));
        }
    }

    @Test
    public void processFieldGenerations_zeroMaxRetries_providerCalledOnce() throws Exception {
        // arrange
        final Path contextFile = Files.createTempFile("Test", ".java");
        final AiMdHeader header = new AiMdHeader("Test.java", "1.0", "ABCD1234",
                "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "0.1.0", "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE);
        final AiPromptSupport promptSupport = new AiPromptSupport(CommonTestFixtures.createFilePromptDefinitions());
        final AtomicInteger retryCallCount = new AtomicInteger(0);
        final AiGenerationProvider alwaysEmptyProvider = new AiGenerationProvider() {
            @Override
            public String generate(final AiGenerationRequest request) {
                return "";
            }

            @Override
            public String generate(final AiGenerationRequest request, final float temperatureOverride) {
                retryCallCount.incrementAndGet();
                return "";
            }
        };
        // Build a field generation config with maxRetries=0
        final AiFieldGenerationConfig fieldConfig = new AiFieldGenerationConfig();
        fieldConfig.setPromptKey(CommonTestFixtures.PROMPT_KEY_FILE_BODY);
        final AiGenerationConfig genConfig = new AiGenerationConfig();
        genConfig.setMaxRetries(0);
        fieldConfig.setGeneration(genConfig);

        final AiFieldGenerationSupport support = new AiFieldGenerationSupport(
                capturingLog, alwaysEmptyProvider, new AiPromptPreparationSupport(promptSupport));

        // act
        support.processFieldGenerations(
                List.of(fieldConfig), contextFile, "file", "public class Test {}", header);

        // assert — no retry calls when maxRetries=0
        assertThat(retryCallCount.get(), is(equalTo(0)));
        assertThat(capturingLog.getCapturedWarnings().size(), is(equalTo(1)));
    }
    // </editor-fold>
}
