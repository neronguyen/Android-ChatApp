#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=service.sh
source "$SCRIPT_DIR/service.sh"

REMOTE_RAW_VIDEO="/data/local/tmp/e2e-avd-raw.mp4"
JOURNEY="direct-chat"
CAPTURE_STEPS=false
TEARDOWN=false
CLEAN=false
GRADLE_EXTRA_ARGS=()

usage() {
  cat <<EOF
Usage: $(basename "$0") [options] [gradle args...]

Records an E2E journey video on AVD and uses ffmpeg to export demo.mp4 and demo.gif directly into
build/reports/e2e/<journey>/.

Options:
  --journey <direct-chat|otp-recovery>  Select E2E journey to record (default: direct-chat)
  --capture-steps                       Also capture checkpoint PNGs on passing steps
  --teardown                            Stop E2E backend and Docker containers on exit
  --clean                               Teardown and wipe Docker volumes on exit
  -h, --help                            Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --journey)
      if [ "${2:-}" = "" ]; then
        echo "ERROR: --journey requires an argument <direct-chat|otp-recovery>." >&2
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

if ! NORMALIZED_JOURNEY="$(normalize_journey "$JOURNEY")" || [ "$NORMALIZED_JOURNEY" = "all" ]; then
  echo "ERROR: Invalid --journey '$JOURNEY'. Expected one of: direct-chat, otp-recovery." >&2
  exit 2
fi
JOURNEY="$NORMALIZED_JOURNEY"

require_tool ffmpeg
require_tool ffprobe

JOURNEY_DIR="${REPO_DIR}/build/reports/e2e/${JOURNEY}"
rm -rf "$JOURNEY_DIR"
mkdir -p "$JOURNEY_DIR"

SCREENRECORD_PID=""
ARTIFACTS_PULLED=false
VIDEO_RENDERED=false
TEST_EXIT=0

start_screen_recording() {
  echo "==> [E2E RECORD] Starting AVD screenrecord ($REMOTE_RAW_VIDEO)..."
  adb shell "rm -f ${REMOTE_RAW_VIDEO}" </dev/null >/dev/null 2>&1 || true
  adb shell "log -t E2E_TEST '[RECORDER] Screen recording started'" </dev/null >/dev/null 2>&1 || true
  adb shell screenrecord --bit-rate 6000000 --time-limit 180 "$REMOTE_RAW_VIDEO" </dev/null >/dev/null 2>&1 &
  SCREENRECORD_PID=$!
  sleep 0.3
}

stop_screen_recording() {
  if [ -z "$SCREENRECORD_PID" ]; then
    return 0
  fi
  echo "==> [E2E RECORD] Stopping AVD screenrecord and flushing MP4..."
  adb shell "pkill -INT screenrecord || pkill -2 screenrecord || kill -2 \$(pidof screenrecord)" </dev/null >/dev/null 2>&1 || true

  local waited=0
  while [ "$waited" -lt 20 ]; do
    if ! adb shell "pidof screenrecord" </dev/null >/dev/null 2>&1; then
      break
    fi
    sleep 0.5
    waited=$((waited + 1))
  done

  if kill -0 "$SCREENRECORD_PID" 2>/dev/null; then
    local pipe_wait=0
    while [ "$pipe_wait" -lt 10 ] && kill -0 "$SCREENRECORD_PID" 2>/dev/null; do
      sleep 0.3
      pipe_wait=$((pipe_wait + 1))
    done
    if kill -0 "$SCREENRECORD_PID" 2>/dev/null; then
      adb shell "kill -KILL \$(pidof screenrecord)" </dev/null >/dev/null 2>&1 || true
      kill "$SCREENRECORD_PID" 2>/dev/null || true
    fi
  fi

  wait "$SCREENRECORD_PID" 2>/dev/null || true
  SCREENRECORD_PID=""
  sleep 1

  if adb pull "$REMOTE_RAW_VIDEO" "${JOURNEY_DIR}/avd-raw.mp4" </dev/null >/dev/null 2>&1 && [ -s "${JOURNEY_DIR}/avd-raw.mp4" ]; then
    echo "==> [E2E RECORD] Pulled raw AVD recording."
  else
    echo "WARN: [E2E RECORD] Could not pull non-empty $REMOTE_RAW_VIDEO from device." >&2
    rm -f "${JOURNEY_DIR}/avd-raw.mp4"
  fi
  adb shell "rm -f ${REMOTE_RAW_VIDEO}" </dev/null >/dev/null 2>&1 || true
}

