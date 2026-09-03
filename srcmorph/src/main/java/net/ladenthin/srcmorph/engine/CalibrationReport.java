// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.srcmorph.indexer.AiCalibrationMeasurement;

/**
 * The outcome of one {@link CalibrateEngine#execute()} run: one {@link ModelMeasurement} per distinct
 * routed model, in calibration order.
 *
 * <p>{@link #renderXml()} is the pure, paste-ready {@code <calibration>} XML block renderer — the same
 * text the {@code srcmorph:calibrate} goal has always printed, extracted here so it is unit-testable
 * without a Maven {@code Log} and reusable by any future caller (e.g. a CLI).</p>
 */
@ToString
@EqualsAndHashCode
public final class CalibrationReport {

    /**
     * File name of the machine-readable JSON report, written by {@link CalibrateEngine#execute()}
     * into the configured output directory.
     */
    public static final String JSON_FILE_NAME = "srcmorph-calibration.json";

    /** File name of the machine-readable YAML report, written alongside {@link #JSON_FILE_NAME}. */
    public static final String YAML_FILE_NAME = "srcmorph-calibration.yaml";

    /** Format of the two throughput figures; identical to the one {@link #renderXml()} pastes. */
    private static final String FORMAT_TOKENS_PER_SECOND = "%.1f";

    /** Format of the chars-per-token figure; identical to the one {@link #renderXml()} pastes. */
    private static final String FORMAT_CHARS_PER_TOKEN = "%.2f";

    /** Format of the load duration, which has no counterpart in the XML block. */
    private static final String FORMAT_LOAD_SECONDS = "%.3f";

    private final List<ModelMeasurement> measurements;

    /**
     * Creates a new {@link CalibrationReport}.
     *
     * @param measurements one measurement per distinct routed model, in calibration order
     */
    public CalibrationReport(final List<ModelMeasurement> measurements) {
        this.measurements = new ArrayList<>(measurements);
    }

    /**
     * Returns an unmodifiable view of the per-model measurements, in calibration order.
     *
     * @return the measurements
     */
    public List<ModelMeasurement> measurements() {
        return Collections.unmodifiableList(measurements);
    }

    /**
     * Renders one paste-ready {@code <calibration>} XML block per measured model, each preceded by a
     * comment naming the model key, in calibration order.
     *
     * @return the rendered blocks, or an empty string when no model was measured
     */
    public String renderXml() {
        final StringBuilder out = new StringBuilder();
        for (final ModelMeasurement entry : measurements) {
            appendPasteBlock(out, entry.modelKey(), entry.measurement());
        }
        return out.toString();
    }

