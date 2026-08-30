// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.provider;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import org.junit.jupiter.api.Test;

public class AiGenerationProviderFactoryTest {

    private final AiGenerationProviderFactory factory = new AiGenerationProviderFactory();
    private final AiPromptSupport promptSupport = new AiPromptSupport(null);

    private static LlamaCppJniConfig jniConfig() {
        return new LlamaCppJniConfig(
                "model.gguf",
                2048,
                128,
                0.15f,
                2,
                0.9f,
                40,
                0.0f,
                -1.0f,
                1.1f,
                false,
                true,
                false,
                0,
                -1,
                -1,
                -1,
                -1,
                -1,
                "",
                -1,
                "",
                "low",
                -1,
                0.0f,
                1.75f,
                2,
                -1,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @Test
    public void nullProviderNameDefaultsToMock() {
        assertThat(factory.create(null, jniConfig(), promptSupport), is(instanceOf(MockAiGenerationProvider.class)));
    }

    @Test
    public void blankProviderNameDefaultsToMock() {
        assertThat(factory.create("   ", jniConfig(), promptSupport), is(instanceOf(MockAiGenerationProvider.class)));
    }

    @Test
    public void mockProviderNameReturnsMock() {
        assertThat(factory.create("mock", jniConfig(), promptSupport), is(instanceOf(MockAiGenerationProvider.class)));
    }

    @Test
    public void llamacppJniProviderNameReturnsJniProvider() {
        // Lazy model loading means this constructs without touching the native lib / GGUF,
        // so the factory's jni branch is covered without a model on disk.
        assertThat(
                factory.create("llamacpp-jni", jniConfig(), promptSupport),
                is(instanceOf(LlamaCppJniAiGenerationProvider.class)));
    }

    @Test
    public void unknownProviderNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("nope", jniConfig(), promptSupport));
    }
}
