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

# Resolve JDK 21 dynamically (1: valid JAVA_HOME, 2: PATH java 21, 3: ~/.gradle/jdks/*-21-*)
RESOLVED_JDK=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  if "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q 'version "21'; then
    RESOLVED_JDK="$JAVA_HOME"
  fi
fi

if [ -z "$RESOLVED_JDK" ] && command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
  JAVA_BIN="$(readlink -f "$(command -v java)")"
  RESOLVED_JDK="$(dirname "$(dirname "$JAVA_BIN")")"
fi

if [ -z "$RESOLVED_JDK" ] && [ -d "${HOME}/.gradle/jdks" ]; then
  CANDIDATE_JDK="$(find "${HOME}/.gradle/jdks" -maxdepth 1 -type d -name "*-21-*" 2>/dev/null | head -n 1 || true)"
  if [ -n "$CANDIDATE_JDK" ] && [ -x "$CANDIDATE_JDK/bin/java" ]; then
    RESOLVED_JDK="$CANDIDATE_JDK"
  fi
fi

if [ -n "$RESOLVED_JDK" ]; then
  export JAVA_HOME="$RESOLVED_JDK"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Resolve Android SDK dynamically if ANDROID_HOME is not set
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "${HOME}/Android/Sdk" ]; then
    export ANDROID_HOME="${HOME}/Android/Sdk"
  elif [ -d "${HOME}/.android/sdk" ]; then
    export ANDROID_HOME="${HOME}/.android/sdk"
  fi
fi

IS_CI="${CI:-false}"
GRADLE_ARGS=(-p "$REPO_DIR")

if [ "$IS_CI" = "true" ]; then
  # On CI: preserve default ~/.gradle and ~/.android for GitHub Actions caching and disable background daemon
  GRADLE_ARGS+=(--no-daemon)
else
  # On Local / Sandbox: isolate ANDROID_USER_HOME and GRADLE_USER_HOME in /tmp and reuse host read-only cache
  export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/tmp/android-home}"
  mkdir -p "$ANDROID_USER_HOME"

  export GRADLE_RO_DEP_CACHE="${GRADLE_RO_DEP_CACHE:-${HOME}/.gradle/caches}"
  GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-ro-home}"

  mkdir -p "$GRADLE_USER_HOME"
  if [ -d "${HOME}/.gradle/jdks" ] && [ ! -e "$GRADLE_USER_HOME/jdks" ]; then
    ln -sfn "${HOME}/.gradle/jdks" "$GRADLE_USER_HOME/jdks"
  fi
  if [ -d "${HOME}/.gradle/wrapper" ] && [ ! -e "$GRADLE_USER_HOME/wrapper" ]; then
    ln -sfn "${HOME}/.gradle/wrapper" "$GRADLE_USER_HOME/wrapper"
  fi

  GRADLE_ARGS+=(--gradle-user-home="$GRADLE_USER_HOME")
fi

if [ "$MODE" = "clean" ]; then
  echo "==> [CLEAN GATE] Running clean unit tests & lintDebug on Android-ChatApp (CI=${IS_CI})..."
  exec "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" testDebugUnitTest lintDebug --rerun-tasks "$@"
else
  echo "==> [FAST FEEDBACK] Running fast cached unit tests on Android-ChatApp (CI=${IS_CI})..."
  exec "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" testDebugUnitTest "$@"
fi
