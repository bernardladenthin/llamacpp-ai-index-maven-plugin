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
public class AiSummaryResponse {
    private final String summary;
    private final String keywords;

    public AiSummaryResponse(String summary, String keywords) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(keywords, "keywords");
        this.summary = summary;
        this.keywords = keywords;
    }

    public String summary() {
        return summary;
    }

    public String keywords() {
        return keywords;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        AiSummaryResponse that = (AiSummaryResponse) obj;
        return Objects.equals(this.summary, that.summary) &&
                Objects.equals(this.keywords, that.keywords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(summary, keywords);
    }

    @Override
    public String toString() {
        return "AiSummaryResponse[" +
                "summary=" + summary + ", " +
                "keywords=" + keywords + ']';
    }

}