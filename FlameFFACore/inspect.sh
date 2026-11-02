#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")" || exit 1

java_sources="src/main/java"
plugin_descriptor="src/main/resources/paper-plugin.yml"
errors=0
warnings=0

error() {
    echo "error: $*"
    errors=$((errors + 1))
}

warning() {
    echo "warning: $*"
    warnings=$((warnings + 1))
}

build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT

echo "==> Building"
if ! ./gradlew clean build --console=plain -Dorg.gradle.warning.mode=all >"$build_log" 2>&1; then
    error "gradle build failed: $(grep -m1 -E 'error:|FAILURE|What went wrong' "$build_log" || echo 'see gradle output')"
fi
while IFS= read -r line; do
    warning "javac: $line"
done < <(grep -E '\.java:[0-9]+: warning:' "$build_log")
while IFS= read -r line; do
    warning "gradle: $line"
done < <(grep -E '^(warning:|w: )' "$build_log")

echo "==> Checking jar"
version="$(sed -n 's/^version = "\(.*\)"$/\1/p' build.gradle.kts)"
jar="build/libs/FlameFFACore-${version}.jar"
if [[ -z "$version" ]]; then
    error "build.gradle.kts has no version"
elif [[ ! -f "$jar" ]]; then
    error "$jar was not built"
else
    jar_version="$(unzip -p "$jar" paper-plugin.yml 2>/dev/null | sed -n "s/^version: '\(.*\)'$/\1/p")"
    if [[ "$jar_version" != "$version" ]]; then
        error "paper-plugin.yml version '$jar_version' does not match build.gradle.kts version '$version'"
    fi
fi

echo "==> Checking descriptor"
if ! grep -qE '^folia-supported: true$' "$plugin_descriptor"; then
    error "paper-plugin.yml is missing folia-supported: true"
fi

echo "==> Checking permissions"
declared_permissions="$(awk '/^permissions:/{inside=1; next} /^[a-z]/{inside=0} inside && /^  [a-z0-9_.*-]+:$/{gsub(/[ :]/, ""); print}' "$plugin_descriptor")"
while IFS= read -r permission; do
    if ! grep -qxF "$permission" <<<"$declared_permissions"; then
        warning "permission $permission is used in Java but not declared in paper-plugin.yml"
    fi
done < <(grep -rhoE '"flame[a-z]*\.[a-z0-9_.-]*[a-z0-9_-]"' "$java_sources" | tr -d '"' | sort -u)

echo "==> Checking threading"
while IFS= read -r line; do
    error "bukkit scheduler: $line"
done < <(grep -rnE 'getScheduler\(\)|BukkitRunnable|BukkitScheduler|BukkitTask' "$java_sources")

echo "==> Checking sql"
while IFS= read -r line; do
    error "string-built sql: $line"
done < <(grep -rnE '(prepareStatement|execute(Query|Update|LargeUpdate)?|addBatch)\("[^"]*"\s*\+' "$java_sources")

echo "==> Checking sources"
while IFS= read -r line; do
    warning "comment: $line"
done < <(grep -rnE '^\s*(//|/\*|\*)' "$java_sources")
while IFS= read -r line; do
    warning "unfinished: $line"
done < <(grep -rnE 'TODO|FIXME|XXX' src)
while IFS= read -r line; do
    error "attribution: $line"
done < <(grep -rniE '\b(claude|anthropic)\b' src build.gradle.kts settings.gradle.kts)

echo "==> $errors error(s), $warnings warning(s)"
[[ $errors -eq 0 && $warnings -eq 0 ]]
