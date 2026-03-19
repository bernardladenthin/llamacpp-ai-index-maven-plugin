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

import java.util.List;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class AiMdHeaderCodecTest {

    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    // <editor-fold defaultstate="collapsed" desc="write">
    @Test
    public void write_fileNodeHeader_roundtripsToEqualHeader() {
        // arrange
        final AiMdHeader original = new AiMdHeader(
                "GenerateMojo.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "D56BA12A",
                "2026-03-15T18:33:40Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "Mock summary",
                "mock,file"
        );

        // act
        final String encoded = headerCodec.write(original);
        final AiMdHeader decoded = headerCodec.read(List.of(encoded.split("\\R")));

        // assert
        assertThat(decoded, is(equalTo(original)));
    }

    @Test
    public void write_packageNodeHeader_decodedFieldsMatchOriginal() {
        // arrange
        final AiMdHeader original = new AiMdHeader(
                "main/java/net/ladenthin/maven/llamacpp/aiindex",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "9863444A",
                "2026-03-15T18:33:50Z",
                "2026-03-15T18:34:26Z",
                "0.1.0-SNAPSHOT",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                AiMdHeaderCodec.DEFAULT_SUMMARY,
                AiMdHeaderCodec.DEFAULT_KEYWORDS
        );

        // act
        final String encoded = headerCodec.write(original);
        final List<String> lines = List.of(encoded.split("\\R"));
        final AiMdHeader decoded = headerCodec.read(lines);

        // assert
        assertThat(decoded.title(), is(equalTo("main/java/net/ladenthin/maven/llamacpp/aiindex")));
        assertThat(decoded.h(), is(equalTo(AiMdHeaderCodec.HEADER_VERSION_1_0)));
        assertThat(decoded.c(), is(equalTo("9863444A")));
        assertThat(decoded.d(), is(equalTo("2026-03-15T18:33:50Z")));
        assertThat(decoded.t(), is(equalTo("2026-03-15T18:34:26Z")));
        assertThat(decoded.g(), is(equalTo("0.1.0-SNAPSHOT")));
        assertThat(decoded.a(), is(equalTo("0.0.0")));
        assertThat(decoded.x(), is(equalTo(AiMdHeaderCodec.NODE_TYPE_PACKAGE)));
        assertThat(decoded.s(), is(equalTo(AiMdHeaderCodec.DEFAULT_SUMMARY)));
        assertThat(decoded.k(), is(equalTo(AiMdHeaderCodec.DEFAULT_KEYWORDS)));
    }
    // </editor-fold>
}
