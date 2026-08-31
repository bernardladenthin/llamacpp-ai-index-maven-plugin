#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0

# Integration test for srcmorph-maven-plugin: runs the plugin as a PLUGIN, from the local
# repository, through a real Maven lifecycle, against the fixture project in .github/plugin-it/.
#
# WHY IT EXISTS. Every other test of this module calls Java methods directly --
# PluginArchitectureTest, MojoPhaseSkipTest, MojoConfigurationMappingTest, PIT at 62/62. None of
# them involves Maven, so none of them covers the parts only Maven does:
#
#   * plexus binding of the <configuration> XML onto the mojos' @Parameter fields, including the
#     nested List<AiPromptDefinition> / List<AiModelDefinition> / List<AiFieldGenerationConfig>
#     structures and the <condition><extensions> tree inside them (the CLI's Jackson binding is a
#     DIFFERENT code path -- ExamplesConfigBindingTest covers that one, not this one);
#   * goal-prefix resolution: that `mvn srcmorph:generate` finds this plugin at all;
#   * the srcmorph.* @Parameter property strings, which nothing else asserts -- rename one and
#     every unit test still passes because they set the field, not the property;
#   * lifecycle-phase binding of the goals, and per-execution <configuration> overriding the
#     plugin-level one;
#   * the descriptor maven-plugin-plugin generates, which is what carries all of the above.
#
# A renamed @Parameter property would have shipped green. That is the gap this closes.
#
# The fixture uses the default `mock` provider, so no GGUF, no GPU and no network are needed, and
# its <configuration> deliberately mirrors the worked example in srcmorph-maven-plugin/README.md --
# so this also fails when the documented XML stops being the XML that works.
#
# NO `-s settings.xml`, AND THAT IS LOAD-BEARING. An earlier version passed a settings.xml whose
# only content was <pluginGroups><pluginGroup>net.ladenthin</pluginGroup></pluginGroups>, on the
# assumption that `mvn srcmorph:generate` needs it. It does not: the fixture declares the plugin in
# its own <build><plugins>, and Maven resolves the prefix by comparing the request against THAT
# plugin's descriptor -- which is exactly the check we want. Adding the pluginGroup opens a second
# resolution path, through the group's repository metadata, which maps srcmorph -> the plugin by
# artifactId regardless of what the descriptor says. Measured: with a mutated <goalPrefix>, the run
# succeeded WITH settings.xml (logging `morphsrc:1.2.0:generate`) and failed without it. The masking
# is not a stale-local-repo artifact either -- Central serves net/ladenthin/maven-metadata.xml
# carrying <prefix>srcmorph</prefix>, so a pristine runner would be masked too. Do not reintroduce
# that file.
#
# Usage: plugin-it.sh <reactor-version>
#   The reactor must already be installed into the local repository, e.g.
#   mvn -B -pl srcmorph-maven-plugin -am -Dmaven.test.skip=true install

set -euo pipefail

VERSION="${1:?usage: plugin-it.sh <reactor-version>}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_JAR="$REPO_ROOT/srcmorph-maven-plugin/target/srcmorph-maven-plugin-$VERSION.jar"
IT_DIR="$SCRIPT_DIR/plugin-it"
OUT="$IT_DIR/target/ai"
# The mojos' own default when <outputDirectory> does not bind. Checked as well as OUT, so a run
# that writes to the wrong place cannot satisfy a "nothing was written" assertion.
DEFAULT_OUT="$IT_DIR/src/site/ai"
# Logs live under target/ so .gitignore already covers them and the CI job can upload them on
# failure; reset() therefore clears only the output trees, never the whole directory.
LOGS="$IT_DIR/target/logs"
MVN=(mvn -B --no-transfer-progress -f "$IT_DIR/pom.xml" "-Dsrcmorph.it.version=$VERSION")

fail() {
    echo "::error::$*" >&2
    exit 1
}

# Every .ai.md under a directory, one per line (empty when the directory is absent).
written() {
    [ -d "$1" ] || return 0
    find "$1" -type f -name '*.ai.md' | sort
}

assert_written() {
    [ -e "$1" ] || fail "expected $1 to be written -- $2"
}

