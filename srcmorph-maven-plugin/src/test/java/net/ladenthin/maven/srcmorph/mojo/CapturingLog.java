// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.mojo;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;

/**
 * Maven {@link org.apache.maven.plugin.logging.Log} that records the {@code info} lines a goal
 * emits, so a test can assert on a goal's output.
 *
 * <p>Extends {@link SystemStreamLog} rather than implementing the ~20-method {@code Log} interface
 * by hand; this module has no mocking framework on the test classpath. Shared by
 * {@code MojoPhaseSkipTest} (which asserts the skip message) and
 * {@code MojoConfigurationMappingTest} (which asserts the calibrate report).</p>
 */
final class CapturingLog extends SystemStreamLog {

    private final List<String> infoMessages = new ArrayList<>();

    @Override
    public void info(final CharSequence content) {
        infoMessages.add(String.valueOf(content));
    }

    /**
     * Returns the recorded {@code info} lines, in emission order.
     *
     * @return the recorded messages
     */
    List<String> infoMessages() {
        return infoMessages;
    }
}
