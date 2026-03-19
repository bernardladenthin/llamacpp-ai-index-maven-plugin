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

import java.util.Objects;

/**
 * Canonical header model for a single {@code .ai.md} document.
 *
 * <p>The header is the stable machine-readable metadata block used by the generator
 * and by later AI enrichment steps. It is intentionally compact and versioned.</p>
 *
 * <p>Field semantics:</p>
 * <ul>
 *   <li><b>title</b>: Display title of the node, usually the file name or logical package path.</li>
 *   <li><b>h</b>: Header format version. Used to detect incompatible header schema changes.</li>
 *   <li><b>c</b>: Checksum of the represented node state.
 *     <ul>
 *       <li>For {@code file}: CRC32 of the source file content.</li>
 *       <li>For {@code package}: deterministic CRC32 derived from the direct child entries,
 *           ordered ascending by child name/path.</li>
 *     </ul>
 *   </li>
 *   <li><b>d</b>: Source date of the represented node state.
 *     <ul>
 *       <li>For {@code file}: last modification timestamp of the source file.</li>
 *       <li>For {@code package}: newest {@code d} value of the direct child entries.</li>
 *     </ul>
 *   </li>
 *   <li><b>t</b>: Timestamp when this {@code .ai.md} document was last generated.</li>
 *   <li><b>g</b>: Version of the template/generator implementation that produced this document.</li>
 *   <li><b>a</b>: Version of the AI summarization logic or AI output schema applied to this document.</li>
 *   <li><b>x</b>: Node type, for example {@code file} or {@code package}.</li>
 *   <li><b>s</b>: Compact summary text intended for fast navigation and retrieval.</li>
 *   <li><b>k</b>: Compact keyword list intended for retrieval and indexing.</li>
 * </ul>
 *
 * <p>Important invariants:</p>
 * <ul>
 *   <li>Header comparison for rewrite decisions should be based on structural fields such as
 *       {@code h}, {@code c}, {@code d}, {@code g}, {@code a}, {@code x}, and {@code title},
 *       not on generated text fields such as {@code s} and {@code k}.</li>
 *   <li>Package aggregation must be deterministic. Child traversal order must therefore be stable,
 *       typically ascending by child file name or relative path.</li>
 *   <li>The generator should preserve AI-authored fields when the structural state did not change.</li>
 * </ul>
 *
 * @param title display title of the node
 * @param h header format version
 * @param c checksum of the represented node state
 * @param d source date of the represented node state
 * @param t generation timestamp of this {@code .ai.md}
 * @param g generator version
 * @param a AI generation version
 * @param x node type, e.g. {@code file} or {@code package}
 * @param s compact summary
 * @param k compact keywords
 */
public record AiMdHeader(
        String title,
        String h,
        String c,
        String d,
        String t,
        String g,
        String a,
        String x,
        String s,
        String k
) {

    public AiMdHeader {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(h, "h");
        Objects.requireNonNull(c, "c");
        Objects.requireNonNull(d, "d");
        Objects.requireNonNull(t, "t");
        Objects.requireNonNull(g, "g");
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(s, "s");
        Objects.requireNonNull(k, "k");
    }
}