#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")" || exit 1

core="FlameFFACore"
legacy_modules=(FlameDaily FlameFFAProxy FlameHolograms FlameSpawnGuard FlameStore FlameTrims FlameTweaks FlameWindBrake)
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

echo "==> Inspecting $core"
core_output="$("./$core/inspect.sh" 2>&1)"
core_status=$?
while IFS= read -r line; do
    case "$line" in
        error:*) error "$core: ${line#error: }" ;;
        warning:*) warning "$core: ${line#warning: }" ;;
    esac
done <<<"$core_output"
if [[ $core_status -ne 0 ]] && ! grep -qE '^(error|warning):' <<<"$core_output"; then
    error "$core: inspect.sh failed: $(tail -n 1 <<<"$core_output")"
fi

for module in "${legacy_modules[@]}"; do
    echo "==> Building $module"
    if [[ ! -f "$module/build.gradle.kts" ]]; then
        error "$module has no build.gradle.kts"
        continue
    fi
    if ! "./$core/gradlew" -p "$module" clean build --console=plain -Dorg.gradle.warning.mode=all >"$build_log" 2>&1; then
        error "$module: gradle build failed: $(grep -m1 -E 'error:|FAILURE|What went wrong' "$build_log" || echo 'see gradle output')"
    fi
    while IFS= read -r line; do
        warning "$module: javac: $line"
    done < <(grep -E '\.java:[0-9]+: warning:' "$build_log")
    while IFS= read -r line; do
        warning "$module: gradle: $line"
    done < <(grep -E '^(warning:|w: )' "$build_log")
    while IFS= read -r line; do
        error "$module: attribution: $line"
    done < <(grep -rniE '\b(claude|anthropic)\b' "$module/src" "$module/build.gradle.kts" "$module/settings.gradle.kts")
done

echo "==> $errors error(s), $warnings warning(s)"
[[ $errors -eq 0 && $warnings -eq 0 ]]
