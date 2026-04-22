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
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.fail;

public class AiResponseNormalizerTest {

    private final AiResponseNormalizer normalizer = new AiResponseNormalizer();

    // <editor-fold defaultstate="collapsed" desc="normalize">
    @Test
    public void normalize_completeThinkingBlock_returnsBodyOnly() throws Exception {
        // arrange
        final String response = AiResponseNormalizer.THINKING_BLOCK_START_MARKER + "\nreasoning\n"
                + AiResponseNormalizer.THINKING_BLOCK_END_MARKER + "\nactual answer";

        // act
        final String result = normalizer.normalize(response);

        // assert
        assertThat(result, is(equalTo("actual answer")));
    }

    @Test
    public void normalize_truncatedThinkingBlock_throwsIOException() {
        // arrange
        final String response = AiResponseNormalizer.THINKING_BLOCK_START_MARKER + "\nreasoning only, no end";

        try {
            // act
            normalizer.normalize(response);
            fail("Expected IOException");
        } catch (final IOException e) {
            // assert
            assertThat(e.getMessage(), containsString(AiResponseNormalizer.THINKING_BLOCK_START_MARKER));
            assertThat(e.getMessage(), containsString(AiResponseNormalizer.THINKING_BLOCK_END_MARKER));
        }
    }

    @Test
    public void normalize_noThinkingBlock_returnsTrimmedResponse() throws Exception {
        // arrange
        final String response = "  plain answer  ";

        // act
        final String result = normalizer.normalize(response);

        // assert
        assertThat(result, is(equalTo("plain answer")));
    }
    // </editor-fold>
}
