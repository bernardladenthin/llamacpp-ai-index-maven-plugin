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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "generate", threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File baseDirectory;

    @Parameter(
            property = "aiIndex.outputDirectory",
            defaultValue = "${project.basedir}/src/site/ai"
    )
    private File outputDirectory;

    @Parameter(property = "aiIndex.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "aiIndex.force", defaultValue = "false")
    private boolean force;

    @Parameter(property = "aiIndex.subtrees")
    private List<String> subtrees;

    @Parameter(property = "aiIndex.fileExtensions")
    private List<String> fileExtensions;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String pluginVersion;

    @Parameter(property = "aiIndex.aiVersion", defaultValue = "0.0.0")
    private String aiVersion;

    @Parameter
    private List<AiPromptDefinition> promptDefinitions;

    @Parameter
    private List<AiFieldGenerationConfig> fieldGenerations;

    @Parameter(property = "aiIndex.summaryProvider", defaultValue = "mock")
    private String summaryProvider;

    @Parameter(property = "aiIndex.llama.libraryPath")
    private String llamaLibraryPath;

    @Parameter(property = "aiIndex.llama.modelPath")
    private String llamaModelPath;

    @Parameter(property = "aiIndex.llama.contextSize", defaultValue = "2048")
    private int llamaContextSize;

    @Parameter(property = "aiIndex.llama.maxTokens", defaultValue = "128")
    private int llamaMaxTokens;

    @Parameter(property = "aiIndex.llama.temperature", defaultValue = "0.15")
    private float llamaTemperature;

    @Parameter(property = "aiIndex.llama.threads", defaultValue = "2")
    private int llamaThreads;

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

        getLog().info("Starting AI index generation");
        getLog().info("Base directory  : " + basePath);
        getLog().info("Output directory: " + outputPath);
        getLog().info("Subtrees        : " + resolvedSubtrees);
        getLog().info("Extensions      : " + resolvedExtensions);
        getLog().info("Force           : " + force);
        getLog().info("Provider        : " + summaryProvider);

        try {
            final LlamaCppJniConfig llamaConfig = new LlamaCppJniConfig(
                    llamaLibraryPath,
                    llamaModelPath,
                    llamaContextSize,
                    llamaMaxTokens,
                    llamaTemperature,
                    llamaThreads
            );

            final AiPromptSupport promptSupport = new AiPromptSupport(promptDefinitions);
            final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();

            try (AiGenerationProvider generationProvider =
                         providerFactory.create(summaryProvider, llamaConfig, promptSupport)) {

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
            return List.of(".java");
        }
        return fileExtensions;
    }

    private List<Path> resolveSubtrees(final Path basePath) {
        final List<Path> resolved = new ArrayList<>();

        if (subtrees == null || subtrees.isEmpty()) {
            return resolved;
        }

        for (String subtree : subtrees) {
            final Path path = basePath.resolve(subtree).normalize();
            if (path.toFile().exists()) {
                resolved.add(path);
            } else {
                getLog().warn("Skipping missing subtree: " + path);
            }
        }

        return resolved;
    }
}