// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the per-phase skip mechanism: the generalized {@code shouldSkip()} rule in
 * {@link AbstractAiIndexMojo} and each concrete goal's independent phase flag.
 */
public class MojoPhaseSkipTest {

    // <editor-fold defaultstate="collapsed" desc="base shouldSkip() truth table">
    @Test
    public void shouldSkip_isGlobalSkipOrPhaseSkip() {
        // neither flag -> run
        assertThat(mojo(false, false).shouldSkip(), is(false));
        // global skip alone -> skip every phase
        assertThat(mojo(true, false).shouldSkip(), is(true));
        // phase skip alone -> skip just this phase
        assertThat(mojo(false, true).shouldSkip(), is(true));
        // both -> skip
        assertThat(mojo(true, true).shouldSkip(), is(true));
    }

    private static AbstractAiIndexMojo mojo(final boolean globalSkip, final boolean phaseSkip) {
        final AbstractAiIndexMojo mojo = new AbstractAiIndexMojo() {
            @Override
            protected int getLlamaContextSize() {
                return 0;
            }

            @Override
            protected int getLlamaThreads() {
                return 0;
            }

            @Override
            protected boolean isPhaseSkipped() {
                return phaseSkip;
            }

            @Override
            public void execute() throws MojoExecutionException {
                // no-op
            }
        };
        mojo.skip = globalSkip;
        return mojo;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="each concrete goal wires its own phase flag">
    @Test
    public void generateMojo_phaseFlagTogglesIndependently() throws Exception {
        final GenerateMojo mojo = new GenerateMojo();
        assertThat(mojo.isPhaseSkipped(), is(false));
        setBooleanField(mojo, "skipFile", true);
        assertThat(mojo.isPhaseSkipped(), is(true));
        assertThat(mojo.shouldSkip(), is(true));
    }

    @Test
    public void aggregatePackagesMojo_phaseFlagTogglesIndependently() throws Exception {
        final AggregatePackagesMojo mojo = new AggregatePackagesMojo();
        assertThat(mojo.isPhaseSkipped(), is(false));
        setBooleanField(mojo, "skipPackage", true);
        assertThat(mojo.isPhaseSkipped(), is(true));
        assertThat(mojo.shouldSkip(), is(true));
    }

    @Test
    public void aggregateProjectMojo_phaseFlagTogglesIndependently() throws Exception {
        final AggregateProjectMojo mojo = new AggregateProjectMojo();
        assertThat(mojo.isPhaseSkipped(), is(false));
        setBooleanField(mojo, "skipProject", true);
        assertThat(mojo.isPhaseSkipped(), is(true));
        assertThat(mojo.shouldSkip(), is(true));
    }

    @Test
    public void globalSkipSkipsAPhaseEvenWhenItsPhaseFlagIsOff() throws Exception {
        final GenerateMojo mojo = new GenerateMojo();
        setBooleanField(mojo, "skip", true);
        assertThat(mojo.isPhaseSkipped(), is(false));
        assertThat(mojo.shouldSkip(), is(true));
    }
    // </editor-fold>

    /**
     * Sets a {@code boolean} field by name on {@code target}, walking up the class hierarchy so a
     * field declared on a superclass (e.g. the global {@code skip} on {@link AbstractAiIndexMojo}) is
     * found. Mirrors how Maven's plugin framework injects {@code @Parameter} fields via reflection.
     *
     * @param target    the object whose field to set
     * @param fieldName the field name
     * @param value     the boolean value to set
     * @throws NoSuchFieldException if no such field exists anywhere in the hierarchy
     * @throws IllegalAccessException if the field cannot be set
     */
    private static void setBooleanField(final Object target, final String fieldName, final boolean value)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    // <editor-fold defaultstate="collapsed" desc="execute() honours the skip flag">

    @TempDir
    Path tempDir;

    /**
     * Runs a mojo with the global skip flag set and returns what it logged.
     *
     * <p>The output directory is a path inside the temp dir that does not exist yet: if the mojo did
     * NOT honour the flag it would run the engine and create it, so the directory's continued absence
     * is the second, independent assertion that nothing ran.
     *
     * @param mojo the mojo under test, already constructed
     * @return the captured info lines
     * @throws Exception if the mojo throws or a field cannot be set
     */
    private List<String> executeWithGlobalSkip(final AbstractAiIndexMojo mojo) throws Exception {
        final CapturingLog log = new CapturingLog();
        mojo.setLog(log);
        setBooleanField(mojo, "skip", true);
        mojo.baseDirectory = tempDir.toFile();
        mojo.outputDirectory = new File(tempDir.toFile(), "never-created");

        mojo.execute();

        assertThat("skipping must not create the output directory", mojo.outputDirectory.exists(), is(false));
        return log.infoMessages();
    }

    /**
     * The risk this closes: nothing verified that any {@code execute()} actually consults
     * {@code shouldSkip()}. {@code shouldSkip()} itself was well tested, but a mojo that never called
     * it would still pass every one of those tests -- and would load a model and write files under
     * {@code -Dsrcmorph.skip=true}.
     *
     * @throws Exception if the mojo throws
     */
    @Test
    public void generateMojo_globalSkip_returnsWithoutRunningTheEngine() throws Exception {
        assertThat(executeWithGlobalSkip(new GenerateMojo()).contains("AI index generation skipped."), is(true));
    }

    /**
     * Same contract for the package-aggregation goal.
     *
     * @throws Exception if the mojo throws
     */
    @Test
    public void aggregatePackagesMojo_globalSkip_returnsWithoutRunningTheEngine() throws Exception {
        assertThat(
                executeWithGlobalSkip(new AggregatePackagesMojo()).contains("AI package aggregation skipped."),
                is(true));
    }

    /**
     * Same contract for the project-aggregation goal.
     *
     * @throws Exception if the mojo throws
     */
    @Test
    public void aggregateProjectMojo_globalSkip_returnsWithoutRunningTheEngine() throws Exception {
        assertThat(
                executeWithGlobalSkip(new AggregateProjectMojo()).contains("AI project index aggregation skipped."),
                is(true));
    }

    /**
     * Same contract for the calibrate goal, which had no test constructing it at all.
     *
     * @throws Exception if the mojo throws
     */
    @Test
    public void calibrateMojo_globalSkip_returnsWithoutRunningTheEngine() throws Exception {
        assertThat(executeWithGlobalSkip(new CalibrateMojo()).contains("AI index calibration skipped."), is(true));
    }

    /**
     * {@code calibrate} is a manual diagnostic goal with no phase flag of its own -- its
     * {@code isPhaseSkipped()} is a hard {@code false}, so only the global flag disables it. That is a
     * documented contract with no guard behind it, which makes it worth pinning.
     */
    @Test
    public void calibrateMojo_hasNoPhaseFlagOfItsOwn() {
        // arrange
        final CalibrateMojo mojo = new CalibrateMojo();

        // act / assert
        assertThat(mojo.isPhaseSkipped(), is(false));
        assertThat(mojo.shouldSkip(), is(false));
    }

    // </editor-fold>
}
