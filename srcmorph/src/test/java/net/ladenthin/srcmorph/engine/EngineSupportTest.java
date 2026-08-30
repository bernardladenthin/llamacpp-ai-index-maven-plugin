// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.srcmorph.engine;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.ladenthin.srcmorph.config.AiFieldGenerationConfig;
import net.ladenthin.srcmorph.config.AiModelDefinition;
import net.ladenthin.srcmorph.config.AiModelDefinitionSupport;
import net.ladenthin.srcmorph.config.SrcMorphConfiguration;
import net.ladenthin.srcmorph.prompt.AiPromptDefinition;
import net.ladenthin.srcmorph.prompt.AiPromptSupport;
import net.ladenthin.srcmorph.provider.LlamaCppJniConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EngineSupportTest {

    @TempDir
    Path tempDir;

    // <editor-fold defaultstate="collapsed" desc="resolveSubtrees">
    @Test
    public void resolveSubtrees_nullReturnsEmpty() {
        assertThat(EngineSupport.resolveSubtrees(tempDir, null), is(Collections.<Path>emptyList()));
    }

    @Test
    public void resolveSubtrees_emptyReturnsEmpty() {
        assertThat(
                EngineSupport.resolveSubtrees(tempDir, Collections.<String>emptyList()),
                is(Collections.<Path>emptyList()));
    }

    @Test
    public void resolveSubtrees_earlyReturnResultIsMutable() {
        // The early-return (null/empty subtrees) branch returns the same mutable ArrayList as the
        // loop-completion branch, not an immutable Collections.emptyList(), so callers can treat every
        // result uniformly. Mutating it here must not throw UnsupportedOperationException.
        final List<Path> resolved = EngineSupport.resolveSubtrees(tempDir, null);
        resolved.add(tempDir);
        assertThat(resolved, hasItem(tempDir));
    }

    @Test
    public void resolveSubtrees_existingSubtreeIsResolved() throws IOException {
        final Path sub = tempDir.resolve("exists");
        Files.createDirectories(sub);

        final List<Path> resolved = EngineSupport.resolveSubtrees(tempDir, Arrays.asList("exists"));

        assertThat(resolved, hasItem(sub.normalize()));
    }

    @Test
    public void resolveSubtrees_missingSubtreeIsSkipped() {
        final List<Path> resolved = EngineSupport.resolveSubtrees(tempDir, Arrays.asList("does-not-exist"));

        assertThat(resolved, is(Collections.<Path>emptyList()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="buildPromptSupport">
    @Test
    public void buildPromptSupport_nullListBuildsEmptySupport() throws SrcMorphException {
        final AiPromptSupport support = EngineSupport.buildPromptSupport(null);
        assertThat(support, is(notNullValue()));
    }

    @Test
    public void buildPromptSupport_missingKeyThrowsSrcMorphException() {
        final AiPromptDefinition bad = new AiPromptDefinition();
        bad.setTemplate("template with no key");

        final SrcMorphException e =
                assertThrows(SrcMorphException.class, () -> EngineSupport.buildPromptSupport(Arrays.asList(bad)));
        assertThat(e.getMessage(), containsString("Invalid plugin configuration:"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="buildAiModelDefinitionSupport">
    @Test
    public void buildAiModelDefinitionSupport_nullListBuildsEmptySupport() throws SrcMorphException {
        assertThat(EngineSupport.buildAiModelDefinitionSupport(null), is(notNullValue()));
    }

    @Test
    public void buildAiModelDefinitionSupport_missingKeyThrowsSrcMorphException() {
        final AiModelDefinition bad = new AiModelDefinition();
        bad.setModelPath("model.gguf");

        final SrcMorphException e = assertThrows(
                SrcMorphException.class, () -> EngineSupport.buildAiModelDefinitionSupport(Arrays.asList(bad)));
        assertThat(e.getMessage(), containsString("Invalid plugin configuration:"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="resolveLlamaCppJniConfig">
    @Test
    public void resolveLlamaCppJniConfig_noKey_usesFallbackWhenNoFieldGenerations() {
        final SrcMorphConfiguration config = new SrcMorphConfiguration();
        config.setLlamaModelPath("fallback.gguf");
        config.setLlamaContextSize(2048);
        config.setLlamaMaxOutputTokens(64);
        config.setLlamaTemperature(0.2f);
        config.setLlamaThreads(3);

        final AiModelDefinitionSupport modelDefinitionSupport = new AiModelDefinitionSupport(null);
        final LlamaCppJniConfig result = EngineSupport.resolveLlamaCppJniConfig(config, modelDefinitionSupport);

        assertThat(result.modelPath(), is("fallback.gguf"));
        assertThat(result.contextSize(), is(2048));
        assertThat(result.maxOutputTokens(), is(64));
        assertThat(result.temperature(), is(0.2f));
        assertThat(result.threads(), is(3));
    }

    @Test
    public void resolveLlamaCppJniConfig_noKey_usesFirstFieldGenerationsEntryWhenPresent() {
        final SrcMorphConfiguration config = new SrcMorphConfiguration();
        final AiFieldGenerationConfig rule = new AiFieldGenerationConfig();
        rule.setPromptKey("prompt");
        rule.setAiDefinitionKey("routed-model");
        config.setFieldGenerations(Arrays.asList(rule));

        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey("routed-model");
        definition.setModelPath("routed.gguf");
        final AiModelDefinitionSupport modelDefinitionSupport = new AiModelDefinitionSupport(Arrays.asList(definition));

        final LlamaCppJniConfig result = EngineSupport.resolveLlamaCppJniConfig(config, modelDefinitionSupport);

        assertThat(result.modelPath(), is("routed.gguf"));
    }

    @Test
    public void resolveLlamaCppJniConfig_byKey_looksUpNamedDefinition() {
        final SrcMorphConfiguration config = new SrcMorphConfiguration();

        final AiModelDefinition definition = new AiModelDefinition();
        definition.setKey("named");
        definition.setModelPath("named.gguf");
        final AiModelDefinitionSupport modelDefinitionSupport = new AiModelDefinitionSupport(Arrays.asList(definition));

        final LlamaCppJniConfig result = EngineSupport.resolveLlamaCppJniConfig(modelDefinitionSupport, "named");

        assertThat(result.modelPath(), is("named.gguf"));
    }

    @Test
    public void resolveLlamaCppJniConfig_byKey_missingKeyThrowsIllegalArgumentException() {
        final SrcMorphConfiguration config = new SrcMorphConfiguration();
        final AiModelDefinitionSupport modelDefinitionSupport = new AiModelDefinitionSupport(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> EngineSupport.resolveLlamaCppJniConfig(modelDefinitionSupport, "missing"));
    }
    // </editor-fold>
}
