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

import java.io.IOException;
import java.nio.file.Path;

public class MockAiGenerationProvider implements AiGenerationProvider {

    /**
     * Suffix that identifies a keywords-generation request.
     * Prompt keys ending with this suffix trigger the mock keywords response;
     * all other keys receive the mock body/summary response.
     */
    private static final String KEYWORDS_PROMPT_SUFFIX = "-keywords";

    @Override
    public String generate(final AiGenerationRequest request) throws IOException {
        final Path file = request.sourceFile();
        final String fileName = file.getFileName().toString();

        if (request.promptKey().endsWith(KEYWORDS_PROMPT_SUFFIX)) {
            return "mock,keywords," + fileName;
        }

        return "Mock summary for " + fileName;
    }
}