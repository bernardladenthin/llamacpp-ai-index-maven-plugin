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

import java.nio.file.Path;

public class AiPathSupport {

    public Path relativizeFromSrc(final Path baseDirectory, final Path path) {
        final Path relative = baseDirectory.relativize(path);
        if (relative.getNameCount() > 0 && "src".equals(relative.getName(0).toString())) {
            return relative.subpath(1, relative.getNameCount());
        }
        return relative;
    }
}
