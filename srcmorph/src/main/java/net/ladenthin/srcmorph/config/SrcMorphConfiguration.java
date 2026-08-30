// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import java.io.File;
import java.util.List;
import lombok.ToString;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Root mutable JavaBean holding every parameter a {@code srcmorph} run needs, independent of how it is
 * bound: today from Maven {@code @Parameter} fields (the {@code srcmorph-maven-plugin} module's
 * mojos each build one of these from their own annotated fields), tomorrow from a JSON/YAML config file
 * (a future CLI) or from plain Java code constructing one directly.
 *
 * <p><strong>Field names intentionally mirror today's Maven {@code @Parameter} field names</strong> (e.g.
 * {@link #outputDirectory}, {@link #fieldGenerations}, {@link #llamaContextSize}) so that a future
 * JSON/YAML config's keys read the same as the existing plugin XML — this class is the single shared
 * config object bindable from every surface. See {@code net.ladenthin.maven.srcmorph.mojo}'s
 * mojo classes (in the sibling plugin module) for the exact provenance of every field.</p>
 *
 * <p>Deliberately excluded: the per-goal {@code skip}/{@code skipFile}/{@code skipPackage}/
 * {@code skipProject} flags. Those are a Maven lifecycle concern (whether an execution runs at all) and
 * stay mojo-side; an engine constructed from this configuration always executes when asked.</p>
 *
 * <p><strong>Note:</strong> this class must remain a mutable JavaBean with setters so that every binding
 * technology (Maven plexus reflection, a Jackson {@code ObjectMapper}/{@code YAMLMapper}, or a plain
 * caller) can populate it the same way.</p>
 */
@SuppressWarnings({"NullAway.Init", "initialization.fields.uninitialized"})
@ToString
public class SrcMorphConfiguration {

    /** Default output directory (relative to {@link #baseDirectory}) when none is configured. */
    public static final String DEFAULT_OUTPUT_DIRECTORY = "src/site/ai";

    /** Default AI generation provider name when none is configured. */
    public static final String DEFAULT_GENERATION_PROVIDER = "mock";

    /** Default llama.cpp context window size when none is configured. */
    public static final int DEFAULT_LLAMA_CONTEXT_SIZE = 2048;

    /** Default maximum number of llama.cpp output tokens per call when none is configured. */
    public static final int DEFAULT_LLAMA_MAX_OUTPUT_TOKENS = 128;

    /** Default llama.cpp sampling temperature when none is configured. */
    public static final float DEFAULT_LLAMA_TEMPERATURE = 0.15f;

    /** Default number of llama.cpp CPU threads when none is configured. */
    public static final int DEFAULT_LLAMA_THREADS = 2;

    /** Default AI summarisation logic version when none is configured. */
    public static final String DEFAULT_AI_VERSION = "0.0.0";

    /** Creates a new {@link SrcMorphConfiguration} with every default applied. */
    public SrcMorphConfiguration() {
        // no-op
    }

    private File baseDirectory;
    private File outputDirectory = new File(DEFAULT_OUTPUT_DIRECTORY);
    private boolean force;
    private @Nullable List<String> subtrees;
    private @Nullable List<String> excludes;
    private @Nullable List<String> fileExtensions;
    private long minFileSizeBytes;
    private long maxFileSizeBytes;
    private boolean planOnly;
    private String generationProvider = DEFAULT_GENERATION_PROVIDER;
    private @Nullable List<AiPromptDefinition> promptDefinitions;
    private @Nullable List<AiModelDefinition> aiDefinitions;
    private @Nullable List<AiFieldGenerationConfig> fieldGenerations;
    private @Nullable List<AiFactDefinition> factDefinitions;
    private @Nullable String llamaModelPath;
    private int llamaContextSize = DEFAULT_LLAMA_CONTEXT_SIZE;
    private int llamaMaxOutputTokens = DEFAULT_LLAMA_MAX_OUTPUT_TOKENS;
    private float llamaTemperature = DEFAULT_LLAMA_TEMPERATURE;
    private int llamaThreads = DEFAULT_LLAMA_THREADS;
    private String pluginVersion = "";
    private String aiVersion = DEFAULT_AI_VERSION;
    private @Nullable String projectName;

    /**
     * Returns the project base directory.
     *
     * @return the base directory
     */
    public File getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * Sets the project base directory.
     *
     * @param baseDirectory the base directory
     */
    public void setBaseDirectory(final File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * Returns the directory into which {@code .ai.md} files are written.
     *
     * @return the output directory; defaults to {@link #DEFAULT_OUTPUT_DIRECTORY}
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Sets the directory into which {@code .ai.md} files are written.
     *
     * @param outputDirectory the output directory
     */
    public void setOutputDirectory(final File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns whether AI fields are regenerated even when they already have a value.
     *
     * @return {@code true} to always regenerate
     */
    public boolean isForce() {
        return force;
    }

    /**
     * Sets whether AI fields are regenerated even when they already have a value.
     *
     * @param force {@code true} to always regenerate
     */
    public void setForce(final boolean force) {
        this.force = force;
    }

    /**
     * Returns the source subdirectory paths (relative to {@link #baseDirectory}) that restrict processing.
     *
     * @return the configured subtrees, or {@code null} when every discovered source root is in scope
     */
    public @Nullable List<String> getSubtrees() {
        return subtrees;
    }

    /**
     * Sets the source subdirectory paths that restrict processing.
     *
     * @param subtrees the subtrees, or {@code null} for every discovered source root
     */
    public void setSubtrees(final @Nullable List<String> subtrees) {
        this.subtrees = subtrees;
    }

    /**
     * Returns the glob patterns (base-relative, {@code /} separators) for source files to skip.
     *
     * @return the configured excludes, or {@code null} when nothing is excluded
     */
    public @Nullable List<String> getExcludes() {
        return excludes;
    }

    /**
     * Sets the glob patterns for source files to skip.
     *
     * @param excludes the exclude globs, or {@code null} for none
     */
    public void setExcludes(final @Nullable List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * Returns the file extensions to index.
     *
     * @return the configured extensions, or {@code null} to use the engine's own default (typically
     *         {@code .java})
     */
    public @Nullable List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Sets the file extensions to index.
     *
     * @param fileExtensions the extensions, or {@code null} to use the engine's own default
     */
    public void setFileExtensions(final @Nullable List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /**
     * Returns the exclusive lower file-size bound in bytes.
     *
     * @return the lower bound; {@code <= 0} disables it
     */
    public long getMinFileSizeBytes() {
        return minFileSizeBytes;
    }

    /**
     * Sets the exclusive lower file-size bound in bytes.
     *
     * @param minFileSizeBytes the lower bound; {@code <= 0} disables it
     */
    public void setMinFileSizeBytes(final long minFileSizeBytes) {
        this.minFileSizeBytes = minFileSizeBytes;
    }

    /**
     * Returns the inclusive upper file-size bound in bytes.
     *
     * @return the upper bound; {@code <= 0} means unlimited
     */
    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    /**
     * Sets the inclusive upper file-size bound in bytes.
     *
     * @param maxFileSizeBytes the upper bound; {@code <= 0} means unlimited
     */
    public void setMaxFileSizeBytes(final long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Returns whether a {@code generate} run stops after printing the routing plan, without loading any
     * model or generating anything.
     *
     * @return {@code true} to stop after planning
     */
    public boolean isPlanOnly() {
        return planOnly;
    }

    /**
     * Sets whether a {@code generate} run stops after printing the routing plan.
     *
     * @param planOnly {@code true} to stop after planning
     */
    public void setPlanOnly(final boolean planOnly) {
        this.planOnly = planOnly;
    }

    /**
     * Returns the name of the AI generation provider to use.
     *
     * @return the provider name ({@code mock} or {@code llamacpp-jni}); defaults to
     *         {@link #DEFAULT_GENERATION_PROVIDER}
     */
    public String getGenerationProvider() {
        return generationProvider;
    }

    /**
     * Sets the name of the AI generation provider to use.
     *
     * @param generationProvider the provider name ({@code mock} or {@code llamacpp-jni})
     */
    public void setGenerationProvider(final String generationProvider) {
        this.generationProvider = generationProvider;
    }

    /**
     * Returns the prompt template definitions referenced by field generation configurations.
     *
     * @return the prompt definitions, or {@code null} when none are configured
     */
    public @Nullable List<AiPromptDefinition> getPromptDefinitions() {
        return promptDefinitions;
    }

    /**
     * Sets the prompt template definitions.
     *
     * @param promptDefinitions the prompt definitions, or {@code null} for none
     */
    public void setPromptDefinitions(final @Nullable List<AiPromptDefinition> promptDefinitions) {
        this.promptDefinitions = promptDefinitions;
    }

    /**
     * Returns the AI model definitions that pair a lookup key with a complete set of model parameters.
     *
     * @return the AI model definitions, or {@code null} when none are configured
     */
    public @Nullable List<AiModelDefinition> getAiDefinitions() {
        return aiDefinitions;
    }

    /**
     * Sets the AI model definitions.
     *
     * @param aiDefinitions the AI model definitions, or {@code null} for none
     */
    public void setAiDefinitions(final @Nullable List<AiModelDefinition> aiDefinitions) {
        this.aiDefinitions = aiDefinitions;
    }

    /**
     * Returns the per-field AI generation configurations (routing rules) controlling which prompt and AI
     * definition each matched file uses.
     *
     * @return the field generation rules, or {@code null} when none are configured
     */
    public @Nullable List<AiFieldGenerationConfig> getFieldGenerations() {
        return fieldGenerations;
    }

    /**
     * Sets the per-field AI generation configurations (routing rules).
     *
     * @param fieldGenerations the field generation rules, or {@code null} for none
     */
    public void setFieldGenerations(final @Nullable List<AiFieldGenerationConfig> fieldGenerations) {
        this.fieldGenerations = fieldGenerations;
    }

    /**
     * Returns the reusable, named {@code <factDefinitions>} groups referenced from a rule's
     * {@code factsKey}.
     *
     * @return the fact definitions, or {@code null} when none are configured
     */
    public @Nullable List<AiFactDefinition> getFactDefinitions() {
        return factDefinitions;
    }

    /**
     * Sets the reusable, named {@code <factDefinitions>} groups.
     *
     * @param factDefinitions the fact definitions, or {@code null} for none
     */
    public void setFactDefinitions(final @Nullable List<AiFactDefinition> factDefinitions) {
        this.factDefinitions = factDefinitions;
    }

    /**
     * Returns the path to the GGUF model file used as the llama.cpp JNI provider fallback (only used
     * when {@link #fieldGenerations} is empty).
     *
     * @return the model path, or {@code null} if not set
     */
    public @Nullable String getLlamaModelPath() {
        return llamaModelPath;
    }

    /**
     * Sets the path to the GGUF model file used as the llama.cpp JNI provider fallback.
     *
     * @param llamaModelPath the model path
     */
    public void setLlamaModelPath(final @Nullable String llamaModelPath) {
        this.llamaModelPath = llamaModelPath;
    }

    /**
     * Returns the llama.cpp context window size used as the fallback (only used when
     * {@link #fieldGenerations} is empty).
     *
     * @return the context window size; defaults to {@link #DEFAULT_LLAMA_CONTEXT_SIZE}
     */
    public int getLlamaContextSize() {
        return llamaContextSize;
    }

    /**
     * Sets the llama.cpp context window size used as the fallback.
     *
     * @param llamaContextSize the context window size
     */
    public void setLlamaContextSize(final int llamaContextSize) {
        this.llamaContextSize = llamaContextSize;
    }

    /**
     * Returns the maximum number of llama.cpp output tokens per call, used as the fallback.
     *
     * @return the maximum output tokens; defaults to {@link #DEFAULT_LLAMA_MAX_OUTPUT_TOKENS}
     */
    public int getLlamaMaxOutputTokens() {
        return llamaMaxOutputTokens;
    }

    /**
     * Sets the maximum number of llama.cpp output tokens per call, used as the fallback.
     *
     * @param llamaMaxOutputTokens the maximum output tokens
     */
    public void setLlamaMaxOutputTokens(final int llamaMaxOutputTokens) {
        this.llamaMaxOutputTokens = llamaMaxOutputTokens;
    }

    /**
     * Returns the llama.cpp sampling temperature, used as the fallback.
     *
     * @return the sampling temperature; defaults to {@link #DEFAULT_LLAMA_TEMPERATURE}
     */
    public float getLlamaTemperature() {
        return llamaTemperature;
    }

    /**
     * Sets the llama.cpp sampling temperature, used as the fallback.
     *
     * @param llamaTemperature the sampling temperature
     */
    public void setLlamaTemperature(final float llamaTemperature) {
        this.llamaTemperature = llamaTemperature;
    }

    /**
     * Returns the number of CPU threads for llama.cpp inference, used as the fallback.
     *
     * @return the thread count; defaults to {@link #DEFAULT_LLAMA_THREADS}
     */
    public int getLlamaThreads() {
        return llamaThreads;
    }

    /**
     * Sets the number of CPU threads for llama.cpp inference, used as the fallback.
     *
     * @param llamaThreads the thread count
     */
    public void setLlamaThreads(final int llamaThreads) {
        this.llamaThreads = llamaThreads;
    }

    /**
     * Returns the generator (plugin) version recorded in every written {@code .ai.md} header.
     *
     * @return the generator version
     */
    public String getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Sets the generator (plugin) version recorded in every written {@code .ai.md} header.
     *
     * @param pluginVersion the generator version
     */
    public void setPluginVersion(final String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    /**
     * Returns the AI summarisation logic version recorded in every written {@code .ai.md} header.
     *
     * @return the AI version; defaults to {@link #DEFAULT_AI_VERSION}
     */
    public String getAiVersion() {
        return aiVersion;
    }

    /**
     * Sets the AI summarisation logic version recorded in every written {@code .ai.md} header.
     *
     * @param aiVersion the AI version
     */
    public void setAiVersion(final String aiVersion) {
        this.aiVersion = aiVersion;
    }

    /**
     * Returns the project title recorded as the {@code aggregate-project} index title.
     *
     * @return the project name, or {@code null} to fall back to the engine's own default title
     */
    public @Nullable String getProjectName() {
        return projectName;
    }

    /**
     * Sets the project title recorded as the {@code aggregate-project} index title.
     *
     * @param projectName the project name, or {@code null} to use the engine's own default title
     */
    public void setProjectName(final @Nullable String projectName) {
        this.projectName = projectName;
    }
}
