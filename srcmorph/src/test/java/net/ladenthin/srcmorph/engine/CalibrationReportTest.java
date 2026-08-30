// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ladenthin.srcmorph.indexer.AiCalibrationMeasurement;
import org.junit.jupiter.api.Test;

public class CalibrationReportTest {

    @Test
    public void modelMeasurement_accessorsReflectConstructorArguments() {
        final AiCalibrationMeasurement measurement = new AiCalibrationMeasurement(1.5d, 100.0d, 50.0d, 4.0d, 90.0d, 16);
        final CalibrationReport.ModelMeasurement m = new CalibrationReport.ModelMeasurement("my-model", measurement);

        assertThat(m.modelKey(), is("my-model"));
        assertThat(m.measurement(), is(measurement));
    }

    @Test
    public void measurements_returnsDefensiveCopyInOrder() {
        final CalibrationReport.ModelMeasurement m1 = new CalibrationReport.ModelMeasurement(
                "a", new AiCalibrationMeasurement(1.0d, 1.0d, 1.0d, 1.0d, 1.0d, 1));
        final CalibrationReport.ModelMeasurement m2 = new CalibrationReport.ModelMeasurement(
                "b", new AiCalibrationMeasurement(2.0d, 2.0d, 2.0d, 2.0d, 2.0d, 2));
        final List<CalibrationReport.ModelMeasurement> source = new ArrayList<>(Arrays.asList(m1, m2));
        final CalibrationReport report = new CalibrationReport(source);

        // Mutating the source list after construction must not affect the report (defensive copy).
        source.clear();

        assertThat(report.measurements(), is(equalTo(Arrays.asList(m1, m2))));
    }

    @Test
    public void measurements_isUnmodifiable() {
        final CalibrationReport report = new CalibrationReport(new ArrayList<CalibrationReport.ModelMeasurement>());
        try {
            report.measurements()
                    .add(new CalibrationReport.ModelMeasurement(
                            "x", new AiCalibrationMeasurement(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0)));
            org.junit.jupiter.api.Assertions.fail("expected UnsupportedOperationException");
        } catch (final UnsupportedOperationException expected) {
            // expected: measurements() must not allow external mutation
        }
    }

    @Test
    public void renderXml_isEmptyForNoMeasurements() {
        final CalibrationReport report = new CalibrationReport(new ArrayList<CalibrationReport.ModelMeasurement>());
        assertThat(report.renderXml(), is(""));
    }

    @Test
    public void renderXml_rendersOnePasteBlockPerModelInOrder() {
        final AiCalibrationMeasurement measurementA =
                new AiCalibrationMeasurement(1.234d, 1000.5d, 200.3d, 4.57d, 900.0d, 128);
        final AiCalibrationMeasurement measurementB =
                new AiCalibrationMeasurement(2.0d, 500.0d, 100.0d, 3.0d, 450.0d, 64);
        final CalibrationReport report = new CalibrationReport(Arrays.asList(
                new CalibrationReport.ModelMeasurement("model-a", measurementA),
                new CalibrationReport.ModelMeasurement("model-b", measurementB)));

        final String expected = "<!-- calibration for aiDefinition 'model-a' (this machine) -->\n"
                + "<calibration>\n"
                + "    <prefillTokensPerSecond>1000.5</prefillTokensPerSecond>\n"
                + "    <decodeTokensPerSecond>200.3</decodeTokensPerSecond>\n"
                + "    <charsPerToken>4.57</charsPerToken>\n"
                + "</calibration>\n"
                + "<!-- calibration for aiDefinition 'model-b' (this machine) -->\n"
                + "<calibration>\n"
                + "    <prefillTokensPerSecond>500.0</prefillTokensPerSecond>\n"
                + "    <decodeTokensPerSecond>100.0</decodeTokensPerSecond>\n"
                + "    <charsPerToken>3.00</charsPerToken>\n"
                + "</calibration>\n";

        assertThat(report.renderXml(), is(expected));
    }

    @Test
    public void toString_containsModelKey() {
        final CalibrationReport report = new CalibrationReport(Arrays.asList(new CalibrationReport.ModelMeasurement(
                "unique-model-key", new AiCalibrationMeasurement(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0))));
        assertThat(report.toString(), org.hamcrest.CoreMatchers.containsString("unique-model-key"));
    }
}
