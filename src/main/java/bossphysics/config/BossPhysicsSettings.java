package bossphysics.config;

import customsettingslib.components.CustomModSetting;
import customsettingslib.components.settings.IntSetting;
import customsettingslib.settings.CustomModSettings;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * The vanilla per-mod settings file ({@code cfg/mods/a632079.bossphysics.cfg}) plus a page under Settings > Mod
 * settings (Mod Settings Lib) for the switches and multipliers. The whitelists (PROJECTILES, EXPLOSIONS, SUMMONS,
 * BOSS_TOOL_TIERS, ...) stay in the file only. Labels are in {@code locale/*.lang} under {@code [settingsui]}.
 *
 * <p>The page's options are stored under the page ids ({@code bossphysics...}); a file from before the page existed
 * has them under their old names, which are read once and then saved under the page ids. An old key found next to its
 * page id wins (saving drops old keys, so it was written since). Multipliers show as percent.
 *
 * <p>Every page option is a lib "server setting": the page can only be changed in the main menu, because in a world
 * the server's settings are in force (sent by {@link ConfigSync}). Saving the page updates this machine's settings
 * ({@link BossPhysicsConfig#updateLocal}), never the ones received from a remote server.
 *
 * <p>Order of a save from the page: the lib copies the page's values into its setting objects, then the game saves
 * every mod's settings, which calls {@link #addSaveData}; that is where the page's values reach the config (the lib's
 * own save listeners run before the values are copied).
 */
public class BossPhysicsSettings extends CustomModSettings {

    static final String ENABLED = "bossphysicsenabled";
    static final String BLOCK = "bossphysicsblockprojectiles";
    static final String PROJECTILES_BREAK = "bossphysicsprojectilesbreakwalls";
    static final String EXPLOSIONS_BREAK = "bossphysicsexplosionsbreakwalls";
    static final String SUMMONS_BREAK = "bossphysicssummonsbreakwalls";
    static final String SETTLEMENTS = "bossphysicsdamageinsettlements";
    static final String WALL_DAMAGE = "bossphysicswalldamagepercent";
    static final String MAX_FRACTION = "bossphysicsmaxwallfractionpercent";
    static final String EXPLOSION_DAMAGE = "bossphysicsexplosiondamagepercent";
    static final String DOOR_HITS = "bossphysicssummondoorhits";
    static final String SUMMON_DAMAGE = "bossphysicssummondamagepercent";
    private static final String[] IDS = {ENABLED, BLOCK, PROJECTILES_BREAK, EXPLOSIONS_BREAK, SUMMONS_BREAK, SETTLEMENTS,
        WALL_DAMAGE, MAX_FRACTION, EXPLOSION_DAMAGE, DOOR_HITS, SUMMON_DAMAGE};
    /** The {@link BossPhysicsConfig} key of each page id, same order as {@link #IDS}. */
    private static final String[] OLD_KEYS = {"enabled", "blockProjectiles", "projectilesBreakWalls", "explosionsBreakWalls",
        "summonsBreakWalls", "damageInSettlements", "wallDamageMultiplier", "maxWallDamageFraction",
        "explosionWallDamageMultiplier", "summonDoorHits", "summonWallDamageMultiplier"};

    /** {@link BossPhysicsConfig} keys the page writes under its own ids instead. */
    private static final Set<String> PAGE_KEYS = new HashSet<>(Arrays.asList(OLD_KEYS));

    private static BossPhysicsSettings instance;

    public BossPhysicsSettings() {
        BossPhysicsConfig d = BossPhysicsConfig.defaults();
        addTextSeparator("bossphysicssection");
        addBooleanSetting(ENABLED, d.enabled);
        addParagraph("bossphysicsserverhint");
        addTextSeparator("bossphysicsattackssection");
        addBooleanSetting(BLOCK, d.blockProjectiles);
        addBooleanSetting(PROJECTILES_BREAK, d.projectilesBreakWalls);
        addBooleanSetting(EXPLOSIONS_BREAK, d.explosionsBreakWalls);
        addBooleanSetting(SUMMONS_BREAK, d.summonsBreakWalls);
        addBooleanSetting(SETTLEMENTS, d.damageInSettlements);
        addTextSeparator("bossphysicsdamagesection");
        addIntSetting(WALL_DAMAGE, percent(d.wallDamageMultiplier), 0, 1000, IntSetting.DisplayMode.INPUT_BAR);
        addIntSetting(MAX_FRACTION, percent(d.maxWallDamageFraction), 1, 100, IntSetting.DisplayMode.INPUT_BAR);
        addIntSetting(EXPLOSION_DAMAGE, percent(d.explosionWallDamageMultiplier), 0, 1000, IntSetting.DisplayMode.INPUT_BAR);
        addIntSetting(DOOR_HITS, d.summonDoorHits, 0, 100, IntSetting.DisplayMode.INPUT);
        addIntSetting(SUMMON_DAMAGE, percent(d.summonWallDamageMultiplier), 0, 1000, IntSetting.DisplayMode.INPUT_BAR);
        addParagraph("bossphysicsfilehint");
        addServerSettings(IDS);
        instance = this;
    }

    @Override
    public void addSaveData(SaveData save) {
        // Always this machine's settings: settings received from a remote server must not end up in the local file.
        pullFromPage();
        BossPhysicsConfig.local().addSaveData(save, PAGE_KEYS);
        super.addSaveData(save);
    }

    @Override
    public void applyLoadData(LoadData load) {
        super.applyLoadData(load);
        BossPhysicsConfig fromFile = BossPhysicsConfig.fromLoadData(load);
        BossPhysicsConfig.setLocal(fromFile);
        // An old key wins over the page id: saving drops old keys, so one in the file was written since (by hand,
        // by a test script, or before the page existed). Missing page ids take the file's (or default) value too.
        for (int i = 0; i < IDS.length; i++) {
            if (load.hasLoadDataByName(OLD_KEYS[i]) || !load.hasLoadDataByName(IDS[i])) push(IDS[i], fromFile);
        }
        pullFromPage();
    }

    /** After the file was re-read by a command: show its values on the page. */
    public static void pushToPage() {
        if (instance == null) return;
        BossPhysicsConfig c = BossPhysicsConfig.local();
        for (String id : IDS) instance.push(id, c);
    }

    private void pullFromPage() {
        BossPhysicsConfig local = BossPhysicsConfig.local();
        BossPhysicsConfig.Builder b = local.toBuilder();
        b.enabled = bool(ENABLED, b.enabled);
        b.blockProjectiles = bool(BLOCK, b.blockProjectiles);
        b.projectilesBreakWalls = bool(PROJECTILES_BREAK, b.projectilesBreakWalls);
        b.explosionsBreakWalls = bool(EXPLOSIONS_BREAK, b.explosionsBreakWalls);
        b.summonsBreakWalls = bool(SUMMONS_BREAK, b.summonsBreakWalls);
        b.damageInSettlements = bool(SETTLEMENTS, b.damageInSettlements);
        b.wallDamageMultiplier = multiplier(WALL_DAMAGE, b.wallDamageMultiplier);
        b.maxWallDamageFraction = multiplier(MAX_FRACTION, b.maxWallDamageFraction);
        b.explosionWallDamageMultiplier = multiplier(EXPLOSION_DAMAGE, b.explosionWallDamageMultiplier);
        b.summonDoorHits = Math.max(0, Math.min(1000, integer(DOOR_HITS, b.summonDoorHits)));
        b.summonWallDamageMultiplier = multiplier(SUMMON_DAMAGE, b.summonWallDamageMultiplier);
        BossPhysicsConfig.updateLocal(b.build());
    }

    @SuppressWarnings("unchecked")
    private void push(String id, BossPhysicsConfig c) {
        CustomModSetting<?> setting = settingsMap.get(id);
        if (setting == null) return;
        Object value;
        switch (id) {
            case ENABLED: value = c.enabled; break;
            case BLOCK: value = c.blockProjectiles; break;
            case PROJECTILES_BREAK: value = c.projectilesBreakWalls; break;
            case EXPLOSIONS_BREAK: value = c.explosionsBreakWalls; break;
            case SUMMONS_BREAK: value = c.summonsBreakWalls; break;
            case SETTLEMENTS: value = c.damageInSettlements; break;
            case WALL_DAMAGE: value = Math.min(1000, percent(c.wallDamageMultiplier)); break;
            case MAX_FRACTION: value = Math.max(1, Math.min(100, percent(c.maxWallDamageFraction))); break;
            case EXPLOSION_DAMAGE: value = Math.min(1000, percent(c.explosionWallDamageMultiplier)); break;
            case DOOR_HITS: value = Math.min(100, c.summonDoorHits); break;
            case SUMMON_DAMAGE: value = Math.min(1000, percent(c.summonWallDamageMultiplier)); break;
            default: return;
        }
        ((CustomModSetting<Object>) setting).changeValue(value);
    }

    private static int percent(float multiplier) {
        return Math.round(multiplier * 100);
    }

    /** The page's percent as a multiplier; an unchanged percent keeps the exact value (e.g. 1.234 from the file). */
    private float multiplier(String id, float current) {
        int page = integer(id, percent(current));
        return page == percent(current) ? current : page / 100.0F;
    }

    private boolean bool(String id, boolean fallback) {
        CustomModSetting<?> s = settingsMap.get(id);
        Object v = s == null ? null : s.getValue();
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private int integer(String id, int fallback) {
        CustomModSetting<?> s = settingsMap.get(id);
        Object v = s == null ? null : s.getValue();
        return v instanceof Integer ? (Integer) v : fallback;
    }
}
