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

@ConvertToRecord
public class AiMdDocument {
    private final AiMdHeader header;
    private final String body;


    public AiMdDocument(AiMdHeader header, String body) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(body, "body");
        this.header = header;
        this.body = body;
    }

    public AiMdHeader header() {
        return header;
    }

    public String body() {
        return body;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        AiMdDocument that = (AiMdDocument) obj;
        return Objects.equals(this.header, that.header) &&
                Objects.equals(this.body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, body);
    }

    @Override
    public String toString() {
        return "AiMdDocument[" +
                "header=" + header + ", " +
                "body=" + body + ']';
    }

}