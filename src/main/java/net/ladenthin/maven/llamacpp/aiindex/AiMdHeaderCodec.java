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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AiMdHeaderCodec {

    /**
     * Current metadata header format version written into every AI document.
     *
     * @see AiMdHeader#h()
     */
    public static final String HEADER_VERSION_1_0 = "1.0";

    /**
     * Node type value for source-file-level AI index documents.
     *
     * @see AiMdHeader#x()
     */
    public static final String NODE_TYPE_FILE = "file";

    /**
     * Node type value for package-level AI index documents.
     *
     * @see AiMdHeader#x()
     */
    public static final String NODE_TYPE_PACKAGE = "package";

    /** Placeholder summary written when no AI summary has been generated yet. */
    public static final String DEFAULT_SUMMARY = "TODO";

    /** Placeholder keywords string written when no AI keywords have been generated yet. */
    public static final String DEFAULT_KEYWORDS = "TODO";

    /**
     * Title of the root AI index node representing the top-level output directory.
     *
     * @see AiMdHeader#title()
     */
    public static final String ROOT_NODE_TITLE = "ai";

    /**
     * File extension appended to every source file name to produce its AI index file name.
     * Example: {@code "MyClass.java"} becomes {@code "MyClass.java.ai.md"}.
     */
    public static final String AI_MD_EXTENSION = ".ai.md";

    /**
     * File name used for package-level AI index documents.
     * One {@value} file is created per indexed package directory.
     */
    public static final String PACKAGE_AI_MD_FILENAME = "package.ai.md";

    /**
     * Prefix of internally generated marker files that must be excluded from content
     * listings and checksum calculations.
     */
    public static final String GENERATED_BY_PREFIX = ".generated-by-";

    public AiMdHeader read(final List<String> lines) {
        String title = null;
        final Map<String, String> values = new HashMap<>();

        for (String line : lines) {
            if (line.startsWith("### ")) {
                title = line.substring(4).trim();
                continue;
            }

            if (!line.startsWith("- ")) {
                continue;
            }

            final int colonIndex = line.indexOf(':');
            if (colonIndex < 0 || colonIndex < 3) {
                continue;
            }

            final String key = line.substring(2, colonIndex).trim();
            final String value = line.substring(colonIndex + 1).trim();
            values.put(key, value);
        }

        return new AiMdHeader(
                nullToEmpty(title),
                valueOrEmpty(values, "H"),
                valueOrEmpty(values, "C"),
                valueOrEmpty(values, "D"),
                valueOrEmpty(values, "T"),
                valueOrEmpty(values, "G"),
                valueOrEmpty(values, "A"),
                valueOrEmpty(values, "X"),
                valueOrEmpty(values, "S"),
                valueOrEmpty(values, "K")
        );
    }

    public String write(final AiMdHeader header) {
        return """
                ### %s
                - H: %s
                - C: %s
                - D: %s
                - T: %s
                - G: %s
                - A: %s
                - X: %s
                - S: %s
                - K: %s
                """.formatted(
                header.title(),
                header.h(),
                header.c(),
                header.d(),
                header.t(),
                header.g(),
                header.a(),
                header.x(),
                header.s(),
                header.k()
        );
    }

    private String valueOrEmpty(final Map<String, String> values, final String key) {
        return nullToEmpty(values.get(key));
    }

    private String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
    
    public AiMdHeader read(final Path file) throws IOException {
        return read(Files.readAllLines(file, StandardCharsets.UTF_8));
    }
}