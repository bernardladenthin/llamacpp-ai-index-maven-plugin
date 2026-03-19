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

@Mojo(name = "aggregate-packages", threadSafe = true)
public class AggregatePackagesMojo extends AbstractAiIndexMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String pluginVersion;

    @Parameter(property = "aiIndex.aiVersion", defaultValue = "0.0.0")
    private String aiVersion;

    /** llama.cpp context window size; smaller default suits the fast aggregate pass. */
    @Parameter(property = "aiIndex.llama.contextSize", defaultValue = "2048")
    private int llamaContextSize;

    /** CPU threads for llama.cpp inference during package aggregation. */
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
            getLog().info("AI package aggregation skipped.");
            return;
        }

        final Path basePath = baseDirectory.toPath().toAbsolutePath().normalize();
        final Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
        final List<Path> resolvedSubtrees = resolveSubtrees(basePath);

        getLog().info("Starting AI package aggregation");
        getLog().info("Base directory  : " + basePath);
        getLog().info("Output directory: " + outputPath);
        getLog().info("Subtrees        : " + resolvedSubtrees);
        getLog().info("Force           : " + force);
        getLog().info("Provider        : " + summaryProvider);

        if (!outputPath.toFile().exists()) {
            getLog().info("AI output directory does not exist, skipping package aggregation: " + outputPath);
            return;
        }

        try {
            final AiPromptSupport promptSupport = buildPromptSupport();
            final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();

            try (AiGenerationProvider generationProvider = providerFactory.create(summaryProvider, buildLlamaCppJniConfig(), promptSupport)) {
                final PackageIndexer packageIndexer = new PackageIndexer(
                        getLog(),
                        basePath,
                        outputPath,
                        pluginVersion,
                        aiVersion,
                        resolvedSubtrees,
                        force,
                        generationProvider,
                        fieldGenerations,
                        promptSupport
                );

                final int aggregated = packageIndexer.aggregate(outputPath);
                getLog().info("Aggregated packages: " + aggregated);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to aggregate package AI index files", e);
        }

        getLog().info("AI package aggregation finished.");
    }
}
