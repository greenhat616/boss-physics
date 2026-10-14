package bossphysics.rules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import necesse.entity.mobs.Mob;

/**
 * The tool tier each boss breaks walls with, compared against the wall's {@code toolTier} by
 * {@code DamagedObjectEntity.doObjectDamage}. Vanilla wall tiers: wood/stone/brick and other early walls 0, dungeon
 * 1, deepstone 2, obsidian/deep snow stone 6, ..., dawn/dusk/ancient ruin 10.
 *
 * <p>Provisional values, to be tuned once more bosses are whitelisted; M4 moves them into the config file.
 */
public final class BossTiers {

    /** Bosses without an entry break tier 0 walls (wood, stone, brick, ...) and dungeon walls. */
    public static final float DEFAULT_TIER = 1.0F;

    private static final Map<String, Float> TIERS;

    static {
        Map<String, Float> tiers = new HashMap<>();
        tiers.put("evilsprotector", 1.0F);
        tiers.put("queenspider", 1.0F);
        tiers.put("swampguardian", 2.0F);
        tiers.put("crystaldragon", 6.0F);
        TIERS = Collections.unmodifiableMap(tiers);
    }

    private BossTiers() {
    }

    public static float forBoss(Mob boss) {
        if (boss == null) return DEFAULT_TIER;
        Float tier = TIERS.get(boss.getStringID());
        return tier != null ? tier : DEFAULT_TIER;
    }
}
