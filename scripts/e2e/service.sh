#!/usr/bin/env bash
set -euo pipefail

E2E_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$E2E_SCRIPT_DIR/../.." && pwd)"

E2E_APP_ID="io.github.neronguyen.chat"
E2E_PERSISTENT_DEVICE_DIR="/sdcard/Download/e2e"
E2E_APP_DEVICE_DIR="/sdcard/Android/data/${E2E_APP_ID}/files/e2e"
# shellcheck disable=SC2034
E2E_ALL_JOURNEYS=("direct-chat" "otp-recovery")

E2E_BACKEND_DIR="${E2E_BACKEND_DIR:-${BACKEND_DIR:-"$(cd "$REPO_DIR/.." && pwd)/KMP-ChatBackend"}}"
E2E_PID_FILE="${E2E_PID_FILE:-/tmp/nerochat-e2e-backend.pid}"
E2E_BACKEND_LOG="${E2E_BACKEND_LOG:-/tmp/nerochat-e2e-backend.log}"
E2E_EMULATOR_LOG="${E2E_EMULATOR_LOG:-/tmp/nerochat-e2e-emulator.log}"
E2E_AVD_NAME="${E2E_AVD_NAME:-}"
E2E_BACKEND_URL="${E2E_BACKEND_URL:-http://localhost:8080}"
E2E_MAILPIT_URL="${E2E_MAILPIT_URL:-http://localhost:8025}"
E2E_HEADLESS="${E2E_HEADLESS:-${HEADLESS:-false}}"

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

normalize_journey() {
  local raw="${1%.kt}"
  case "$raw" in
    all) echo "all" ;;
    direct-chat|DirectChatE2ETest|DirectChat) echo "direct-chat" ;;
    otp-recovery|OtpRecoveryE2ETest|OtpRecovery) echo "otp-recovery" ;;
    *) return 1 ;;
  esac
}

journey_class_name() {
  local j_id="$1"
  local pascal=""
  local part
  IFS='-' read -r -a parts <<<"$j_id"
  for part in "${parts[@]}"; do
    pascal+="${part^}"
  done
  echo "${pascal}E2ETest"
}

journey_test_fqcn() {
  local cls
  cls="$(journey_class_name "$1")"
  echo "io.github.neronguyen.chat.e2e.${cls}"
}

journey_title() {
  case "$1" in
    direct-chat) echo "DirectChatE2ETest (direct-chat)" ;;
    otp-recovery) echo "OtpRecoveryE2ETest (otp-recovery)" ;;
    *) echo "$(journey_class_name "$1") ($1)" ;;
  esac
}

journey_checkpoints() {
  case "$1" in
    direct-chat)
      cat <<'EOF'
00_register_form:S0_REGISTER_FORM
01_otp_challenge:S1_OTP_CHALLENGE
02_login_verified:S2_LOGIN_VERIFIED
03_chat_list_empty:S3_CHAT_LIST_EMPTY
04_new_chat_found_peer:S4_NEW_CHAT_FOUND_PEER
05_outbound_message_sent:S5_OUTBOUND_MESSAGE_SENT
06_inbound_message_received:S6_INBOUND_MESSAGE_RECEIVED
07_reconnect_catchup_sync:S7_RECONNECT_CATCHUP_SYNC
EOF
      ;;
    otp-recovery)
      cat <<'EOF'
00_register_and_challenge:N0_REGISTER_AND_CHALLENGE
01_invalid_otp_rejected:N1_INVALID_OTP_REJECTED
02_valid_otp_recovery_to_login:N2_VALID_OTP_RECOVERY_TO_LOGIN
EOF
      ;;
  esac
}

