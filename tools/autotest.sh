#!/usr/bin/env bash
# Runs the scripted boss audit in a throwaway singleplayer client: fresh world, fresh character, only this mod enabled.
#
#   tools/autotest.sh [boss1,boss2,...] [secondsPerBoss] [open|walled] [on|off] [wallObjectID] [protect]
#
# open:   the player moves away halfway through each fight (M0 owner-resolution audit)
# walled: the player stays in a closed wall box; the log summary counts wall vs player hits (M1) and wall damage (M2)
# on|off: whether the projectile rules are applied, for A/B comparisons
# wallObjectID: the walled-mode wall, default stonewall (tier 0); e.g. dungeonwall (1), deepstonewall (2)
# protect: mark the box's walls as protected tiles, like boss arenas
#
# Needs Steam running. Output goes to build/autotest/: client.log and data/bossphysics-audit.csv.
# The user's own saves, settings and mod list are never touched (everything lives under -datadir).
# For a dedicated server plus a connecting client, see tools/autotest-mp.sh.
set -euo pipefail
source "$(dirname "$0")/autotest-common.sh"

BOSSES="${1:-}"
SECONDS_PER_BOSS="${2:-40}"
MODE="${3:-open}"
RULES="${4:-on}"
WALL="${5:-stonewall}"
PROTECT=false
[ "${6:-}" = "protect" ] && PROTECT=true

OUT="$ROOT/build/autotest"
DATA="$OUT/data"

build_mod
rm -rf "$OUT"
write_modlist "$DATA"
write_client_settings "$DATA"
prepare_client_run_dir "$OUT/run"

cd "$OUT/run"
"$JAVA" -Xmx4G \
    -Dbossphysics.autotest="$BOSSES" -Dbossphysics.autotest.seconds="$SECONDS_PER_BOSS" \
    -Dbossphysics.autotest.mode="$MODE" -Dbossphysics.rules="$RULES" -Dbossphysics.autotest.wall="$WALL" \
    -Dbossphysics.autotest.protectWalls="$PROTECT" \
    -cp "$GAME_DIR/Necesse.jar" StartSteamClient \
    -datadir "$DATA" -load bptest -character BossTester -mod "$ROOT/build/jar" \
    > "$OUT/client.log" 2>&1 || true

echo "client exited; audit: $DATA/bossphysics-audit.csv"
print_audit_lines "$OUT/client.log"
