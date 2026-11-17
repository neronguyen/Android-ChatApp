#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=service.sh
source "$SCRIPT_DIR/service.sh"

JOURNEY="all"
CAPTURE_STEPS=false
TEARDOWN=false
CLEAN=false
GRADLE_EXTRA_ARGS=()

usage() {
  cat <<EOF
Usage: $(basename "$0") [options] [gradle args...]

Verifies deterministic Tier 3 E2E journeys against KMP-ChatBackend + Docker Compose on an Android
Virtual Device, saves per-journey steps.csv, logcat.txt, and failure.png (on failure) into
build/reports/e2e/<journey>/, and writes build/reports/e2e/summary.md.

Options:
  --journey <all|direct-chat|otp-recovery>  Select E2E journey(s) to verify (default: all)
  --capture-steps                           Also capture checkpoint PNGs on passing steps
  --teardown                                Stop E2E backend and Docker containers on exit
  --clean                                   Teardown and wipe Docker volumes on exit
  -h, --help                                Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --journey)
      if [ "${2:-}" = "" ]; then
        echo "ERROR: --journey requires an argument <all|direct-chat|otp-recovery>." >&2
        exit 2
      fi
      JOURNEY="$2"
      shift
      ;;
    --journey=*)
      JOURNEY="${1#*=}"
      ;;
    --capture-steps)
      CAPTURE_STEPS=true
      ;;
    --teardown)
      TEARDOWN=true
      ;;
    --clean)
      TEARDOWN=true
      CLEAN=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      GRADLE_EXTRA_ARGS+=("$1")
      ;;
  esac
  shift
done

if ! NORMALIZED_JOURNEY="$(normalize_journey "$JOURNEY")"; then
  echo "ERROR: Invalid --journey '$JOURNEY'. Expected one of: all, direct-chat, otp-recovery." >&2
  exit 2
fi
JOURNEY="$NORMALIZED_JOURNEY"

REPORT_DIR="${REPO_DIR}/build/reports/e2e"
if [ "$JOURNEY" = "all" ]; then
  rm -rf "$REPORT_DIR"
  SELECTED_JOURNEYS=("${E2E_ALL_JOURNEYS[@]}")
else
  SELECTED_JOURNEYS=("$JOURNEY")
fi
mkdir -p "$REPORT_DIR"

ARTIFACTS_PULLED=false
TEST_EXIT=0
declare -A JOURNEY_EXIT_CODES=()