pull_journey_artifacts() {
  local j_id="$1"
  local dest_dir="$2"
  mkdir -p "$dest_dir"

  if ! command -v adb >/dev/null 2>&1 || [ "$(adb get-state </dev/null 2>/dev/null || true)" != "device" ]; then
    return 0
  fi

  local app_rel_dir="files/e2e/${j_id}"
  local file_list
  file_list="$(adb shell "run-as ${E2E_APP_ID} ls -1 ${app_rel_dir} 2>/dev/null" </dev/null 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$file_list" ]; then
    local fname
    while IFS= read -r fname; do
      [ -z "$fname" ] && continue
      adb exec-out run-as "$E2E_APP_ID" cat "${app_rel_dir}/${fname}" </dev/null >"${dest_dir}/${fname}" 2>/dev/null || rm -f "${dest_dir}/${fname}"
    done <<<"$file_list"
  fi

  if [ -f "${dest_dir}/steps.csv" ] || [ -f "${dest_dir}/failure.png" ]; then
    return 0
  fi

  local remote_base
  for remote_base in "$E2E_PERSISTENT_DEVICE_DIR" "$E2E_APP_DEVICE_DIR"; do
    local remote_dir="${remote_base}/${j_id}"
    rm -rf "${dest_dir}/_pulled"
    if adb pull "$remote_dir" "${dest_dir}/_pulled" </dev/null >/dev/null 2>&1 && [ -d "${dest_dir}/_pulled" ]; then
      if [ -d "${dest_dir}/_pulled/${j_id}" ]; then
        mv "${dest_dir}/_pulled/${j_id}"/* "${dest_dir}/" 2>/dev/null || true
      else
        mv "${dest_dir}/_pulled"/* "${dest_dir}/" 2>/dev/null || true
      fi
      rm -rf "${dest_dir}/_pulled"
    fi
    if [ -f "${dest_dir}/steps.csv" ] || [ -f "${dest_dir}/failure.png" ]; then
      break
    fi
  done
}

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: Required tool '$tool' not found in PATH." >&2
    exit 1
  fi
}

require_backend_dir() {
  if [ ! -f "$E2E_BACKEND_DIR/docker-compose.yml" ]; then
    echo "ERROR: KMP-ChatBackend docker-compose.yml not found at: $E2E_BACKEND_DIR/docker-compose.yml" >&2
    echo "Set E2E_BACKEND_DIR=/path/to/KMP-ChatBackend if located elsewhere." >&2
    exit 1
  fi
}

port_8080_in_use() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -qE '[:.]8080[[:space:]]'
    return $?
  fi
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:8080 -sTCP:LISTEN -t >/dev/null 2>&1
    return $?
  fi
  curl -sf -o /dev/null --max-time 2 "$E2E_BACKEND_URL/v3/api-docs" 2>/dev/null
}

read_pid_file() {
  if [ ! -f "$E2E_PID_FILE" ]; then
    return 1
  fi
  local pid pgid
  pid="$(sed -n '1p' "$E2E_PID_FILE" 2>/dev/null || true)"
  pgid="$(sed -n '2p' "$E2E_PID_FILE" 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    return 1
  fi
  echo "$pid ${pgid:-$pid}"
}

pid_is_owned_backend() {
  local pid="$1"
  if ! kill -0 "$pid" 2>/dev/null; then
    return 1
  fi
  local cmd
  cmd="$(ps -o args= -p "$pid" 2>/dev/null || true)"
  [[ "$cmd" == *java* || "$cmd" == *gradle* || "$cmd" == *bootRun* ]]
}

online_adb_device() {
  adb devices 2>/dev/null | awk 'NR>1 && $2 == "device" { print $1; exit }'
}

wait_for_backend_health() {
  local timeout_s=120
  local elapsed=0
  local pid="$1"

  while [ "$elapsed" -lt "$timeout_s" ]; do
    if curl -sf -o /dev/null --max-time 2 "$E2E_BACKEND_URL/v3/api-docs" 2>/dev/null; then
      return 0
    fi
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: E2E backend process (PID $pid) exited prematurely. Tail of $E2E_BACKEND_LOG:" >&2
      tail -n 40 "$E2E_BACKEND_LOG" >&2 || true
      rm -f "$E2E_PID_FILE"
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo "ERROR: Timed out waiting for backend readiness at $E2E_BACKEND_URL/v3/api-docs after ${timeout_s}s." >&2
  tail -n 40 "$E2E_BACKEND_LOG" >&2 || true
  return 1
}

ensure_backend_up() {
  require_backend_dir

  echo "==> Starting KMP-ChatBackend Docker Compose dependencies..."
  docker compose -f "$E2E_BACKEND_DIR/docker-compose.yml" up -d --wait

  local pid_info recorded_pid recorded_pgid
  if pid_info="$(read_pid_file)"; then
    read -r recorded_pid recorded_pgid <<<"$pid_info"
    if pid_is_owned_backend "$recorded_pid"; then
      echo "==> Reusing managed E2E backend (PID $recorded_pid, PGID $recorded_pgid)..."
      wait_for_backend_health "$recorded_pid"
      return 0
    else
      echo "==> Removing stale backend PID file ($E2E_PID_FILE)..."
      rm -f "$E2E_PID_FILE"
    fi
  fi

  if port_8080_in_use; then
    echo "ERROR: Port 8080 is already occupied by an unrelated process not tracked by $E2E_PID_FILE." >&2
    if command -v lsof >/dev/null 2>&1; then
      lsof -iTCP:8080 -sTCP:LISTEN >&2 || true
    elif command -v ss >/dev/null 2>&1; then
      ss -ltnp 2>/dev/null | grep -E '[:.]8080[[:space:]]' >&2 || true
    fi
    echo "Stop the existing service on :8080 before running '$0 up'." >&2
    exit 1
  fi

  echo "==> Launching KMP-ChatBackend (--spring.profiles.active=dev,e2e)..."
  (
    cd "$E2E_BACKEND_DIR"
    exec setsid ./gradlew --no-daemon :app:bootRun --args='--spring.profiles.active=dev,e2e' >"$E2E_BACKEND_LOG" 2>&1
  ) &
  local spawned_pid=$!
  local spawned_pgid
  spawned_pgid="$(ps -o pgid= -p "$spawned_pid" 2>/dev/null | tr -d ' ' || echo "$spawned_pid")"
  printf "%s\n%s\n" "$spawned_pid" "$spawned_pgid" >"$E2E_PID_FILE"

  wait_for_backend_health "$spawned_pid"
  echo "==> KMP-ChatBackend is healthy at $E2E_BACKEND_URL."
}

resolve_avd_name() {
  local -a avds=()
  local line
  while IFS= read -r line; do
    line="${line%$'\r'}"
    [ -z "$line" ] && continue
    [[ "$line" =~ ^(INFO|WARNING|ERROR)[[:space:]]*\| ]] && continue
    avds+=("$line")
  done < <(emulator -list-avds 2>/dev/null || true)

  if [ "${#avds[@]}" -eq 0 ]; then
    echo "ERROR: No Android Virtual Devices found. Create one in Android Studio or attach a physical device." >&2
    exit 1
  fi

  if [ -n "$E2E_AVD_NAME" ]; then
    local avd
    for avd in "${avds[@]}"; do
      if [ "$avd" = "$E2E_AVD_NAME" ]; then
        printf '%s\n' "$avd"
        return 0
      fi
    done
    echo "ERROR: Configured E2E_AVD_NAME='$E2E_AVD_NAME' not found. Available AVDs:" >&2
    printf '  %s\n' "${avds[@]}" >&2
    exit 1
  fi

  if [ "${#avds[@]}" -eq 1 ]; then
    printf '%s\n' "${avds[0]}"
    return 0
  fi

  local avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
  local best_avd="${avds[0]}"
  local best_score=-1
  local avd ini_file target api_level is_phone score

  for avd in "${avds[@]}"; do
    is_phone=1
    case "${avd,,}" in
      *wear*|*watch*|*tv*|*automotive*|*auto*|*desktop*|*glasses*|*xr*)
        is_phone=0
        ;;
    esac

    api_level=0
    ini_file="${avd_home}/${avd}.ini"
    if [ -f "$ini_file" ]; then
      target="$(sed -n 's/^target=android-\([0-9][0-9]*\).*/\1/p' "$ini_file" | head -n 1 || true)"
      if [[ "$target" =~ ^[0-9]+$ ]]; then
        api_level="$target"
      fi
    fi
    if [ "$api_level" -eq 0 ] && [[ "$avd" =~ [Aa][Pp][Ii]_?([0-9]+) ]]; then
      api_level="${BASH_REMATCH[1]}"
    fi

    score=$((is_phone * 1000 + api_level))
    if [ "$score" -gt "$best_score" ]; then
      best_score="$score"
      best_avd="$avd"
    fi
  done

  echo "==> Auto-selected AVD '$best_avd' from ${#avds[@]} available AVDs (set E2E_AVD_NAME to override)." >&2
  printf '%s\n' "$best_avd"
}

