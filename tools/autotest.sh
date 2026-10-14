#!/usr/bin/env bash
# Runs the scripted boss audit in a throwaway client: fresh world, fresh character, only this mod enabled.
#
#   tools/autotest.sh [boss1,boss2,...] [secondsPerBoss] [open|walled] [on|off] [wallObjectID]
#
# open:   the player moves away halfway through each fight (M0 owner-resolution audit)
# walled: the player stays in a closed stone-wall box; the log summary counts wall vs player hits (M1)
# on|off: whether the projectile rules are applied, for A/B comparisons
# wallObjectID: the walled-mode wall, default stonewall (tier 0); e.g. dungeonwall (1), deepstonewall (2)
#
# Needs Steam running. Output goes to build/autotest/: client.log and data/bossphysics-audit.csv.
# The user's own saves, settings and mod list are never touched (everything lives under -datadir).
set -euo pipefail

BOSSES="${1:-}"
SECONDS_PER_BOSS="${2:-40}"
MODE="${3:-open}"
RULES="${4:-on}"
WALL="${5:-stonewall}"
GAME_DIR="${NECESSE_GAME_DIR:-D:/Program Files (x86)/Steam/steamapps/common/Necesse}"
WORKSHOP_DIR="${NECESSE_WORKSHOP_DIR:-$(dirname "$(dirname "$GAME_DIR")")/workshop/content/1169040}"
JAVA_HOME_BUILD="${JAVA_HOME_BUILD:-/g/scoop/apps/temurin17-jdk/current}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/autotest"
DATA="$OUT/data"

(cd "$ROOT" && JAVA_HOME="$JAVA_HOME_BUILD" ./gradlew buildModJar -q)

rm -rf "$OUT"
mkdir -p "$DATA/cfg" "$DATA/mods" "$OUT/run"
echo 1169040 > "$OUT/run/steam_appid.txt"

# Only this mod: list every subscribed workshop mod as disabled. Unlisted mods would default to enabled.
{
    echo "{"
    echo "	{ id = a632079.bossphysics, name = Boss Physics, type = DEV_MOD, enabled = true },"
    if [ -d "$WORKSHOP_DIR" ]; then
        for jar in "$WORKSHOP_DIR"/*/*.jar; do
            id=$(unzip -p "$jar" mod.info 2>/dev/null | sed -n 's/^[[:space:]]*id = \(.*\),[[:space:]]*$/\1/p' | head -1)
            [ -n "$id" ] && [ "$id" != "a632079.bossphysics" ] && echo "	{ id = $id, name = $id, type = STEAM_MOD, enabled = false },"
        done
    fi
    echo "}"
} > "$DATA/mods/modlist.data"

# Small muted window; every other setting falls back to its default.
cat > "$DATA/cfg/settings.cfg" <<'EOF'
{
	SOUND = {
		masterVolume = 0
	},
	GRAPHICS = {
		displayMode = Windowed,
		displaySize = [1280, 720]
	}
}
EOF

cd "$OUT/run"
"$GAME_DIR/jre/bin/java" -Xmx4G \
    -Dbossphysics.autotest="$BOSSES" -Dbossphysics.autotest.seconds="$SECONDS_PER_BOSS" \
    -Dbossphysics.autotest.mode="$MODE" -Dbossphysics.rules="$RULES" -Dbossphysics.autotest.wall="$WALL" \
    -cp "$GAME_DIR/Necesse.jar" StartSteamClient \
    -datadir "$DATA" -load bptest -character BossTester -mod "$ROOT/build/jar" \
    > "$OUT/client.log" 2>&1 || true

echo "client exited; audit: $DATA/bossphysics-audit.csv"
grep -a "\[BossPhysics\]" "$OUT/client.log" | sed 's/\x1b\[[0-9;]*m//g' || true
