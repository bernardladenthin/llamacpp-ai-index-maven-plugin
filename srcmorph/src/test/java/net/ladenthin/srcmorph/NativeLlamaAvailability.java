// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assumptions;

/**
 * Decides whether the model-backed llama.cpp tests can run on this machine, and skips them with a
 * reason when they cannot.
 *
 * <p><b>Why this class exists — do not reintroduce an opt-in flag.</b> These tests used to be gated
 * on {@code -DrunNativeLlamaTests=true}. That property was present in the very first commit and was
 * never set by any workflow or POM in the repository's entire history, so the tests never ran
 * anywhere: not in CI, not in a normal local build. The repository nonetheless carries the ~90 MB
 * GGUF they need, checked out on every job.</p>
 *
 * <p>That cost something real. {@code LlamaCppJniAiGenerationProvider} forwarded a {@code -1} DRY
 * penalty window to a binding that rejects any negative window (llama.cpp b10273 dropped the old
 * "{@code -1} = context size" meaning), so <em>every</em> real generation threw
 * {@link IllegalArgumentException} before producing a token. Two releases, 1.1.0 and 1.1.1, shipped
 * with a completely non-functional {@code llamacpp-jni} provider, and every gate stayed green
 * throughout: the whole test suite, the {@code Plan} command and the fat-jar smoke all run on the
 * {@code mock} provider, which never loads the native library. Turning these three tests on found it
 * in ten seconds.</p>
 *
 * <p>So the gate is now a <em>capability</em> check, not a preference. The tests run by default and
 * skip only where they genuinely cannot run — a checkout without the model, or a platform for which
 * {@code net.ladenthin:llama} ships no native library. Both skips name their cause, so a silent
 * "nothing ran" is not possible.</p>
 */
public final class NativeLlamaAvailability {

    /** The committed test model, resolved from the module base directory Surefire runs in. */
    public static final Path MODEL = Paths.get("src", "test", "resources", "SmolLM2-135M-Instruct-Q3_K_M.gguf")
            .toAbsolutePath();

    /** Fully qualified name of the binding entry point whose static initializer loads the native library. */
    private static final String LLAMA_MODEL_CLASS = "net.ladenthin.llama.LlamaModel";

    /**
     * Why the native library could not be loaded, or {@code null} when it loaded.
     *
     * <p>Resolved once per JVM: loading is a static-initializer side effect, so a second attempt would
     * report {@link NoClassDefFoundError} rather than the original cause.</p>
     */
    private static final String NATIVE_FAILURE = resolveNativeFailure();

    /** Utility class; not instantiable. */
    private NativeLlamaAvailability() {
        // no-op
    }

    private static String resolveNativeFailure() {
        try {
            Class.forName(LLAMA_MODEL_CLASS);
            return null;
        } catch (final ClassNotFoundException | LinkageError e) {
            // LinkageError covers UnsatisfiedLinkError (no native for this OS/arch in the jar) and
            // ExceptionInInitializerError (the loader threw while extracting). Either way the tests
            // cannot run here, and the message says which.
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * System property that turns the skips below into failures. Set it wherever these tests are
     * <em>expected</em> to run.
     */
    public static final String REQUIRE_PROPERTY = "net.ladenthin.srcmorph.requireNativeLlama";

    /**
     * Skips the calling test unless both the model file and the native library are available &mdash;
     * unless {@link #REQUIRE_PROPERTY} says they must be, in which case it fails instead.
     *
     * <p>Call this first in every test that loads a real model.</p>
     *
     * <p><b>Why the property exists.</b> A capability check that skips is right on a developer machine
     * with an unsupported OS/arch, and wrong in CI: a native library that stops loading, or a model
     * file that stops being checked out, would silently return the build to a green run with zero real
     * inference — which is exactly how this repository shipped a dead provider through two releases.
     * CI sets the property, so there the same conditions fail loudly and name what is missing.</p>
     */
    public static void assumeAvailable() {
        final boolean required = Boolean.getBoolean(REQUIRE_PROPERTY);
        if (NATIVE_FAILURE != null) {
            final String message =
                    "net.ladenthin:llama ships no loadable native library for this platform — " + NATIVE_FAILURE;
            if (required) {
                throw new AssertionError(message + " (-D" + REQUIRE_PROPERTY + " is set, so this is a failure)");
            }
            Assumptions.assumeTrue(false, message);
        }
        if (!Files.exists(MODEL)) {
            final String message = "Model file missing: " + MODEL;
            if (required) {
                throw new AssertionError(message + " (-D" + REQUIRE_PROPERTY + " is set, so this is a failure)");
            }
            Assumptions.assumeTrue(false, message);
        }
    }

    /**
     * Returns the absolute path of the committed test model.
     *
     * @return the model path as a string, for APIs that take one
     */
    public static String modelPath() {
        return MODEL.toString();
    }
}
