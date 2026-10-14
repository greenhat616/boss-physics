package bossphysics.config;

import bossphysics.rules.ProjectileRule;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * Every setting of the mod, as one immutable snapshot. {@link #get()} is the snapshot in force; it is replaced as a
 * whole when the settings file is (re)loaded or the server sends its settings to a remote client.
 *
 * <p>The file is the vanilla per-mod settings file, {@code cfg/mods/a632079.bossphysics.cfg}: the game creates it with
 * the defaults on first launch ({@link BossPhysicsSettings}), and {@code /bp reload} re-reads it.
 */
public final class BossPhysicsConfig {

    public static final String MOD_ID = "a632079.bossphysics";

    private static volatile BossPhysicsConfig local = defaults();
    private static volatile BossPhysicsConfig current = local;

    /** Master switch: off restores vanilla behaviour completely. */
    public final boolean enabled;
    /** M1: whitelisted boss projectiles are stopped by walls. Must match on server and clients (synced). */
    public final boolean blockProjectiles;
    /** M2: whitelisted boss projectiles damage the wall they hit. */
    public final boolean projectilesBreakWalls;
    /** M3: whitelisted boss explosions damage objects in range. */
    public final boolean explosionsBreakWalls;
    /** M5: boss summons break walls and doors to reach a player they cannot path to. */
    public final boolean summonsBreakWalls;
    public final boolean damageInSettlements;
    public final float wallDamageMultiplier;
    public final float explosionWallDamageMultiplier;
    public final int wallDamageDelayMs;
    public final float defaultBossToolTier;
    /** Boss mob stringID -> tool tier; walls with a higher tier cannot be damaged by that boss. */
    public final Map<String, Float> bossToolTiers;
    /** Projectile stringID -> rule. */
    public final Map<String, ProjectileRule> projectiles;
    /** Explosion level event stringIDs that break walls when a boss owns them. */
    public final Set<String> explosions;
    /** Mob stringIDs that break walls and doors when a boss summoned them. */
    public final Set<String> summons;

    private BossPhysicsConfig(Builder b) {
        enabled = b.enabled;
        blockProjectiles = b.blockProjectiles;
        projectilesBreakWalls = b.projectilesBreakWalls;
        explosionsBreakWalls = b.explosionsBreakWalls;
        summonsBreakWalls = b.summonsBreakWalls;
        damageInSettlements = b.damageInSettlements;
        wallDamageMultiplier = b.wallDamageMultiplier;
        explosionWallDamageMultiplier = b.explosionWallDamageMultiplier;
        wallDamageDelayMs = b.wallDamageDelayMs;
        defaultBossToolTier = b.defaultBossToolTier;
        bossToolTiers = Collections.unmodifiableMap(new LinkedHashMap<>(b.bossToolTiers));
        projectiles = Collections.unmodifiableMap(new LinkedHashMap<>(b.projectiles));
        explosions = Collections.unmodifiableSet(new LinkedHashSet<>(b.explosions));
        summons = Collections.unmodifiableSet(new LinkedHashSet<>(b.summons));
    }

    /** The settings in force: the local file's, or the server's while connected to a remote server. */
    public static BossPhysicsConfig get() {
        return current;
    }

    /** The settings from this machine's file, which is what the settings file is saved from. */
    public static BossPhysicsConfig local() {
        return local;
    }

    /** New settings from this machine's file; they take effect at once. */
    public static void setLocal(BossPhysicsConfig config) {
        local = config;
        current = config;
    }

    /** The remote server's settings; they take effect until this machine's file is used again. */
    public static void setFromServer(BossPhysicsConfig config) {
        current = config;
    }

    /** Back to this machine's settings, e.g. when a local world starts after playing on a remote server. */
    public static void useLocal() {
        current = local;
    }

    public static File file() {
        return new File(GlobalData.cfgPath() + "mods/" + MOD_ID + ".cfg");
    }

    /**
     * Re-reads the settings file and makes it current. Returns an error message, or null on success; on error the
     * current settings stay.
     */
    public static String reloadFromFile() {
        File file = file();
        if (!file.exists()) {
            setLocal(defaults());
            return null;
        }
        try {
            LoadData settings = new LoadData(file).getFirstLoadDataByName("SETTINGS");
            setLocal(settings != null ? fromLoadData(settings) : defaults());
            return null;
        } catch (RuntimeException e) {
            GameLog.err.println("[BossPhysics] could not read " + file + ": " + e);
            return e.toString();
        }
    }

    /** The shipped balance. Every entry here must pass the balance test (tools/balance-test.sh, docs/balance.md). */
    public static BossPhysicsConfig defaults() {
        Builder b = new Builder();
        b.bossToolTiers.put("evilsprotector", 1.0F);
        b.bossToolTiers.put("queenspider", 1.0F);
        b.bossToolTiers.put("swampguardian", 2.0F);
        b.bossToolTiers.put("crystaldragon", 6.0F);
        b.projectiles.put("evilsprotector1", ProjectileRule.BLOCKED);
        b.projectiles.put("evilsprotector2", ProjectileRule.BLOCKED);
        b.projectiles.put("crystaldragonshard", ProjectileRule.BLOCKED);
        b.explosions.add("boulderhit");
        b.summons.add("portalminion");
        b.summons.add("spiderhatchling");
        b.summons.add("crystalgolem");
        return new BossPhysicsConfig(b);
    }

    public static BossPhysicsConfig fromLoadData(LoadData data) {
        BossPhysicsConfig d = defaults();
        Builder b = new Builder();
        b.enabled = data.getBoolean("enabled", d.enabled, false);
        b.blockProjectiles = data.getBoolean("blockProjectiles", d.blockProjectiles, false);
        b.projectilesBreakWalls = data.getBoolean("projectilesBreakWalls", d.projectilesBreakWalls, false);
        b.explosionsBreakWalls = data.getBoolean("explosionsBreakWalls", d.explosionsBreakWalls, false);
        b.summonsBreakWalls = data.getBoolean("summonsBreakWalls", d.summonsBreakWalls, false);
        b.damageInSettlements = data.getBoolean("damageInSettlements", d.damageInSettlements, false);
        b.wallDamageMultiplier = data.getFloat("wallDamageMultiplier", d.wallDamageMultiplier, 0.0F, 1000.0F, false);
        b.explosionWallDamageMultiplier =
            data.getFloat("explosionWallDamageMultiplier", d.explosionWallDamageMultiplier, 0.0F, 1000.0F, false);
        b.wallDamageDelayMs = data.getInt("wallDamageDelayMs", d.wallDamageDelayMs, 0, 5000, false);
        b.defaultBossToolTier = data.getFloat("defaultBossToolTier", d.defaultBossToolTier, 0.0F, 100.0F, false);

        LoadData tiers = data.getFirstLoadDataByName("BOSS_TOOL_TIERS");
        if (tiers == null) {
            b.bossToolTiers.putAll(d.bossToolTiers);
        } else {
            for (LoadData entry : tiers.getLoadData()) {
                b.bossToolTiers.put(entry.getName(), LoadData.getFloat(entry));
            }
        }
        LoadData projectiles = data.getFirstLoadDataByName("PROJECTILES");
        if (projectiles == null) {
            b.projectiles.putAll(d.projectiles);
        } else {
            for (LoadData entry : projectiles.getLoadData()) {
                ProjectileRule rule = ProjectileRule.parse(LoadData.getUnsafeString(entry));
                if (rule == null) {
                    GameLog.warn.println("[BossPhysics] unknown projectile rule " + LoadData.getUnsafeString(entry) + " for " + entry.getName());
                } else if (rule != ProjectileRule.VANILLA) {
                    b.projectiles.put(entry.getName(), rule);
                }
            }
        }
        if (data.hasLoadDataByName("EXPLOSIONS")) {
            b.explosions.addAll(data.getStringList("EXPLOSIONS"));
        } else {
            b.explosions.addAll(d.explosions);
        }
        if (data.hasLoadDataByName("SUMMONS")) {
            b.summons.addAll(data.getStringList("SUMMONS"));
        } else {
            b.summons.addAll(d.summons);
        }
        return new BossPhysicsConfig(b);
    }

    public void addSaveData(SaveData save) {
        save.addBoolean("enabled", enabled, "Master switch; false restores vanilla behaviour");
        save.addBoolean("blockProjectiles", blockProjectiles, "Walls stop the projectiles listed in PROJECTILES");
        save.addBoolean("projectilesBreakWalls", projectilesBreakWalls, "Listed projectiles damage the wall they hit");
        save.addBoolean("explosionsBreakWalls", explosionsBreakWalls, "Explosions listed in EXPLOSIONS damage objects");
        save.addBoolean("summonsBreakWalls", summonsBreakWalls, "Boss summons break doors and walls to reach players");
        save.addBoolean("damageInSettlements", damageInSettlements, "Also damage walls inside settlements");
        save.addFloat("wallDamageMultiplier", wallDamageMultiplier, "Wall damage = projectile damage x this");
        save.addFloat("explosionWallDamageMultiplier", explosionWallDamageMultiplier, "Object damage = explosion damage x this");
        save.addInt("wallDamageDelayMs", wallDamageDelayMs, "Delay before a hit wall takes damage, so clients see the hit first");
        save.addFloat("defaultBossToolTier", defaultBossToolTier, "Tool tier of bosses not in BOSS_TOOL_TIERS");
        SaveData tiers = new SaveData("BOSS_TOOL_TIERS", "Boss mob ID = tool tier (wood/stone walls 0, dungeon 1, deep stone 2, obsidian 6)");
        for (Map.Entry<String, Float> e : bossToolTiers.entrySet()) tiers.addFloat(e.getKey(), e.getValue());
        save.addSaveData(tiers);
        SaveData projectileData = new SaveData("PROJECTILES", "Projectile ID = BLOCKED (stopped, breaks walls) | SHIELDED (stopped only) | BOUNCE (bounces, breaks walls)");
        for (Map.Entry<String, ProjectileRule> e : projectiles.entrySet()) projectileData.addUnsafeString(e.getKey(), e.getValue().name());
        save.addSaveData(projectileData);
        save.addStringList("EXPLOSIONS", new ArrayList<>(explosions), "Explosion event IDs that break objects when a boss owns them");
        save.addStringList("SUMMONS", new ArrayList<>(summons), "Mob IDs that break doors and walls when a boss summoned them");
    }

    /** The whole snapshot as a settings script, for the server-to-client sync packet. */
    public String toScript() {
        SaveData save = new SaveData("SETTINGS");
        addSaveData(save);
        return save.getScript();
    }

    public static BossPhysicsConfig fromScript(String script) {
        return fromLoadData(new LoadData(script));
    }

    public float toolTierFor(String bossID) {
        Float tier = bossToolTiers.get(bossID);
        return tier != null ? tier : defaultBossToolTier;
    }

    private static final class Builder {
        boolean enabled = true;
        boolean blockProjectiles = true;
        boolean projectilesBreakWalls = true;
        boolean explosionsBreakWalls = true;
        boolean summonsBreakWalls = true;
        boolean damageInSettlements = false;
        float wallDamageMultiplier = 1.0F;
        float explosionWallDamageMultiplier = 1.0F;
        int wallDamageDelayMs = 250;
        float defaultBossToolTier = 1.0F;
        final Map<String, Float> bossToolTiers = new LinkedHashMap<>();
        final Map<String, ProjectileRule> projectiles = new LinkedHashMap<>();
        final Set<String> explosions = new LinkedHashSet<>();
        final Set<String> summons = new LinkedHashSet<>();
    }
}
