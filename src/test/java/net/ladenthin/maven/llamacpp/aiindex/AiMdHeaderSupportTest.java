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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AiMdHeaderSupportTest {

    private final AiMdHeaderSupport headerSupport = new AiMdHeaderSupport();
    private final AiMdHeaderCodec headerCodec = new AiMdHeaderCodec();

    @Test
    public void shouldWriteIfFileDoesNotExist() throws IOException {

        final Path temp = Files.createTempDirectory("ai-md-test");
        final Path target = temp.resolve("test.ai.md");

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "12345678",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        final boolean result = headerSupport.shouldWrite(false, target, header);

        Assert.assertTrue(result);
    }

    @Test
    public void shouldNotWriteIfHeaderMatches() throws IOException {

        final Path temp = Files.createTempDirectory("ai-md-test");
        final Path target = temp.resolve("test.ai.md");

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "ABCDEF12",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        final String content = headerCodec.write(header);
        Files.writeString(target, content);

        final boolean result = headerSupport.shouldWrite(false, target, header);

        Assert.assertFalse(result);
    }

    @Test
    public void shouldWriteIfChecksumChanges() throws IOException {

        final Path temp = Files.createTempDirectory("ai-md-test");
        final Path target = temp.resolve("test.ai.md");

        final AiMdHeader original = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "AAAAAAAA",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        Files.writeString(target, headerCodec.write(original));

        final AiMdHeader changed = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "BBBBBBBB",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        final boolean result = headerSupport.shouldWrite(false, target, changed);

        Assert.assertTrue(result);
    }

    @Test
    public void shouldWriteIfGeneratorVersionChanges() throws IOException {

        final Path temp = Files.createTempDirectory("ai-md-test");
        final Path target = temp.resolve("test.ai.md");

        final AiMdHeader original = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "12345678",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        Files.writeString(target, headerCodec.write(original));

        final AiMdHeader changed = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "12345678",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "2.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        final boolean result = headerSupport.shouldWrite(false, target, changed);

        Assert.assertTrue(result);
    }

    @Test
    public void shouldAlwaysWriteWhenForceEnabled() throws IOException {

        final Path temp = Files.createTempDirectory("ai-md-test");
        final Path target = temp.resolve("test.ai.md");

        final AiMdHeader header = new AiMdHeader(
                "Test.java",
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                "12345678",
                "2026-03-16T00:00:00Z",
                "2026-03-16T00:00:10Z",
                "1.0.0",
                "0.0.0",
                AiMdHeaderCodec.NODE_TYPE_FILE,
                "TODO",
                "TODO"
        );

        Files.writeString(target, headerCodec.write(header));

        final boolean result = headerSupport.shouldWrite(true, target, header);

        Assert.assertTrue(result);
    }
}