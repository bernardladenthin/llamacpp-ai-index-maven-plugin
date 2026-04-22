// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.maven.llamacpp.aiindex;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LlamaCppJniAiSummaryProvider implements AiGenerationProvider, AutoCloseable {

    private final LlamaCppJniConfig config;
    private final LlamaModel model;
    private final AiPromptSupport promptSupport;
    private final AiResponseNormalizer responseNormalizer = new AiResponseNormalizer();

    public LlamaCppJniAiSummaryProvider(
            final LlamaCppJniConfig config,
            final AiPromptSupport promptSupport
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.promptSupport = Objects.requireNonNull(promptSupport, "promptSupport");

        final ModelParameters modelParameters = new ModelParameters()
                .setModel(config.modelPath())
                .setCtxSize(config.contextSize())
                .setThreads(config.threads());

        this.model = new LlamaModel(modelParameters);
    }

    @Override
    public String generate(final AiGenerationRequest request) throws IOException {
        return generate(request, config.temperature());
    }

    @Override
    public String generate(final AiGenerationRequest request, final float temperatureOverride) throws IOException {
        final String prompt = promptSupport.buildPrompt(request);

        final List<Pair<String, String>> messages = new ArrayList<>();
        messages.add(new Pair<>("user", prompt));

        final InferenceParameters inferenceParameters = new InferenceParameters("")
                .setMessages(null, messages)
                .setTemperature(temperatureOverride)
                .setNPredict(config.maxOutputTokens())
                .setTopP(config.topP())
                .setTopK(config.topK())
                .setRepeatPenalty(config.repeatPenalty());

        final List<String> stops = config.stopStrings();
        if (stops != null && !stops.isEmpty()) {
            inferenceParameters.setStopStrings(stops.toArray(new String[0]));
        }

        final StringBuilder sb = new StringBuilder();
        for (final LlamaOutput output : model.generateChat(inferenceParameters)) {
            sb.append(output.text);
        }
        return responseNormalizer.normalize(sb.toString());
    }

    @Override
    public void close() throws IOException {
        model.close();
    }
}
