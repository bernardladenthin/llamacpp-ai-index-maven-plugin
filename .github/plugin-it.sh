#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0

# Integration test for srcmorph-maven-plugin: runs the plugin as a PLUGIN, from the local
# repository, through a real Maven lifecycle, against the fixture project in .github/plugin-it/.
#
# WHY IT EXISTS. Every existing test of this module calls Java methods directly --
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
#   * lifecycle-phase binding of the three goals, and per-execution <configuration> overriding the
#     plugin-level one;
#   * the descriptor maven-plugin-plugin generates, which is what carries all of the above.
#
# A renamed @Parameter property would have shipped green. That is the gap this closes.
#
# The fixture uses the default `mock` provider, so no GGUF, no GPU and no network are needed, and
# its <configuration> deliberately mirrors the worked example in srcmorph-maven-plugin/README.md --
# so this also fails when the documented XML stops being the XML that works.
#
# Usage: plugin-it.sh <reactor-version>
#   The reactor must already be installed into the local repository, e.g.
#   mvn -B -pl srcmorph-maven-plugin -am -DskipTests install

set -euo pipefail

VERSION="${1:?usage: plugin-it.sh <reactor-version>}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_JAR="$REPO_ROOT/srcmorph-maven-plugin/target/srcmorph-maven-plugin-$VERSION.jar"
IT_DIR="$SCRIPT_DIR/plugin-it"
OUT="$IT_DIR/target/ai"
# Only run 3 writes here; it exists so reset() can clear it too.
ALT_OUT="$IT_DIR/target/ai-alt"
# Logs live under target/ so .gitignore already covers them and the CI job can upload them on
# failure; reset() therefore clears only the two output trees, never the whole directory.
LOGS="$IT_DIR/target/logs"
MVN=(mvn -B --no-transfer-progress -f "$IT_DIR/pom.xml" -s "$IT_DIR/settings.xml" "-Dsrcmorph.it.version=$VERSION")

fail() {
    echo "::error::$*" >&2
    exit 1
}

# Every .ai.md currently under the fixture's output directory, one per line (empty when none).
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
}

reset() {
    rm -rf "$OUT" "$ALT_OUT"
    mkdir -p "$LOGS"
}

echo "== 1/5: full lifecycle, all three goals, configured entirely from the pom XML =="
reset
"${MVN[@]}" verify > "$LOGS/run1.log" 2>&1 || { cat "$LOGS/run1.log"; fail "the fixture build failed"; }

# generate: one .ai.md per source file, under the package path the source lives in.
assert_written "$OUT/main/java/com/example/app/Greeter.java.ai.md" "the generate goal did not index the fixture sources"
assert_written "$OUT/main/java/com/example/util/Names.java.ai.md" "the generate goal indexed only part of the subtree"
# aggregate-packages: one per package directory.
assert_written "$OUT/main/java/com/example/app/package.ai.md" "the aggregate-packages goal did not run or wrote nothing"
# aggregate-project: the index on top.
assert_written "$OUT/project.ai.md" "the aggregate-project goal did not run or wrote nothing"

# The mock provider's marker: proves the generated body came through the provider abstraction and
# not from some fallback that writes an empty document.
grep -q "Mock summary" "$OUT/main/java/com/example/app/Greeter.java.ai.md" \
    || fail "the generated file summary carries no mock-provider marker"
# The deterministic header the codec writes -- pins that a real AiMdDocument was written, not a stub.
grep -q "^- X: file$" "$OUT/main/java/com/example/app/Greeter.java.ai.md" \
    || fail "the generated file summary has no 'X: file' header field"
grep -q "^- X: project$" "$OUT/project.ai.md" \
    || fail "the project index has no 'X: project' header field"
# <excludes> is bound from the XML: package-info.java exists in neither package, so the strongest
# available check that the routing rules bound at all is that the .java rule (not the fallback)
# matched -- the plan in run 2 asserts that by name.
echo "ok: $(written "$OUT" | wc -l) .ai.md file(s) written by the three goals"

echo
echo "== 2/5: the generated plugin descriptor =="
# Read the descriptor out of the packaged plugin jar. maven-plugin-plugin generates it from the
# @Mojo/@Parameter annotations, and it is what carries the goal prefix, the goal names and every
# property string -- none of which any unit test can see.
#
# This is also what makes the goal-prefix check below exact. Maven resolves `srcmorph:generate`
# through maven-metadata-local.xml in the local repository, and that file ACCUMULATES prefixes: on a
# machine that once installed this plugin under a different prefix, the stale entry keeps the short
# form resolving. Verified, not assumed -- changing <goalPrefix> and re-installing left run 3 green
# on a developer machine while the descriptor already said the new name. So the invocation proves
# end-to-end resolvability and this block proves the prefix is the one we ship.
[ -e "$PLUGIN_JAR" ] || fail "plugin jar not found at $PLUGIN_JAR -- install the reactor first"
DESCRIPTOR="$(unzip -p "$PLUGIN_JAR" META-INF/maven/plugin.xml)" \
    || fail "the plugin jar carries no META-INF/maven/plugin.xml -- maven-plugin-plugin did not run"