    /**
     * Renders the report as JSON.
     *
     * <p>Hand-rolled rather than delegated to Jackson on purpose: this module is framework-free and
     * carries no JSON dependency (only {@code srcmorph-cli} does), and {@link #renderXml()} sets the
     * same precedent. The document is small and fully determined by six numbers and a key.</p>
     *
     * <p>The three figures that also appear in the {@code <calibration>} block are formatted
     * identically, so the JSON and the paste-ready XML can never disagree about what was measured.</p>
     *
     * @return the JSON document; a {@code models} array that is empty when no model was measured
     */
    public String renderJson() {
        if (measurements.isEmpty()) {
            return "{\n  \"models\": []\n}\n";
        }
        final StringBuilder out = new StringBuilder("{\n  \"models\": [\n");
        for (int i = 0; i < measurements.size(); i++) {
            final ModelMeasurement entry = measurements.get(i);
            final AiCalibrationMeasurement m = entry.measurement();
            out.append("    {\n");
            out.append("      \"modelKey\": ").append(quote(entry.modelKey())).append(",\n");
            appendJsonNumber(out, "loadSeconds", String.format(Locale.ROOT, FORMAT_LOAD_SECONDS, m.loadSeconds()));
            appendJsonNumber(
                    out,
                    "prefillTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.prefillTokensPerSecond()));
            appendJsonNumber(
                    out,
                    "decodeTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.decodeTokensPerSecond()));
            appendJsonNumber(
                    out, "charsPerToken", String.format(Locale.ROOT, FORMAT_CHARS_PER_TOKEN, m.charsPerToken()));
            appendJsonNumber(
                    out,
                    "midPrefillTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.midPrefillTokensPerSecond()));
            out.append("      \"cachedPromptTokens\": ")
                    .append(m.cachedPromptTokens())
                    .append('\n');
            out.append("    }");
            out.append(i + 1 < measurements.size() ? ",\n" : "\n");
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    /**
     * Renders the report as YAML, carrying exactly the same keys, order and number formatting as
     * {@link #renderJson()}.
     *
     * <p>Every scalar is emitted double-quoted or numeric, which keeps the output inside the subset of
     * YAML that needs no emitter to get right &#x2014; no block scalars, no anchors, no indentation
     * subtleties beyond a fixed two-level indent.</p>
     *
     * @return the YAML document; {@code models: []} when no model was measured
     */
    public String renderYaml() {
        if (measurements.isEmpty()) {
            return "models: []\n";
        }
        final StringBuilder out = new StringBuilder("models:\n");
        for (final ModelMeasurement entry : measurements) {
            final AiCalibrationMeasurement m = entry.measurement();
            out.append("  - modelKey: ").append(quote(entry.modelKey())).append('\n');
            appendYamlNumber(out, "loadSeconds", String.format(Locale.ROOT, FORMAT_LOAD_SECONDS, m.loadSeconds()));
            appendYamlNumber(
                    out,
                    "prefillTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.prefillTokensPerSecond()));
            appendYamlNumber(
                    out,
                    "decodeTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.decodeTokensPerSecond()));
            appendYamlNumber(
                    out, "charsPerToken", String.format(Locale.ROOT, FORMAT_CHARS_PER_TOKEN, m.charsPerToken()));
            appendYamlNumber(
                    out,
                    "midPrefillTokensPerSecond",
                    String.format(Locale.ROOT, FORMAT_TOKENS_PER_SECOND, m.midPrefillTokensPerSecond()));
            out.append("    cachedPromptTokens: ")
                    .append(m.cachedPromptTokens())
                    .append('\n');
        }
        return out.toString();
    }

    /**
     * Appends one {@code "key": number,} line to the JSON buffer.
     *
     * <p>The value arrives already rendered rather than as a value plus a {@link String#format}
     * pattern: a pattern that reaches {@code String.format} through a parameter is no longer a
     * compile-time constant, which SpotBugs reports as {@code FORMAT_STRING_MANIPULATION}. Formatting
     * at the call site keeps every pattern a literal constant.</p>
     *
     * @param out    the buffer
     * @param key    the JSON key
     * @param value  the already-formatted value
     */
    private static void appendJsonNumber(final StringBuilder out, final String key, final String value) {
        out.append("      \"").append(key).append("\": ").append(value).append(",\n");
    }

    /**
     * Appends one {@code key: number} line to the YAML buffer.
     *
     * <p>Takes the already-rendered value for the same reason as
     * {@link #appendJsonNumber(StringBuilder, String, String)}.</p>
     *
     * @param out    the buffer
     * @param key    the YAML key
     * @param value  the already-formatted value
     */
    private static void appendYamlNumber(final StringBuilder out, final String key, final String value) {
        out.append("    ").append(key).append(": ").append(value).append('\n');
    }

    /**
     * Renders a string as a double-quoted scalar that is valid in both JSON and YAML.
     *
     * <p>A model key comes from user configuration, so it can carry a quote, a backslash or a control
     * character; without escaping, one such key would produce a file neither parser accepts.</p>
     *
     * @param value the string
     * @return the quoted, escaped scalar
     */
    private static String quote(final String value) {
        final int length = value.length();
        final StringBuilder out = new StringBuilder(length + 2);
        out.append('"');
        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < ' ') {
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Appends a copy-pasteable {@code <calibration>} block (with a comment naming the model) to the buffer.
     *
     * @param out      the buffer
     * @param modelKey the model key (for the comment)
     * @param m        the measurement
     */
    private static void appendPasteBlock(
            final StringBuilder out, final String modelKey, final AiCalibrationMeasurement m) {
        out.append("<!-- calibration for aiDefinition '").append(modelKey).append("' (this machine) -->\n");
        out.append("<calibration>\n");
        out.append(String.format(
                Locale.ROOT,
                "    <prefillTokensPerSecond>%.1f</prefillTokensPerSecond>%n",
                m.prefillTokensPerSecond()));
        out.append(String.format(
                Locale.ROOT, "    <decodeTokensPerSecond>%.1f</decodeTokensPerSecond>%n", m.decodeTokensPerSecond()));
        out.append(String.format(Locale.ROOT, "    <charsPerToken>%.2f</charsPerToken>%n", m.charsPerToken()));
        out.append("</calibration>\n");
    }

    /** One model's key paired with its {@link AiCalibrationMeasurement}. */
    @ToString
    @EqualsAndHashCode
    public static final class ModelMeasurement {

        private final String modelKey;
        private final AiCalibrationMeasurement measurement;

        /**
         * Creates a new {@link ModelMeasurement}.
         *
         * @param modelKey    the {@code aiDefinitionKey} that was calibrated
         * @param measurement the measurement
         */
        public ModelMeasurement(final String modelKey, final AiCalibrationMeasurement measurement) {
            this.modelKey = modelKey;
            this.measurement = measurement;
        }

        /**
         * Returns the calibrated model's key.
         *
         * @return the {@code aiDefinitionKey}
         */
        public String modelKey() {
            return modelKey;
        }

        /**
         * Returns the measurement.
         *
         * @return the calibration measurement
         */
        public AiCalibrationMeasurement measurement() {
            return measurement;
        }
    }
}