render_video_with_ffmpeg() {
  if [ "$VIDEO_RENDERED" = true ]; then
    return 0
  fi
  VIDEO_RENDERED=true

  local raw_mp4="${JOURNEY_DIR}/avd-raw.mp4"
  local out_mp4="${JOURNEY_DIR}/demo.mp4"
  local out_gif="${JOURNEY_DIR}/demo.gif"
  local ass_file="${JOURNEY_DIR}/_terminal.ass"

  if [ ! -s "$raw_mp4" ]; then
    echo "WARN: [E2E RECORD] Skipping ffmpeg processing because avd-raw.mp4 is missing or empty." >&2
    return 0
  fi

  echo "==> [E2E RECORD] Rendering video with ffmpeg for '${JOURNEY}'..."

  local video_duration
  video_duration="$(python3 - "$raw_mp4" "${JOURNEY_DIR}/logcat.txt" "$ass_file" "$JOURNEY" <<'PY'
import datetime
import json
import os
import re
import subprocess
import sys

raw_mp4, log_path, ass_path, journey_id = sys.argv[1:5]

probe = json.loads(
    subprocess.check_output(
        [
            "ffprobe", "-v", "error", "-select_streams", "v:0",
            "-show_entries", "stream=duration:format=duration", "-of", "json", raw_mp4,
        ],
        stdin=subprocess.DEVNULL,
        text=True,
    )
)
stream = (probe.get("streams") or [{}])[0]
fmt = probe.get("format") or {}
raw_dur = stream.get("duration") or fmt.get("duration")
try:
    raw_duration = max(float(raw_dur), 3.0) if raw_dur and raw_dur != "N/A" else 30.0
except (TypeError, ValueError):
    raw_duration = 30.0

hold_sec = 2.0
total_duration = raw_duration + hold_sec

pat = re.compile(
    r"^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+[VDIWEF]/(E2E_TEST|E2E_PEER)\s*\(\s*\d+\):\s*(.*)$"
)
peer_events, test_events = [], []
start_ts = None
year = datetime.datetime.now().year

if os.path.isfile(log_path):
    with open(log_path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            m = pat.match(line.strip())
            if not m:
                continue
            ts_str, tag, msg = m.group(1), m.group(2), m.group(3).strip()
            try:
                ts = datetime.datetime.strptime(f"{year}-{ts_str}", "%Y-%m-%d %H:%M:%S.%f").timestamp()
            except ValueError:
                ts = 0.0
            clock = ts_str.split()[1].split(".")[0]
            if tag == "E2E_TEST" and msg.startswith("[RECORDER]") and start_ts is None:
                start_ts = ts
            elif tag == "E2E_PEER":
                peer_events.append((ts, clock, msg))
            elif tag == "E2E_TEST":
                test_events.append((ts, clock, msg))

events = peer_events if peer_events else test_events
header = "Bob Client (Headless Peer - bob@example.com)" if peer_events else f"E2E Journey ({journey_id})"
base_ts = start_ts if start_ts is not None else (events[0][0] if events else 0.0)


def fmt_ass_time(sec: float) -> str:
    sec = max(0.0, sec)
    h = int(sec // 3600)
    m = int((sec % 3600) // 60)
    s = sec % 60
    return f"{h}:{m:02d}:{s:05.2f}"


def esc_ass(text: str) -> str:
    return text.replace("\\", r"\\").replace("{", r"\{").replace("}", r"\}")


def bgr_color(msg: str) -> str:
    if "Error" in msg or "FAIL" in msg:
        return "&H4951F8&"
    if "Server ACK" in msg or "Connected" in msg or "Online" in msg or "PASS" in msg:
        return "&H50B93F&"
    if "Sending" in msg or "State transition" in msg:
        return "&HFFA8D2&"
    if "Received" in msg or "Latest msg" in msg or "[MAILPIT]" in msg:
        return "&H3E88F0&"
    return "&HFFD079&"


ass_lines = [
    "[Script Info]",
    "ScriptType: v4.00+",
    "PlayResX: 1000",
    "PlayResY: 960",
    "WrapStyle: 2",
    "",
    "[V4+ Styles]",
    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
    "Style: Header,Monospace,32,&H009E948B,&H000000FF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,0,0,7,24,24,20,1",
    "Style: Log,Monospace,30,&H00D9D1C9,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,24,24,92,1",
    "",
    "[Events]",
    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
    f"Dialogue: 0,0:00:00.00,{fmt_ass_time(total_duration)},Header,,0,0,0,,{esc_ass(header)}",
]

keyframes = []
visible = []
for ts, clock, msg in events:
    rel_t = max(0.0, min(raw_duration - 0.05, ts - base_ts))
    display = f"{clock}  {msg}"
    if len(display) > 56:
        display = display[:53] + "..."
    visible = (visible + [f"{{\\c{bgr_color(msg)}}}{esc_ass(display)}"])[-18:]
    if keyframes and abs(keyframes[-1][0] - rel_t) < 0.08:
        keyframes[-1] = (keyframes[-1][0], list(visible))
    else:
        keyframes.append((rel_t, list(visible)))

for idx, (t0, lines) in enumerate(keyframes):
    t1 = keyframes[idx + 1][0] if idx + 1 < len(keyframes) else total_duration
    if t1 > t0:
        body = r"\N".join(lines)
        ass_lines.append(
            f"Dialogue: 0,{fmt_ass_time(t0)},{fmt_ass_time(t1)},Log,,0,0,0,,{body}"
        )

with open(ass_path, "w", encoding="utf-8") as f:
    f.write("\n".join(ass_lines) + "\n")

print(f"{total_duration:.3f}")
PY
)"

  local ass_file_escaped
  ass_file_escaped="${ass_file//\\/\\\\}"
  ass_file_escaped="${ass_file_escaped//:/\\:}"
  ass_file_escaped="${ass_file_escaped//\'/\\\'}"

  local ffmpeg_err
  if ! ffmpeg_err="$(ffmpeg -nostdin -y -v error \
    -i "$raw_mp4" \
    -f lavfi -i "color=c=0x0d1117:s=1000x960:r=30:d=${video_duration}" \
    -filter_complex "[0:v]fps=30,scale=-2:960:flags=lanczos,setsar=1,tpad=stop_mode=clone:stop_duration=2[avd];[1:v]drawbox=x=0:y=0:w=1000:h=72:color=0x161b22:t=fill,ass=filename='${ass_file_escaped}'[term];[avd][term]hstack=inputs=2:shortest=1[v]" \
    -map "[v]" \
    -c:v libx264 -preset fast -crf 23 -pix_fmt yuv420p -movflags +faststart -shortest \
    "$out_mp4" 2>&1)"; then
    echo "==> [E2E RECORD] Side-by-side ffmpeg render failed (${ffmpeg_err:-unknown error}); encoding clean AVD video directly..." >&2
    ffmpeg -nostdin -y -v error \
      -i "$raw_mp4" \
      -vf "fps=30,scale=-2:960:flags=lanczos,setsar=1,tpad=stop_mode=clone:stop_duration=2" \
      -c:v libx264 -preset fast -crf 23 -pix_fmt yuv420p -movflags +faststart \
      "$out_mp4"
  fi

  ffmpeg -nostdin -y -v error \
    -i "$out_mp4" \
    -vf "fps=8,scale=1080:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" \
    "$out_gif" || true

  rm -f "$raw_mp4" "$ass_file"

  if [ -f "$out_mp4" ]; then
    echo "==> [E2E RECORD] MP4: build/reports/e2e/${JOURNEY}/demo.mp4"
  fi
  if [ -f "$out_gif" ]; then
    echo "==> [E2E RECORD] GIF: build/reports/e2e/${JOURNEY}/demo.gif"
  fi
}

# shellcheck disable=SC2317
finalize() {
  local rc=$?
  set +e

  stop_screen_recording

  if [ "$ARTIFACTS_PULLED" = false ]; then
    adb logcat -d -v time -s E2E_TEST:V E2E_PEER:V TestRunner:I AndroidRuntime:E OkHttp:I </dev/null >"${JOURNEY_DIR}/logcat.txt" 2>/dev/null || true
    pull_journey_artifacts "$JOURNEY" "$JOURNEY_DIR"
    ARTIFACTS_PULLED=true
  fi
  render_video_with_ffmpeg

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

adb shell "rm -rf ${E2E_PERSISTENT_DEVICE_DIR} && mkdir -p ${E2E_PERSISTENT_DEVICE_DIR}" </dev/null >/dev/null 2>&1 || true

GRADLE_ARGS=(-p "$REPO_DIR")
if [ "${CI:-false}" = "true" ]; then
  GRADLE_ARGS+=(--no-daemon)
fi

echo "==> [E2E RECORD] Pre-building and installing debug + androidTest APKs..."
"$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" :app:installDebug :app:installDebugAndroidTest \
  -PBASE_URL=http://localhost:8080/ \
  "${GRADLE_EXTRA_ARGS[@]}"

test_fqcn="$(journey_test_fqcn "$JOURNEY")"
echo "==> [E2E RECORD] Preparing clean device state for '${JOURNEY}' (${test_fqcn})..."
adb shell pm clear "$E2E_APP_ID" </dev/null >/dev/null 2>&1 || true
adb logcat -c </dev/null >/dev/null 2>&1 || true

INSTR_ARGS=(-e e2e true -e class "$test_fqcn")
if [ "$CAPTURE_STEPS" = true ]; then
  INSTR_ARGS+=(-e e2eCaptureSteps true)
fi

start_screen_recording
echo "==> [E2E RECORD] Launching '${JOURNEY}' via am instrument for zero-lag recording..."
set +e
INSTR_OUT="$(adb shell am instrument -w "${INSTR_ARGS[@]}" "${E2E_APP_ID}.test/androidx.test.runner.AndroidJUnitRunner" </dev/null 2>&1 | tr -d '\r')"
am_rc=$?
set -e
printf '%s\n' "$INSTR_OUT"
if [ "$am_rc" -eq 0 ] && printf '%s\n' "$INSTR_OUT" | grep -qE '^OK \([1-9][0-9]* test'; then
  TEST_EXIT=0
else
  TEST_EXIT=1
fi
stop_screen_recording

adb logcat -d -v time -s E2E_TEST:V E2E_PEER:V TestRunner:I AndroidRuntime:E OkHttp:I </dev/null >"${JOURNEY_DIR}/logcat.txt" 2>/dev/null || true
pull_journey_artifacts "$JOURNEY" "$JOURNEY_DIR"
ARTIFACTS_PULLED=true
render_video_with_ffmpeg

echo "==> [E2E RECORD] Artifacts saved to: build/reports/e2e/${JOURNEY}/"
exit "$TEST_EXIT"
