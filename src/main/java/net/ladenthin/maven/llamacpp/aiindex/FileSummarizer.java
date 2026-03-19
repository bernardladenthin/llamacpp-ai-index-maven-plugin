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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class FileSummarizer {

    private final Log log;
    private final Path baseDirectory;
    private final Path outputRoot;
    private final List<Path> subtrees;
    private final boolean force;
    private final AiGenerationProvider generationProvider;
    private final List<AiFieldGenerationConfig> fieldGenerations;
    private final AiPromptPreparationSupport promptPreparationSupport;

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    public FileSummarizer(
            final Log log,
            final Path baseDirectory,
            final Path outputRoot,
            final List<Path> subtrees,
            final boolean force,
            final AiGenerationProvider generationProvider,
            final List<AiFieldGenerationConfig> fieldGenerations,
            final AiPromptSupport promptSupport
    ) {
        this.log = log;
        this.baseDirectory = baseDirectory;
        this.outputRoot = outputRoot;
        this.subtrees = subtrees;
        this.force = force;
        this.generationProvider = generationProvider;
        this.fieldGenerations = fieldGenerations;
        this.promptPreparationSupport = new AiPromptPreparationSupport(promptSupport);
    }

    public int summarizeFiles() throws IOException {
        if (!Files.exists(outputRoot)) {
            log.info("AI output directory does not exist, skipping file summarization: " + outputRoot);
            return 0;
        }

        int count = 0;

        try (Stream<Path> stream = Files.walk(outputRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                final String name = path.getFileName().toString();
                if (!name.endsWith(AiMdHeaderCodec.AI_MD_EXTENSION)) {
                    continue;
                }
                if (AiMdHeaderCodec.PACKAGE_AI_MD_FILENAME.equals(name)) {
                    continue;
                }
                if (!matchesSubtree(path)) {
                    continue;
                }

                if (summarizeFile(path)) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean summarizeFile(final Path aiFile) throws IOException {
        final AiMdDocument document = documentCodec.read(aiFile);
        final AiMdHeader header = document.header();

        if (!AiMdHeaderCodec.NODE_TYPE_FILE.equals(header.x())) {
            return false;
        }

        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            log.info("No field generations configured for AI file: " + aiFile);
            return false;
        }

        final Path sourceFile = toSourceFile(aiFile);
        if (!Files.exists(sourceFile)) {
            log.warn("Skipping missing source file for AI summary: " + sourceFile);
            return false;
        }

        final String sourceText = Files.readString(sourceFile, StandardCharsets.UTF_8);

        String summary = header.s();
        String keywords = header.k();
        String body = document.body();

        boolean changed = false;

        for (AiFieldGenerationConfig fieldGeneration : fieldGenerations) {
            if (fieldGeneration == null) {
                continue;
            }

            final String target = fieldGeneration.getTarget();
            if (!force && !needsGeneration(target, header, document)) {
                continue;
            }

            final AiGenerationConfig generationConfig = fieldGeneration.getGeneration();
            if (generationConfig == null) {
                throw new IllegalArgumentException("Missing generation config for field: " + fieldGeneration.getFieldName());
            }

            final AiGenerationRequest request = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    sourceFile,
                    sourceText,
                    header
            );

            final AiPreparedPrompt preparedPrompt = promptPreparationSupport.preparePrompt(
                    request,
                    generationConfig.getMaxInputChars()
            );

            if (preparedPrompt.trimmed() && generationConfig.isWarnOnTrim()) {
                log.warn("Trimmed AI input for file field '" + fieldGeneration.getFieldName() + "': " + sourceFile
                        + " (source chars " + preparedPrompt.originalSourceLength()
                        + " -> " + preparedPrompt.trimmedSourceLength()
                        + ", available source chars " + preparedPrompt.availableSourceChars()
                        + ", max input chars " + generationConfig.getMaxInputChars() + ")");
            }

            final String generatedValue = generationProvider.generate(new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    sourceFile,
                    preparedPrompt.sourceText(),
                    header
            ));

            if (AiFieldGenerationConfig.TARGET_HEADER_SUMMARY.equals(target)) {
                summary = generatedValue;
            } else if (AiFieldGenerationConfig.TARGET_HEADER_KEYWORDS.equals(target)) {
                keywords = generatedValue;
            } else if (AiFieldGenerationConfig.TARGET_BODY.equals(target)) {
                body = generatedValue;
            } else {
                throw new IllegalArgumentException("Unsupported field target: " + target);
            }

            changed = true;
        }

        if (!changed) {
            log.info("Unchanged AI summary file: " + aiFile);
            return false;
        }

        final AiMdHeader updatedHeader = new AiMdHeader(
                header.title(),
                header.h(),
                header.c(),
                header.d(),
                header.t(),
                header.g(),
                header.a(),
                header.x(),
                summary,
                keywords
        );

        final AiMdDocument updatedDocument = new AiMdDocument(updatedHeader, body);
        documentCodec.write(aiFile, updatedDocument);

        log.info("Summarized AI file: " + aiFile);
        return true;
    }

    private boolean needsGeneration(
            final String target,
            final AiMdHeader header,
            final AiMdDocument document
    ) {
        if (AiFieldGenerationConfig.TARGET_HEADER_SUMMARY.equals(target)) {
            return header.s() == null || header.s().isBlank();
        }
        if (AiFieldGenerationConfig.TARGET_HEADER_KEYWORDS.equals(target)) {
            return header.k() == null || header.k().isBlank();
        }
        if (AiFieldGenerationConfig.TARGET_BODY.equals(target)) {
            return document.body() == null || document.body().isBlank();
        }
        throw new IllegalArgumentException("Unsupported field target: " + target);
    }

    private boolean matchesSubtree(final Path aiFile) {
        if (subtrees == null || subtrees.isEmpty()) {
            return true;
        }

        final Path sourceFile = toSourceFile(aiFile);
        for (Path subtree : subtrees) {
            if (sourceFile.startsWith(subtree)) {
                return true;
            }
        }

        return false;
    }

    private Path toSourceFile(final Path aiFile) {
        final Path relativeToOutput = outputRoot.relativize(aiFile);
        final String fileName = relativeToOutput.getFileName().toString();
        final String sourceFileName = fileName.substring(0, fileName.length() - AiMdHeaderCodec.AI_MD_EXTENSION.length());

        final Path relativeParent = relativeToOutput.getParent();
        final Path relativeSource = relativeParent == null
                ? Path.of(sourceFileName)
                : relativeParent.resolve(sourceFileName);

        return baseDirectory.resolve("src").resolve(relativeSource).normalize();
    }
}