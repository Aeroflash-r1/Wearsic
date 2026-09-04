#!/data/data/com.termux/files/usr/bin/bash
# Wearsic server launcher (Termux) — auto-heal supervisor.
#
# Responsibilities:
#   - start the server (source build via installDist, or legacy bin/ layout)
#   - restart it if it crashes, with exponential backoff
#   - detect hangs via /health and force-restart after repeated failures
#   - keep a wake lock so Android does not freeze Termux in the background
#   - rotate the log at ~2 MB
#   - fail loudly (exit non-zero) if ffmpeg installation genuinely fails
#
# JVM tuning: G1GC with a 512m heap (SerialGC froze the whole server on every
# collection). Drop -Xmx512m to -Xmx384m on phones with <3-4GB free RAM.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOG="$SCRIPT_DIR/wearsic-server.log"

# ZIP extraction can drop execute permissions — restore them.
chmod +x "$SCRIPT_DIR/run-termux.sh" 2>/dev/null || true
chmod +x "$SCRIPT_DIR/bin/wearsic-server" 2>/dev/null || true

# Support both layouts:
#  - packaged install: wearsic-server/bin/wearsic-server (next to this script)
#  - from the Git repo: wearsic-server/build/install/wearsic-server/bin/wearsic-server
if [ -x "$SCRIPT_DIR/bin/wearsic-server" ]; then
  APP="$SCRIPT_DIR/bin/wearsic-server"
elif [ -x "$SCRIPT_DIR/build/install/wearsic-server/bin/wearsic-server" ]; then
  APP="$SCRIPT_DIR/build/install/wearsic-server/bin/wearsic-server"
else
  echo "Missing wearsic-server binary. Unzip the full package (bin/ and lib/ must sit next to this script) or run ./gradlew :wearsic-server:installDist first." >&2
  exit 1
fi

if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/.env"
  set +a
fi

# --- Perf-tuned JVM flags ---
export JAVA_OPTS="${JAVA_OPTS:--Xms64m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=150 -Dhttp.keepAlive=true -Dhttp.maxConnections=10}"
export PORT="${PORT:-8080}"
export WEARSIC_DB_PATH="${WEARSIC_DB_PATH:-$SCRIPT_DIR/wearsic.db}"

log() { echo "[wearsic $(date '+%H:%M:%S')] $*" | tee -a "$LOG"; }

rotate_log() {
  if [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 2000000 ]; then
    mv -f "$LOG" "$LOG.old"
  fi
}

health_url() {
  echo "http://127.0.0.1:${PORT}/health"
}

health_ok() {
  if command -v curl >/dev/null 2>&1; then
    curl -sf -m 10 "$(health_url)" >/dev/null 2>&1
  elif command -v wget >/dev/null 2>&1; then
    wget -q -T 10 -O /dev/null "$(health_url)"
  else
    return 0   # no probe tool available: treat as OK (crash-restart still active)
  fi
}

cleanup() {
  [ -n "${CHILD:-}" ] && kill "$CHILD" 2>/dev/null
  exit 0
}
trap cleanup INT TERM

# Keep Android from freezing/killing Termux in the background.
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock && log "wake lock acquired"

log "auto-heal supervisor starting (health checks every 30s)"

# --- FFmpeg: required only for the rare Opus/WebM-only song (503 otherwise). ---
# Installation failures must NOT be reported as success: verify the binary
# actually exists after pkg install, and exit non-zero if it does not — a
# silent partial install previously made the supervisor print "ffmpeg
# installed" while transcoding stayed broken.
if ! command -v ffmpeg >/dev/null 2>&1; then
  log "ffmpeg not found — attempting installation..."
  if command -v pkg >/dev/null 2>&1; then
    if pkg install -y ffmpeg >> "$LOG" 2>&1; then
      if command -v ffmpeg >/dev/null 2>&1; then
        log "ffmpeg installed and verified"
      else
        log "ERROR: pkg reported success but ffmpeg is still not on PATH"
        log "Install it manually with: pkg install ffmpeg   (then re-run this script)"
        exit 1
      fi
    else
      log "ERROR: ffmpeg installation FAILED (unmet dependencies or network problem)"
      log "The server will still run, but songs YouTube only offers in Opus/WebM will answer 503."
      log "Fix with: pkg update -y && pkg install -y ffmpeg   (then re-run this script)"
      exit 1
    fi
  else
    log "ERROR: pkg not found — cannot install ffmpeg automatically"
    log "Install it manually with: pkg install ffmpeg   (then re-run this script)"
    exit 1
  fi
fi

# --- Restart loop with exponential backoff ---
# crash -> restart after 3s -> next crash after 6s -> 12s ... capped at 60s.
# A permanently broken server stays visible (repeated ERROR logs every ~60s)
# instead of burning battery on a tight restart loop.
RESTART_DELAY=3
MAX_RESTART_DELAY=60

while true; do
  rotate_log
  "$APP" >> "$LOG" 2>&1 &
  CHILD=$!
  log "server started (pid $CHILD, port $PORT)"

  FAILS=0
  EVER_HEALTHY=0
  while kill -0 "$CHILD" 2>/dev/null; do
    sleep 30
    if ! kill -0 "$CHILD" 2>/dev/null; then
      break   # process died on its own; outer loop handles restart
    fi
    if health_ok; then
      FAILS=0
      EVER_HEALTHY=1
    else
      FAILS=$((FAILS+1))
      log "health check FAILED ($FAILS/3)"
      if [ "$FAILS" -ge 3 ]; then
        log "server unhealthy for 90s — killing for auto-heal restart"
        kill "$CHILD" 2>/dev/null
        sleep 5
        kill -9 "$CHILD" 2>/dev/null
        break
      fi
    fi
  done

  wait "$CHILD" 2>/dev/null
  EXIT_CODE=$?

  if [ "$EVER_HEALTHY" -eq 1 ]; then
    # The server worked this cycle — treat the exit as a one-off crash and
    # restart quickly again.
    RESTART_DELAY=3
    log "server exited (code $EXIT_CODE) — restarting in ${RESTART_DELAY}s (Ctrl+C to stop supervisor)"
  else
    log "ERROR: server never became healthy this cycle (exit code $EXIT_CODE)"
    log "restart in ${RESTART_DELAY}s — check '$LOG' if this repeats"
  fi

  sleep "$RESTART_DELAY"
  # Exponential backoff capped at MAX_RESTART_DELAY; resets after any healthy cycle.
  RESTART_DELAY=$(( RESTART_DELAY * 2 ))
  if [ "$RESTART_DELAY" -gt "$MAX_RESTART_DELAY" ]; then
    RESTART_DELAY=$MAX_RESTART_DELAY
  fi
done
