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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class AiChecksumSupport {

    public String calculateCrc32Hex(final Path file) throws IOException {
        final byte[] bytes = Files.readAllBytes(file);
        return calculateCrc32Hex(bytes);
    }

    public String calculateCrc32Hex(final String value) {
        return calculateCrc32Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String calculateCrc32Hex(final byte[] bytes) {
        final CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return String.format("%08X", crc32.getValue());
    }
}