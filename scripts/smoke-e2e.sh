#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_ID="io.github.neronguyen.chat"
PERSISTENT_DEVICE_ARTIFACT_DIR="/sdcard/Download/e2e-smoke"
DEVICE_ARTIFACT_DIR="/sdcard/Android/data/${APP_ID}/files/e2e-smoke"
REMOTE_RAW_VIDEO="${PERSISTENT_DEVICE_ARTIFACT_DIR}/avd-raw.mp4"

JOURNEY="all"
RECORD_DEMO=false
TEARDOWN=false
CLEAN=false
GRADLE_EXTRA_ARGS=()

usage() {
  cat <<EOF
Usage: $(basename "$0") [options] [gradle args...]

Runs deterministic Tier 3 E2E journeys against KMP-ChatBackend + Docker Compose on an Android
Virtual Device, pulls all PNG/XML checkpoints into build/reports/e2e-smoke/<run-id>/, and writes
summary.md.

Options:
  --journey <all|happy-path|otp-recovery>  Select E2E journey(s) to execute (default: all)
  --record-demo                            Record AVD screen during happy-path and synthesize
                                           side-by-side MP4/GIF/WebP with live Peer/FSM terminal
  --teardown                               Stop E2E backend and Docker Compose containers on exit
  --clean                                  Teardown and wipe Docker Compose volumes on exit
  -h, --help                               Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --journey)
      if [ "${2:-}" = "" ]; then
        echo "ERROR: --journey requires an argument <all|happy-path|otp-recovery>." >&2
        exit 2
      fi
      JOURNEY="$2"
      shift
      ;;
    --journey=*)
      JOURNEY="${1#*=}"
      ;;
    --record-demo)
      RECORD_DEMO=true
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

case "$JOURNEY" in
  all|happy-path|otp-recovery) ;;
  *)
    echo "ERROR: Invalid --journey '$JOURNEY'. Expected one of: all, happy-path, otp-recovery." >&2
    exit 2
    ;;
esac

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
mkdir -p "$REPORT_DIR"

ARTIFACTS_PULLED=false
TEST_EXIT=0
SCREENRECORD_PID=""
RECORD_START_EPOCH=""
SELECTED_JOURNEYS=()

if [ "$JOURNEY" = "all" ]; then
  SELECTED_JOURNEYS=("happy-path" "otp-recovery")
else
  SELECTED_JOURNEYS=("$JOURNEY")
fi

declare -A JOURNEY_EXIT_CODES=()

journey_test_class() {
  case "$1" in
    happy-path) echo "io.github.neronguyen.chat.e2e.FullStackSmokeE2ETest" ;;
    otp-recovery) echo "io.github.neronguyen.chat.e2e.OtpRecoverySmokeE2ETest" ;;
    *) return 1 ;;
  esac
}

journey_title() {
  case "$1" in
    happy-path) echo "CUJ 1: Happy-Path Full-Stack Smoke (FullStackSmokeE2ETest)" ;;
    otp-recovery) echo "CUJ 2: Invalid OTP Rejection & Recovery (OtpRecoverySmokeE2ETest)" ;;
    *) echo "$1" ;;
  esac
}

journey_checkpoints() {
  case "$1" in
    happy-path)
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

start_screen_recording() {
  if [ "$RECORD_DEMO" != true ]; then
    return 0
  fi
  echo "==> [DEMO RECORDER] Starting AVD screenrecord ($REMOTE_RAW_VIDEO)..."
  adb shell "rm -f ${REMOTE_RAW_VIDEO}" >/dev/null 2>&1 || true
  RECORD_START_EPOCH="$(python3 -c 'import time; print(f"{time.time():.3f}")')"
  adb shell screenrecord --bit-rate 6000000 --time-limit 180 "$REMOTE_RAW_VIDEO" >/dev/null 2>&1 &
  SCREENRECORD_PID=$!
}

