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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class PackageIndexer {

    private final Log log;
    private final Path baseDirectory;
    private final Path outputRoot;
    private final String pluginVersion;
    private final String aiVersion;
    private final List<Path> sourceSubtrees;
    private final List<Path> aiSubtrees;
    private final boolean force;

    private final AiGenerationProvider generationProvider;
    private final List<AiFieldGenerationConfig> fieldGenerations;
    private final AiPromptPreparationSupport promptPreparationSupport;

    private final AiPathSupport pathSupport = new AiPathSupport();
    private final AiTimeSupport timeSupport = new AiTimeSupport();
    private final AiChecksumSupport checksumSupport = new AiChecksumSupport();
    private final AiMdHeaderSupport headerSupport = new AiMdHeaderSupport();
    private final AiMdDocumentCodec documentCodec = new AiMdDocumentCodec();

    public PackageIndexer(
            final Log log,
            final Path baseDirectory,
            final Path outputRoot,
            final String pluginVersion,
            final String aiVersion,
            final List<Path> sourceSubtrees,
            final boolean force,
            final AiGenerationProvider generationProvider,
            final List<AiFieldGenerationConfig> fieldGenerations,
            final AiPromptSupport promptSupport
    ) {
        this.log = log;
        this.baseDirectory = baseDirectory;
        this.outputRoot = outputRoot;
        this.pluginVersion = pluginVersion;
        this.aiVersion = aiVersion;
        this.sourceSubtrees = sourceSubtrees;
        this.aiSubtrees = toAiSubtrees(sourceSubtrees);
        this.force = force;
        this.generationProvider = generationProvider;
        this.fieldGenerations = fieldGenerations;
        this.promptPreparationSupport = new AiPromptPreparationSupport(promptSupport);
    }

    public int aggregate(final Path rootDirectory) throws IOException {
        return aggregateRecursive(rootDirectory);
    }

    private int aggregateRecursive(final Path directory) throws IOException {
        int count = 0;

        final List<Path> subDirectories;
        try (Stream<Path> stream = Files.list(directory)) {
            subDirectories = stream
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }

        for (Path subDirectory : subDirectories) {
            count += aggregateRecursive(subDirectory);
        }

        if (!matchesAggregationScope(directory)) {
            return count;
        }

        if (shouldCreatePackageFile(directory)) {
            writePackageFile(directory);
            count++;
        }

        return count;
    }

    private boolean matchesAggregationScope(final Path directory) {
        if (aiSubtrees == null || aiSubtrees.isEmpty()) {
            return true;
        }

        for (Path aiSubtree : aiSubtrees) {
            if (directory.startsWith(aiSubtree)) {
                return true;
            }
            if (aiSubtree.startsWith(directory)) {
                return true;
            }
        }

        return false;
    }

    private List<Path> toAiSubtrees(final List<Path> sourceSubtrees) {
        final List<Path> result = new ArrayList<>();
        if (sourceSubtrees == null || sourceSubtrees.isEmpty()) {
            return result;
        }

        for (Path sourceSubtree : sourceSubtrees) {
            final Path relative = pathSupport.relativizeFromSrc(baseDirectory, sourceSubtree);
            result.add(outputRoot.resolve(relative).normalize());
        }

        return result;
    }

    private boolean shouldCreatePackageFile(final Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.anyMatch(path -> {
                final String name = path.getFileName().toString();
                if (Files.isDirectory(path)) {
                    return Files.exists(path.resolve("package.ai.md"));
                }
                return name.endsWith(".ai.md")
                        && !name.equals("package.ai.md")
                        && !name.startsWith(".generated-by-");
            });
        }
    }

    private void writePackageFile(final Path directory) throws IOException {
        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            throw new IllegalArgumentException("No field generations configured for package indexing.");
        }

        final List<String> contents = collectContents(directory);
        final Path packageFile = directory.resolve("package.ai.md");

        final String nodeName = outputRoot.relativize(directory).toString().replace('\\', '/');
        final String title = nodeName.isEmpty() ? "ai" : nodeName;
        final String packageChecksum = calculatePackageChecksum(directory);
        final String packageDate = calculatePackageDate(directory);
        final String generatedAt = timeSupport.formatInstant(java.time.Instant.now());

        final AiMdHeader baseHeader = new AiMdHeader(
                title,
                AiMdHeaderCodec.HEADER_VERSION_1_0,
                packageChecksum,
                packageDate,
                generatedAt,
                pluginVersion,
                aiVersion,
                AiMdHeaderCodec.NODE_TYPE_PACKAGE,
                "",
                ""
        );

        if (!headerSupport.shouldWrite(force, packageFile, baseHeader)) {
            log.info("Unchanged package AI index file: " + packageFile);
            return;
        }

        final String sourceText = buildPackageSourceText(baseHeader, contents);

        String summary = "";
        String keywords = "";
        String body = "";

        for (AiFieldGenerationConfig fieldGeneration : fieldGenerations) {
            if (fieldGeneration == null) {
                continue;
            }

            final AiGenerationConfig generationConfig = fieldGeneration.getGeneration();
            if (generationConfig == null) {
                throw new IllegalArgumentException("Missing generation config for field: " + fieldGeneration.getFieldName());
            }

            final AiGenerationRequest request = new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    packageFile,
                    sourceText,
                    baseHeader
            );

            final AiPreparedPrompt preparedPrompt = promptPreparationSupport.preparePrompt(
                    request,
                    generationConfig.getMaxInputChars()
            );

            if (preparedPrompt.trimmed() && generationConfig.isWarnOnTrim()) {
                log.warn("Trimmed AI input for package field '" + fieldGeneration.getFieldName() + "': " + packageFile
                        + " (source chars " + preparedPrompt.originalSourceLength()
                        + " -> " + preparedPrompt.trimmedSourceLength()
                        + ", available source chars " + preparedPrompt.availableSourceChars()
                        + ", max input chars " + generationConfig.getMaxInputChars() + ")");
            }

            final String generatedValue = generationProvider.generate(new AiGenerationRequest(
                    fieldGeneration.getPromptKey(),
                    packageFile,
                    preparedPrompt.sourceText(),
                    baseHeader
            ));

            final String target = fieldGeneration.getTarget();
            if ("header.s".equals(target)) {
                summary = generatedValue;
            } else if ("header.k".equals(target)) {
                keywords = generatedValue;
            } else if ("body".equals(target)) {
                body = generatedValue;
            } else {
                throw new IllegalArgumentException("Unsupported field target: " + target);
            }
        }

        if (body == null || body.isBlank()) {
            body = buildDefaultPackageBody(contents);
        }

        final AiMdHeader finalHeader = new AiMdHeader(
                baseHeader.title(),
                baseHeader.h(),
                baseHeader.c(),
                baseHeader.d(),
                baseHeader.t(),
                baseHeader.g(),
                baseHeader.a(),
                baseHeader.x(),
                summary,
                keywords
        );

        final AiMdDocument document = new AiMdDocument(finalHeader, body);
        documentCodec.write(packageFile, document);

        log.info("Wrote package AI index file: " + packageFile);
    }

    private List<String> collectContents(final Path directory) throws IOException {
        final List<String> entries = new ArrayList<>();

        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                final String name = path.getFileName().toString();

                if (Files.isDirectory(path)) {
                    if (Files.exists(path.resolve("package.ai.md"))) {
                        entries.add(path.getFileName().toString() + "/");
                    }
                    continue;
                }

                if (name.equals("package.ai.md") || name.startsWith(".generated-by-")) {
                    continue;
                }

                if (name.endsWith(".ai.md")) {
                    entries.add(name);
                }
            }
        }

        return entries;
    }

    private String buildPackageSourceText(final AiMdHeader header, final List<String> contents) {
        final StringBuilder builder = new StringBuilder();
        builder.append("### ").append(header.title()).append('\n');
        builder.append("- H: ").append(header.h()).append('\n');
        builder.append("- C: ").append(header.c()).append('\n');
        builder.append("- D: ").append(header.d()).append('\n');
        builder.append("- T: ").append(header.t()).append('\n');
        builder.append("- G: ").append(header.g()).append('\n');
        builder.append("- A: ").append(header.a()).append('\n');
        builder.append("- X: ").append(header.x()).append('\n');

        if (!contents.isEmpty()) {
            builder.append('\n');
            builder.append("#### Contents").append('\n');
            for (String entry : contents) {
                builder.append("- ").append(entry).append('\n');
            }
        }

        return builder.toString();
    }

    private String buildDefaultPackageBody(final List<String> contents) {
        final StringBuilder builder = new StringBuilder();

        if (!contents.isEmpty()) {
            builder.append("#### Contents").append('\n');
            for (String entry : contents) {
                builder.append("- ").append(entry).append('\n');
            }
        }

        return builder.toString();
    }

    private String calculatePackageChecksum(final Path directory) throws IOException {
        final StringBuilder builder = new StringBuilder();

        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                final String name = path.getFileName().toString();

                if (Files.isDirectory(path)) {
                    final Path childPackageFile = path.resolve("package.ai.md");
                    if (Files.exists(childPackageFile)) {
                        final AiMdDocument childDocument = documentCodec.read(childPackageFile);
                        final AiMdHeader childHeader = childDocument.header();
                        builder.append(headerSupport.buildChecksumLine(path.getFileName().toString(), childHeader));
                    }
                    continue;
                }

                if (name.equals("package.ai.md") || name.startsWith(".generated-by-")) {
                    continue;
                }

                if (name.endsWith(".ai.md")) {
                    final AiMdDocument childDocument = documentCodec.read(path);
                    final AiMdHeader childHeader = childDocument.header();
                    builder.append(headerSupport.buildChecksumLine(name, childHeader));
                }
            }
        }

        return checksumSupport.calculateCrc32Hex(builder.toString());
    }

    private String calculatePackageDate(final Path directory) throws IOException {
        String latest = "1970-01-01T00:00:00Z";

        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                final String name = path.getFileName().toString();

                if (Files.isDirectory(path)) {
                    final Path childPackageFile = path.resolve("package.ai.md");
                    if (Files.exists(childPackageFile)) {
                        final AiMdDocument childDocument = documentCodec.read(childPackageFile);
                        final AiMdHeader childHeader = childDocument.header();
                        if (childHeader.d().compareTo(latest) > 0) {
                            latest = childHeader.d();
                        }
                    }
                    continue;
                }

                if (name.equals("package.ai.md") || name.startsWith(".generated-by-")) {
                    continue;
                }

                if (name.endsWith(".ai.md")) {
                    final AiMdDocument childDocument = documentCodec.read(path);
                    final AiMdHeader childHeader = childDocument.header();
                    if (childHeader.d().compareTo(latest) > 0) {
                        latest = childHeader.d();
                    }
                }
            }
        }

        return latest;
    }
}