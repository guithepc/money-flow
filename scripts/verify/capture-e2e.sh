#!/usr/bin/env bash
# Proves the PWA capture flow end to end in headless Chrome with a fake microphone that "says"
# "gastei vinte e cinco reais no supermercado" (macOS `say`). Needs the app running (scripts/dev-pwa.sh).
#
#   scripts/verify/capture-e2e.sh [BASE_URL]     # default http://localhost:5173
#
# Uses the demo user from dev-pwa.sh. Screenshots go to .dev-logs/e2e/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE_URL="${1:-http://localhost:5173}"
OUT="$ROOT/.dev-logs/e2e"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
PORT=9334
mkdir -p "$OUT"

# Sentence + 9s of silence: the fake device loops the file, and the sentence must not repeat inside one recording.
say -v Luciana "gastei vinte e cinco reais no supermercado" -o "$OUT/sentence.wav" --data-format=LEI16@48000
python3 -I - "$OUT/sentence.wav" "$OUT/fake-mic.wav" <<'PY'
import sys, wave
with wave.open(sys.argv[1]) as r:
    params, frames = r.getparams(), r.readframes(r.getnframes())
with wave.open(sys.argv[2], "wb") as w:
    w.setparams(params)
    w.writeframes(frames + b"\x00" * (params.sampwidth * params.nchannels * params.framerate * 9))
PY

PROFILE="$(mktemp -d)"
# AudioServiceSandbox off: otherwise the audio service can't read the fake microphone file.
"$CHROME" --headless=new --remote-debugging-port=$PORT --user-data-dir="$PROFILE" --no-first-run \
  --disable-features=AudioServiceSandbox --use-fake-ui-for-media-stream --use-fake-device-for-media-stream \
  --use-file-for-fake-audio-capture="$OUT/fake-mic.wav" about:blank > "$OUT/chrome.log" 2>&1 &
CHROME_PID=$!
trap 'kill $CHROME_PID 2>/dev/null; wait $CHROME_PID 2>/dev/null; rm -rf "$PROFILE"' EXIT
for _ in $(seq 1 20); do curl -s "localhost:$PORT/json/version" >/dev/null && break; sleep 0.5; done

CDP_PORT=$PORT BASE_URL="$BASE_URL" EMAIL="demo@moneyflow.local" PASSWORD="$(cat "$ROOT/.dev-logs/demo-password.txt")" OUT="$OUT" \
  node "$ROOT/scripts/verify/capture-e2e.mjs"
echo "Screenshots: $OUT"