stop_screen_recording() {
  if [ -z "$SCREENRECORD_PID" ]; then
    return 0
  fi
  echo "==> [DEMO RECORDER] Stopping AVD screenrecord and flushing MP4..."
  adb shell "pkill -l INT screenrecord || kill -2 \$(pidof screenrecord)" >/dev/null 2>&1 || true

  local waited=0
  while [ "$waited" -lt 10 ]; do
    if ! adb shell "pidof screenrecord" >/dev/null 2>&1; then
      break
    fi
    sleep 0.5
    waited=$((waited + 1))
  done

  wait "$SCREENRECORD_PID" 2>/dev/null || true
  SCREENRECORD_PID=""
  sleep 1

  if adb pull "$REMOTE_RAW_VIDEO" "${REPORT_DIR}/avd-raw.mp4" >/dev/null 2>&1; then
    echo "==> [DEMO RECORDER] Pulled raw AVD recording to ${REPORT_DIR}/avd-raw.mp4"
  else
    echo "WARN: [DEMO RECORDER] Could not pull $REMOTE_RAW_VIDEO from device." >&2
  fi
}

pull_journey_artifacts() {
  local j_id="$1"
  local dest_dir="${REPORT_DIR}/${j_id}"
  mkdir -p "$dest_dir"

  if ! command -v adb >/dev/null 2>&1 || [ "$(adb get-state 2>/dev/null || true)" != "device" ]; then
    return 0
  fi

  for remote_base in "$PERSISTENT_DEVICE_ARTIFACT_DIR" "$DEVICE_ARTIFACT_DIR"; do
    local remote_dir="${remote_base}/${j_id}"
    rm -rf "${dest_dir}/_pulled"
    if adb pull "$remote_dir" "${dest_dir}/_pulled" >/dev/null 2>&1 && [ -d "${dest_dir}/_pulled" ]; then
      if [ -d "${dest_dir}/_pulled/${j_id}" ]; then
        mv "${dest_dir}/_pulled/${j_id}"/* "${dest_dir}/" 2>/dev/null || true
      else
        mv "${dest_dir}/_pulled"/* "${dest_dir}/" 2>/dev/null || true
      fi
      rm -rf "${dest_dir}/_pulled"
    fi
    if [ -f "${dest_dir}/steps.tsv" ] || [ -f "${dest_dir}/failure.png" ]; then
      break
    fi
  done

  # Mirror single-journey or happy-path artifacts to top-level REPORT_DIR for convenience
  if [ "$j_id" = "happy-path" ] || [ "${#SELECTED_JOURNEYS[@]}" -eq 1 ]; then
    cp -f "${dest_dir}"/*.png "${dest_dir}"/*.xml "${dest_dir}/steps.tsv" "${REPORT_DIR}/" 2>/dev/null || true
  fi
}

render_side_by_side_demo() {
  if [ "$RECORD_DEMO" != true ]; then
    return 0
  fi
  if [ ! -f "${REPORT_DIR}/avd-raw.mp4" ]; then
    echo "WARN: [DEMO RECORDER] Skipping side-by-side synthesis because avd-raw.mp4 is missing." >&2
    return 0
  fi
  if ! command -v ffmpeg >/dev/null 2>&1 || ! command -v ffprobe >/dev/null 2>&1; then
    echo "WARN: [DEMO RECORDER] ffmpeg/ffprobe not found in PATH; raw AVD video kept at ${REPORT_DIR}/avd-raw.mp4" >&2
    return 0
  fi

  local log_source="${REPORT_DIR}/logcat-happy-path.txt"
  if [ ! -f "$log_source" ]; then
    log_source="${REPORT_DIR}/logcat-e2e.txt"
  fi

  echo "==> [DEMO RECORDER] Synthesizing side-by-side AVD + Live Peer/FSM Terminal video..."
  python3 - "$REPORT_DIR" "$log_source" "${RECORD_START_EPOCH:-0}" <<'PY'
import datetime
import json
import os
import re
import shutil
import subprocess
import sys

report_dir = sys.argv[1]
log_path = sys.argv[2]
start_epoch_arg = float(sys.argv[3] or 0.0)

raw_mp4 = os.path.join(report_dir, "avd-raw.mp4")
out_mp4 = os.path.join(report_dir, "e2e-demo-side-by-side.mp4")
out_webp = os.path.join(report_dir, "e2e-demo-side-by-side.webp")
out_gif = os.path.join(report_dir, "e2e-demo-side-by-side.gif")

probe_cmd = [
    "ffprobe", "-v", "error",
    "-select_streams", "v:0",
    "-show_entries", "stream=width,height,duration:format=duration",
    "-of", "json",
    raw_mp4,
]
probe = json.loads(subprocess.check_output(probe_cmd, text=True))
stream = (probe.get("streams") or [{}])[0]
fmt = probe.get("format") or {}
duration = float(stream.get("duration") or fmt.get("duration") or 30.0)
duration = max(duration, 3.0)

target_h = 960
term_w = 760

log_pattern = re.compile(
    r"^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+[VDIWEF]/(?:E2E_SMOKE|E2E_PEER)\s*\(\s*\d+\):\s*(.*)$"
)

events = []
year = datetime.datetime.now().year
if os.path.isfile(log_path):
    with open(log_path, "r", encoding="utf-8", errors="replace") as f:
        for raw_line in f:
            m = log_pattern.match(raw_line.strip())
            if not m:
                continue
            ts_str, msg = m.group(1), m.group(2).strip()
            try:
                dt = datetime.datetime.strptime(f"{year}-{ts_str}", "%Y-%m-%d %H:%M:%S.%f")
                ts_sec = dt.timestamp()
            except ValueError:
                ts_sec = 0.0
            events.append((ts_sec, ts_str.split()[1], msg))

if events:
    base_ts = events[0][0]
    rel_events = [(max(0.0, min(duration - 0.1, ev[0] - base_ts)), ev[1], ev[2]) for ev in events]
else:
    rel_events = [(0.0, "00:00:00.000", "[FSM] Journey=happy-path START")]

# Deduplicate timestamps into discrete terminal keyframes
keyframes = [(0.0, [])]
visible_lines = []
for rel_t, clock_str, msg in rel_events:
    visible_lines = list(visible_lines) + [(clock_str, msg)]
    if len(visible_lines) > 22:
        visible_lines = visible_lines[-22:]
    if keyframes and abs(keyframes[-1][0] - rel_t) < 0.08:
        keyframes[-1] = (keyframes[-1][0], visible_lines)
    else:
        keyframes.append((rel_t, visible_lines))

frames_dir = os.path.join(report_dir, "_term_frames")
os.makedirs(frames_dir, exist_ok=True)


def classify_color(msg):
    if "FAIL" in msg:
        return (248, 81, 73)      # Red
    if "PASS" in msg or "DONE" in msg:
        return (63, 185, 80)      # Green
    if msg.startswith("[BOB-WS]"):
        return (210, 168, 255)    # Purple
    if msg.startswith("[BOB]"):
        return (121, 192, 255)    # Blue
    if msg.startswith("[MAILPIT]"):
        return (240, 136, 62)     # Amber
    if msg.startswith("[FSM]"):
        return (86, 211, 100)     # Mint
    return (201, 209, 217)        # Default light gray


# Try Pillow with system monospace TTF first; fall back to pure-Python 5x7 scaled bitmap renderer
use_pil = False
try:
    from PIL import Image, ImageDraw, ImageFont
    use_pil = True
except Exception:
    use_pil = False

FONT_5X7 = {
    " ": [0, 0, 0, 0, 0, 0, 0],
    "!": [4, 4, 4, 4, 0, 0, 4],
    "\"": [10, 10, 10, 0, 0, 0, 0],
    "#": [10, 10, 31, 10, 31, 10, 10],
    "$": [4, 15, 20, 14, 5, 30, 4],
    "%": [24, 25, 2, 4, 8, 19, 3],
    "&": [12, 18, 20, 8, 21, 18, 13],
    "'": [4, 4, 8, 0, 0, 0, 0],
    "(": [2, 4, 8, 8, 8, 4, 2],
    ")": [8, 4, 2, 2, 2, 4, 8],
    "*": [0, 4, 21, 14, 21, 4, 0],
    "+": [0, 4, 4, 31, 4, 4, 0],
    ",": [0, 0, 0, 0, 4, 4, 8],
    "-": [0, 0, 0, 31, 0, 0, 0],
    ".": [0, 0, 0, 0, 0, 12, 12],
    "/": [1, 1, 2, 4, 8, 16, 16],
    "0": [14, 17, 19, 21, 25, 17, 14],
    "1": [4, 12, 4, 4, 4, 4, 14],
    "2": [14, 17, 1, 2, 4, 8, 31],
    "3": [31, 2, 4, 2, 1, 17, 14],
    "4": [2, 6, 10, 18, 31, 2, 2],
    "5": [31, 16, 30, 1, 1, 17, 14],
    "6": [6, 8, 16, 30, 17, 17, 14],
    "7": [31, 1, 2, 4, 8, 8, 8],
    "8": [14, 17, 17, 14, 17, 17, 14],
    "9": [14, 17, 17, 15, 1, 2, 12],
    ":": [0, 12, 12, 0, 12, 12, 0],
    ";": [0, 12, 12, 0, 12, 4, 8],
    "<": [2, 4, 8, 16, 8, 4, 2],
    "=": [0, 0, 31, 0, 31, 0, 0],
    ">": [8, 4, 2, 1, 2, 4, 8],
    "?": [14, 17, 1, 2, 4, 0, 4],
    "@": [14, 17, 1, 13, 21, 21, 14],
    "A": [14, 17, 17, 31, 17, 17, 17],
    "B": [30, 17, 17, 30, 17, 17, 30],
    "C": [14, 17, 16, 16, 16, 17, 14],
    "D": [28, 18, 17, 17, 17, 18, 28],
    "E": [31, 16, 16, 30, 16, 16, 31],
    "F": [31, 16, 16, 30, 16, 16, 16],
    "G": [14, 17, 16, 23, 17, 17, 15],
    "H": [17, 17, 17, 31, 17, 17, 17],
    "I": [14, 4, 4, 4, 4, 4, 14],
    "J": [7, 2, 2, 2, 2, 18, 12],
    "K": [17, 18, 20, 24, 20, 18, 17],
    "L": [16, 16, 16, 16, 16, 16, 31],
    "M": [17, 27, 21, 21, 17, 17, 17],
    "N": [17, 17, 25, 21, 19, 17, 17],
    "O": [14, 17, 17, 17, 17, 17, 14],
    "P": [30, 17, 17, 30, 16, 16, 16],
    "Q": [14, 17, 17, 17, 21, 18, 13],
    "R": [30, 17, 17, 30, 20, 18, 17],
    "S": [15, 16, 16, 14, 1, 1, 30],
    "T": [31, 4, 4, 4, 4, 4, 4],
    "U": [17, 17, 17, 17, 17, 17, 14],
    "V": [17, 17, 17, 17, 17, 10, 4],
    "W": [17, 17, 17, 21, 21, 21, 10],
    "X": [17, 17, 10, 4, 10, 17, 17],
    "Y": [17, 17, 17, 10, 4, 4, 4],
    "Z": [31, 1, 2, 4, 8, 16, 31],
    "[": [14, 8, 8, 8, 8, 8, 14],
    "\\": [16, 16, 8, 4, 2, 1, 1],
    "]": [14, 2, 2, 2, 2, 2, 14],
    "^": [4, 10, 17, 0, 0, 0, 0],
    "_": [0, 0, 0, 0, 0, 0, 31],
    "{": [2, 4, 4, 8, 4, 4, 2],
    "|": [4, 4, 4, 4, 4, 4, 4],
    "}": [8, 4, 4, 2, 4, 4, 8],
    "~": [0, 8, 21, 2, 0, 0, 0],
}


def render_frame_pil(path, lines):
    img = Image.new("RGB", (term_w, target_h), (13, 17, 23))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, term_w, 48], fill=(22, 27, 34))
    draw.ellipse([18, 16, 32, 30], fill=(255, 95, 86))
    draw.ellipse([40, 16, 54, 30], fill=(255, 189, 46))
    draw.ellipse([62, 16, 76, 30], fill=(39, 201, 63))

    font_Candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
        "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
        "/usr/share/fonts/google-noto-vf/NotoSansMono[wght].ttf",
        "/usr/share/fonts/liberation-mono/LiberationMono-Regular.ttf",
    ]
    font_sm = None
    font_md = None
    for fp in font_Candidates:
        if os.path.exists(fp):
            font_sm = ImageFont.truetype(fp, 14)
            font_md = ImageFont.truetype(fp, 15)
            break
    if font_sm is None:
        font_sm = ImageFont.load_default()
        font_md = font_sm

    draw.text((96, 15), "nerochat-e2e — Live FSM & Peer Driver (Bob + Mailpit + WS)", fill=(139, 148, 158), font=font_md)
    y = 68
    for clock_str, msg in lines:
        color = classify_color(msg)
        display = f"{clock_str}  {msg}"
        if len(display) > 72:
            display = display[:69] + "..."
        draw.text((20, y), display, fill=color, font=font_sm)
        y += 36
    img.save(path)


def render_frame_ppm(path, lines):
    buf = bytearray((13, 17, 23) * (term_w * target_h))

    def fill_rect(x0, y0, x1, y1, rgb):
        r, g, b = rgb
        row_bytes = bytes((r, g, b)) * max(0, x1 - x0)
        for y in range(max(0, y0), min(target_h, y1)):
            start = (y * term_w + x0) * 3
            end = (y * term_w + x1) * 3
            buf[start:end] = row_bytes

    def draw_text(x, y, text, rgb, scale=2):
        cx = x
        for ch in text:
            glyph = FONT_5X7.get(ch) or FONT_5X7.get(ch.upper()) or FONT_5X7[" "]
            for row_idx, row_bits in enumerate(glyph):
                for col_idx in range(5):
                    if (row_bits >> (4 - col_idx)) & 1:
                        px = cx + col_idx * scale
                        py = y + row_idx * scale
                        fill_rect(px, py, px + scale, py + scale, rgb)
            cx += 6 * scale
            if cx + 6 * scale >= term_w - 12:
                break

    fill_rect(0, 0, term_w, 48, (22, 27, 34))
    fill_rect(18, 18, 30, 30, (255, 95, 86))
    fill_rect(38, 18, 50, 30, (255, 189, 46))
    fill_rect(58, 18, 70, 30, (39, 201, 63))
    draw_text(88, 17, "NEROCHAT E2E - LIVE FSM & PEER DRIVER", (139, 148, 158), scale=2)

    y = 68
    for clock_str, msg in lines:
        color = classify_color(msg)
        display = f"{clock_str} {msg}"
        if len(display) > 60:
            display = display[:57] + "..."
        draw_text(18, y, display, color, scale=2)
        y += 36

    with open(path, "wb") as f:
        f.write(f"P6\n{term_w} {target_h}\n255\n".encode("ascii"))
        f.write(buf)


concat_lines = []
ext = "png" if use_pil else "ppm"
for idx, (t_start, lines) in enumerate(keyframes):
    t_next = keyframes[idx + 1][0] if idx + 1 < len(keyframes) else duration
    seg_dur = max(0.05, t_next - t_start)
    frame_path = os.path.join(frames_dir, f"frame_{idx:04d}.{ext}")
    if use_pil:
        render_frame_pil(frame_path, lines)
    else:
        render_frame_ppm(frame_path, lines)
    concat_lines.append(f"file '{frame_path}'")
    concat_lines.append(f"duration {seg_dur:.3f}")

# Repeat last frame for ffmpeg concat demuxer quirk
concat_lines.append(f"file '{os.path.join(frames_dir, f'frame_{len(keyframes) - 1:04d}.{ext}')}'")
concat_file = os.path.join(frames_dir, "concat.txt")
with open(concat_file, "w", encoding="utf-8") as f:
    f.write("\n".join(concat_lines) + "\n")

term_mp4 = os.path.join(frames_dir, "terminal.mp4")
subprocess.run(
    [
        "ffmpeg", "-y", "-v", "error",
        "-f", "concat", "-safe", "0", "-i", concat_file,
        "-vf", f"fps=15,scale={term_w}:{target_h}:flags=neighbor",
        "-c:v", "libx264", "-pix_fmt", "yuv420p",
        term_mp4,
    ],
    check=True,
)

subprocess.run(
    [
        "ffmpeg", "-y", "-v", "error",
        "-i", raw_mp4,
        "-i", term_mp4,
        "-filter_complex",
        f"[0:v]scale=-2:{target_h}[avd];[1:v]scale={term_w}:{target_h}[term];[avd][term]hstack=inputs=2[v]",
        "-map", "[v]",
        "-c:v", "libx264", "-preset", "fast", "-crf", "23", "-pix_fmt", "yuv420p",
        "-shortest",
        out_mp4,
    ],
    check=True,
)

# Also export animated GIF for direct README embedding
subprocess.run(
    [
        "ffmpeg", "-y", "-v", "error",
        "-i", out_mp4,
        "-vf", "fps=8,scale=960:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
        out_gif,
    ],
    check=False,
)

# Export animated WebP if libwebp is available
subprocess.run(
    [
        "ffmpeg", "-y", "-v", "error",
        "-i", out_mp4,
        "-vf", "fps=10,scale=960:-1:flags=lanczos",
        "-loop", "0",
        out_webp,
    ],
    check=False,
)

shutil.rmtree(frames_dir, ignore_errors=True)
PY

  if [ -f "${REPORT_DIR}/e2e-demo-side-by-side.mp4" ]; then
    echo "==> [DEMO RECORDER] Side-by-side MP4 written to: build/reports/e2e-smoke/${RUN_ID}/e2e-demo-side-by-side.mp4"
  fi
  if [ -f "${REPORT_DIR}/e2e-demo-side-by-side.gif" ]; then
    echo "==> [DEMO RECORDER] Side-by-side GIF written to: build/reports/e2e-smoke/${RUN_ID}/e2e-demo-side-by-side.gif"
  fi
}

write_summary() {
  local exit_code="$1"
  local status_label="PASS"
  if [ "$exit_code" -ne 0 ]; then
    status_label="FAIL (exit ${exit_code})"
  fi

  local summary_file="${REPORT_DIR}/summary.md"

  {
    echo "# Full-Stack E2E Smoke Report (${RUN_ID})"
    echo ""
    echo "- **Status:** ${status_label}"
    echo "- **Selected Journey(s):** \`${JOURNEY}\` (${SELECTED_JOURNEYS[*]})"
    echo "- **Timestamp:** $(date -Iseconds)"
    echo "- **Artifacts Directory:** \`build/reports/e2e-smoke/${RUN_ID}/\`"
    if [ -f "${REPORT_DIR}/e2e-demo-side-by-side.mp4" ]; then
      echo "- **Side-by-Side Demo Video:** [e2e-demo-side-by-side.mp4](./e2e-demo-side-by-side.mp4)"
    fi
    if [ -f "${REPORT_DIR}/e2e-demo-side-by-side.gif" ]; then
      echo "- **Side-by-Side Demo GIF:** [e2e-demo-side-by-side.gif](./e2e-demo-side-by-side.gif)"
    fi

    for j_id in "${SELECTED_JOURNEYS[@]}"; do
      local j_title
      j_title="$(journey_title "$j_id")"
      local j_exit="${JOURNEY_EXIT_CODES[$j_id]:-1}"
      local j_badge="PASS"
      if [ "$j_exit" -ne 0 ]; then
        j_badge="FAIL (exit ${j_exit})"
      fi
      local steps_tsv="${REPORT_DIR}/${j_id}/steps.tsv"

      echo ""
      echo "## ${j_title} — ${j_badge}"
      echo ""
      echo "| Checkpoint | FSM State | Status | Duration (ms) | Screenshot | UI Hierarchy |"
      echo "|:-----------|:----------|:-------|--------------:|:-----------|:-------------|"

      local entry
      while IFS= read -r entry; do
        [ -z "$entry" ] && continue
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
        elif [ -f "${REPORT_DIR}/${j_id}/${cp}.png" ]; then
          row_status="CAPTURED"
        fi

        local png_link="-"
        if [ -f "${REPORT_DIR}/${j_id}/${cp}.png" ]; then
          png_link="[${cp}.png](./${j_id}/${cp}.png)"
        fi

        local xml_link="-"
        if [ -f "${REPORT_DIR}/${j_id}/${cp}.xml" ]; then
          xml_link="[${cp}.xml](./${j_id}/${cp}.xml)"
        fi

        echo "| \`${cp}\` | \`${state}\` | **${row_status}** | ${duration} | ${png_link} | ${xml_link} |"
      done < <(journey_checkpoints "$j_id")

      if [ -f "${REPORT_DIR}/${j_id}/failure.png" ] || [ -f "${REPORT_DIR}/${j_id}/failure.xml" ]; then
        echo ""
        echo "### Failure Diagnostics (\`${j_id}\`)"
        echo ""
        [ -f "${REPORT_DIR}/${j_id}/failure.png" ] && echo "- Screenshot: [failure.png](./${j_id}/failure.png)"
        [ -f "${REPORT_DIR}/${j_id}/failure.xml" ] && echo "- UI Hierarchy: [failure.xml](./${j_id}/failure.xml)"
        if [ -f "$steps_tsv" ]; then
          local fail_note
          fail_note="$(grep -E $'\tFAIL\t' "$steps_tsv" | tail -n 1 | cut -f5 || true)"
          [ -n "$fail_note" ] && echo "- Error: \`${fail_note}\`"
        fi
      fi
    done
  } >"$summary_file"
}

print_console_diagnostics() {
  local exit_code="$1"
  echo ""
  echo "==================== [TIER 3 E2E CONSOLE SUMMARY] ===================="
  if [ -f "${REPORT_DIR}/logcat-e2e.txt" ]; then
    local e2e_lines
    e2e_lines="$(grep -E 'E2E_SMOKE|E2E_PEER' "${REPORT_DIR}/logcat-e2e.txt" || true)"
    if [ -n "$e2e_lines" ]; then
      echo "--- E2E_SMOKE & E2E_PEER Timeline ---"
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

  stop_screen_recording

  if [ "$ARTIFACTS_PULLED" = false ]; then
    for j_id in "${SELECTED_JOURNEYS[@]}"; do
      pull_journey_artifacts "$j_id"
    done
    render_side_by_side_demo
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

adb shell "rm -rf ${PERSISTENT_DEVICE_ARTIFACT_DIR} && mkdir -p ${PERSISTENT_DEVICE_ARTIFACT_DIR}" >/dev/null 2>&1 || true
: >"${REPORT_DIR}/logcat-e2e.txt"

GRADLE_ARGS=(-p "$REPO_DIR")
if [ "${CI:-false}" = "true" ]; then
  GRADLE_ARGS+=(--no-daemon)
fi

for j_id in "${SELECTED_JOURNEYS[@]}"; do
  test_fqcn="$(journey_test_class "$j_id")"
  echo "==> [TIER 3 E2E] Preparing clean device state for journey '${j_id}' (${test_fqcn})..."
  adb shell pm clear "$APP_ID" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true

  if [ "$j_id" = "happy-path" ] && [ "$RECORD_DEMO" = true ]; then
    start_screen_recording
  fi

  echo "==> [TIER 3 E2E] Running journey '${j_id}' (${test_fqcn})..."
  set +e
  "$REPO_DIR/gradlew" "${GRADLE_ARGS[@]}" :app:connectedDebugAndroidTest \
    -PBASE_URL=http://localhost:8080/ \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true \
    -Pandroid.testInstrumentationRunnerArguments.class="$test_fqcn" \
    "${GRADLE_EXTRA_ARGS[@]}"
  j_rc=$?
  set -e

  if [ "$j_id" = "happy-path" ] && [ "$RECORD_DEMO" = true ]; then
    stop_screen_recording
  fi

  JOURNEY_EXIT_CODES["$j_id"]="$j_rc"
  if [ "$j_rc" -ne 0 ]; then
    TEST_EXIT="$j_rc"
  fi

  adb logcat -d -v time -s E2E_SMOKE:V E2E_PEER:V TestRunner:I AndroidRuntime:E OkHttp:I >"${REPORT_DIR}/logcat-${j_id}.txt" 2>/dev/null || true
  cat "${REPORT_DIR}/logcat-${j_id}.txt" >>"${REPORT_DIR}/logcat-e2e.txt" 2>/dev/null || true

  pull_journey_artifacts "$j_id"
done

render_side_by_side_demo
write_summary "$TEST_EXIT"
ARTIFACTS_PULLED=true
print_console_diagnostics "$TEST_EXIT"

echo "==> [TIER 3 E2E] Summary written to: build/reports/e2e-smoke/${RUN_ID}/summary.md"
exit "$TEST_EXIT"