assert_nothing_written() {
    local found
    found="$(written "$OUT")"
    [ -z "$found" ] || fail "$1 -- but these were written: $(echo "$found" | tr '\n' ' ')"
    # A run that wrote to the DEFAULT tree instead would otherwise satisfy the check above while
    # proving the opposite of what it claims.
    found="$(written "$DEFAULT_OUT")"
    [ -z "$found" ] || fail "$1 -- output landed in the default directory, so <outputDirectory> did not bind: $(echo "$found" | tr '\n' ' ')"
}

reset() {
    rm -rf "$OUT" "$DEFAULT_OUT" "$IT_DIR"/*.log
    mkdir -p "$LOGS"
}

# ---------------------------------------------------------------------------
# Preconditions -- checked before any assertion, so a missing install is reported as a missing
# install rather than as a failed check.
# ---------------------------------------------------------------------------
[ -e "$PLUGIN_JAR" ] || fail "plugin jar not found at $PLUGIN_JAR -- install the reactor first"

LOCAL_REPO="$(mvn -B -q --no-transfer-progress -N -DforceStdout help:evaluate -Dexpression=settings.localRepository -f "$REPO_ROOT/pom.xml" | tail -n1)"
RESOLVED_JAR="$LOCAL_REPO/net/ladenthin/srcmorph-maven-plugin/$VERSION/srcmorph-maven-plugin-$VERSION.jar"
[ -e "$RESOLVED_JAR" ] || fail "the plugin is not in the local repository at $RESOLVED_JAR -- install the reactor first"
# The descriptor check below reads the jar in target/, while every Maven run below resolves the one
# in the local repository. With a non-SNAPSHOT version and a restored build cache those can be
# different files, and then the descriptor assertions would be about an artifact nothing executes.
cmp -s "$PLUGIN_JAR" "$RESOLVED_JAR" \
    || fail "the plugin resolved from $LOCAL_REPO is NOT the jar just built -- stale local repository"

echo "== 1/6: full lifecycle, all three goals, configured entirely from the pom XML =="
reset
"${MVN[@]}" verify > "$LOGS/run1.log" 2>&1 || { cat "$LOGS/run1.log"; fail "the fixture build failed"; }

GENERATED="$OUT/main/java/com/example/app/Greeter.java.ai.md"
# generate: one .ai.md per source file, under the package path the source lives in.
assert_written "$GENERATED" "the generate goal did not index the fixture sources"
assert_written "$OUT/main/java/com/example/util/Names.java.ai.md" "the generate goal indexed only part of the subtree"
# aggregate-packages: one per package directory.
assert_written "$OUT/main/java/com/example/app/package.ai.md" "the aggregate-packages goal did not run or wrote nothing"
# aggregate-project: the index on top.
assert_written "$OUT/project.ai.md" "the aggregate-project goal did not run or wrote nothing"

# <subtrees> -- Extra.java is reachable ONLY through the second entry (src/extra/java). The engine's
# own fallback is src/main/java, so without this file a bound and an unbound <subtrees> look alike.
assert_written "$OUT/extra/java/com/example/extra/Extra.java.ai.md" \
    "the second <subtree> did not bind -- src/extra/java was not indexed"
# <excludes> -- package-info.java exists for exactly this assertion; a pattern matching nothing
# makes the element invisible.
[ ! -e "$OUT/main/java/com/example/app/package-info.java.ai.md" ] \
    || fail "<excludes> did not bind -- package-info.java was indexed although the pattern excludes it"

# The mock provider's marker: proves the generated body came through the provider abstraction and
# not from some fallback that writes an empty document.
grep -q "Mock summary" "$GENERATED" || fail "the generated file summary carries no mock-provider marker"
# The deterministic header the codec writes -- pins that a real AiMdDocument was written, not a stub.
grep -q "^- X: file$" "$GENERATED" || fail "the generated file summary has no 'X: file' header field"
grep -q "^- X: project$" "$OUT/project.ai.md" || fail "the project index has no 'X: project' header field"

# Per-execution <configuration> overriding the plugin-level block: aiVersion is set ONLY on the
# aggregate-project execution, so the file summaries must keep the 0.0.0 default while the project
# index carries 7.7.7. Stripping the execution blocks used to leave this test green.
grep -q "^- A: 0.0.0$" "$GENERATED" \
    || fail "the file summary does not carry the default aiVersion -- an execution-level value leaked into the generate goal"
grep -q "^- A: 7.7.7$" "$OUT/project.ai.md" \
    || fail "the aggregate-project execution's own <aiVersion> did not override the plugin-level configuration"

# The two readonly ${project.*} injections. 1.0.0 is the FIXTURE pom's version, fixed and
# independent of the reactor version, so this stays stable across releases.
grep -q "^- G: 1.0.0$" "$GENERATED" || fail "\${project.version} did not inject into pluginVersion"
grep -q "^### srcmorph plugin integration-test fixture$" "$OUT/project.ai.md" \
    || fail "\${project.name} did not reach the project index"
echo "ok: $(written "$OUT" | wc -l) .ai.md file(s); subtrees, excludes, execution override and \${project.*} all observable"

echo
echo "== 2/6: the generated plugin descriptor =="
# maven-plugin-plugin generates it from the @Mojo/@Parameter annotations, and it carries the goal
# prefix, the goal names and every property string -- none of which any unit test can see.
DESCRIPTOR="$(unzip -p "$PLUGIN_JAR" META-INF/maven/plugin.xml)" \
    || fail "the plugin jar carries no META-INF/maven/plugin.xml -- maven-plugin-plugin did not run"

grep -q "<goalPrefix>srcmorph</goalPrefix>" <<<"$DESCRIPTOR" \
    || fail "descriptor goalPrefix is not 'srcmorph': $(grep -o '<goalPrefix>[^<]*' <<<"$DESCRIPTOR")"
for goal in generate aggregate-packages aggregate-project calibrate; do
    grep -q "<goal>$goal</goal>" <<<"$DESCRIPTOR" || fail "descriptor declares no goal '$goal'"
done

# The property set is compared BOTH ways against the expected list, so a renamed property fails and
# a NEW property also fails until somebody lists it here deliberately. Pinning only the handful the
# runs below drive would leave the headline failure mode open for every other one.
ACTUAL_PROPS="$(grep -oE '\$\{srcmorph[^}]*\}' <<<"$DESCRIPTOR" | tr -d '${}' | sort -u)"
EXPECTED_PROPS="$(printf '%s\n' \
    srcmorph.aiVersion \
    srcmorph.excludes \
    srcmorph.file.maxSizeBytes \
    srcmorph.file.minSizeBytes \
    srcmorph.file.skip \
    srcmorph.fileExtensions \
    srcmorph.force \
    srcmorph.generationProvider \
    srcmorph.llama.contextSize \
    srcmorph.llama.maxOutputTokens \
    srcmorph.llama.modelPath \
    srcmorph.llama.temperature \
    srcmorph.llama.threads \
    srcmorph.outputDirectory \
    srcmorph.package.skip \
    srcmorph.planOnly \
    srcmorph.project.skip \
    srcmorph.skip \
    srcmorph.subtrees | sort)"
if ! diff <(echo "$ACTUAL_PROPS") <(echo "$EXPECTED_PROPS") > "$LOGS/props.diff" 2>&1; then
    cat "$LOGS/props.diff"
    fail "the descriptor's srcmorph.* property set drifted (< actual, > expected) -- a rename, or a new property that has to be listed in this script deliberately"
fi
echo "ok: prefix 'srcmorph', 4 goals, all $(echo "$EXPECTED_PROPS" | wc -l) property strings unchanged"

echo
echo "== 3/6: goal prefix + srcmorph.planOnly, from the command line =="
reset
# `srcmorph:generate` (not the fully-qualified coordinates) resolves through the descriptor of the
# plugin the fixture declares -- see the no-settings.xml note in the header for why that only holds
# without a <pluginGroups> entry.
"${MVN[@]}" srcmorph:generate -Dsrcmorph.planOnly=true > "$LOGS/run3.log" 2>&1 \
    || { cat "$LOGS/run3.log"; fail "mvn srcmorph:generate did not resolve or failed"; }
grep -q "AI index plan" "$LOGS/run3.log" || fail "the plan was not printed"
# The routing rule is matched BY ID here, which is what proves the nested <condition><extensions>
# tree bound from the XML rather than everything falling through to <fallback>.
grep -q "| java | file-body-java |" "$LOGS/run3.log" \
    || fail "the .java routing rule did not match -- the <condition> tree did not bind from the XML"
# If srcmorph.planOnly is ever renamed, the property stops binding, the run generates for real, and
# this is the assertion that catches it.
assert_nothing_written "srcmorph.planOnly=true must stop before generating"
echo "ok: goal prefix resolved, plan printed, nothing generated"

echo
echo "== 4/6: srcmorph.aiVersion, from the command line =="
reset
# Deliberately NOT srcmorph.outputDirectory, even though that would be the obvious knob to redirect:
# an explicit value in the pom's <configuration> beats a -D property in Maven, and the fixture sets
# <outputDirectory>. So a -D on it is expected to do nothing, and asserting otherwise would be
# testing a misunderstanding of Maven rather than the plugin. aiVersion is left unset at plugin
# level, and its value lands in the written header, which makes the effect observable.
"${MVN[@]}" srcmorph:generate -Dsrcmorph.aiVersion=9.9.9 > "$LOGS/run4.log" 2>&1 \
    || { cat "$LOGS/run4.log"; fail "the property-configured run failed"; }
assert_written "$GENERATED" "the property-configured run generated nothing"
grep -q "^- A: 9.9.9$" "$GENERATED" \
    || fail "srcmorph.aiVersion did not reach the document header -- got: $(grep '^- A: ' "$GENERATED" || echo none)"
echo "ok: the command-line property bound and reached the output"

echo
echo "== 5/6: the four skip properties, including one off-diagonal =="
# The global skip plus each phase's own. The off-diagonal case is the one that catches a
# copy-pasted property or field among the three per-phase flags -- the diagonal alone cannot.
reset
"${MVN[@]}" srcmorph:generate -Dsrcmorph.skip=true > "$LOGS/run5-global.log" 2>&1 \
    || { cat "$LOGS/run5-global.log"; fail "the globally skipped run failed"; }
grep -q "AI index generation skipped." "$LOGS/run5-global.log" \
    || fail "srcmorph.skip=true did not reach the skip branch"
assert_nothing_written "srcmorph.skip=true must write nothing"

reset
"${MVN[@]}" srcmorph:generate -Dsrcmorph.file.skip=true > "$LOGS/run5-file.log" 2>&1 \
    || { cat "$LOGS/run5-file.log"; fail "the file-skipped run failed"; }
grep -q "AI index generation skipped." "$LOGS/run5-file.log" \
    || fail "srcmorph.file.skip=true did not skip the generate goal"
assert_nothing_written "srcmorph.file.skip=true must write nothing"

reset
"${MVN[@]}" srcmorph:aggregate-packages -Dsrcmorph.package.skip=true > "$LOGS/run5-package.log" 2>&1 \
    || { cat "$LOGS/run5-package.log"; fail "the package-skipped run failed"; }
grep -q "AI package aggregation skipped." "$LOGS/run5-package.log" \
    || fail "srcmorph.package.skip=true did not skip the aggregate-packages goal"

reset
"${MVN[@]}" srcmorph:aggregate-project -Dsrcmorph.project.skip=true > "$LOGS/run5-project.log" 2>&1 \
    || { cat "$LOGS/run5-project.log"; fail "the project-skipped run failed"; }
grep -q "AI project index aggregation skipped." "$LOGS/run5-project.log" \
    || fail "srcmorph.project.skip=true did not skip the aggregate-project goal"

# Off-diagonal: the generate goal's flag must NOT skip a different phase. Two mojos wired to the
# same field, or two @Parameter lines carrying the same property string, pass every diagonal check.
reset
"${MVN[@]}" srcmorph:aggregate-packages -Dsrcmorph.file.skip=true > "$LOGS/run5-cross.log" 2>&1 \
    || { cat "$LOGS/run5-cross.log"; fail "the cross-skip run failed"; }
grep -q "AI package aggregation skipped." "$LOGS/run5-cross.log" \
    && fail "srcmorph.file.skip leaked into the aggregate-packages goal -- the per-phase flags are not independent"
echo "ok: global skip, three per-phase skips, and one off-diagonal"

echo
echo "== 6/6: the calibrate goal =="
reset
# The only goal the lifecycle run above does not reach. It loads no model with the mock provider and
# finishes in well under a second, so there is no reason to leave its execute() path unexercised.
"${MVN[@]}" srcmorph:calibrate > "$LOGS/run6.log" 2>&1 \
    || { cat "$LOGS/run6.log"; fail "srcmorph:calibrate failed"; }
grep -q "Model 'fixture-model'" "$LOGS/run6.log" \
    || fail "calibrate did not resolve the aiDefinition by key -- <aiDefinitions> did not bind for this goal"
grep -q "<calibration>" "$LOGS/run6.log" || fail "calibrate printed no paste-ready <calibration> block"
echo "ok: calibrate ran and reported the fixture model"

echo
echo "plugin integration test PASSED"
