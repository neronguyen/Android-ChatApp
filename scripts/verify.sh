#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Resolve JDK 21 dynamically
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
  GRADLE_ARGS+=(--no-daemon)
else
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

# CI Mode: Full clean unit test & lint run
if [ "$IS_CI" = "true" ] || [ "${1:-}" = "clean" ]; then
  if [ "${1:-}" = "clean" ]; then shift; fi
  echo "==> [CLEAN GATE] Running clean unit tests & lintDebug on Android-ChatApp (CI=${IS_CI})..."
  "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" testDebugUnitTest lintDebug :core:ui:validateDebugScreenshotTest --rerun-tasks "$@"

  if [ -n "$(git -C "$REPO_DIR" status --porcelain)" ]; then
    echo "ERROR: Clean verification produced uncommitted or untracked changes (drift detected):" >&2
    git -C "$REPO_DIR" status --short >&2
    git -C "$REPO_DIR" diff >&2
    exit 1
  fi
  echo "==> [CLEAN GATE] Verification passed and working tree is clean."
  exit 0
fi

# If specific gradle tasks/flags were passed, execute them directly
if [ "$#" -gt 0 ]; then
  echo "==> [DIRECT TASK] Running specified Gradle tasks: $*"
  exec "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" "$@"
fi

# Local Fast Mode: Automatically detect affected modules via Git
echo "==> [AFFECTED MODULES] Detecting changed files via Git..."

CHANGED_FILES=()
# 1. Uncommitted changes (working tree & staging)
while IFS= read -r f; do
  [ -n "$f" ] && CHANGED_FILES+=("$f")
done < <(git -C "$REPO_DIR" status --porcelain 2>/dev/null | awk '{print $2}' || true)

# 2. Committed changes compared to main branch
if git -C "$REPO_DIR" rev-parse --verify main >/dev/null 2>&1; then
  while IFS= read -r f; do
    [ -n "$f" ] && CHANGED_FILES+=("$f")
  done < <(git -C "$REPO_DIR" diff --name-only main...HEAD 2>/dev/null || true)
fi

# Deduplicate changed files
UNIQUE_FILES=($(printf "%s\n" "${CHANGED_FILES[@]:-}" | sort -u))

# Map changed files to Gradle sub-projects
RUN_ALL=false
GRADLE_TASKS=()

for file in "${UNIQUE_FILES[@]:-}"; do
  case "$file" in
    build.gradle.kts|settings.gradle.kts|gradle/*)
      RUN_ALL=true
      break
      ;;
    app/*)
      GRADLE_TASKS+=(":app:testDebugUnitTest")
      ;;
    core/common/*)
      GRADLE_TASKS+=(":core:common:test")
      ;;
    core/model/*)
      GRADLE_TASKS+=(":core:model:test")
      ;;
    core/data/*)
      GRADLE_TASKS+=(":core:data:testDebugUnitTest")
      ;;
    core/database/*)
      GRADLE_TASKS+=(":core:database:testDebugUnitTest")
      ;;
    core/datastore/*)
      GRADLE_TASKS+=(":core:datastore:testDebugUnitTest")
      ;;
    core/network/*)
      GRADLE_TASKS+=(":core:network:testDebugUnitTest")
      ;;
    core/security/*)
      GRADLE_TASKS+=(":core:security:testDebugUnitTest")
      ;;
    core/ui/*)
      GRADLE_TASKS+=(":core:ui:testDebugUnitTest")
      ;;
    feature/auth/*)
      GRADLE_TASKS+=(":feature:auth:testDebugUnitTest")
      ;;
    feature/chat/*)
      GRADLE_TASKS+=(":feature:chat:testDebugUnitTest")
      ;;
    sync/work/*)
      GRADLE_TASKS+=(":sync:work:testDebugUnitTest")
      ;;
  esac
done

if [ "$RUN_ALL" = "true" ] || [ "${#GRADLE_TASKS[@]}" -eq 0 ]; then
  if [ "$RUN_ALL" = "true" ]; then
    echo "==> [FAST FEEDBACK] Build config changed. Running testDebugUnitTest on entire project..."
  else
    echo "==> [FAST FEEDBACK] No specific module code changed (docs or clean). Running fast cached test..."
  fi
  exec "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" testDebugUnitTest "$@"
fi

# Deduplicate tasks
UNIQUE_TASKS=($(printf "%s\n" "${GRADLE_TASKS[@]}" | sort -u))

echo "==> [FAST FEEDBACK] Running ${#UNIQUE_TASKS[@]} affected test task(s): ${UNIQUE_TASKS[*]}"
exec "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" "${UNIQUE_TASKS[@]}" "$@"