ensure_emulator_and_reverse() {
  require_tool adb

  local device_serial
  device_serial="$(online_adb_device || true)"
  if [ -z "$device_serial" ]; then
    require_tool emulator
    local selected_avd
    selected_avd="$(resolve_avd_name)"

    local emu_args=(-avd "$selected_avd" -no-snapshot-load -no-audio -no-boot-anim)
    if [ "$E2E_HEADLESS" = "true" ] || [ "${CI:-false}" = "true" ]; then
      emu_args+=(-no-window -gpu swiftshader_indirect)
    fi

    echo "==> Booting AVD '$selected_avd'..."
    nohup emulator "${emu_args[@]}" >"$E2E_EMULATOR_LOG" 2>&1 &
  else
    echo "==> Reusing online Android device ($device_serial)..."
  fi

  echo "==> Waiting for Android boot completion..."
  adb wait-for-device
  local boot_timeout=180
  local waited=0
  while [ "$waited" -lt "$boot_timeout" ]; do
    local boot_completed
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)"
    if [ "$boot_completed" = "1" ]; then
      break
    fi
    sleep 2
    waited=$((waited + 2))
  done

  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)" != "1" ]; then
    echo "ERROR: Android device did not finish booting within ${boot_timeout}s." >&2
    exit 1
  fi

  local pm_timeout=60
  local pm_waited=0
  until adb shell pm path android >/dev/null 2>&1; do
    if [ "$pm_waited" -ge "$pm_timeout" ]; then
      echo "ERROR: Android Package Manager service was not ready within ${pm_timeout}s." >&2
      exit 1
    fi
    sleep 1
    pm_waited=$((pm_waited + 1))
  done

  echo "==> Configuring adb reverse tunnels (8080, 8025)..."
  adb reverse tcp:8080 tcp:8080
  adb reverse tcp:8025 tcp:8025
}

