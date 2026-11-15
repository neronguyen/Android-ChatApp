#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_ID="io.github.neronguyen.chat"
PERSISTENT_DEVICE_ARTIFACT_DIR="/sdcard/Download/e2e-smoke"
DEVICE_ARTIFACT_DIR="/sdcard/Android/data/${APP_ID}/files/e2e-smoke"

TEARDOWN=false
CLEAN=false
GRADLE_EXTRA_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --teardown)
      TEARDOWN=true
      ;;
    --clean)
      TEARDOWN=true
      CLEAN=true
      ;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [--teardown] [--clean] [gradle args...]

Runs the deterministic 8-state FullStackSmokeE2ETest against KMP-ChatBackend + Docker Compose
on an Android Virtual Device, pulls all PNG/XML checkpoints into build/reports/e2e-smoke/<run-id>/,
and writes summary.md.

Options:
  --teardown    Stop E2E backend and Docker Compose containers after completion
  --clean       Teardown and wipe Docker Compose volumes after completion
EOF
      exit 0
      ;;
    *)
      GRADLE_EXTRA_ARGS+=("$1")
      ;;
  esac
  shift
done

resolve_jdk21() {
  local resolved=""
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    if "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q 'version "21'; then
      resolved="$JAVA_HOME"
    fi
  fi
  if [ -z "$resolved" ] && command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
    local java_bin
    java_bin="$(readlink -f "$(command -v java)")"
    resolved="$(dirname "$(dirname "$java_bin")")"
  fi
  if [ -z "$resolved" ] && [ -d "${HOME}/.gradle/jdks" ]; then
    local candidate
    candidate="$(find "${HOME}/.gradle/jdks" -maxdepth 1 -type d -name "*-21-*" 2>/dev/null | head -n 1 || true)"
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      resolved="$candidate"
    fi
  fi
  if [ -n "$resolved" ]; then
    export JAVA_HOME="$resolved"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

resolve_android_sdk() {
  local local_sdk=""
  if [ -f "$REPO_DIR/local.properties" ]; then
    local_sdk="$(sed -n 's/^sdk\.dir=//p' "$REPO_DIR/local.properties" | head -n 1 | tr -d '\r' | sed 's/\\:/:/g')"
  fi

  local resolved_sdk=""
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$local_sdk" "${HOME}/.android/sdk" "${HOME}/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -x "${candidate}/emulator/emulator" ]; then
      resolved_sdk="$candidate"
      break
    fi
  done

  if [ -z "$resolved_sdk" ]; then
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$local_sdk" "${HOME}/.android/sdk" "${HOME}/Android/Sdk"; do
      if [ -n "$candidate" ] && [ -d "$candidate" ]; then
        resolved_sdk="$candidate"
        break
      fi
    done
  fi

  if [ -n "$resolved_sdk" ]; then
    export ANDROID_HOME="$resolved_sdk"
    export ANDROID_SDK_ROOT="$resolved_sdk"
    export PATH="${resolved_sdk}/platform-tools:${resolved_sdk}/emulator:${PATH}"
  fi
}

RUN_ID="$(date +%Y%m%d-%H%M%S)"
REPORT_DIR="${REPO_DIR}/build/reports/e2e-smoke/${RUN_ID}"
ARTIFACTS_PULLED=false
TEST_EXIT=1

