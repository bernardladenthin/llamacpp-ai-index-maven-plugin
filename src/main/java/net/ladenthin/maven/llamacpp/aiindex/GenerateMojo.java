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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Mojo(name = "generate", threadSafe = true)
public class GenerateMojo extends AbstractAiIndexMojo {

    /**
     * Default file extension used when no explicit {@code fileExtensions} parameter
     * is configured. Only files whose names end with this extension are indexed.
     */
    private static final String DEFAULT_FILE_EXTENSION = ".java";

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String pluginVersion;

    @Parameter(property = "aiIndex.aiVersion", defaultValue = "0.0.0")
    private String aiVersion;

    @Parameter(property = "aiIndex.fileExtensions")
    private List<String> fileExtensions;

    /** llama.cpp context window size; smaller default suits the fast generate pass. */
    @Parameter(property = "aiIndex.llama.contextSize", defaultValue = "2048")
    private int llamaContextSize;

    /** CPU threads for llama.cpp inference during the generate pass. */
    @Parameter(property = "aiIndex.llama.threads", defaultValue = "2")
    private int llamaThreads;

    @Override
    protected int getLlamaContextSize() {
        return llamaContextSize;
    }

    @Override
    protected int getLlamaThreads() {
        return llamaThreads;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("AI index generation skipped.");
            return;
        }

        final Path basePath = baseDirectory.toPath().toAbsolutePath().normalize();
        final Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
        final List<Path> resolvedSubtrees = resolveSubtrees(basePath);
        final List<String> resolvedExtensions = resolveFileExtensions();

        logExecutionParameters("Starting AI index generation", basePath, outputPath, resolvedSubtrees, resolvedExtensions);

        try {
            final AiPromptSupport promptSupport = buildPromptSupport();
            final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();

            try (AiGenerationProvider generationProvider =
                         providerFactory.create(summaryProvider, buildLlamaCppJniConfig(), promptSupport)) {

                final SourceFileIndexer fileIndexer = new SourceFileIndexer(
                        getLog(),
                        basePath,
                        outputPath,
                        resolvedExtensions,
                        pluginVersion,
                        aiVersion,
                        resolvedSubtrees,
                        force,
                        generationProvider,
                        fieldGenerations,
                        promptSupport
                );

                int count = 0;

                for (Path subtree : resolvedSubtrees.isEmpty()
                        ? List.of(basePath.resolve("src/main/java"))
                        : resolvedSubtrees) {

                    if (!subtree.toFile().exists()) {
                        getLog().warn("Skipping missing subtree: " + subtree);
                        continue;
                    }

                    count += fileIndexer.indexSourceRoot(subtree);
                }

                getLog().info("Generated AI files: " + count);
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate AI index files", e);
        }

        getLog().info("AI index generation finished.");
    }

    private List<String> resolveFileExtensions() {
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            return List.of(DEFAULT_FILE_EXTENSION);
        }
        return fileExtensions;
    }
}
