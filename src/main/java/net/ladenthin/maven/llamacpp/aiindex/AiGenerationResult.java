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

/**
 * Immutable result produced by {@link AiFieldGenerationSupport#processFieldGenerations}.
 *
 * <p>Groups the three generated field values that a single processing pass may produce:
 * a header summary, a header keyword string, and a document body. All fields default to
 * an empty string when the corresponding target is not present in the field generation
 * configuration.</p>
 *
 * @param summary  AI-generated summary text destined for {@link AiMdHeader#s()}
 * @param keywords AI-generated keywords string destined for {@link AiMdHeader#k()}
 * @param body     AI-generated body text destined for {@link AiMdDocument#body()}
 */
public record AiGenerationResult(
        String summary,
        String keywords,
        String body
) {}
