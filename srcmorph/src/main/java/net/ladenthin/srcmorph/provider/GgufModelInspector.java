// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import java.io.IOException;
import java.nio.file.Path;
import net.ladenthin.llama.GgufInspector;
import net.ladenthin.llama.value.GgufMetadata;

/**
 * Reads what a GGUF file declares about itself, so a run can check its configuration against the
 * model <em>before</em> anything is loaded.
 *
 * <p>The binding's {@link GgufInspector} parses only the header key/value table: no native library,
 * no tensor data, no RAM beyond that table. That is what makes this usable from the plan phase,
 * whose whole promise is that it loads no model.</p>
 *
 * <p>Never throws. An unreadable file is a {@link GgufModelInfo} carrying the reason, because the
 * caller decides what a given failure means — a missing context length is worth a warning, a file
 * that is not a GGUF at all is worth stopping for.</p>
 */
public final class GgufModelInspector {

    /** Creates a new {@link GgufModelInspector}. */
    public GgufModelInspector() {
        // no-op
    }

    /**
     * Reads the header of the given GGUF file.
     *
     * @param modelFile the file to read
     * @return what the file declares, or an unreadable result carrying the reason
     */
    public GgufModelInfo inspect(final Path modelFile) {
        try {
            final GgufMetadata metadata = GgufInspector.read(modelFile);
            return new GgufModelInfo(
                    true,
                    metadata.getModelName().orElse(""),
                    metadata.getContextLength().orElse(GgufModelInfo.UNKNOWN_CONTEXT_LENGTH),
                    "");
        } catch (final IOException | RuntimeException e) {
            // RuntimeException too: a truncated or non-GGUF file fails inside the header parse with
            // whatever unchecked exception the format check raises, and "unreadable" is the answer
            // either way. Swallowing it here is safe because the reason is carried out to the caller.
            return new GgufModelInfo(false, "", GgufModelInfo.UNKNOWN_CONTEXT_LENGTH, String.valueOf(e.getMessage()));
        }
    }
}
