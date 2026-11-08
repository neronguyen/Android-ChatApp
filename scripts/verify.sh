#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-fast}"

case "$MODE" in
  fast|clean)
    shift || true
    ;;
  *)
    echo "Usage: $0 [fast|clean] [gradle-args...]" >&2
    exit 1
    ;;
esac

# Resolve JDK 21 dynamically
if [ -z "${JAVA_HOME:-}" ]; then
  CANDIDATE_JDK="$(find "${HOME}/.gradle/jdks" -maxdepth 1 -type d -name "eclipse_adoptium-21-*" 2>/dev/null | head -n 1 || true)"
  if [ -n "$CANDIDATE_JDK" ] && [ -d "$CANDIDATE_JDK" ]; then
    export JAVA_HOME="$CANDIDATE_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

export GRADLE_RO_DEP_CACHE="${GRADLE_RO_DEP_CACHE:-${HOME}/.gradle/caches}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-ro-home}"
mkdir -p "$GRADLE_USER_HOME"
if [ -d "${HOME}/.gradle/jdks" ] && [ ! -e "$GRADLE_USER_HOME/jdks" ]; then
  ln -sfn "${HOME}/.gradle/jdks" "$GRADLE_USER_HOME/jdks"
fi
if [ -d "${HOME}/.gradle/wrapper" ] && [ ! -e "$GRADLE_USER_HOME/wrapper" ]; then
  ln -sfn "${HOME}/.gradle/wrapper" "$GRADLE_USER_HOME/wrapper"
fi

if [ "$MODE" = "clean" ]; then
  echo "==> [CLEAN GATE] Running clean unit tests on Android-ChatApp..."
  "$REPO_DIR/gradlew" -p "$REPO_DIR" testDebugUnitTest --rerun-tasks --gradle-user-home="$GRADLE_USER_HOME" "$@"
else
  echo "==> [FAST FEEDBACK] Running fast cached unit tests on Android-ChatApp..."
  "$REPO_DIR/gradlew" -p "$REPO_DIR" testDebugUnitTest --gradle-user-home="$GRADLE_USER_HOME" "$@"
fi
