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
import java.util.Objects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AiMdHeaderCodec {

    /**
     * Prefix used for the title line in every AI index document header.
     * Example: {@code "### MyClass.java"}.
     */
    public static final String HEADER_TITLE_PREFIX = "### ";

    /**
     * Prefix used for each key-value field line in every AI index document header.
     * Example: {@code "- H: 1.0"}.
     */
    public static final String HEADER_FIELD_PREFIX = "- ";

    /** Field key for the header format version ({@code h}). */
    public static final String FIELD_KEY_H = "H";

    /** Field key for the source file checksum ({@code c}). */
    public static final String FIELD_KEY_C = "C";

    /** Field key for the index creation date ({@code d}). */
    public static final String FIELD_KEY_D = "D";

    /** Field key for the last generation timestamp ({@code t}). */
    public static final String FIELD_KEY_T = "T";

    /** Field key for the plugin version ({@code g}). */
    public static final String FIELD_KEY_G = "G";

    /** Field key for the AI model version ({@code a}). */
    public static final String FIELD_KEY_A = "A";

    /** Field key for the node type ({@code x}). */
    public static final String FIELD_KEY_X = "X";

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

    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    public AiMdHeader read(final List<String> lines) {
        String title = null;
        final Map<String, String> values = new HashMap<>();

        for (String line : lines) {
            if (line.startsWith(HEADER_TITLE_PREFIX)) {
                title = line.substring(HEADER_TITLE_PREFIX.length()).trim();
                continue;
            }

            if (!line.startsWith(HEADER_FIELD_PREFIX)) {
                continue;
            }

            final int colonIndex = line.indexOf(':');
            if (colonIndex < 0 || colonIndex < HEADER_FIELD_PREFIX.length() + 1) {
                continue;
            }

            final String key = line.substring(HEADER_FIELD_PREFIX.length(), colonIndex).trim();
            final String value = line.substring(colonIndex + 1).trim();
            values.put(key, value);
        }

        return new AiMdHeader(
                title != null ? title : "",
                valueOrEmpty(values, FIELD_KEY_H),
                valueOrEmpty(values, FIELD_KEY_C),
                valueOrEmpty(values, FIELD_KEY_D),
                valueOrEmpty(values, FIELD_KEY_T),
                valueOrEmpty(values, FIELD_KEY_G),
                valueOrEmpty(values, FIELD_KEY_A),
                valueOrEmpty(values, FIELD_KEY_X)
        );
    }

    public String write(final AiMdHeader header) {
        return compatibilityHelper.formatted("### %s\n" +
                "- H: %s\n" +
                "- C: %s\n" +
                "- D: %s\n" +
                "- T: %s\n" +
                "- G: %s\n" +
                "- A: %s\n" +
                "- X: %s\n",
                header.title(),
                header.h(),
                header.c(),
                header.d(),
                header.t(),
                header.g(),
                header.a(),
                header.x()
        );
    }

    private String valueOrEmpty(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        return value != null ? value : "";
    }

    public AiMdHeader read(final Path file) throws IOException {
        return read(Files.readAllLines(file, StandardCharsets.UTF_8));
    }
}