// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.indexer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class AiCalibrationMeasurementTest {

    @Test
    public void accessorsReturnConstructorValues() {
        final AiCalibrationMeasurement m = new AiCalibrationMeasurement(3.5d, 900.0d, 45.0d, 4.2d, 1200.0d, 480);
        assertThat(m.loadSeconds(), is(3.5d));
        assertThat(m.prefillTokensPerSecond(), is(900.0d));
        assertThat(m.decodeTokensPerSecond(), is(45.0d));
        assertThat(m.charsPerToken(), is(4.2d));
        assertThat(m.midPrefillTokensPerSecond(), is(1200.0d));
        assertThat(m.cachedPromptTokens(), is(480));
    }
}