write_summary() {
  local exit_code="$1"
  local status_label="PASS"
  if [ "$exit_code" -ne 0 ]; then
    status_label="FAIL (exit ${exit_code})"
  fi

  local steps_tsv="${REPORT_DIR}/steps.tsv"
  local summary_file="${REPORT_DIR}/summary.md"

  {
    echo "# Full-Stack E2E Smoke Report (${RUN_ID})"
    echo ""
    echo "- **Status:** ${status_label}"
    echo "- **Timestamp:** $(date -Iseconds)"
    echo "- **Artifacts Directory:** \`build/reports/e2e-smoke/${RUN_ID}/\`"
    echo ""
    echo "| Checkpoint | FSM State | Status | Duration (ms) | Screenshot | UI Hierarchy |"
    echo "|:-----------|:----------|:-------|--------------:|:-----------|:-------------|"

    local checkpoints=(
      "00_register_form:S0_REGISTER_FORM"
      "01_otp_challenge:S1_OTP_CHALLENGE"
      "02_login_verified:S2_LOGIN_VERIFIED"
      "03_chat_list_empty:S3_CHAT_LIST_EMPTY"
      "04_new_chat_found_peer:S4_NEW_CHAT_FOUND_PEER"
      "05_outbound_message_sent:S5_OUTBOUND_MESSAGE_SENT"
      "06_inbound_message_received:S6_INBOUND_MESSAGE_RECEIVED"
      "07_reconnect_catchup_sync:S7_RECONNECT_CATCHUP_SYNC"
    )

    for entry in "${checkpoints[@]}"; do
      local cp="${entry%%:*}"
      local state="${entry#*:}"
      local row_status="NOT_RUN"
      local duration="-"

      if [ -f "$steps_tsv" ]; then
        local tsv_line
        tsv_line="$(grep -E "^${cp}[[:space:]]" "$steps_tsv" | tail -n 1 || true)"
        if [ -n "$tsv_line" ]; then
          row_status="$(printf '%s' "$tsv_line" | cut -f3)"
          duration="$(printf '%s' "$tsv_line" | cut -f4)"
        fi
      elif [ -f "${REPORT_DIR}/${cp}.png" ]; then
        row_status="CAPTURED"
      fi

      local png_link="-"
      if [ -f "${REPORT_DIR}/${cp}.png" ]; then
        png_link="[${cp}.png](./${cp}.png)"
      fi

      local xml_link="-"
      if [ -f "${REPORT_DIR}/${cp}.xml" ]; then
        xml_link="[${cp}.xml](./${cp}.xml)"
      fi

      echo "| \`${cp}\` | \`${state}\` | **${row_status}** | ${duration} | ${png_link} | ${xml_link} |"
    done

    if [ -f "${REPORT_DIR}/failure.png" ] || [ -f "${REPORT_DIR}/failure.xml" ]; then
      echo ""
      echo "## Failure Diagnostics"
      echo ""
      [ -f "${REPORT_DIR}/failure.png" ] && echo "- Screenshot: [failure.png](./failure.png)"
      [ -f "${REPORT_DIR}/failure.xml" ] && echo "- UI Hierarchy: [failure.xml](./failure.xml)"
      if [ -f "$steps_tsv" ]; then
        local fail_note
        fail_note="$(grep -E $'\tFAIL\t' "$steps_tsv" | tail -n 1 | cut -f5 || true)"
        [ -n "$fail_note" ] && echo "- Error: \`${fail_note}\`"
      fi
    fi
  } >"$summary_file"
}

