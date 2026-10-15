#!/usr/bin/env bash
# Multiplayer variant of tools/autotest.sh: a dedicated server (Server.jar, its own JVM) runs the scripted boss fights
# in walled mode, and a separate client process connects to it over the network.
#
#   tools/autotest-mp.sh [boss1,boss2,...] [secondsPerBoss] [wallObjectID] [port] [key=value ...]
#
# key=value pairs go into the server's settings file only; the client keeps the defaults, so a run with e.g.
# blockProjectiles=false checks that the server's settings reach the client (its wall hits must drop to 0 as well).
#
# Each side writes its own audit: build/autotest-mp/server/data and build/autotest-mp/client/data
# (bossphysics-audit.csv, and bossphysics-summary.txt rewritten every 5 s). The client is stopped once the server
# has finished and exited.
set -euo pipefail
source "$(dirname "$0")/autotest-common.sh"

BOSSES="${1:-evilsprotector,crystaldragon}"
SECONDS_PER_BOSS="${2:-45}"
WALL="${3:-stonewall}"
PORT="${4:-14179}"
shift $(( $# < 4 ? $# : 4 ))

OUT="$ROOT/build/autotest-mp"
SERVER_DATA="$OUT/server/data"
CLIENT_DATA="$OUT/client/data"

build_mod
rm -rf "$OUT"
write_modlist "$SERVER_DATA" server
write_modlist "$CLIENT_DATA"
write_mod_config "$SERVER_DATA" "" "$@"
write_client_settings "$CLIENT_DATA"
prepare_client_run_dir "$OUT/client/run"

# Server.jar expects to run from the game directory. Its console reads stdin, so give it a pipe that stays open.
(
    cd "$GAME_DIR"
    sleep 100000 | "$JAVA" -Xmx4G \
        -Dbossphysics.autotest="$BOSSES" -Dbossphysics.autotest.seconds="$SECONDS_PER_BOSS" \
        -Dbossphysics.autotest.mode=walled -Dbossphysics.autotest.wall="$WALL" \
        -jar Server.jar -nogui -datadir "$SERVER_DATA" -world bptest -port "$PORT" -mod "$ROOT/build/jar" \
        > "$OUT/server.log" 2>&1
) &
SERVER_PID=$!

for _ in $(seq 1 120); do
    grep -aq "Started server" "$OUT/server.log" 2>/dev/null && break
    sleep 1
done
if ! grep -aq "Started server" "$OUT/server.log" 2>/dev/null; then
    echo "server did not start; see $OUT/server.log"
    kill "$SERVER_PID" 2>/dev/null || true
    exit 1
fi

# The autotest property makes the client create its character and autosave its own audit.
(
    cd "$OUT/client/run"
    "$JAVA" -Xmx4G -Dbossphysics.autotest="$BOSSES" \
        -cp "$GAME_DIR/Necesse.jar" StartSteamClient \
        -datadir "$CLIENT_DATA" -connect "127.0.0.1:$PORT" -character BossTester -mod "$ROOT/build/jar" \
        > "$OUT/client.log" 2>&1
) &

# The server exits by itself once the scripted run is done.
while ! grep -aq "\[BossPhysics\] autotest DONE" "$OUT/server.log" 2>/dev/null; do
    sleep 2
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then break; fi
done
sleep 8   # let the client's autosave catch up
powershell -NoProfile -Command \
    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*autotest-mp*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" \
    || true
# Git Bash on Windows has no pkill: the stdin feeder would keep this script's output open.
powershell -NoProfile -Command \
    "Get-CimInstance Win32_Process -Filter \"Name='sleep.exe'\" | Where-Object { \$_.CommandLine -like '*100000*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" \
    || true

echo "== server"
print_audit_lines "$OUT/server.log" | grep -a "autotest \(started\|built\|hits\|walldamage \|DONE\)" || true
echo "== client"
cat "$CLIENT_DATA/bossphysics-summary.txt" 2>/dev/null | grep -a "^hits\|^walldamage" || echo "(no client summary)"
grep -a "BossPhysics\] could not" "$OUT/client.log" "$OUT/server.log" || true
