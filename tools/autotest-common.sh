# Shared setup for tools/autotest.sh and tools/autotest-mp.sh; source it, don't run it.

GAME_DIR="${NECESSE_GAME_DIR:-D:/Program Files (x86)/Steam/steamapps/common/Necesse}"
WORKSHOP_DIR="${NECESSE_WORKSHOP_DIR:-$(dirname "$(dirname "$GAME_DIR")")/workshop/content/1169040}"
JAVA_HOME_BUILD="${JAVA_HOME_BUILD:-/g/scoop/apps/temurin17-jdk/current}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA="$GAME_DIR/jre/bin/java"
# Required dependency, loaded from the Steam Workshop subscription (3558238376).
SETTINGS_LIB_ID="aizsave.modsettingslib"
SETTINGS_LIB_JAR="$(ls "$WORKSHOP_DIR"/3558238376/*.jar 2>/dev/null | head -1)"

build_mod() {
    (cd "$ROOT" && JAVA_HOME="$JAVA_HOME_BUILD" ./gradlew buildModJar -q)
}

# write_modlist <datadir> [server]: only this mod and Mod Settings Lib enabled; every other subscribed workshop mod
# listed as disabled, because unlisted mods would default to enabled. The list order is the load order, so the lib
# comes first. A dedicated server has no Steam Workshop: with "server" the lib is copied into <datadir>/mods/.
write_modlist() {
    mkdir -p "$1/mods"
    {
        echo "{"
        if [ "${2:-}" = server ]; then
            cp "$SETTINGS_LIB_JAR" "$1/mods/"
            echo "	{ id = $SETTINGS_LIB_ID, name = Mod Settings Lib, type = FILE_MOD, enabled = true },"
        else
            echo "	{ id = $SETTINGS_LIB_ID, name = Mod Settings Lib, type = STEAM_MOD, enabled = true },"
        fi
        echo "	{ id = a632079.bossphysics, name = Boss Physics, type = DEV_MOD, enabled = true },"
        if [ -d "$WORKSHOP_DIR" ]; then
            for jar in "$WORKSHOP_DIR"/*/*.jar; do
                id=$(unzip -p "$jar" mod.info 2>/dev/null | sed -n 's/^[[:space:]]*id = \(.*\),[[:space:]]*$/\1/p' | head -1)
                [ -n "$id" ] && [ "$id" != "a632079.bossphysics" ] && [ "$id" != "$SETTINGS_LIB_ID" ] && echo "	{ id = $id, name = $id, type = STEAM_MOD, enabled = false },"
            done
        fi
        echo "}"
    } > "$1/mods/modlist.data"
}

# write_client_settings <datadir>: small muted window that keeps running without focus (a singleplayer world
# otherwise pauses as soon as another window is clicked, freezing every boss mid-test); every other setting falls
# back to its default. AUTOTEST_LANGUAGE (e.g. en) sets the game language, for screenshots.
write_client_settings() {
    mkdir -p "$1/cfg"
    local language=""
    [ -n "${AUTOTEST_LANGUAGE:-}" ] && language=",
		language = $AUTOTEST_LANGUAGE"
    cat > "$1/cfg/settings.cfg" <<EOF
{
	GENERAL = {
		pauseOnFocusLoss = false,
		savePerformanceOnFocusLoss = false$language
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
