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

public class PackageSummarizer {

    private final Log log;
    private final Path baseDirectory;
    private final Path outputRoot;
    private final List<Path> subtrees;
    private final boolean force;
    private final AiGenerationProvider generationProvider;
    private final List<AiFieldGenerationConfig> fieldGenerations;
    private final AiPromptPreparationSupport promptPreparationSupport;

    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    public PackageSummarizer(
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

    public int summarizePackages() throws IOException {
        if (!Files.exists(outputRoot)) {
            log.info("AI output directory does not exist, skipping package summarization: " + outputRoot);
            return 0;
        }

        int count = 0;

        try (Stream<Path> stream = Files.walk(outputRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                final String name = path.getFileName().toString();
                if (!"package.ai.md".equals(name)) {
                    continue;
                }
                if (!matchesSubtree(path)) {
                    continue;
                }

                if (summarizePackage(path)) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean summarizePackage(final Path aiFile) throws IOException {
        final AiMdDocument document = documentCodec.read(aiFile);
        final AiMdHeader header = document.header();

        if (!AiMdHeaderCodec.NODE_TYPE_PACKAGE.equals(header.x())) {
            return false;
        }

        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            log.info("No field generations configured for AI package: " + aiFile);
            return false;
        }

        final String sourceText = documentCodec.write(document);

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
                    aiFile,
                    sourceText,
                    header
            );

            final AiPreparedPrompt preparedPrompt = promptPreparationSupport.preparePrompt(
                    request,
                    generationConfig.getMaxInputChars()
            );

            if (preparedPrompt.trimmed() && generationConfig.isWarnOnTrim()) {
                log.warn("Trimmed AI input for package field '" + fieldGeneration.getFieldName() + "': " + aiFile
                        + " (source chars " + preparedPrompt.originalSourceLength()
                        + " -> " + preparedPrompt.trimmedSourceLength()
                        + ", available source chars " + preparedPrompt.availableSourceChars()
                        + ", max input chars " + generationConfig.getMaxInputChars() + ")");
            }

            final String generatedValue = generationProvider.generate(new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    aiFile,
                    preparedPrompt.sourceText(),
                    header
            ));

            if ("header.s".equals(target)) {
                summary = generatedValue;
            } else if ("header.k".equals(target)) {
                keywords = generatedValue;
            } else if ("body".equals(target)) {
                body = generatedValue;
            } else {
                throw new IllegalArgumentException("Unsupported field target: " + target);
            }

            changed = true;
        }

        if (!changed) {
            log.info("Unchanged AI package summary file: " + aiFile);
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

        log.info("Summarized AI package file: " + aiFile);
        return true;
    }

    private boolean needsGeneration(
            final String target,
            final AiMdHeader header,
            final AiMdDocument document
    ) {
        if ("header.s".equals(target)) {
            return header.s() == null || header.s().isBlank();
        }
        if ("header.k".equals(target)) {
            return header.k() == null || header.k().isBlank();
        }
        if ("body".equals(target)) {
            return document.body() == null || document.body().isBlank();
        }
        throw new IllegalArgumentException("Unsupported field target: " + target);
    }

    private boolean matchesSubtree(final Path aiFile) {
        if (subtrees == null || subtrees.isEmpty()) {
            return true;
        }

        final Path sourceLikePath = toSourceLikePath(aiFile);
        for (Path subtree : subtrees) {
            if (sourceLikePath.startsWith(subtree)) {
                return true;
            }
        }

        return false;
    }

    private Path toSourceLikePath(final Path aiFile) {
        final Path relativeToOutput = outputRoot.relativize(aiFile);
        final Path parent = relativeToOutput.getParent();

        if (parent == null) {
            return baseDirectory.resolve("src").normalize();
        }

        return baseDirectory.resolve("src").resolve(parent).normalize();
    }
}