write_summary() {
  local exit_code="$1"
  local summary_file="${REPORT_DIR}/summary.md"

  local -a meta_args=()
  for j_id in "${SELECTED_JOURNEYS[@]}"; do
    local j_title
    j_title="$(journey_title "$j_id")"
    local j_exit="${JOURNEY_EXIT_CODES[$j_id]:-1}"
    local checkpoints
    checkpoints="$(journey_checkpoints "$j_id" | tr '\n' ';')"
    meta_args+=("${j_id}|${j_exit}|${j_title}|${checkpoints}")
  done

  python3 - "$summary_file" "$REPORT_DIR" "$JOURNEY" "$exit_code" "${meta_args[@]}" <<'PY'
import csv
import datetime
import os
import sys

summary_file = sys.argv[1]
report_dir = sys.argv[2]
selected_arg = sys.argv[3]
overall_exit = int(sys.argv[4])
journey_specs = sys.argv[5:]

status_label = "PASS" if overall_exit == 0 else f"FAIL (exit {overall_exit})"
now_iso = datetime.datetime.now().astimezone().isoformat(timespec="seconds")

journey_ids = []
parsed_specs = []
for spec in journey_specs:
    j_id, j_exit_s, j_title, cp_blob = spec.split("|", 3)
    j_exit = int(j_exit_s)
    checkpoints = []
    for item in cp_blob.split(";"):
        if ":" in item:
            cp, state = item.split(":", 1)
            checkpoints.append((cp, state))
    journey_ids.append(j_id)
    parsed_specs.append((j_id, j_exit, j_title, checkpoints))

out = [
    "# E2E Verification Report",
    "",
    f"- **Status:** {status_label}",
    f"- **Journey(s):** `{selected_arg}` ({', '.join(journey_ids)})",
    f"- **Timestamp:** {now_iso}",
    "- **Reports Directory:** `build/reports/e2e/`",
]

ci_annotations = []

for j_id, j_exit, j_title, checkpoints in parsed_specs:
    j_dir = os.path.join(report_dir, j_id)
    csv_path = os.path.join(j_dir, "steps.csv")
    log_path = os.path.join(j_dir, "logcat.txt")
    failure_png = os.path.join(j_dir, "failure.png")
    j_badge = "PASS" if j_exit == 0 else f"FAIL (exit {j_exit})"

    records = {}
    if os.path.isfile(csv_path):
        with open(csv_path, "r", encoding="utf-8", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                cp = (row.get("checkpoint") or "").strip()
                if cp:
                    records[cp] = row

    rows = []
    fail_entry = None
    for cp, default_state in checkpoints:
        rec = records.get(cp)
        state = (rec.get("state") or default_state).strip() if rec else default_state
        row_status = (rec.get("status") or "NOT_RUN").strip() if rec else "NOT_RUN"
        dur_raw = (rec.get("duration_ms") or "").strip() if rec else ""
        dur_str = dur_raw if dur_raw else "-"

        step_png = os.path.join(j_dir, f"{cp}.png")
        if row_status == "FAIL" and os.path.isfile(failure_png):
            artifact_cell = f"[failure.png](./{j_id}/failure.png)"
        elif os.path.isfile(step_png):
            artifact_cell = f"[{cp}.png](./{j_id}/{cp}.png)"
        else:
            artifact_cell = "-"

        if row_status == "FAIL":
            fail_entry = (
                cp,
                state,
                (rec.get("error") or "Test assertion failed").strip() if rec else "Unknown error",
            )

        rows.append((f"`{cp}`", f"`{state}`", row_status, dur_str, artifact_cell))

    headers = ("Checkpoint", "FSM State", "Status", "Duration (ms)", "Artifact")
    w0 = max(len(headers[0]), *(len(r[0]) for r in rows))
    w1 = max(len(headers[1]), *(len(r[1]) for r in rows))
    w2 = max(len(headers[2]), *(len(r[2]) for r in rows))
    w3 = max(len(headers[3]), *(len(r[3]) for r in rows))
    w4 = max(len(headers[4]), *(len(r[4]) for r in rows))

    out.append("")
    out.append(f"## {j_title} — {j_badge}")
    out.append("")
    out.append(
        f"| {headers[0]:<{w0}} | {headers[1]:<{w1}} | {headers[2]:<{w2}} | {headers[3]:>{w3}} | {headers[4]:<{w4}} |"
    )
    out.append(
        f"| :{'-' * (w0 - 1)} | :{'-' * (w1 - 1)} | :{'-' * (w2 - 1)} | {'-' * (w3 - 1)}: | :{'-' * (w4 - 1)} |"
    )
    for r0, r1, r2, r3, r4 in rows:
        out.append(
            f"| {r0:<{w0}} | {r1:<{w1}} | {r2:<{w2}} | {r3:>{w3}} | {r4:<{w4}} |"
        )

    if j_exit != 0 or fail_entry is not None or os.path.isfile(failure_png):
        out.append("")
        out.append(f"### Failure Diagnostics (`{j_id}`)")
        out.append("")
        if fail_entry:
            cp, state, err = fail_entry
            out.append(f"- **Failed Step:** `{cp}` (`{state}`)")
            out.append(f"- **Error:** `{err}`")
            ci_annotations.append(f"::error title=E2E Fail ({j_id} @ {state})::{cp} - {err}")
        else:
            out.append("- **Failed Step:** Before first checkpoint or runner crash (see `logcat.txt`)")
            ci_annotations.append(
                f"::error title=E2E Fail ({j_id})::Journey exited with code {j_exit} before completing checkpoints"
            )
        if os.path.isfile(failure_png):
            out.append(f"- **Screenshot:** [failure.png](./{j_id}/failure.png)")
        if os.path.isfile(log_path):
            out.append(f"- **Logcat:** [logcat.txt](./{j_id}/logcat.txt)")
        if os.path.isfile(csv_path):
            out.append(f"- **Steps CSV:** [steps.csv](./{j_id}/steps.csv)")

with open(summary_file, "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")

if os.environ.get("GITHUB_ACTIONS") == "true":
    for ann in ci_annotations:
        print(ann, flush=True)
PY
}

write_ci_step_summary() {
  local exit_code="$1"
  if [ -z "${GITHUB_STEP_SUMMARY:-}" ]; then
    return 0
  fi

  {
    cat "${REPORT_DIR}/summary.md"
    if [ "$exit_code" -ne 0 ]; then
      for j_id in "${SELECTED_JOURNEYS[@]}"; do
        local j_exit="${JOURNEY_EXIT_CODES[$j_id]:-1}"
        local log_file="${REPORT_DIR}/${j_id}/logcat.txt"
        if [ "$j_exit" -ne 0 ] && [ -f "$log_file" ]; then
          echo ""
          echo "<details>"
          echo "<summary>Failure Log Excerpt (${j_id})</summary>"
          echo ""
          echo '```text'
          grep -E 'E2E_TEST|E2E_PEER|TestRunner|FATAL EXCEPTION|Caused by:|androidx\.compose\.ui\.test|OkHttp.*(-->|<--)' "$log_file" | tail -n 35 || true
          echo '```'
          echo "</details>"
        fi
      done
    fi
  } >>"$GITHUB_STEP_SUMMARY"
}

print_console_diagnostics() {
  local exit_code="$1"
  echo ""
  echo "==================== [TIER 3 E2E VERIFICATION SUMMARY] ===================="
  if [ -f "${REPORT_DIR}/summary.md" ]; then
    cat "${REPORT_DIR}/summary.md"
  fi

  if [ "$exit_code" -ne 0 ]; then
    for j_id in "${SELECTED_JOURNEYS[@]}"; do
      local j_exit="${JOURNEY_EXIT_CODES[$j_id]:-1}"
      local log_file="${REPORT_DIR}/${j_id}/logcat.txt"
      if [ "$j_exit" -ne 0 ] && [ -f "$log_file" ]; then
        echo ""
        echo "--- Failure Diagnostics (${j_id}) ---"
        grep -E 'E/TestRunner:|FATAL EXCEPTION|Caused by:|androidx\.compose\.ui\.test|E/E2E_TEST' "$log_file" | head -n 30 || true
        echo "--- Last E2E & OkHttp Events (${j_id}) ---"
        grep -E 'E2E_TEST|E2E_PEER|OkHttp.*(-->|<--)' "$log_file" | tail -n 20 || true
        echo "Logcat: build/reports/e2e/${j_id}/logcat.txt"
      fi
    done
    echo "Backend log: ${E2E_BACKEND_LOG}"
  fi
  echo "==========================================================================="
}

# shellcheck disable=SC2317
finalize() {
  local rc=$?
  set +e

  local summary_exit="$TEST_EXIT"
  if [ "$rc" -ne 0 ] && [ "$TEST_EXIT" -eq 0 ]; then
    summary_exit="$rc"
  fi

  if [ "$ARTIFACTS_PULLED" = false ]; then
    for j_id in "${SELECTED_JOURNEYS[@]}"; do
      pull_journey_artifacts "$j_id" "${REPORT_DIR}/${j_id}"
    done
    write_summary "$summary_exit"
    write_ci_step_summary "$summary_exit"
    ARTIFACTS_PULLED=true
    print_console_diagnostics "$summary_exit"
  fi

  if [ "$TEARDOWN" = true ]; then
    if [ "$CLEAN" = true ]; then
      cmd_down --clean || true
    else
      cmd_down || true
    fi
  fi

  if [ "$rc" -ne 0 ] && [ "$TEST_EXIT" -eq 0 ]; then
    exit "$rc"
  fi
}

trap finalize EXIT

cmd_up

adb shell "rm -rf ${E2E_PERSISTENT_DEVICE_DIR} && mkdir -p ${E2E_PERSISTENT_DEVICE_DIR}" >/dev/null 2>&1 || true

GRADLE_ARGS=(-p "$REPO_DIR")
if [ "${CI:-false}" = "true" ]; then
  GRADLE_ARGS+=(--no-daemon)
fi

EXTRA_RUNNER_ARGS=()
if [ "$CAPTURE_STEPS" = true ]; then
  EXTRA_RUNNER_ARGS+=(-Pandroid.testInstrumentationRunnerArguments.e2eCaptureSteps=true)
fi

for j_id in "${SELECTED_JOURNEYS[@]}"; do
  test_fqcn="$(journey_test_fqcn "$j_id")"
  journey_report_dir="${REPORT_DIR}/${j_id}"
  rm -rf "$journey_report_dir"
  mkdir -p "$journey_report_dir"

  echo "==> [E2E VERIFY] Preparing clean device state for '${j_id}' (${test_fqcn})..."
  adb shell pm clear "$E2E_APP_ID" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true

  echo "==> [E2E VERIFY] Running journey '${j_id}' (${test_fqcn})..."
  set +e
  "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" :app:connectedDebugAndroidTest \
    -PBASE_URL=http://localhost:8080/ \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.e2e=true \
    -Pandroid.testInstrumentationRunnerArguments.class="$test_fqcn" \
    "${EXTRA_RUNNER_ARGS[@]}" \
    "${GRADLE_EXTRA_ARGS[@]}"
  j_rc=$?
  set -e

  JOURNEY_EXIT_CODES["$j_id"]="$j_rc"
  if [ "$j_rc" -ne 0 ]; then
    TEST_EXIT="$j_rc"
  fi

  adb logcat -d -v time -s E2E_TEST:V E2E_PEER:V TestRunner:I AndroidRuntime:E OkHttp:I >"${journey_report_dir}/logcat.txt" 2>/dev/null || true
  pull_journey_artifacts "$j_id" "$journey_report_dir"
done

write_summary "$TEST_EXIT"
write_ci_step_summary "$TEST_EXIT"
ARTIFACTS_PULLED=true
print_console_diagnostics "$TEST_EXIT"

echo "==> [E2E VERIFY] Summary written to: build/reports/e2e/summary.md"
exit "$TEST_EXIT"