pull_device_artifacts() {
  mkdir -p "$REPORT_DIR"
  if ! command -v adb >/dev/null 2>&1 || [ "$(adb get-state 2>/dev/null || true)" != "device" ]; then
    return 0
  fi

  # Capture scoped logcat for E2E harness, test runner, and HTTP/WS traffic.
  adb logcat -d -v time -s E2E_SMOKE:V TestRunner:I AndroidRuntime:E OkHttp:I >"${REPORT_DIR}/logcat-e2e.txt" 2>/dev/null || true

  # Pull artifacts: prefer persistent /sdcard/Download/e2e-smoke (survives APK uninstall),
  # then fall back to app external storage and internal filesDir via run-as.
  for remote_dir in "$PERSISTENT_DEVICE_ARTIFACT_DIR" "$DEVICE_ARTIFACT_DIR"; do
    rm -rf "${REPORT_DIR}/_pulled"
    if adb pull "${remote_dir}" "${REPORT_DIR}/_pulled" >/dev/null 2>&1 && [ -d "${REPORT_DIR}/_pulled" ]; then
      if [ -d "${REPORT_DIR}/_pulled/e2e-smoke" ]; then
        mv "${REPORT_DIR}/_pulled/e2e-smoke"/* "${REPORT_DIR}/" 2>/dev/null || true
      else
        mv "${REPORT_DIR}/_pulled"/* "${REPORT_DIR}/" 2>/dev/null || true
      fi
      rm -rf "${REPORT_DIR}/_pulled"
    fi
    if [ -f "${REPORT_DIR}/steps.tsv" ] || [ -f "${REPORT_DIR}/failure.png" ] || [ -f "${REPORT_DIR}/00_register_form.png" ]; then
      break
    fi
  done

  if [ ! -f "${REPORT_DIR}/steps.tsv" ]; then
    local file_list
    file_list="$(adb shell "run-as ${APP_ID} ls files/e2e-smoke" 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$file_list" ]; then
      local file_name
      while IFS= read -r file_name; do
        [ -z "$file_name" ] && continue
        adb exec-out "run-as ${APP_ID} cat files/e2e-smoke/${file_name}" >"${REPORT_DIR}/${file_name}" 2>/dev/null || true
      done <<<"$file_list"
    fi
  fi
}

print_console_diagnostics() {
  local exit_code="$1"
  echo ""
  echo "==================== [TIER 3 E2E CONSOLE SUMMARY] ===================="
  if [ -f "${REPORT_DIR}/logcat-e2e.txt" ]; then
    local e2e_lines
    e2e_lines="$(grep 'E2E_SMOKE' "${REPORT_DIR}/logcat-e2e.txt" || true)"
    if [ -n "$e2e_lines" ]; then
      echo "--- E2E_SMOKE Timeline ---"
      printf '%s\n' "$e2e_lines"
    fi
  fi

  if [ -f "${REPORT_DIR}/summary.md" ]; then
    echo "--- Checkpoint Summary ---"
    cat "${REPORT_DIR}/summary.md"
  fi

  if [ "$exit_code" -ne 0 ]; then
    echo "--- Failure Diagnostics ---"
    if [ -f "${REPORT_DIR}/logcat-e2e.txt" ]; then
      grep -E 'E/TestRunner:|FATAL EXCEPTION|Caused by:|androidx\.compose\.ui\.test' "${REPORT_DIR}/logcat-e2e.txt" | head -n 30 || true
      echo "--- Last OkHttp Events ---"
      grep -E 'OkHttp.*(-->|<--)' "${REPORT_DIR}/logcat-e2e.txt" | tail -n 20 || true
    fi
    echo "Logs: build/reports/e2e-smoke/${RUN_ID}/logcat-e2e.txt"
    echo "Backend log: ${E2E_BACKEND_LOG:-/tmp/nerochat-e2e-backend.log}"
  fi
  echo "========================================================================"
}

finalize() {
  local rc=$?
  set +e

  if [ "$ARTIFACTS_PULLED" = false ]; then
    pull_device_artifacts
    write_summary "$TEST_EXIT"
    ARTIFACTS_PULLED=true
    print_console_diagnostics "$TEST_EXIT"
  fi

  if [ "$TEARDOWN" = true ]; then
    if [ "$CLEAN" = true ]; then
      bash "$SCRIPT_DIR/e2e-ctl.sh" down --clean || true
    else
      bash "$SCRIPT_DIR/e2e-ctl.sh" down || true
    fi
  fi

  if [ "$rc" -ne 0 ] && [ "$TEST_EXIT" -eq 0 ]; then
    exit "$rc"
  fi
}

trap finalize EXIT

resolve_jdk21
resolve_android_sdk

echo "==> [TIER 3 E2E] Ensuring infrastructure, backend, AVD, and adb reverse are ready..."
bash "$SCRIPT_DIR/e2e-ctl.sh" up

echo "==> [TIER 3 E2E] Clearing app state ($APP_ID), preparing persistent artifact dir, and resetting logcat buffer..."
adb shell pm clear "$APP_ID" >/dev/null 2>&1 || true
adb shell "rm -rf ${PERSISTENT_DEVICE_ARTIFACT_DIR} && mkdir -p ${PERSISTENT_DEVICE_ARTIFACT_DIR}" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

GRADLE_ARGS=(-p "$REPO_DIR")
if [ "${CI:-false}" = "true" ]; then
  GRADLE_ARGS+=(--no-daemon)
fi

echo "==> [TIER 3 E2E] Running FullStackSmokeE2ETest..."
set +e
"$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" :app:connectedDebugAndroidTest \
  -PBASE_URL=http://localhost:8080/ \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.neronguyen.chat.e2e.FullStackSmokeE2ETest \
  "${GRADLE_EXTRA_ARGS[@]}"
TEST_EXIT=$?
set -e

pull_device_artifacts
write_summary "$TEST_EXIT"
ARTIFACTS_PULLED=true
print_console_diagnostics "$TEST_EXIT"

echo "==> [TIER 3 E2E] Summary written to: build/reports/e2e-smoke/${RUN_ID}/summary.md"
exit "$TEST_EXIT"
