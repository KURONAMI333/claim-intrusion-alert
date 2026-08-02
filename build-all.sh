#!/usr/bin/env bash
# Claim Intrusion Alert — 4セルを順にビルドして _release/jars/ にステージする。
#
# 4セルは独立ビルド（各々 settings.gradle と gradlew を持つ）。gradle.properties の
# org.gradle.java.home が daemon スコープのため 1.20.1(JDK17) と 1.21.1(JDK21) を
# 単一の Gradle 実行に同居させられない。よってセルごとに wrapper を叩く（GAP_LOG G4）。
#
# 使い方:  ./build-all.sh [--no-build-cache]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_ARGS=("build" "$@")

VERSION="$(grep -E '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')"

# cell_dir:loader:mcversion  （forge-1.21.1 はリリース対象外＝GAP_LOG G3）
CELLS=(
  ".:neoforge:1.21.1"
  "fabric-1.21.1:fabric:1.21.1"
  "forge-1.20.1:forge:1.20.1"
  "fabric-1.20.1:fabric:1.20.1"
)

STAGE="$ROOT/_release/jars"
mkdir -p "$STAGE"

failed=()
for cell in "${CELLS[@]}"; do
  dir="${cell%%:*}"; rest="${cell#*:}"; loader="${rest%%:*}"; mcver="${rest#*:}"
  echo ""
  echo "=== build: $dir ($loader $mcver) ==="
  ( cd "$ROOT/$dir" && ./gradlew "${GRADLE_ARGS[@]}" ) || { failed+=("$dir"); continue; }

  # そのセル自身の mod_version の jar だけを拾う。
  # build/libs には旧版の jar が残るので「最初に見つかった jar」で拾うと、
  # ファイル名だけ新版・中身は旧版という取り違えが起きる（実際に 0.1.0 を掴んだ）。
  cellver="$(grep -E '^mod_version=' "$ROOT/$dir/gradle.properties" | cut -d= -f2 | tr -d '\r')"
  if [[ "$cellver" != "$VERSION" ]]; then
    echo "!! version mismatch: root=$VERSION but $dir=$cellver"; failed+=("$dir"); continue
  fi
  mapfile -t found < <(find "$ROOT/$dir/build/libs" -maxdepth 1 -name "*-${cellver}.jar" \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
        ! -name '*-dev.jar' ! -name '*-dev-shadow.jar')
  if (( ${#found[@]} != 1 )); then
    echo "!! expected exactly 1 jar for version $cellver in $dir/build/libs, got ${#found[@]}"
    failed+=("$dir"); continue
  fi
  jar="${found[0]}"
  dest="$STAGE/claimintrusionalert-${cellver}-${loader}-${mcver}.jar"
  cp "$jar" "$dest"
  echo "staged: $(basename "$dest")"
done

echo ""
echo "=== _release/jars/ ==="
ls -1 "$STAGE"

if (( ${#failed[@]} )); then
  echo ""
  echo "FAILED cells: ${failed[*]}"
  exit 1
fi