cmd_up() {
  resolve_jdk21
  resolve_android_sdk
  require_tool docker
  require_tool curl
  ensure_backend_up
  ensure_emulator_and_reverse
  echo "==> E2E services are UP."
}

cmd_down() {
  local clean=false
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --clean) clean=true ;;
      *)
        echo "ERROR: Unknown option for 'down': $1" >&2
        exit 2
        ;;
    esac
    shift
  done

  resolve_android_sdk
  require_backend_dir

  local pid_info recorded_pid recorded_pgid
  if pid_info="$(read_pid_file)"; then
    read -r recorded_pid recorded_pgid <<<"$pid_info"
    if pid_is_owned_backend "$recorded_pid"; then
      echo "==> Stopping E2E backend process group (-$recorded_pgid)..."
      kill -TERM "-$recorded_pgid" 2>/dev/null || kill -TERM "$recorded_pid" 2>/dev/null || true
      local wait_s=0
      while kill -0 "$recorded_pid" 2>/dev/null && [ "$wait_s" -lt 15 ]; do
        sleep 1
        wait_s=$((wait_s + 1))
      done
      if kill -0 "$recorded_pid" 2>/dev/null; then
        kill -KILL "-$recorded_pgid" 2>/dev/null || kill -KILL "$recorded_pid" 2>/dev/null || true
      fi
    else
      echo "==> Cleaning stale PID file ($E2E_PID_FILE)..."
    fi
    rm -f "$E2E_PID_FILE"
  fi

  if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
    echo "==> Removing adb reverse rules..."
    adb reverse --remove-all >/dev/null 2>&1 || true
  fi

  if [ "$clean" = true ]; then
    echo "==> Tearing down Docker Compose stack and volumes..."
    docker compose -f "$E2E_BACKEND_DIR/docker-compose.yml" down -v
  else
    echo "==> Stopping Docker Compose stack (preserving warm volumes)..."
    docker compose -f "$E2E_BACKEND_DIR/docker-compose.yml" stop
  fi
}

