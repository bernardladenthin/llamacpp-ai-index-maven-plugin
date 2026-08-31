// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * JPMS module descriptor for the srcmorph core library.
 *
 * <p>The module exports the seven framework-free packages extracted from the
 * llamacpp-ai-index-maven-plugin's non-mojo layers: {@code config}, {@code document},
 * {@code engine}, {@code indexer}, {@code prompt}, {@code provider}, and {@code support}. These
 * hold the routing/configuration model (incl. the shared root {@code SrcMorphConfiguration}), the
 * {@code .ai.md} document codec, the per-phase orchestration engines, the file/package/project
 * indexers, the prompt templating support, the AI generation provider abstraction (incl. the
 * llama.cpp JNI binding), and the shared stateless helpers. The Maven plugin module
 * ({@code net.ladenthin.maven.srcmorph}, in its own reactor module {@code srcmorph-maven-plugin})
 * depends on this module and supplies only the {@code mojo} entry points, which now delegate to the
 * {@code engine} package.</p>
 *
 * <p>JSpecify {@code @NullMarked} is declared at the module level here so that no source
 * file compiled at {@code --release 8} references the JSpecify annotation type directly.
 * Otherwise javac would emit an unsuppressible {@code unknown enum constant
 * ElementType.MODULE} classfile-read warning for each source compiled at release 8 that
 * resolves {@code @NullMarked} ({@code @NullMarked} carries
 * {@code @Target({MODULE, PACKAGE, TYPE})} and Java 8 does not know about
 * {@code ElementType.MODULE}). Confining the reference to {@code module-info.java} —
 * which compiles at {@code --release 9} — keeps that warning out of the build entirely.</p>
 *
 * <p>{@code requires static org.jspecify} is needed only at compile time of this
 * descriptor; JSpecify annotations carry {@code RetentionPolicy.CLASS} so module-path
 * consumers never need jspecify on their runtime path. Checker Framework qualifiers are
 * likewise compile-time only.</p>
 *
 * <p>{@code requires net.ladenthin.llama} is a real (non-static) requires: the {@code provider}
 * package calls into the binding at runtime, so a module-path consumer must be able to read it.
 * An earlier version of this descriptor omitted it, reasoning that the isolated
 * {@code module-info-compile} execution never needs the module name — true of <em>compiling this
 * file</em>, and irrelevant to <em>running the result</em>. On the module path every llama.cpp-backed
 * path, including the plan-time {@code GgufModelInspector}, failed for want of a readable module.
 * It is deliberately <b>not</b> {@code transitive}: the binding's types appear only in
 * package-private members ({@code tensorReadLazyMode}, {@code cacheType},
 * {@code buildInferenceParameters}), never in an exported signature — {@code LlamaCppJniConfigFactory}
 * returns srcmorph's own {@code LlamaCppJniConfig} — so no consumer needs the module transitively.
 * Should a llama type ever surface in an exported signature, this must become
 * {@code requires transitive}.</p>
 *
 * <p>Maven, in turn, loads any consumer of this jar classpath-only and ignores the descriptor at
 * runtime, which is why the gap survived unnoticed.</p>
 *
 * <p>This descriptor compiles at {@code --release 9}; the rest of the source compiles
 * at {@code --release 8}. Java 8 runtimes silently ignore {@code module-info.class} at
 * the JAR root.</p>
 */
@org.jspecify.annotations.NullMarked
module net.ladenthin.srcmorph {
    requires static org.jspecify;

    // Lombok is `provided` scope: only used at compile time to generate equals/hashCode/toString.
    // `requires static` means the runtime does not need the lombok jar on the module path —
    // the @lombok.Generated annotation carried on generated members has CLASS retention.
    requires static lombok;

    // indexer/ and engine/ classes log via SLF4J at runtime (not compile-time-only, unlike the
    // annotation-only requires above), so this must be a real (non-static) requires: a
    // module-path consumer needs org.slf4j resolvable at runtime too, not just here.
    requires org.slf4j;

    // The provider package calls the llama.cpp JNI binding at runtime, so this is a real requires,
    // not `requires static`. Not transitive -- see the class javadoc above for why.
    requires net.ladenthin.llama;

    exports net.ladenthin.srcmorph.config;
    exports net.ladenthin.srcmorph.document;
    exports net.ladenthin.srcmorph.engine;
    exports net.ladenthin.srcmorph.indexer;
    exports net.ladenthin.srcmorph.prompt;
    exports net.ladenthin.srcmorph.provider;
    exports net.ladenthin.srcmorph.support;
}
