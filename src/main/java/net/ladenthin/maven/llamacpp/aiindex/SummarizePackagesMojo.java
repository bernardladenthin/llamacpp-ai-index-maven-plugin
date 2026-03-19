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

@Mojo(name = "summarize-packages", threadSafe = true)
public class SummarizePackagesMojo extends AbstractAiIndexMojo {

    /** llama.cpp context window size; larger default accommodates full package documents. */
    @Parameter(property = "aiIndex.llama.contextSize", defaultValue = "32768")
    private int llamaContextSize;

    /** CPU threads for llama.cpp inference during package summarization. */
    @Parameter(property = "aiIndex.llama.threads", defaultValue = "8")
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
            getLog().info("AI package summarization skipped.");
            return;
        }

        final Path basePath = baseDirectory.toPath().toAbsolutePath().normalize();
        final Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
        final List<Path> resolvedSubtrees = resolveSubtrees(basePath);

        getLog().info("Starting AI package summarization");
        getLog().info("Base directory  : " + basePath);
        getLog().info("Output directory: " + outputPath);
        getLog().info("Subtrees        : " + resolvedSubtrees);
        getLog().info("Force           : " + force);
        getLog().info("Provider        : " + summaryProvider);
        getLog().info("Prompt count    : " + sizeOf(promptDefinitions));
        getLog().info("Field count     : " + sizeOf(fieldGenerations));
        getLog().info("Llama library   : " + llamaLibraryPath);
        getLog().info("Llama model     : " + llamaModelPath);
        getLog().info("Llama ctx size  : " + llamaContextSize);
        getLog().info("Llama max tokens: " + llamaMaxTokens);
        getLog().info("Llama temp      : " + llamaTemperature);
        getLog().info("Llama threads   : " + llamaThreads);

        try {
            final AiPromptSupport promptSupport = buildPromptSupport();
            final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();

            try (AiGenerationProvider provider = providerFactory.create(summaryProvider, buildLlamaCppJniConfig(), promptSupport)) {
                final PackageSummarizer summarizer = new PackageSummarizer(
                        getLog(),
                        basePath,
                        outputPath,
                        resolvedSubtrees,
                        force,
                        provider,
                        fieldGenerations,
                        promptSupport
                );

                final int summarized = summarizer.summarizePackages();
                getLog().info("Summarized packages: " + summarized);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to summarize AI package files", e);
        }

        getLog().info("AI package summarization finished.");
    }
}
