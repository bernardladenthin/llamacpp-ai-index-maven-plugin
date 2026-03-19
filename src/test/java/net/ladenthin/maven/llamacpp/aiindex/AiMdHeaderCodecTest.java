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

import java.util.List;

public class AiMdHeaderCodecTest {
    
    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    @Test
    public void shouldRoundtripHeader() {
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

        final String encoded = headerCodec.write(original);
        final AiMdHeader decoded = headerCodec.read(List.of(encoded.split("\\R")));

        Assert.assertEquals(original, decoded);
    }

    @Test
    public void shouldReadWrittenHeaderFromLines() {
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

        final String encoded = headerCodec.write(original);
        final List<String> lines = List.of(encoded.split("\\R"));

        final AiMdHeader decoded = headerCodec.read(lines);

        Assert.assertEquals("main/java/net/ladenthin/maven/llamacpp/aiindex", decoded.title());
        Assert.assertEquals(AiMdHeaderCodec.HEADER_VERSION_1_0, decoded.h());
        Assert.assertEquals("9863444A", decoded.c());
        Assert.assertEquals("2026-03-15T18:33:50Z", decoded.d());
        Assert.assertEquals("2026-03-15T18:34:26Z", decoded.t());
        Assert.assertEquals("0.1.0-SNAPSHOT", decoded.g());
        Assert.assertEquals("0.0.0", decoded.a());
        Assert.assertEquals(AiMdHeaderCodec.NODE_TYPE_PACKAGE, decoded.x());
        Assert.assertEquals(AiMdHeaderCodec.DEFAULT_SUMMARY, decoded.s());
        Assert.assertEquals(AiMdHeaderCodec.DEFAULT_KEYWORDS, decoded.k());
    }
}
