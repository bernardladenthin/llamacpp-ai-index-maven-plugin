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
import java.util.List;

public class AiMdDocumentCodec {

    public AiMdDocument read(final Path file) throws IOException {
        return read(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public AiMdDocument read(final List<String> lines) {
        final AiMdHeader header = new AiMdHeaderCodec().read(lines);

        final StringBuilder body = new StringBuilder();
        boolean bodyStarted = false;
        boolean headerFinished = false;

        for (String line : lines) {
            if (!headerFinished) {
                if (line.startsWith(AiMdHeaderCodec.HEADER_TITLE_PREFIX) || line.startsWith(AiMdHeaderCodec.HEADER_FIELD_PREFIX)) {
                    continue;
                }

                if (line.isBlank()) {
                    headerFinished = true;
                    continue;
                }

                headerFinished = true;
            }

            if (!bodyStarted) {
                if (line.isBlank()) {
                    continue;
                }
                bodyStarted = true;
            }

            body.append(line).append('\n');
        }

        return new AiMdDocument(header, body.toString());
    }

    public String write(final AiMdDocument document) {
        final StringBuilder builder = new StringBuilder();
        builder.append(new AiMdHeaderCodec().write(document.header()));

        if (!document.body().isBlank()) {
            builder.append('\n');
            builder.append(document.body());
            if (!document.body().endsWith("\n")) {
                builder.append('\n');
            }
        }

        return builder.toString();
    }

    public void write(final Path file, final AiMdDocument document) throws IOException {
        Files.writeString(file, write(document), StandardCharsets.UTF_8);
    }
}