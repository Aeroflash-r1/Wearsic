# Wearsic Server — Complete Termux Setup Guide

Every command you need, in order, to run the Wearsic music server on a spare
Android phone using Termux — and connect your Wear OS watch to it.

---

## 0. What this is

```
[ Watch: Wearsic APK ]  ──WiFi/Internet──▶  [ Phone (Termux): wearsic-server ]  ──▶  YouTube Music
        search / stream / playlists                extracts & streams audio
```

The watch app is just a lightweight player. All the heavy work (searching
YouTube, extracting audio streams, artwork) is done by `wearsic-server` on an
old Android phone running [Termux](https://termux.dev).

The server **self-heals**: if it crashes it restarts automatically, if it hangs
it is detected via `/health` checks every 30 s and killed + restarted, and
`termux-wake-lock` stops Android from freezing it in the background.

---

## 1. Install Termux

- Install **Termux from F-Droid** (the Play Store version is outdated/broken):
  https://f-droid.org/en/packages/com.termux/
- Open Termux, then update packages:

```bash
pkg update -y && pkg upgrade -y
```

## 2. Get the server onto the phone

### Option A — download directly on the phone

```bash
pkg install -y curl
curl -L -o ~/wearsic-server-termux-FIXED.zip \
  "https://github.com/roopanganesan40-glitch/Wearos-music/releases/latest/download/wearsic-server-termux-FIXED.zip"
```

### Option B — copy from somewhere else

Download the zip on a PC/another phone, then move it to the Termux phone
(Share → "Save to storage", or USB). Note where it lands (usually
`/storage/emulated/0/Download/`). Then enable access:

```bash
pkg install -y unzip
termux-setup-storage          # tap ALLOW on the permission popup
cp ~/storage/downloads/wearsic-server-termux-FIXED.zip ~/
```

### Extract and enter the folder

```bash
cd ~ && unzip -o wearsic-server-termux-FIXED.zip
cd ~/wearsic-server
```

> If you downloaded from GitHub with Option A, also run:
> `pkg install -y unzip && cd ~ && unzip -o wearsic-server-termux-FIXED.zip && cd wearsic-server`

---

## 3. First start

```bash
chmod +x run-termux.sh        # zip tools sometimes drop execute permissions
./run-termux.sh
```

You should see:

```
[wearsic HH:MM:SS] wake lock acquired
[wearsic HH:MM:SS] auto-heal supervisor starting (health checks every 30s)
[wearsic HH:MM:SS] server started (pid XXXX, port 8080)
```

**Leave that Termux session running.** The screen can lock; just don't swipe
Termux away from recents.

### Verify it works

Open a **second** Termux session (swipe from left edge → New session) and run:

```bash
curl http://127.0.0.1:8080/health
```

Expected response:

```json
{"status":"ok","version":"1.0.0","serverName":"Wearsic Engine"}
```

Try a real search too:

```bash
curl "http://127.0.0.1:8080/api/search?q=crowded+house"
```

You should get JSON with a `"results"` array.

---

## 4. Make it private (API key) 🔐

Without a key, anyone who can reach the server URL can use it. One shared
secret protects everything.

### Step 1 — pick a secret key

Invent one, e.g. `my-secret-wearsic-2026`. Longer = better. Avoid spaces.

### Step 2 — save it in the server config

```bash
echo "WEARSIC_API_KEY=my-secret-wearsic-2026" >> ~/wearsic-server/.env
```

### Step 3 — restart the server

Stop the supervisor with `Ctrl+C` in its session (or `pkill -f wearsic-server`),
then start again:

```bash
cd ~/wearsic-server && ./run-termux.sh
```

### Step 4 — verify it is locked

```bash
# without key -> rejected (HTTP error):
curl "http://127.0.0.1:8080/api/search?q=test"

# with key -> works:
curl -H "X-Wearsic-Key: my-secret-wearsic-2026" "http://127.0.0.1:8080/api/search?q=test"
```

### Step 5 — tell the watch the key

On the watch: **Wearsic → Settings → API Key** → type/paste the *same* key.
The app now sends it (`X-Wearsic-Key` header) automatically with every request.

---

## 5. Connect the watch (choose ONE)

### A. Same WiFi network (simplest)

1. Find the server phone's IP: in Termux run
   ```bash
   ifconfig wlan0 | grep inet
   ```
   (e.g. `192.168.1.42`)
2. On the watch: **Settings → Server URL** → `http://192.168.1.42:8080`
3. Works only when both devices are on the same WiFi.

### B. Tailscale private network (works everywhere)

1. Install Tailscale on the **server phone** (Play Store) and log in
2. Install Tailscale on the **watch** (Play Store has a Wear OS version)
3. Find the phone's tailnet IP:
   ```bash
   ifconfig tun0 | grep inet     # usually 100.x.y.z
   ```
   or check https://login.tailscale.com/admin/machines
4. Watch → **Settings → Server URL** → `http://100.x.y.z:8080`
- Private, encrypted, works over any network. API key optional but recommended.

### C. Public HTTPS via Cloudflare Tunnel / Tailscale Funnel

Funnel is **not supported by the Android Tailscale app**, so use either:
- **Cloudflare Tunnel** (`cloudflared`) pointed at `http://localhost:8080`, or
- **Tailscale Funnel from a PC/Linux box**: run the server (or an ssh/socat
  relay to the phone) there, then `tailscale funnel --bg 8080`.

Then watch → **Settings → Server URL** → `https://your-name.example.ts.net`.
⚠️ A public URL **must** have an API key set (Section 4).

---

## 6. Daily-use commands cheat sheet

| Action | Command |
|---|---|
| Start server | `cd ~/wearsic-server && ./run-termux.sh` |
| Stop server | `Ctrl+C` in the server session |
| Force kill | `pkill -f wearsic-server` |
| Health check | `curl http://127.0.0.1:8080/health` |
| Live logs | `tail -f ~/wearsic-server/wearsic-server.log` |
| Your WiFi IP | `ifconfig wlan0 \| grep inet` |
| Free disk space | `df -h ~` |

Where your data lives:
- `~/wearsic-server/wearsic.db` — favorites & playlists (**back this up!**)
- `~/wearsic-server/.env` — API key & settings
- `~/wearsic-server/wearsic-server.log` — logs (auto-rotated at ~2 MB)

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `Missing wearsic-server binary` | You're not inside `~/wearsic-server`; re-extract the zip fully (`bin/` and `lib/` must sit next to `run-termux.sh`) |
| `Permission denied` on start | `chmod +x run-termux.sh bin/wearsic-server` |
| Search returns nothing / errors | YouTube changed internals → update extractor: `cd ~/wearsic-server && ./update-newpipe.sh` (the supervisor also auto-runs it after repeated failures) |
| `Sign in to confirm you're not a bot` errors | Set a YouTube cookie: see `wearsic-server/README.md` → `WEARSIC_YOUTUBE_COOKIE` env var, or POST it to `/api/config/youtube-cookie` |
| Server dies when phone sleeps | Run `termux-wake-lock` manually; disable battery optimization for Termux (Android Settings → Apps → Termux → Battery → Unrestricted) |
| Watch shows "Host not found" | Wrong IP, different WiFi networks, or server not running — redo Section 5-A step 1 |
| Watch shows HTTP 401/403 | API key missing/different between `.env` and watch Settings — retype both |
| Port already in use | Another copy is running: `pkill -f wearsic-server` then start again |

---

## 8. Keeping it alive long-term (optional)

Install **Termux:Boot** (F-Droid) to auto-start the server after reboot:

```bash
pkg install -y termux-services
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/start-wearsic.sh <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
exec ~/wearsic-server/run-termux.sh
EOF
chmod +x ~/.termux/boot/start-wearsic.sh
```

Reboot the phone once to confirm it comes up by itself.

---

*Server internals & patch history: [`server-patches/PATCHES.md`](server-patches/PATCHES.md).
Full endpoint reference: [`wearsic-server/README.md`](wearsic-server/README.md)
and [`API_CONTRACT.md`](API_CONTRACT.md).*
