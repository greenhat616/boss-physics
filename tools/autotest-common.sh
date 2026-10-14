# Shared setup for tools/autotest.sh and tools/autotest-mp.sh; source it, don't run it.

GAME_DIR="${NECESSE_GAME_DIR:-D:/Program Files (x86)/Steam/steamapps/common/Necesse}"
WORKSHOP_DIR="${NECESSE_WORKSHOP_DIR:-$(dirname "$(dirname "$GAME_DIR")")/workshop/content/1169040}"
JAVA_HOME_BUILD="${JAVA_HOME_BUILD:-/g/scoop/apps/temurin17-jdk/current}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA="$GAME_DIR/jre/bin/java"

build_mod() {
    (cd "$ROOT" && JAVA_HOME="$JAVA_HOME_BUILD" ./gradlew buildModJar -q)
}

# write_modlist <datadir>: only this mod enabled; every subscribed workshop mod listed as disabled, because
# unlisted mods would default to enabled.
write_modlist() {
    mkdir -p "$1/mods"
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
    } > "$1/mods/modlist.data"
}

# write_client_settings <datadir>: small muted window that keeps running without focus (a singleplayer world
# otherwise pauses as soon as another window is clicked, freezing every boss mid-test); every other setting falls
# back to its default.
write_client_settings() {
    mkdir -p "$1/cfg"
    cat > "$1/cfg/settings.cfg" <<'EOF'
{
	GENERAL = {
		pauseOnFocusLoss = false,
		savePerformanceOnFocusLoss = false
	},
	SOUND = {
		masterVolume = 0
	},
	GRAPHICS = {
		displayMode = Windowed,
		displaySize = [1280, 720]
	}
}
EOF
}

# prepare_client_run_dir <dir>: the Steam client needs steam_appid.txt in its working directory.
prepare_client_run_dir() {
    mkdir -p "$1"
    echo 1169040 > "$1/steam_appid.txt"
}

print_audit_lines() {
    grep -a "\[BossPhysics\]" "$1" | sed 's/\x1b\[[0-9;]*m//g' || true
}

# write_mod_config <datadir> <config file or ""> [key=value ...]: the mod's settings file for a test run. Starts from
# <config file> if given (a full cfg/mods/a632079.bossphysics.cfg), then sets each key=value inside SETTINGS. Keys left
# out keep the mod's defaults.
write_mod_config() {
    local data="$1" base="$2"
    shift 2
    local file="$data/cfg/mods/a632079.bossphysics.cfg"
    mkdir -p "$(dirname "$file")"
    if [ -z "$base" ]; then
        # No base: write just the overrides (the game fills in the rest and rewrites the file on start-up).
        {
            echo "{"
            echo "	SETTINGS = {"
            local first=1 kv
            for kv in "$@"; do
                [ $first = 1 ] || echo ","
                printf '		%s = %s' "${kv%%=*}" "${kv#*=}"
                first=0
            done
            echo
            echo "	}"
            echo "}"
        } > "$file"
        return
    fi
    cp "$base" "$file"
    local kv
    for kv in "$@"; do
        local key="${kv%%=*}"
        sed -i "/^		$key = /d" "$file"
        sed -i "0,/SETTINGS = {/s//SETTINGS = {
		$key = ${kv#*=},/" "$file"
    done
}
