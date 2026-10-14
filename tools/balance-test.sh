#!/usr/bin/env bash
# The balance test: every boss fights in the open, in a closed box, behind half cover and in a box with a door, and
# the balance report (docs/balance.md) decides whether the settings in force pass. Shipped defaults must pass.
#
#   tools/balance-test.sh [bosses] [secondsPerFight] [extra autotest.sh options...]
#
# The report is copied to build/balance/<timestamp>.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BOSSES="${1:-evilsprotector,crystaldragon,swampguardian,queenspider}"
SECONDS_PER_FIGHT="${2:-45}"
shift $(( $# < 2 ? $# : 2 ))

"$ROOT/tools/autotest.sh" --bosses "$BOSSES" --seconds "$SECONDS_PER_FIGHT" --mode balance "$@" \
    | grep -a "autotest \(fight\|balance\|    \)\|failed\|Exception" || true
mkdir -p "$ROOT/build/balance"
REPORT="$ROOT/build/balance/$(date +%Y%m%d-%H%M%S).md"
cp "$ROOT/build/autotest/data/bossphysics-balance.md" "$REPORT" 2>/dev/null && echo "report: $REPORT" || echo "no report written; see build/autotest/client.log"
