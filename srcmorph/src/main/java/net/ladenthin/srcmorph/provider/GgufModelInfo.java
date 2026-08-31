// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.srcmorph.support.ConvertToRecord;
import org.jspecify.annotations.Nullable;

/**
 * What a GGUF file says about itself, read from its header without loading the model.
 *
 * <p>Deliberately framework-free: {@link GgufModelInspector} produces it inside the {@code provider}
 * package (the only package the {@code jniConfinedToProvider} architecture rule lets touch the
 * llama binding), and callers in {@code engine} consume plain Java out of it.</p>
 *
 * <p>Record-shaped value type marked {@link ConvertToRecord} for the future Java&nbsp;17+ migration.</p>
 */
@ConvertToRecord
@ToString
@EqualsAndHashCode
public final class GgufModelInfo {

    /** {@link #contextLength()} when the file declares no context length. */
    public static final long UNKNOWN_CONTEXT_LENGTH = -1L;

    private final boolean readable;
    private final String modelName;
    private final long contextLength;
    private final String failure;

    /**
     * Creates a new {@link GgufModelInfo}.
     *
     * @param readable      whether the file parsed as a GGUF at all
     * @param modelName     the declared model name, or empty when absent/unreadable
     * @param contextLength the declared context length in tokens, or {@link #UNKNOWN_CONTEXT_LENGTH}
     * @param failure       why the file could not be read, or empty when it could
     */
    public GgufModelInfo(
            final boolean readable,
            final @Nullable String modelName,
            final long contextLength,
            final @Nullable String failure) {
        this.readable = readable;
        this.modelName = modelName != null ? modelName : "";
        this.contextLength = contextLength;
        this.failure = failure != null ? failure : "";
    }

    /**
     * Returns whether the file parsed as a GGUF.
     *
     * @return {@code true} when the header was read successfully
     */
    public boolean readable() {
        return readable;
    }

    /**
     * Returns the model name the file declares.
     *
     * @return the model name, or an empty string when absent or unreadable
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Returns the context length in tokens the file declares.
     *
     * @return the declared context length, or {@link #UNKNOWN_CONTEXT_LENGTH} when it declares none
     */
    public long contextLength() {
        return contextLength;
    }

    /**
     * Returns why the file could not be read.
     *
     * @return the failure description, or an empty string when the file was readable
     */
    public String failure() {
        return failure;
    }
}
