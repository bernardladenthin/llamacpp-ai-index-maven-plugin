// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.kotlinx.lincheck.LinChecker;
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions;
import org.jetbrains.lincheck.datastructures.Operation;
import org.junit.jupiter.api.Test;

/**
 * Linearizability check demonstrating the Lincheck setup.
 *
 * <p>Uses a simple {@link AtomicInteger} counter as the system under test to
 * verify that concurrent increment and read operations are linearizable.</p>
 *
 * <p>Named after what it actually exercises. It was previously called
 * {@code AiGenerationKindLincheckTest}, which merely borrowed the name of an enum it never
 * referenced; that enum has since been deleted as a leftover of the fixed generation taxonomy the
 * configurable prompt-key routing replaced.</p>
 */
public class AtomicCounterLincheckTest {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Operation
    public int increment() {
        return counter.incrementAndGet();
    }

    @Operation
    public int get() {
        return counter.get();
    }

    @Test
    public void modelCheckingTest() {
        ModelCheckingOptions options = new ModelCheckingOptions()
                .iterations(20)
                .invocationsPerIteration(500)
                .threads(2)
                .actorsPerThread(3);
        LinChecker.check(AtomicCounterLincheckTest.class, options);
    }
}