cmd_status() {
  resolve_android_sdk
  require_backend_dir
  require_tool docker
  require_tool curl

  local overall=0

  echo "==> E2E Service Status:"

  local compose_ps
  compose_ps="$(docker compose -f "$E2E_BACKEND_DIR/docker-compose.yml" ps --format '{{.Service}}:{{.State}}:{{.Health}}' 2>/dev/null || true)"
  for svc in postgres redis rabbitmq mailpit; do
    local line
    line="$(printf '%s\n' "$compose_ps" | grep -E "^${svc}:" || true)"
    if [[ "$line" == *":running:healthy"* ]] || [[ "$line" == *":running:"* && "$svc" == "mailpit" && "$line" != *unhealthy* ]]; then
      echo "  [PASS] Docker ($svc): $line"
    else
      echo "  [FAIL] Docker ($svc): ${line:-not running}"
      overall=1
    fi
  done

  if curl -sf -o /dev/null --max-time 3 "$E2E_BACKEND_URL/v3/api-docs" 2>/dev/null; then
    echo "  [PASS] Backend HTTP ($E2E_BACKEND_URL/v3/api-docs): 200 OK"
  else
    echo "  [FAIL] Backend HTTP ($E2E_BACKEND_URL/v3/api-docs): unreachable"
    overall=1
  fi

  if curl -sf -o /dev/null --max-time 3 "$E2E_MAILPIT_URL/api/v1/info" 2>/dev/null; then
    echo "  [PASS] Mailpit API ($E2E_MAILPIT_URL/api/v1/info): 200 OK"
  else
    echo "  [FAIL] Mailpit API ($E2E_MAILPIT_URL/api/v1/info): unreachable"
    overall=1
  fi

  if command -v adb >/dev/null 2>&1 && [ "$(adb get-state 2>/dev/null || true)" = "device" ]; then
    local boot_prop
    boot_prop="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)"
    if [ "$boot_prop" = "1" ]; then
      echo "  [PASS] Android device: booted ($(online_adb_device))"
    else
      echo "  [FAIL] Android device: attached but sys.boot_completed=${boot_prop:-unset}"
      overall=1
    fi

    local reverse_list
    reverse_list="$(adb reverse --list 2>/dev/null || true)"
    if printf '%s\n' "$reverse_list" | grep -q 'tcp:8080' && printf '%s\n' "$reverse_list" | grep -q 'tcp:8025'; then
      echo "  [PASS] adb reverse: tcp:8080 & tcp:8025 active"
    else
      echo "  [FAIL] adb reverse: missing tcp:8080 or tcp:8025"
      overall=1
    fi
  else
    echo "  [FAIL] Android device: no online adb device"
    overall=1
  fi

  exit "$overall"
}

