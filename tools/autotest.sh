#!/usr/bin/env bash
# Runs scripted boss fights in a throwaway singleplayer client: fresh world, fresh character, only this mod enabled.
#
#   tools/autotest.sh [options]
#     --bosses a,b,...     boss mob IDs (default: evilsprotector,crystaldragon,swampguardian,queenspider)
#     --seconds N          fight length (default 40)
#     --mode M             audit   : the player moves away halfway through each fight (M0 owner-resolution audit)
#                          walled  : one fight per boss in a closed wall box (M1/M2)
#                          balance : open, box and half-cover fights per boss, judged by the balance report
#     --layouts a,b        override the layouts: open, box, halfcover, doorbox
#     --wall ID            wall object for the layouts (default stonewall; dungeonwall is tier 1, deepstonewall 2)
#     --protect            mark the walls as protected tiles, like boss arenas
#     --config FILE        start from this settings file (cfg/mods/a632079.bossphysics.cfg format)
#     (env AUTOTEST_SCREENSHOTS=N: a game screenshot every N seconds, saved to build/autotest/data/screenshots/;
#      AUTOTEST_DAYTIME=true: fight at midday except Evils Protector; AUTOTEST_LANGUAGE=en: game language)
#     --set key=value      override one setting, e.g. --set blockProjectiles=false (repeatable)
#
# Needs Steam running. Output goes to build/autotest/: client.log, data/bossphysics-audit.csv and, for layout runs,
# data/bossphysics-balance.md. The user's own saves, settings and mod list are never touched (all under -datadir).
# For a dedicated server plus a connecting client, see tools/autotest-mp.sh.
set -euo pipefail
source "$(dirname "$0")/autotest-common.sh"

BOSSES=""
SECONDS_PER_BOSS=40
MODE=audit
LAYOUTS=""
WALL=stonewall
PROTECT=false
CONFIG=""
SETS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --bosses) BOSSES="$2"; shift 2 ;;
        --seconds) SECONDS_PER_BOSS="$2"; shift 2 ;;
        --mode) MODE="$2"; shift 2 ;;
        --layouts) LAYOUTS="$2"; shift 2 ;;
        --wall) WALL="$2"; shift 2 ;;
        --protect) PROTECT=true; shift ;;
        --config) CONFIG="$2"; shift 2 ;;
        --set) SETS+=("$2"); shift 2 ;;
        *) echo "unknown option $1"; exit 2 ;;
    esac
done

OUT="${AUTOTEST_OUT:-$ROOT/build/autotest}"
DATA="$OUT/data"

build_mod
rm -rf "$OUT"
write_modlist "$DATA"
write_client_settings "$DATA"
write_mod_config "$DATA" "$CONFIG" ${SETS[@]+"${SETS[@]}"}
prepare_client_run_dir "$OUT/run"

cd "$OUT/run"
"$JAVA" -Xmx4G \
    -Dbossphysics.autotest="$BOSSES" -Dbossphysics.autotest.seconds="$SECONDS_PER_BOSS" \
    -Dbossphysics.autotest.mode="$MODE" -Dbossphysics.autotest.layouts="$LAYOUTS" \
    -Dbossphysics.autotest.wall="$WALL" -Dbossphysics.autotest.protectWalls="$PROTECT" \
    -Dbossphysics.autotest.screenshots="${AUTOTEST_SCREENSHOTS:-0}" -Dbossphysics.autotest.daytime="${AUTOTEST_DAYTIME:-false}" \
    -cp "$GAME_DIR/Necesse.jar" StartSteamClient \
    -datadir "$DATA" -load bptest -character BossTester -mod "$ROOT/build/jar" \
    > "$OUT/client.log" 2>&1 || true

echo "client exited; audit: $DATA/bossphysics-audit.csv"
print_audit_lines "$OUT/client.log" | grep -av "autotest \(hit\|projectile\|explosion\|walldamage\),"
