// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import org.junit.jupiter.api.Test;

public class AiIndexPlanTest {

    private static AiFieldGenerationConfig rule(final String promptKey) {
        final AiFieldGenerationConfig config = new AiFieldGenerationConfig();
        config.setPromptKey(promptKey);
        return config;
    }

    private static AiFieldGenerationConfig rule(final String id, final String promptKey) {
        final AiFieldGenerationConfig config = new AiFieldGenerationConfig();
        config.setId(id);
        config.setPromptKey(promptKey);
        return config;
    }

    @Test
    public void routedCountAndPerModelGrouping() {
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("a/Foo.java"), rule("p1"), 60);
        plan.addRoute("modelA", Paths.get("a/Bar.java"), rule("p1"), 120);
        plan.addRoute("modelB", Paths.get("b/Baz.java"), rule("p2"), 30);

        assertThat(plan.routedCount(), is(equalTo(3)));
        assertThat(plan.routesByModel().get("modelA").size(), is(equalTo(2)));
        assertThat(plan.routesByModel().get("modelB").size(), is(equalTo(1)));
    }

    @Test
    public void totalEstimatedSecondsSumsAllEntries() {
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("a/Foo.java"), rule("p1"), 60);
        plan.addRoute("modelA", Paths.get("a/Bar.java"), rule("p1"), 120);
        plan.addRoute("modelB", Paths.get("b/Baz.java"), rule("p2"), 30);

        assertThat(plan.totalEstimatedSeconds(), is(equalTo(210L)));
    }

    @Test
    public void renderMarkdown_containsModelSectionsFilesAndTotals() {
        final Path base = Paths.get("");
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Foo.java"), rule("java-small", "file-body-java"), 60);
        plan.addSkipped(Paths.get("Generated.java"));
        plan.addUnmatched(Paths.get("weird.txt"));

        final String md = plan.renderMarkdown(base);

        assertThat(md, containsString("## AI index plan"));
        assertThat(md, containsString("**Total:**"));
        assertThat(md, containsString("### Model `modelA`"));
        assertThat(md, containsString("| File | Rule | Prompt | Window | Est. |"));
        assertThat(md, containsString("Foo.java"));
        assertThat(md, containsString("java-small")); // the rule id shows in its own column
        assertThat(md, containsString("file-body-java"));
        assertThat(md, containsString("Skipped (1)"));
        assertThat(md, containsString("Generated.java"));
        assertThat(md, containsString("Unmatched"));
        assertThat(md, containsString("weird.txt"));
    }

    @Test
    public void windowExceededCount_countsOnlyOverWindowEntries() {
        final AiIndexPlan plan = new AiIndexPlan();
        // fits: source 1000 <= budget 5000
        plan.addRoute("modelA", Paths.get("Small.java"), rule("p1"), 10, 1000L, 5000L);
        // exceeds: source 9000 > budget 5000
        plan.addRoute("modelA", Paths.get("Huge.java"), rule("p1"), 20, 9000L, 5000L);
        // unchecked (4-arg) never counts as over-window
        plan.addRoute("modelB", Paths.get("Plain.java"), rule("p2"), 5);

        assertThat(plan.windowExceededCount(), is(equalTo(1)));
        assertThat(plan.routesByModel().get("modelA").get(0).exceedsWindow(), is(false));
        assertThat(plan.routesByModel().get("modelA").get(1).exceedsWindow(), is(true));
        assertThat(plan.routesByModel().get("modelB").get(0).windowChecked(), is(false));
    }

    @Test
    public void renderMarkdown_flagsOverWindowFilesAndCells() {
        final Path base = Paths.get("");
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Small.java"), rule("r1", "p1"), 10, 1000L, 5000L);
        plan.addRoute("modelA", Paths.get("Huge.java"), rule("r1", "p1"), 20, 9000L, 5000L);

        final String md = plan.renderMarkdown(base);

        assertThat(md, containsString("1 over window"));
        assertThat(md, containsString("ok")); // the fitting file's window cell
        assertThat(md, containsString("(!) 9000>5000")); // the over-window file's window cell
        assertThat(md, containsString("Over window"));
        assertThat(md, containsString("Huge.java -> model `modelA`"));
    }

    @Test
    public void renderMarkdown_noWindowSectionWhenNothingExceeds() {
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Small.java"), rule("r1", "p1"), 10, 1000L, 5000L);

        final String md = plan.renderMarkdown(Paths.get(""));

        assertThat(md, containsString("0 over window"));
        assertThat(md.contains("### (!) Over window"), is(false));
    }

    // <editor-fold defaultstate="collapsed" desc="oversize rendering and path display">

    /**
     * Builds a rule whose {@code onOversize} strategy is set explicitly.
     *
     * @param id         the rule id
     * @param onOversize the configured strategy value
     * @return the rule
     */
    private static AiFieldGenerationConfig oversizeRule(final String id, final String onOversize) {
        final AiFieldGenerationConfig config = new AiFieldGenerationConfig();
        config.setId(id);
        config.setPromptKey("p1");
        config.setOnOversize(onOversize);
        return config;
    }

    /**
     * An over-window entry whose rule fails the build must be rendered as such, and must trigger the
     * closing "how to fix it" paragraph.
     */
    @Test
    public void renderMarkdown_oversizeWithFailStrategy_marksBuildFailureAndAddsAdvice() {
        // arrange
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Big.java"), oversizeRule("r1", "fail"), 60, 9000L, 1000L);

        // act
        final String rendered = plan.renderMarkdown(Paths.get(""));

        // assert
        assertThat(rendered, containsString("FAILS BUILD"));
        assertThat(rendered, containsString("The onOversize=fail entries fail the build."));
    }

    /**
     * The same entry with a non-failing strategy renders as handled and must NOT emit the advice
     * paragraph. Without this counterpart, a mutant hardcoding either branch survives.
     */
    @Test
    public void renderMarkdown_oversizeWithHandlingStrategy_marksHandledAndOmitsAdvice() {
        // arrange
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Big.java"), oversizeRule("r1", "sample"), 60, 9000L, 1000L);

        // act
        final String rendered = plan.renderMarkdown(Paths.get(""));

        // assert
        assertThat(rendered, containsString("handled"));
        assertThat(rendered, not(containsString("FAILS BUILD")));
        assertThat(rendered, not(containsString("The onOversize=fail entries fail the build.")));
    }

    /**
     * A plan with no over-window entry at all must not emit the advice paragraph either -- this is the
     * zero case for the {@code windowFailCount() > 0} guard, distinct from the "handled" case above.
     */
    @Test
    public void renderMarkdown_noOversizeEntries_omitsAdvice() {
        // arrange
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Small.java"), rule("r1", "p1"), 60, 100L, 1000L);

        // act
        final String rendered = plan.renderMarkdown(Paths.get(""));

        // assert
        assertThat(rendered, not(containsString("The onOversize=fail entries fail the build.")));
    }

    /**
     * The per-entry window cell distinguishes three states, and each must be rendered distinctly:
     * unchecked, fits, exceeds.
     */
    @Test
    public void renderMarkdown_windowCell_rendersAllThreeStates() {
        // arrange
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("Unchecked.java"), rule("r1", "p1"), 60);
        plan.addRoute("modelA", Paths.get("Fits.java"), rule("r2", "p1"), 60, 100L, 1000L);
        plan.addRoute("modelA", Paths.get("Exceeds.java"), oversizeRule("r3", "sample"), 60, 9000L, 1000L);

        // act
        final String rendered = plan.renderMarkdown(Paths.get(""));

        // assert -- match the whole table cell, not the bare word: "ok" also occurs in the prose
        // around the table, so a substring check would pass even against an empty window cell.
        assertThat(rendered, containsString("| ok |"));
        assertThat(rendered, containsString("| (!) 9000>1000"));
        assertThat(rendered, containsString("| - |"));
    }

    /**
     * A file that cannot be relativized against the base directory falls back to its own path rather
     * than throwing. {@code Path.relativize} raises {@link IllegalArgumentException} when one path is
     * absolute and the other relative, which is exactly what this constructs.
     */
    @Test
    public void renderMarkdown_fileNotRelativizableAgainstBase_fallsBackToTheFullPath() {
        // arrange -- absolute base, relative file: relativize cannot bridge the two
        final AiIndexPlan plan = new AiIndexPlan();
        plan.addRoute("modelA", Paths.get("relative/Foo.java"), rule("r1", "p1"), 60);

        // act
        final String rendered = plan.renderMarkdown(Paths.get("/absolute/base").toAbsolutePath());

        // assert
        assertThat(rendered, containsString("relative/Foo.java"));
    }

    // </editor-fold>
}