cmd_get_otp() {
  local email="${1:-}"
  if [ -z "$email" ]; then
    echo "Usage: $0 get-otp <email>" >&2
    exit 2
  fi
  require_tool python3

  python3 - "$E2E_MAILPIT_URL" "$email" <<'PY'
import json
import re
import sys
import urllib.request

base_url, target_email = sys.argv[1].rstrip("/"), sys.argv[2].strip().lower()

with urllib.request.urlopen(f"{base_url}/api/v1/messages", timeout=5) as resp:
    messages = json.load(resp).get("messages", [])

matching = [
    m for m in messages
    if any((r.get("Address") or "").strip().lower() == target_email for r in (m.get("To") or []))
]
if not matching:
    print(f"ERROR: No Mailpit messages addressed to {target_email}", file=sys.stderr)
    sys.exit(1)

matching.sort(key=lambda m: m.get("Created") or "", reverse=True)
msg_id = matching[0]["ID"]

with urllib.request.urlopen(f"{base_url}/api/v1/message/{msg_id}", timeout=5) as resp:
    detail = json.load(resp)

body = detail.get("Text") or ""
match = re.search(r"\b(\d{6})\b", body)
if not match:
    html = detail.get("HTML") or ""
    html = re.sub(r"(?is)<style[^>]*>.*?</style>", " ", html)
    html = re.sub(r"<[^>]+>", " ", html)
    match = re.search(r"\b(\d{6})\b", html)

if not match:
    print(f"ERROR: No 6-digit OTP found in Mailpit message {msg_id}", file=sys.stderr)
    sys.exit(1)

print(match.group(1))
PY
}

cmd_ui_dump() {
  local out_path="${1:-$REPO_DIR/build/reports/e2e/ui-dump.xml}"
  resolve_android_sdk
  require_tool adb
  mkdir -p "$(dirname "$out_path")"

  if [[ "$out_path" == *.json ]] && command -v android >/dev/null 2>&1; then
    if android layout >"$out_path" 2>/dev/null; then
      echo "$out_path"
      return 0
    fi
  fi

  adb shell uiautomator dump /data/local/tmp/e2e-ui-dump.xml >/dev/null
  adb exec-out cat /data/local/tmp/e2e-ui-dump.xml >"$out_path"
  adb shell rm -f /data/local/tmp/e2e-ui-dump.xml >/dev/null 2>&1 || true
  echo "$out_path"
}

cmd_screenshot() {
  local out_path="${1:-$REPO_DIR/build/reports/e2e/screenshot.png}"
  resolve_android_sdk
  require_tool adb
  mkdir -p "$(dirname "$out_path")"

  if command -v android >/dev/null 2>&1; then
    if android screen capture --output "$out_path" >/dev/null 2>&1; then
      echo "$out_path"
      return 0
    fi
  fi

  adb exec-out screencap -p >"$out_path"
  echo "$out_path"
}

service_usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Commands:
  up                    Start Docker Compose deps, KMP-ChatBackend (dev,e2e), AVD, and adb reverse
  down [--clean]        Stop E2E backend, remove adb reverse, stop containers (--clean wipes volumes)
  status                Verify Docker, Backend :8080, Mailpit :8025, AVD boot, and adb reverse
  get-otp <email>       Print latest 6-digit OTP sent to <email> via Mailpit REST API
  ui-dump [out.xml]     Export live AVD UI hierarchy XML (or JSON via android layout if .json)
  screenshot [out.png]  Capture live AVD screenshot PNG
EOF
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  COMMAND="${1:-}"
  if [ -z "$COMMAND" ]; then
    service_usage >&2
    exit 2
  fi
  shift

  case "$COMMAND" in
    up) cmd_up "$@" ;;
    down) cmd_down "$@" ;;
    status) cmd_status "$@" ;;
    get-otp) cmd_get_otp "$@" ;;
    ui-dump) cmd_ui_dump "$@" ;;
    screenshot) cmd_screenshot "$@" ;;
    -h|--help|help) service_usage ;;
    *)
      echo "ERROR: Unknown command '$COMMAND'" >&2
      service_usage >&2
      exit 2
      ;;
  esac
fi
