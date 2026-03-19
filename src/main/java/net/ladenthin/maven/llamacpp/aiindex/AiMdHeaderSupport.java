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
import java.nio.file.Files;
import java.nio.file.Path;

public class AiMdHeaderSupport {

    /**
     * Separator character used between fields in a checksum line produced by
     * {@link #buildChecksumLine(String, AiMdHeader)}.
     */
    private static final char CHECKSUM_LINE_SEPARATOR = '|';

    public boolean shouldWrite(
            final boolean force,
            final Path targetFile,
            final AiMdHeader expectedHeader
    ) throws IOException {
        if (force) {
            return true;
        }

        if (!Files.exists(targetFile)) {
            return true;
        }

        final AiMdDocument actualDocument = new AiMdDocumentCodec().read(targetFile);
        final AiMdHeader actualHeader = actualDocument.header();

        if (!AiMdHeaderCodec.HEADER_VERSION_1_0.equals(actualHeader.h())) {
            return true;
        }

        return !expectedHeader.h().equals(actualHeader.h())
                || !expectedHeader.x().equals(actualHeader.x())
                || !expectedHeader.title().equals(actualHeader.title())
                || !expectedHeader.c().equals(actualHeader.c())
                || !expectedHeader.d().equals(actualHeader.d())
                || !expectedHeader.g().equals(actualHeader.g())
                || !expectedHeader.a().equals(actualHeader.a());
    }

    public String buildChecksumLine(
            final String name,
            final AiMdHeader childHeader
    ) {
        return name
                + CHECKSUM_LINE_SEPARATOR
                + childHeader.c()
                + CHECKSUM_LINE_SEPARATOR
                + childHeader.d()
                + CHECKSUM_LINE_SEPARATOR
                + childHeader.x()
                + '\n';
    }
}