grep -q "<goalPrefix>srcmorph</goalPrefix>" <<<"$DESCRIPTOR" \
    || fail "descriptor goalPrefix is not 'srcmorph': $(grep -o '<goalPrefix>[^<]*' <<<"$DESCRIPTOR")"
for goal in generate aggregate-packages aggregate-project calibrate; do
    grep -q "<goal>$goal</goal>" <<<"$DESCRIPTOR" || fail "descriptor declares no goal '$goal'"
done
# Every property this script drives from the command line must be a property the descriptor
# actually declares; otherwise the runs below could pass for the wrong reason (a -D nobody reads
# changes nothing, and "nothing changed" is what some of those assertions expect).
for prop in srcmorph.planOnly srcmorph.aiVersion srcmorph.skip; do
    grep -q "\${$prop}" <<<"$DESCRIPTOR" || fail "descriptor declares no expression \${$prop}"
done
echo "ok: prefix 'srcmorph', 4 goals, 3 driven properties present in the descriptor"

echo
echo "== 3/5: goal prefix + srcmorph.planOnly, from the command line =="
reset
# `srcmorph:generate` (not the fully-qualified coordinates) is what exercises the goalPrefix in the
# generated descriptor; settings.xml supplies the pluginGroup that makes the short form resolvable.
"${MVN[@]}" srcmorph:generate -Dsrcmorph.planOnly=true > "$LOGS/run2.log" 2>&1 \
    || { cat "$LOGS/run2.log"; fail "mvn srcmorph:generate did not resolve or failed"; }
grep -q "AI index plan" "$LOGS/run2.log" || fail "the plan was not printed"
# The routing rule is matched BY ID here, which is what proves the nested <condition><extensions>
# tree bound from the XML rather than everything falling through to <fallback>.
grep -q "| java | file-body-java |" "$LOGS/run2.log" \
    || fail "the .java routing rule did not match -- the <condition> tree did not bind from the XML"
# If srcmorph.planOnly is ever renamed, the property stops binding, the run generates for real, and
# this is the assertion that catches it.
assert_nothing_written "srcmorph.planOnly=true must stop before generating"
echo "ok: goal prefix resolved, plan printed, nothing generated"

echo
echo "== 4/5: srcmorph.aiVersion, from the command line =="
reset
# Deliberately NOT srcmorph.outputDirectory, even though that would be the obvious knob to redirect:
# an explicit value in the pom's <configuration> beats a -D property in Maven, and the fixture sets
# <outputDirectory>. So a -D on it is expected to do nothing, and asserting otherwise would be
# testing a misunderstanding of Maven rather than the plugin. aiVersion is left unset in the pom,
# so its property is the one actually in play -- and its value lands in the written header, which
# makes the effect observable rather than merely non-crashing.
"${MVN[@]}" srcmorph:generate -Dsrcmorph.aiVersion=9.9.9 > "$LOGS/run3.log" 2>&1 \
    || { cat "$LOGS/run3.log"; fail "the property-configured run failed"; }
GENERATED="$OUT/main/java/com/example/app/Greeter.java.ai.md"
assert_written "$GENERATED" "the property-configured run generated nothing"
# Rename srcmorph.aiVersion and this is the assertion that catches it: the property stops binding,
# the field keeps its 0.0.0 default, and the header says so.
grep -q "^- A: 9.9.9$" "$GENERATED" \
    || fail "srcmorph.aiVersion did not reach the document header -- got: $(grep '^- A: ' "$GENERATED" || echo none)"
echo "ok: the command-line property bound and reached the output"

echo
echo "== 5/5: srcmorph.skip =="
reset
"${MVN[@]}" srcmorph:generate -Dsrcmorph.skip=true > "$LOGS/run4.log" 2>&1 \
    || { cat "$LOGS/run4.log"; fail "the skipped run failed"; }
grep -q "AI index generation skipped." "$LOGS/run4.log" \
    || fail "srcmorph.skip=true did not reach the skip branch"
assert_nothing_written "srcmorph.skip=true must write nothing"
echo "ok: the skip property took effect"

echo
echo "plugin integration test PASSED"
