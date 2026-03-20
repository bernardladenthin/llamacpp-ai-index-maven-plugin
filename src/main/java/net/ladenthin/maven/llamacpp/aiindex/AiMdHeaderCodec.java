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
     * Opening tag for the AI index document header.
     */
    public static final String HEADER_OPENING_TAG = "<!-- ai-md-header";

    /**
     * Closing tag for the AI index document header.
     */
    public static final String HEADER_CLOSING_TAG = "-->";

    /** Field key for the header format version ({@code h}). */
    public static final String FIELD_KEY_H = "h";

    /** Field key for the source file checksum ({@code c}). */
    public static final String FIELD_KEY_C = "c";

    /** Field key for the index creation date ({@code d}). */
    public static final String FIELD_KEY_D = "d";

    /** Field key for the last generation timestamp ({@code t}). */
    public static final String FIELD_KEY_T = "t";

    /** Field key for the plugin version ({@code g}). */
    public static final String FIELD_KEY_G = "g";

    /** Field key for the AI model version ({@code a}). */
    public static final String FIELD_KEY_A = "a";

    /** Field key for the node type ({@code x}). */
    public static final String FIELD_KEY_X = "x";

    /** Field key for the title ({@code title}). */
    public static final String FIELD_KEY_TITLE = "title";

    /** Field key for the AI-generated summary ({@code s}). */
    public static final String FIELD_KEY_S = "s";

    /** Field key for the AI-generated keywords ({@code k}). */
    public static final String FIELD_KEY_K = "k";

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

    public AiMdHeader read(final List<String> lines) {
        final Map<String, String> values = new HashMap<>();
        boolean inHeader = false;

        for (String line : lines) {
            if (line.startsWith(HEADER_OPENING_TAG)) {
                inHeader = true;
                continue;
            }

            if (line.startsWith(HEADER_CLOSING_TAG)) {
                break;
            }

            if (!inHeader || line.isBlank()) {
                continue;
            }

            final int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            final String key = line.substring(0, colonIndex).trim();
            final String rawValue = line.substring(colonIndex + 1).trim();
            // Remove surrounding quotes if present
            final String value = rawValue.startsWith("\"") && rawValue.endsWith("\"")
                    ? rawValue.substring(1, rawValue.length() - 1)
                    : rawValue;
            values.put(key, value);
        }

        return new AiMdHeader(
                valueOrEmpty(values, FIELD_KEY_TITLE),
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
        return """
                <!-- ai-md-header
                h: "%s"
                title: "%s"
                c: "%s"
                d: "%s"
                t: "%s"
                g: "%s"
                a: "%s"
                x: "%s"
                -->
                """.formatted(
                escapeQuotes(header.h()),
                escapeQuotes(header.title()),
                escapeQuotes(header.c()),
                escapeQuotes(header.d()),
                escapeQuotes(header.t()),
                escapeQuotes(header.g()),
                escapeQuotes(header.a()),
                escapeQuotes(header.x())
        );
    }

    private String escapeQuotes(final String value) {
        return value.replace("\"", "\\\"");
    }

    private String valueOrEmpty(final Map<String, String> values, final String key) {
        return Objects.requireNonNullElse(values.get(key), "");
    }

    public AiMdHeader read(final Path file) throws IOException {
        return read(Files.readAllLines(file, StandardCharsets.UTF_8));
    }
}