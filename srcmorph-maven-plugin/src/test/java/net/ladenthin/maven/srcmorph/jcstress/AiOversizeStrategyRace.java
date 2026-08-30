// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.maven.srcmorph.jcstress;

import net.ladenthin.srcmorph.config.AiOversizeStrategy;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Description;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.ZZ_Result;

/**
 * Minimal jcstress example proving the harness is wired up.
 *
 * <p>It races two reads of enum constants, which the JMM already guarantees -- the point is the
 * infrastructure, not the assertion. It used to read {@code AiGenerationKind}, an enum no production
 * code ever referenced; that enum has been deleted, so the example now reads a real one.</p>
 */
@JCStressTest
@Description("Two threads reading enum constants must always see the expected values.")
@Outcome(id = "true, true", expect = Expect.ACCEPTABLE, desc = "Both readers see the correct enum constants")
@Outcome(
        id = {"true, false", "false, true", "false, false"},
        expect = Expect.FORBIDDEN,
        desc = "BUG: enum constant read unexpectedly")
@State
public class AiOversizeStrategyRace {

    @Actor
    public void actor1(ZZ_Result r) {
        r.r1 = AiOversizeStrategy.FAIL == AiOversizeStrategy.FAIL;
    }

    @Actor
    public void actor2(ZZ_Result r) {
        r.r2 = AiOversizeStrategy.MAP_REDUCE == AiOversizeStrategy.MAP_REDUCE;
    }
}
