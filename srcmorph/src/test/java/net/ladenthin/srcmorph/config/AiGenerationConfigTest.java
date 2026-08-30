// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.config;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AiGenerationConfigTest {

    @Test
    public void samplingScalarGettersReflectSetters() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setTopP(0.73f);
        c.setTopK(42);
        c.setRepeatPenalty(1.27f);
        // Distinct non-zero values kill the "return 0" primitive mutants on these getters.
        assertThat(c.getTopP(), is(0.73f));
        assertThat(c.getTopK(), is(42));
        assertThat(c.getRepeatPenalty(), is(1.27f));
    }

    @Test
    public void stopStringsEmptyByDefault() {
        // The field defaults to an empty (non-null) list. Asserting non-null/empty kills the
        // negate mutant on the getStopStrings null-guard (which would return null instead).
        assertThat(new AiGenerationConfig().getStopStrings(), is(Collections.<String>emptyList()));
    }

    @Test
    public void stopStringsRoundTripUnmodifiable() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setStopStrings(Arrays.asList("</s>", "STOP"));
        assertThat(c.getStopStrings(), hasItem("</s>"));
        assertThat(c.getStopStrings(), hasItem("STOP"));
        assertThrows(
                UnsupportedOperationException.class, () -> c.getStopStrings().add("x"));
    }

    @Test
    public void setStopStringsNullResetsToEmptyList() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setStopStrings(Collections.singletonList("a"));
        c.setStopStrings(null);
        // null arg resets to an EMPTY (non-null) list — kills the negate mutant on the setter ternary.
        assertThat(c.getStopStrings(), is(Collections.<String>emptyList()));
    }

    @Test
    public void cachePromptDefaultsTrueAndTogglesFalse() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default true kills the false-default / "return false" getter mutants.
        assertThat(c.isCachePrompt(), is(true));
        c.setCachePrompt(false);
        // Observing false after the setter kills the "return true" getter mutant and the
        // removed-assignment setter mutant.
        assertThat(c.isCachePrompt(), is(false));
    }

    @Test
    public void reasoningEffortDefaultsLowAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default "low" kills the empty/null return mutants on the getter.
        assertThat(c.getReasoningEffort(), is("low"));
        c.setReasoningEffort("high");
        // Round-tripped value kills the removed-assignment setter mutant.
        assertThat(c.getReasoningEffort(), is("high"));
    }

    @Test
    public void minPDefaultsDisabledAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default 0.0 (disabled) kills the inline-constant and "return 1.0" getter mutants.
        assertThat(c.getMinP(), is(0.0f));
        c.setMinP(0.05f);
        // Round-tripped non-zero value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getMinP(), is(0.05f));
    }

    @Test
    public void topNSigmaDefaultsDisabledAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1.0 (disabled) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getTopNSigma(), is(-1.0f));
        c.setTopNSigma(1.5f);
        // Round-tripped value kills the getter "return 0" and removed-assignment setter mutants.
        assertThat(c.getTopNSigma(), is(1.5f));
    }

    @Test
    public void swaFullDefaultsTrueAndTogglesFalse() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default true (E4 batch default) kills the "return false" getter mutant.
        assertThat(c.isSwaFull(), is(true));
        c.setSwaFull(false);
        // Observing false kills the "return true" getter and removed-assignment setter mutants.
        assertThat(c.isSwaFull(), is(false));
    }

    @Test
    public void cacheReuseDefaultsTwoFiftySixAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default 256 (E4 batch default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getCacheReuse(), is(256));
        c.setCacheReuse(128);
        // Round-tripped distinct value kills the removed-assignment setter mutant.
        assertThat(c.getCacheReuse(), is(128));
    }

    @Test
    public void gpuLayersDefaultsMinusOneAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (leave binding/build default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getGpuLayers(), is(-1));
        c.setGpuLayers(33);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getGpuLayers(), is(33));
    }

    @Test
    public void cpuMoeLayersDefaultsMinusOneAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (leave binding/build default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getCpuMoeLayers(), is(-1));
        c.setCpuMoeLayers(24);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getCpuMoeLayers(), is(24));
    }

    @Test
    public void cpuMoeLayersAcceptsZero() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setCpuMoeLayers(0);
        // 0 is a meaningful value ("keep no expert layer on the CPU"), not "unset" — it must survive
        // the setter unchanged, which is what makes the provider's >= 0 guard forward it.
        assertThat(c.getCpuMoeLayers(), is(0));
    }

    @Test
    public void cpuFfnLayersDefaultsMinusOneAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (leave binding/build default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getCpuFfnLayers(), is(-1));
        c.setCpuFfnLayers(16);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getCpuFfnLayers(), is(16));
    }

    @Test
    public void cpuFfnLayersAcceptsZero() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setCpuFfnLayers(0);
        // Same "0 is meaningful, not unset" contract as cpuMoeLayers.
        assertThat(c.getCpuFfnLayers(), is(0));
    }

    @Test
    public void kvUnifiedPerSlotDefaultsMinusOneAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (leave binding/build default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getKvUnifiedPerSlot(), is(-1));
        c.setKvUnifiedPerSlot(4096);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getKvUnifiedPerSlot(), is(4096));
    }

    @Test
    public void tensorReadLazyDefaultsEmptyAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default "" (leave binding/build default) kills the null/non-empty return mutants on the getter.
        assertThat(c.getTensorReadLazy(), is(""));
        c.setTensorReadLazy("on");
        // Round-tripped value kills the empty-return getter and removed-assignment setter mutants.
        assertThat(c.getTensorReadLazy(), is("on"));
    }

    @Test
    public void setTensorReadLazyNullResetsToEmpty() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setTensorReadLazy("auto");
        c.setTensorReadLazy(null);
        // null arg resets to "" — kills the negate mutant on the setter ternary (which would store null).
        assertThat(c.getTensorReadLazy(), is(""));
    }

    @Test
    public void mainGpuDefaultsMinusOneAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (leave binding/build default) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getMainGpu(), is(-1));
        c.setMainGpu(1);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getMainGpu(), is(1));
    }

    @Test
    public void devicesDefaultsEmptyAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default "" (leave binding/build default) kills the null/non-empty return mutants on the getter.
        assertThat(c.getDevices(), is(""));
        c.setDevices("Vulkan1");
        // Round-tripped value kills the empty-return getter and removed-assignment setter mutants.
        assertThat(c.getDevices(), is("Vulkan1"));
    }

    @Test
    public void setDevicesNullResetsToEmpty() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setDevices("CUDA0");
        c.setDevices(null);
        // null arg resets to "" — kills the negate mutant on the setter ternary (which would store null).
        assertThat(c.getDevices(), is(""));
    }

    @Test
    public void reasoningBudgetTokensDefaultsUnrestrictedAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default -1 (unrestricted) kills the inline-constant / "return 0" getter mutants.
        assertThat(c.getReasoningBudgetTokens(), is(-1));
        c.setReasoningBudgetTokens(2048);
        // Round-tripped value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getReasoningBudgetTokens(), is(2048));
    }

    @Test
    public void dryMultiplierDefaultsDisabledAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        // Default 0.0 (disabled) kills the inline-constant / "return 1.0" getter mutants.
        assertThat(c.getDryMultiplier(), is(0.0f));
        c.setDryMultiplier(0.8f);
        // Round-tripped non-zero value kills the "return 0" getter and removed-assignment setter mutants.
        assertThat(c.getDryMultiplier(), is(0.8f));
    }

    @Test
    public void dryBaseDefaultsAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        assertThat(c.getDryBase(), is(1.75f));
        c.setDryBase(1.5f);
        assertThat(c.getDryBase(), is(1.5f));
    }

    @Test
    public void dryAllowedLengthDefaultsAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        assertThat(c.getDryAllowedLength(), is(2));
        c.setDryAllowedLength(5);
        assertThat(c.getDryAllowedLength(), is(5));
    }

    @Test
    public void dryPenaltyLastNDefaultsWholeContextAndRoundTrips() {
        AiGenerationConfig c = new AiGenerationConfig();
        assertThat(c.getDryPenaltyLastN(), is(-1));
        c.setDryPenaltyLastN(256);
        assertThat(c.getDryPenaltyLastN(), is(256));
    }

    @Test
    public void drySequenceBreakersEmptyByDefault() {
        // Defaults to an empty (non-null) list. Asserting non-null/empty kills the negate mutant
        // on the getDrySequenceBreakers null path and the setter ternary.
        assertThat(new AiGenerationConfig().getDrySequenceBreakers(), is(Collections.<String>emptyList()));
    }

    @Test
    public void drySequenceBreakersRoundTripUnmodifiable() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setDrySequenceBreakers(Arrays.asList("\n", ":"));
        assertThat(c.getDrySequenceBreakers(), hasItem("\n"));
        assertThat(c.getDrySequenceBreakers(), hasItem(":"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> c.getDrySequenceBreakers().add("x"));
    }

    @Test
    public void setDrySequenceBreakersNullResetsToEmptyList() {
        AiGenerationConfig c = new AiGenerationConfig();
        c.setDrySequenceBreakers(Collections.singletonList("a"));
        c.setDrySequenceBreakers(null);
        // null arg resets to an EMPTY (non-null) list — kills the negate mutant on the setter ternary.
        assertThat(c.getDrySequenceBreakers(), is(Collections.<String>emptyList()));
    }
}
