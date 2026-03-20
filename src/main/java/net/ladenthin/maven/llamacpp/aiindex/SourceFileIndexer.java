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

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class SourceFileIndexer {

    /**
     * Context-type label passed to {@link AiFieldGenerationSupport} so that trim-warning
     * log messages read "Trimmed AI input for file field '…'".
     */
    private static final String CONTEXT_TYPE_FILE = "file";

    private final Log log;
    private final Path baseDirectory;
    private final Path outputRoot;
    private final List<String> fileExtensions;
    private final String pluginVersion;
    private final String aiVersion;
    private final List<Path> subtrees;
    private final boolean force;

    private final List<AiFieldGenerationConfig> fieldGenerations;

    private final AiPathSupport pathSupport = new AiPathSupport();
    private final AiTimeSupport timeSupport = new AiTimeSupport();
    private final AiChecksumSupport checksumSupport = new AiChecksumSupport();
    private final AiMdHeaderSupport headerSupport = new AiMdHeaderSupport();
    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    private final AiFieldGenerationSupport fieldGenerationSupport;

    public SourceFileIndexer(
            final Log log,
            final Path baseDirectory,
            final Path outputRoot,
            final List<String> fileExtensions,
            final String pluginVersion,
            final String aiVersion,
            final List<Path> subtrees,
            final boolean force,
            final AiGenerationProvider generationProvider,
            final List<AiFieldGenerationConfig> fieldGenerations,
            final AiPromptSupport promptSupport
    ) {
        this.log = log;
        this.baseDirectory = baseDirectory;
        this.outputRoot = outputRoot;
        this.fileExtensions = fileExtensions;
        this.pluginVersion = pluginVersion;
        this.aiVersion = aiVersion;
        this.subtrees = subtrees;
        this.force = force;
        this.fieldGenerations = fieldGenerations;
        this.fieldGenerationSupport = new AiFieldGenerationSupport(
                log, generationProvider, new AiPromptPreparationSupport(promptSupport));
    }

    public int indexSourceRoot(final Path sourceRoot) throws IOException {
        int count = 0;

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (!matchesExtension(path)) {
                    continue;
                }

                if (!matchesSubtree(path)) {
                    continue;
                }

                writeAiFile(path);
                count++;
            }
        }

        return count;
    }

    private boolean matchesExtension(final Path path) {
        final String fileName = path.getFileName().toString();
        for (String extension : fileExtensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void writeAiFile(final Path sourceFile) throws IOException {
        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            throw new IllegalArgumentException("No field generations configured for source file indexing.");
        }

        final Path relativeToBase = pathSupport.relativizeFromSrc(baseDirectory, sourceFile);
        final Path targetFile = outputRoot
                .resolve(relativeToBase.toString() + AiMdHeaderCodec.AI_MD_EXTENSION)
                .normalize();

        Files.createDirectories(targetFile.getParent());

        final String fileName = sourceFile.getFileName().toString();
        final String checksum = checksumSupport.calculateCrc32Hex(sourceFile);
        final String sourceModified = timeSupport.formatInstant(Files.getLastModifiedTime(sourceFile).toInstant());
        final String generatedAt = timeSupport.formatInstant(java.time.Instant.now());

        final AiMdHeader baseHeader = new AiMdHeader(
                fileName,
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                checksum,
                sourceModified,
                generatedAt,
                pluginVersion,
                aiVersion,
                AiMdHeaderCodec.NODE_TYPE_FILE
        );

        if (!headerSupport.shouldWrite(force, targetFile, baseHeader)) {
            log.info("Unchanged AI index file: " + targetFile);
            return;
        }

        final String sourceText = Files.readString(sourceFile);

        final AiGenerationResult result = fieldGenerationSupport.processFieldGenerations(
                fieldGenerations, sourceFile, CONTEXT_TYPE_FILE, sourceText, baseHeader);

        final AiMdDocument document = new AiMdDocument(baseHeader, result.body());
        documentCodec.write(targetFile, document);

        log.info("Wrote AI index file: " + targetFile);
    }

    private boolean matchesSubtree(final Path path) {
        if (subtrees == null || subtrees.isEmpty()) {
            return true;
        }

        for (Path subtree : subtrees) {
            if (path.startsWith(subtree)) {
                return true;
            }
        }

        return false;
    }
}
