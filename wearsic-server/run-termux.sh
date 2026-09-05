#!/data/data/com.termux/files/usr/bin/bash
# Wearsic server launcher (Termux) — auto-heal supervisor.
#
# Responsibilities:
#   - start the server (source build via installDist, or legacy bin/ layout)
#   - restart it if it crashes, with exponential backoff
#   - detect hangs via /health and force-restart after repeated failures
#   - apply engine updates staged by the server's self-healing updater
#     (the JVM cannot replace its own jar; this script swaps bin/ + lib/)
#   - keep a wake lock so Android does not freeze Termux in the background
#   - rotate the log at ~2 MB
#   - fail loudly (exit non-zero) if ffmpeg installation genuinely fails
#
# JVM tuning: G1GC with a 512m heap (SerialGC froze the whole server on every
# collection). Drop -Xmx512m to -Xmx384m on phones with <3-4GB free RAM.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOG="$SCRIPT_DIR/wearsic-server.log"

# Self-healing state dir (staged engine updates + update.json handoff).
# Must match the server's resolution order: WEARSIC_STATE_DIR, else
# <db-dir>/wearsic-state, else ./wearsic-state.
resolve_state_dir() {
  if [ -n "${WEARSIC_STATE_DIR:-}" ]; then
    echo "$WEARSIC_STATE_DIR"
  elif [ -n "${WEARSIC_DB_PATH:-}" ]; then
    echo "$(dirname -- "$WEARSIC_DB_PATH")/wearsic-state"
  else
    echo "$SCRIPT_DIR/wearsic-state"
  fi
}
STATE_DIR="$(resolve_state_dir)"

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

# --- Apply a staged engine update (from the server's EngineUpdater) --------
# The JVM cannot replace its own running jar, so the server exits after
# staging a new version into $STATE_DIR/staging/ and writing
# $STATE_DIR/update.json. Here we swap bin/ + lib/ atomically, keep the old
# build as .bak for rollback, then boot the new engine.
apply_staged_update() {
  UPDATE_JSON="$STATE_DIR/update.json"
  [ -f "$UPDATE_JSON" ] || return 0

  # Accept only a 'staged' state — anything else is stale.
  if ! grep -q '"status"[[:space:]]*:[[:space:]]*"staged"' "$UPDATE_JSON"; then
    return 0
  fi
  NEW_VER=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$UPDATE_JSON" | head -n1)
  OLD_VER=$(sed -n 's/.*"previousVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$UPDATE_JSON" | head -n1)

  STAGE="$STATE_DIR/staging"
  # The release zip extracts as wearsic-server/... — find the package root.
  SRC="$STAGE"
  [ -f "$STAGE/wearsic-server/bin/wearsic-server" ] && SRC="$STAGE/wearsic-server"
  if [ ! -x "$SRC/bin/wearsic-server" ]; then
    log "ERROR: staged update is incomplete (no bin/wearsic-server) — discarding"
    rm -rf "$STAGE" "$UPDATE_JSON"
    return 0
  fi

  log "applying staged engine update: v${OLD_VER:-?} -> v${NEW_VER:-?}"
  APP_DIR="$(dirname -- "$APP")"          # .../wearsic-server/bin
  PKG_DIR="$(dirname -- "$APP_DIR")"      # .../wearsic-server

  if mv "$PKG_DIR/bin" "$PKG_DIR/bin.bak" && mv "$PKG_DIR/lib" "$PKG_DIR/lib.bak"; then
    if cp -r "$SRC/bin" "$PKG_DIR/bin" && cp -r "$SRC/lib" "$PKG_DIR/lib"; then
      chmod +x "$PKG_DIR/bin/wearsic-server" 2>/dev/null
      APP="$PKG_DIR/bin/wearsic-server"
      rm -rf "$STAGE" "$UPDATE_JSON"
      log "engine updated to v${NEW_VER:-?} (old build kept as bin.bak/lib.bak)"
    else
      log "ERROR: copy failed — restoring previous build"
      rm -rf "$PKG_DIR/bin" "$PKG_DIR/lib"
      mv "$PKG_DIR/bin.bak" "$PKG_DIR/bin"
      mv "$PKG_DIR/lib.bak" "$PKG_DIR/lib"
      rm -rf "$STAGE" "$UPDATE_JSON"
    fi
  else
    log "ERROR: could not back up current build — skipping update"
  fi
}

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

# --- Restart loop with exponential backoff ---------------------------------
# crash -> restart after 3s -> next crash after 6s -> 12s ... capped at 60s.
# A permanently broken server stays visible (repeated ERROR logs every ~60s)
# instead of burning battery on a tight restart loop.
# A staged engine update is applied at the top of every cycle: the server
# exits cleanly after staging one, so the supervisor swaps bin/+lib/ and
# boots the new engine automatically.
RESTART_DELAY=3
MAX_RESTART_DELAY=60

while true; do
  rotate_log
  apply_staged_update
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
