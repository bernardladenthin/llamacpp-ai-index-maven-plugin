// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.ToString;
import net.ladenthin.srcmorph.config.AiFactDefinition;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.engine.GenerateEngine;
import net.ladenthin.srcmorph.engine.SrcMorphException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven goal {@code srcmorph:generate}: indexes source files and fills in their
 * AI-generated summary and keyword fields.
 *
 * <p>Thin wrapper: builds a {@link SrcMorphConfiguration} from its {@code @Parameter} fields and
 * delegates the whole run to {@link GenerateEngine}.</p>
 */
// @Parameter fields are populated by the Maven plugin framework via reflection after
// construction. NullAway is configured via ExcludedFieldAnnotations to skip them; Checker
// Framework has no equivalent option for plugin-framework fields, so we suppress class-level.
@SuppressWarnings("initialization.fields.uninitialized")
@Mojo(name = "generate", threadSafe = true)
@ToString(callSuper = true)
public class GenerateMojo extends AbstractAiIndexMojo {

    /** Creates a new {@link GenerateMojo}. */
    public GenerateMojo() {
        // no-op
    }

    /**
     * Phase switch for the <strong>file</strong> phase (the {@code generate} goal): when {@code true},
     * only this phase is skipped. The global {@link #skip} still skips every phase.
     */
    @Parameter(property = "srcmorph.file.skip", defaultValue = "false")
    private boolean skipFile;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String pluginVersion;

    @Parameter(property = "srcmorph.aiVersion", defaultValue = "0.0.0")
    private String aiVersion;

    @Parameter(property = "srcmorph.fileExtensions")
    private List<String> fileExtensions;

    /**
     * Glob patterns for source files to skip, matched against each file's path relative to the
     * project base directory with {@code /} separators (e.g. {@code **}{@code /package-info.java},
     * {@code **}{@code /generated/**}). Lets the index stay focused by excluding trivial or generated
     * sources. Empty by default — nothing is excluded.
     *
     * @see net.ladenthin.srcmorph.support.AiSourceExcludeFilter
     */
    @Parameter(property = "srcmorph.excludes")
    private List<String> excludes;

    /**
     * Reusable, named {@code <factDefinitions>} groups referenced from a rule's {@code <factsKey>}, so a
     * fact set (e.g. {@code java-facts}, {@code sql-facts}) is defined once and shared across rules
     * instead of repeated inline. Resolved onto each rule's {@code facts} before indexing.
     *
     * @see net.ladenthin.srcmorph.config.AiFactDefinitionSupport
     */
    @Parameter
    private List<AiFactDefinition> factDefinitions;

    /**
     * Exclusive lower file-size bound in bytes: source files whose size is {@code <= this} are skipped.
     * {@code 0} (default) disables the lower bound. Together with {@link #maxFileSizeBytes} this lets one
     * {@code generate} execution target a size band, so several executions (each with its own model,
     * context size and prompt) can tier a project by file size while the source-checksum skip indexes
     * every file exactly once. Use non-overlapping bands ({@code band2.min == band1.max}); make the last
     * band unbounded ({@code maxFileSizeBytes=0}) so files above all bands still get indexed.
     */
    @Parameter(property = "srcmorph.file.minSizeBytes", defaultValue = "0")
    private long minFileSizeBytes;

    /**
     * Inclusive upper file-size bound in bytes: source files whose size is {@code > this} are skipped.
     * {@code 0} (default) disables the upper bound (unlimited). See {@link #minFileSizeBytes} for the
     * size-tiering pattern.
     */
    @Parameter(property = "srcmorph.file.maxSizeBytes", defaultValue = "0")
    private long maxFileSizeBytes;

    /** llama.cpp context window size; smaller default suits the fast generate pass. */
    @Parameter(property = "srcmorph.llama.contextSize", defaultValue = "2048")
    private int llamaContextSize;

    /** CPU threads for llama.cpp inference during the generate pass. */
    @Parameter(property = "srcmorph.llama.threads", defaultValue = "2")
    private int llamaThreads;

    /**
     * When {@code true}, only build and log the routing plan (which model indexes which files with which
     * prompt) and then stop — no model is loaded and nothing is generated. Useful to verify routing
     * before a long run.
     */
    @Parameter(property = "srcmorph.planOnly", defaultValue = "false")
    private boolean planOnly;

    @Override
    protected int getLlamaContextSize() {
        return llamaContextSize;
    }

    @Override
    protected int getLlamaThreads() {
        return llamaThreads;
    }

    @Override
    protected boolean isPhaseSkipped() {
        return skipFile;
    }

    /**
     * Maps this goal's own {@code @Parameter} fields onto the shared configuration built by
     * {@link AbstractAiIndexMojo#buildConfiguration()}.
     *
     * <p>Package-private rather than private so a same-package test can assert the mapping without
     * reflection. Its failure mode is silent: a field forgotten here, or two transposed, ignores
     * what the user configured and breaks nothing visible.</p>
     *
     * @return the configuration for this goal
     */
    SrcMorphConfiguration buildGenerateConfiguration() {
        final SrcMorphConfiguration config = buildConfiguration();
        config.setPluginVersion(pluginVersion);
        config.setAiVersion(aiVersion);
        config.setFileExtensions(fileExtensions);
        config.setExcludes(excludes);
        config.setFactDefinitions(factDefinitions);
        config.setMinFileSizeBytes(minFileSizeBytes);
        config.setMaxFileSizeBytes(maxFileSizeBytes);
        config.setPlanOnly(planOnly);
        return config;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (shouldSkip()) {
            getLog().info("AI index generation skipped.");
            return;
        }

        final Path basePath = baseDirectory.toPath().toAbsolutePath().normalize();
        final Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
        try {
            new GenerateEngine(buildGenerateConfiguration()).execute();
        } catch (SrcMorphException e) {
            throw new MojoExecutionException(messageOf(e), e);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to generate AI index files under " + outputPath + " from base " + basePath, e);
        }
    }
}
