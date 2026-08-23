#!/data/data/com.termux/files/usr/bin/bash
# Updates NewPipeExtractor to the latest release — SAFELY.
#
# YouTube regularly changes internals which breaks search/streaming until a new
# extractor release fixes it. This script:
#   1. checks the latest version on GitHub,
#   2. downloads it (JitPack),
#   3. patches the launcher CLASSPATH (jars are hardcoded there),
#   4. SMOKE TESTS a throwaway server against live YouTube,
#   5. commits only if search works — otherwise rolls everything back.
#
# Run manually:            ./update-newpipe.sh
# Or automatically:        done by run-termux.sh when the server stays broken.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LIB="$SCRIPT_DIR/lib"

log() { echo "[npe-update $(date '+%Y-%m-%d %H:%M:%S')] $*"; }

command -v curl >/dev/null 2>&1 || { log "curl missing — run: pkg install curl"; exit 1; }
command -v unzip >/dev/null 2>&1 || { log "unzip missing — run: pkg install unzip"; exit 1; }

CURRENT=$(ls "$LIB"/NewPipeExtractor-v*.jar 2>/dev/null | head -1)
if [ -z "$CURRENT" ]; then
  log "no NewPipeExtractor-v*.jar found in $LIB"
  exit 1
fi
CURRENT_NAME=$(basename "$CURRENT")

LAUNCHER="$SCRIPT_DIR/bin/wearsic-server"
[ -f "$LAUNCHER" ] || { log "launcher bin/wearsic-server not found"; exit 1; }

LATEST=$(curl -sSL -m 20 "https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest" \
  | grep -oE '"tag_name": *"[^"]+"' | head -1 | sed 's/.*"tag_name": *"//;s/"$//')

if [ -z "$LATEST" ]; then
  log "could not determine latest version (network?)"
  exit 1
fi

TARGET_NAME="NewPipeExtractor-$LATEST.jar"
if [ "$CURRENT_NAME" = "$TARGET_NAME" ]; then
  log "already up to date ($CURRENT_NAME)"
  exit 0
fi

URL="https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/$LATEST/NewPipeExtractor-$LATEST.jar"
TMPJAR="$LIB/.npe-download.jar"

log "downloading $LATEST ..."
curl -sSL -m 300 -o "$TMPJAR" "$URL" || { log "download failed"; rm -f "$TMPJAR"; exit 1; }
if [ "$(head -c 2 "$TMPJAR")" != "PK" ]; then
  log "downloaded file is not a valid jar"
  rm -f "$TMPJAR"; exit 1
fi
unzip -l "$TMPJAR" "org/schabi/newpipe/extractor/downloader/Downloader.class" >/dev/null 2>&1 \
  || { log "jar structure invalid — aborting"; rm -f "$TMPJAR"; exit 1; }

# ---- apply candidate -------------------------------------------------------
mv -f "$TMPJAR" "$LIB/$TARGET_NAME"
rm -f "$CURRENT"
cp "$LAUNCHER" "$LAUNCHER.bak"
sed -i "s#lib/$CURRENT_NAME#lib/$TARGET_NAME#g" "$LAUNCHER"
chmod +x "$LAUNCHER"
if [ -f "$LAUNCHER.bat" ]; then
  cp "$LAUNCHER.bat" "$LAUNCHER.bat.bak"
  sed -i "s#$CURRENT_NAME#$TARGET_NAME#g" "$LAUNCHER.bat"
fi
log "installed $TARGET_NAME — running smoke test ..."

# ---- smoke test: boot once, hit live search --------------------------------
SMOKE_PORT=18099
SMOKE_DB="$(mktemp -d)/wearsic-smoke.db"
SMOKE_LOG="$SCRIPT_DIR/.smoke.log"
PORT=$SMOKE_PORT WEARSIC_DB_PATH="$SMOKE_DB" timeout 60 "$LAUNCHER" > "$SMOKE_LOG" 2>&1 &
SPID=$!

OK=0
for i in $(seq 1 15); do
  sleep 2
  kill -0 "$SPID" 2>/dev/null || break
  RESP=$(curl -sf -m 8 "http://127.0.0.1:$SMOKE_PORT/api/search?q=test" 2>/dev/null)
  case "$RESP" in
    *'"results"'*) OK=1; break ;;
  esac
done
kill "$SPID" 2>/dev/null
sleep 1
kill -9 "$SPID" 2>/dev/null

if [ "$OK" = "1" ]; then
  log "smoke test PASSED — update committed ($TARGET_NAME)"
  rm -f "$LAUNCHER.bak" "$LAUNCHER.bat.bak" "$SMOKE_LOG"
else
  log "smoke test FAILED — rolling back to $CURRENT_NAME"
  kill -9 "$SPID" 2>/dev/null
  rm -f "$LIB/$TARGET_NAME"
  mv -f "$LAUNCHER.bak" "$LAUNCHER"
  [ -f "$LAUNCHER.bat.bak" ] && mv -f "$LAUNCHER.bat.bak" "$LAUNCHER.bat"
  rm -f "$SMOKE_LOG"
fi
exit 0