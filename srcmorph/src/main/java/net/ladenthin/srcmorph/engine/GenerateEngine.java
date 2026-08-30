// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.ToString;
import net.ladenthin.srcmorph.config.AiFactDefinitionSupport;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiFieldGenerationSelector;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.indexer.AiFieldGenerationSupport;
import net.ladenthin.srcmorph.indexer.AiIndexPlan;
import net.ladenthin.srcmorph.indexer.SourceFileIndexer;
import net.ladenthin.srcmorph.prompt.AiPromptPreparationSupport;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.AiGenerationProvider;
import net.ladenthin.srcmorph.provider.AiGenerationProviderFactory;
import net.ladenthin.srcmorph.support.AiGenerationTimeEstimator;
import net.ladenthin.srcmorph.support.AiProgressBar;
import net.ladenthin.srcmorph.support.Java8CompatibilityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 orchestration: indexes source files and fills in their AI-generated summary fields.
 *
 * <p>Extracted from what was {@code GenerateMojo.execute()} in the {@code llamacpp-ai-index-maven-plugin}
 * module. Plan-then-execute and rule-routed: {@link #execute()} first plans the whole run (which model +
 * prompt each file gets, or skip/unmatched, and whether each file fits its routed model's context
 * window), fails fast on an unmatched file or a hard oversize failure, stops after the plan when
 * {@link SrcMorphConfiguration#isPlanOnly()}, and otherwise loads each distinct routed model exactly once
 * (one provider resident at a time) and indexes that group's files.</p>
 */
@ToString
public final class GenerateEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateEngine.class);

    /**
     * Default file extension used when {@link SrcMorphConfiguration#getFileExtensions()} is unset. Only
     * files whose names end with this extension are indexed.
     */
    private static final String DEFAULT_FILE_EXTENSION = ".java";

    /** Default source subtree used when no {@link SrcMorphConfiguration#getSubtrees()} is configured. */
    private static final String DEFAULT_SOURCE_SUBTREE = "src/main/java";

    /** Nanoseconds per second, for converting the measured run elapsed time to whole seconds. */
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final SrcMorphConfiguration config;
    private final Java8CompatibilityHelper compatibilityHelper = new Java8CompatibilityHelper();

    /**
     * Creates a new {@link GenerateEngine} for the given run configuration.
     *
     * @param config the run configuration
     */
    public GenerateEngine(final SrcMorphConfiguration config) {
        this.config = config;
    }

    /**
     * Runs the full plan-then-execute Phase 1 pipeline.
     *
     * @return the outcome of the run (a plan-only result when {@link SrcMorphConfiguration#isPlanOnly()})
     * @throws SrcMorphException if the run is misconfigured (no {@code fieldGenerations}, an unmatched
     *                            file with no fallback, a hard oversize failure, or an invalid rule set)
     * @throws IOException       if the source tree cannot be walked or a target file cannot be written
     */
    public GenerateResult execute() throws SrcMorphException, IOException {
        final Path basePath =
                config.getBaseDirectory().toPath().toAbsolutePath().normalize();
        final Path outputPath =
                config.getOutputDirectory().toPath().toAbsolutePath().normalize();
        final List<Path> resolvedSubtrees = EngineSupport.resolveSubtrees(basePath, config.getSubtrees());
        final List<String> resolvedExtensions = resolveFileExtensions();

        LOGGER.info("Starting AI index generation");
        LOGGER.info("Base directory  : {}", basePath);
        LOGGER.info("Output directory: {}", outputPath);
        LOGGER.info("Subtrees        : {}", resolvedSubtrees);
        if (!resolvedExtensions.isEmpty()) {
            LOGGER.info("Extensions      : {}", resolvedExtensions);
        }
        LOGGER.info("Force           : {}", config.isForce());
        LOGGER.info("Provider        : {}", config.getGenerationProvider());
        LOGGER.info("LlamaCpp Temperature: {}", config.getLlamaTemperature());
        LOGGER.info("LlamaCpp Max Output Tokens: {}", config.getLlamaMaxOutputTokens());

        final List<AiFieldGenerationConfig> fieldGenerations = config.getFieldGenerations();
        if (fieldGenerations == null || fieldGenerations.isEmpty()) {
            throw new SrcMorphException("No <fieldGenerations> configured for the generate goal.");
        }

        final AiPromptSupport promptSupport = EngineSupport.buildPromptSupport(config.getPromptDefinitions());
        final AiModelDefinitionSupport modelDefinitionSupport =
                EngineSupport.buildAiModelDefinitionSupport(config.getAiDefinitions());
        final AiPromptPreparationSupport promptPreparationSupport = new AiPromptPreparationSupport(promptSupport);
        // Resolve each rule's factsKey to its shared factDefinitions group (copies the counters onto the
        // rule's facts) BEFORE validation, so the resolved fact patterns are validated too.
        resolveSharedFacts(fieldGenerations);

        final AiFieldGenerationSelector selector = new AiFieldGenerationSelector();
        // Fail fast on a bad rule set (e.g. >1 fallback, a route rule missing prompt/model).
        selector.validate(fieldGenerations);

        final SourceFileIndexer fileIndexer = new SourceFileIndexer(
                basePath,
                outputPath,
                resolvedExtensions,
                config.getPluginVersion(),
                config.getAiVersion(),
                resolvedSubtrees,
                config.getExcludes(),
                config.getMinFileSizeBytes(),
                config.getMaxFileSizeBytes(),
                config.isForce());

        // 1. Collect candidate files across the configured subtrees.
        final List<Path> candidates = new ArrayList<>();
        for (final Path subtree : resolvedSubtrees.isEmpty()
                ? compatibilityHelper.listOf(basePath.resolve(DEFAULT_SOURCE_SUBTREE))
                : resolvedSubtrees) {
            if (!subtree.toFile().exists()) {
                LOGGER.warn("Skipping missing subtree: {}", subtree);
                continue;
            }
            candidates.addAll(fileIndexer.collectCandidates(subtree));
        }

        // 2. Plan the run: which model + prompt each file gets (or skip / unmatched), and whether
        //    each file fits its routed model's context window (computed up front, same threshold the
        //    run uses to trim — see AiInputWindowCalculator).
        final AiIndexPlan plan =
                fileIndexer.classify(candidates, fieldGenerations, modelDefinitionSupport, promptPreparationSupport);
        LOGGER.info("AI index plan (Markdown):\n{}", plan.renderMarkdown(basePath));

        // 3. A file that matched no rule and no fallback is a fatal misconfiguration.
        if (!plan.unmatched().isEmpty()) {
            throw new SrcMorphException(plan.unmatched().size()
                    + " source file(s) matched no rule and no fallback is configured; "
                    + "add a <fallback> rule or a matching rule (see the plan above).");
        }

        // 3b. A file larger than its routed model's window would lose content if trimmed. By default
        //     (onOversize=fail) this is a hard failure: the fix is user configuration, never an
        //     automatic model choice — route oversized files to a larger-context model, or set the
        //     rule's onOversize (sample/mapReduce/deterministic) to handle them at run time. Only the
        //     fail entries abort here; the handled ones are processed during generation.
        final int overWindowFailCount = plan.windowFailCount();
        if (overWindowFailCount > 0) {
            throw new SrcMorphException(overWindowFailCount
                    + " source file(s) exceed their routed model's context window with onOversize=fail "
                    + "(see the 'Over window' section in the plan above). Route them to a model with a "
                    + "large enough context window, or set onOversize=sample|mapReduce|deterministic on "
                    + "the rule. The build does not pick a model for you; this is configuration only.");
        }

        if (config.isPlanOnly()) {
            LOGGER.info("planOnly=true: stopping after the plan; no model loaded, nothing generated.");
            return GenerateResult.planned();
        }

        // 4. Execute model group by model group: load each model once, index its files, close.
        //    Progress is the running sum of each finished file's PLAN estimate over the grand total
        //    (no re-estimation), logged as a bar + percent after every file, with the estimated time
        //    left and the actual wall-clock elapsed for comparison.
        final AiGenerationProviderFactory providerFactory = new AiGenerationProviderFactory();
        final AiGenerationTimeEstimator estimator = new AiGenerationTimeEstimator();
        final long totalEstimatedSeconds = plan.totalEstimatedSeconds();
        final int totalFiles = plan.routedCount();
        final long runStartNanos = System.nanoTime();
        long doneEstimatedSeconds = 0;
        int doneFiles = 0;
        int wrote = 0;
        int unchanged = 0;
        for (final Map.Entry<String, List<AiIndexPlan.Entry>> group :
                plan.routesByModel().entrySet()) {
            final String aiDefinitionKey = group.getKey();
            LOGGER.info(
                    "Loading model '{}' for {} file(s)",
                    aiDefinitionKey,
                    group.getValue().size());
            try (AiGenerationProvider provider = providerFactory.create(
                    config.getGenerationProvider(),
                    EngineSupport.resolveLlamaCppJniConfig(modelDefinitionSupport, aiDefinitionKey),
                    promptSupport)) {
                final AiFieldGenerationSupport support =
                        new AiFieldGenerationSupport(provider, promptPreparationSupport, modelDefinitionSupport);
                for (final AiIndexPlan.Entry entry : group.getValue()) {
                    if (fileIndexer.indexFile(entry.file(), entry.rule(), support)) {
                        wrote++;
                    } else {
                        unchanged++;
                    }
                    doneEstimatedSeconds += entry.estimatedSeconds();
                    doneFiles++;
                    final long elapsedSeconds = (System.nanoTime() - runStartNanos) / NANOS_PER_SECOND;
                    final long remainingSeconds = Math.max(0L, totalEstimatedSeconds - doneEstimatedSeconds);
                    LOGGER.info(
                            "{} {}/{} files - est. {}/{} done, {} left (estimate) | {} elapsed (actual)",
                            AiProgressBar.render(doneEstimatedSeconds, totalEstimatedSeconds),
                            doneFiles,
                            totalFiles,
                            estimator.formatDuration(doneEstimatedSeconds),
                            estimator.formatDuration(totalEstimatedSeconds),
                            estimator.formatDuration(remainingSeconds),
                            estimator.formatDuration(elapsedSeconds));
                }
            }
        }

        final int skippedCount = plan.skipped().size();
        LOGGER.info("Generated AI files: {} written, {} unchanged, {} skipped", wrote, unchanged, skippedCount);
        LOGGER.info("AI index generation finished.");

        return new GenerateResult(false, wrote, unchanged, skippedCount);
    }

    private List<String> resolveFileExtensions() {
        final List<String> configured = config.getFileExtensions();
        if (configured == null || configured.isEmpty()) {
            return compatibilityHelper.listOf(DEFAULT_FILE_EXTENSION);
        }
        return configured;
    }

    /**
     * Resolves each rule's {@code factsKey} to its shared {@code <factDefinitions>} group, copying the
     * counters onto the rule's {@code facts}. Translates a misconfiguration (unknown key, or a definition
     * with a null key) into a {@link SrcMorphException} so the caller reports a configuration error.
     *
     * @param fieldGenerations the routing rules whose {@code factsKey} references are resolved in place
     * @throws SrcMorphException if a {@code factsKey} matches no group or a definition has a null key
     */
    private void resolveSharedFacts(final List<AiFieldGenerationConfig> fieldGenerations) throws SrcMorphException {
        try {
            new AiFactDefinitionSupport(config.getFactDefinitions()).resolveFactsKeys(fieldGenerations);
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new SrcMorphException("Invalid factDefinitions/factsKey configuration: " + e.getMessage(), e);
        }
    }
}
