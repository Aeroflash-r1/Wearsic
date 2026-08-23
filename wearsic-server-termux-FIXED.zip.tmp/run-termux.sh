#!/data/data/com.termux/files/usr/bin/bash
# Wearsic server launcher (Termux) — AUTO-HEAL edition.
#
# PATCHED 2026-08-21:
#   - Audio quality target 70 kbps lives inside lib/wearsic-server-1.0.0.jar
#   - Full JIT enabled (was capped at C1) + 256 MB heap
#   - AUTO-HEAL:
#       * crashes        -> restarted automatically
#       * hangs/unhealthy-> detected via /health every 30s, killed + restarted
#       * android doze   -> termux-wake-lock keeps the server alive
#       * logs           -> wearsic-server.log (rotated at ~2 MB)
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOG="$SCRIPT_DIR/wearsic-server.log"

# ZIP extraction can drop execute permissions — restore them.
chmod +x "$SCRIPT_DIR/run-termux.sh" 2>/dev/null
chmod +x "$SCRIPT_DIR/bin/wearsic-server" 2>/dev/null

# Support both layouts:
#  - inside the Termux ZIP:   wearsic-server/bin/wearsic-server
#  - from the Git repo:       server/build/install/wearsic-server/bin/wearsic-server
if [ -x "$SCRIPT_DIR/bin/wearsic-server" ]; then
  APP="$SCRIPT_DIR/bin/wearsic-server"
elif [ -x "$SCRIPT_DIR/build/install/wearsic-server/bin/wearsic-server" ]; then
  APP="$SCRIPT_DIR/build/install/wearsic-server/bin/wearsic-server"
else
  echo "Missing wearsic-server binary. Unzip the full package (bin/ and lib/ must sit next to this script) or run ./gradlew installDist first." >&2
  exit 1
fi

if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/.env"
  set +a
fi

export JAVA_OPTS="${JAVA_OPTS:--Xms32m -Xmx256m -XX:+UseSerialGC}"
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

# Self-healing against YouTube breakage: if freshly started servers never pass
# a health check, the extractor is probably outdated -> auto-update it
# (rate-limited to one attempt per hour).
PERSISTENT_FAILS=0
LAST_UPDATE_TRY=0

while true; do
  NOW=$(date +%s)
  if [ "$PERSISTENT_FAILS" -ge 2 ] && [ $((NOW - LAST_UPDATE_TRY)) -ge 3600 ]; then
    log "server unhealthy across restarts — attempting extractor auto-update"
    LAST_UPDATE_TRY=$NOW
    bash "$SCRIPT_DIR/update-newpipe.sh" 2>&1 | tee -a "$LOG"
  fi

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
  if [ "$EVER_HEALTHY" -eq 1 ]; then
    PERSISTENT_FAILS=0
  else
    PERSISTENT_FAILS=$((PERSISTENT_FAILS+1))
    log "server never became healthy this cycle ($PERSISTENT_FAILS in a row)"
  fi
  log "server exited — restarting in 3s (Ctrl+C to stop supervisor)"
  sleep 3
done