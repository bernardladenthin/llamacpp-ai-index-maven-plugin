// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import java.io.IOException;
import java.nio.file.Path;
import lombok.ToString;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.engine.AggregatePackagesEngine;
import net.ladenthin.srcmorph.engine.SrcMorphException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven goal {@code srcmorph:aggregate-packages}: aggregates per-package
 * {@code .ai.md} index files and fills in their AI-generated summary and keyword fields.
 *
 * <p>Thin wrapper: builds a {@link SrcMorphConfiguration} from its {@code @Parameter} fields and
 * delegates the whole run to {@link AggregatePackagesEngine}.</p>
 */
// @Parameter fields are populated by the Maven plugin framework via reflection after
// construction. NullAway is configured via ExcludedFieldAnnotations to skip them; Checker
// Framework has no equivalent option for plugin-framework fields, so we suppress class-level.
@SuppressWarnings("initialization.fields.uninitialized")
@Mojo(name = "aggregate-packages", threadSafe = true)
@ToString(callSuper = true)
public class AggregatePackagesMojo extends AbstractAiIndexMojo {

    /** Creates a new {@link AggregatePackagesMojo}. */
    public AggregatePackagesMojo() {
        // no-op
    }

    /**
     * Phase switch for the <strong>package</strong> phase (the {@code aggregate-packages} goal): when
     * {@code true}, only this phase is skipped. The global {@link #skip} still skips every phase.
     */
    @Parameter(property = "srcmorph.package.skip", defaultValue = "false")
    private boolean skipPackage;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String pluginVersion;

    @Parameter(property = "srcmorph.aiVersion", defaultValue = "0.0.0")
    private String aiVersion;

    /** llama.cpp context window size; smaller default suits the fast aggregate pass. */
    @Parameter(property = "srcmorph.llama.contextSize", defaultValue = "2048")
    private int llamaContextSize;

    /** CPU threads for llama.cpp inference during package aggregation. */
    @Parameter(property = "srcmorph.llama.threads", defaultValue = "2")
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
    protected boolean isPhaseSkipped() {
        return skipPackage;
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
    SrcMorphConfiguration buildAggregatePackagesConfiguration() {
        final SrcMorphConfiguration config = buildConfiguration();
        config.setPluginVersion(pluginVersion);
        config.setAiVersion(aiVersion);
        return config;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (shouldSkip()) {
            getLog().info("AI package aggregation skipped.");
            return;
        }

        final Path outputPath = outputDirectory.toPath().toAbsolutePath().normalize();
        try {
            new AggregatePackagesEngine(buildAggregatePackagesConfiguration()).execute();
        } catch (SrcMorphException e) {
            throw new MojoExecutionException(messageOf(e), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to aggregate package AI index files under " + outputPath, e);
        }
    }
}
