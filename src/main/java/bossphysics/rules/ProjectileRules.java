package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;

/**
 * Applies the projectile whitelist ({@code PROJECTILES} in the settings): which boss projectiles are made solid
 * (blocked by walls, unable to hit targets behind walls). Everything not listed keeps its vanilla behaviour.
 *
 * <p>Only direct-fire projectiles belong in the list. Lobbed and summoning projectiles ({@code canHitMobs = false},
 * e.g. spider spit, eggs, boulders) fly over walls, and {@code *Warning*} markers must never be blocked. See
 * {@code docs/projectile-audit.md}.
 */
public final class ProjectileRules {

    private ProjectileRules() {
    }

    /** The rule for this projectile, or null when it should keep vanilla behaviour. */
    public static ProjectileRule ruleFor(Projectile projectile) {
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (!config.enabled || !config.blockProjectiles) return null;
        ProjectileRule rule = config.projectiles.get(projectile.getStringID());
        if (rule == null) return null;
        Mob owner = projectile.getOwner();
        if (owner != null) return owner.isBoss() ? rule : null;
        // Owner known by ID but not loaded on this side: decide by type alone so both sides agree. Never observed in
        // the M0 audit; kept as a defensive branch.
        return projectile.getOwnerID() != 0 && projectile.getOwnerID() != -1 ? rule : null;
    }

    /**
     * Applies the rule once the projectile is fully initialised. Returns the new {@code canBounce} value, since that
     * field is protected and is written back by the patch.
     */
    public static boolean apply(Projectile projectile, boolean canBounce) {
        ProjectileRule rule = ruleFor(projectile);
        if (rule == null) return canBounce;
        projectile.isSolid = rule.solid;
        return canBounce && rule.allowBounce;
    }

    /**
     * The tool tier a boss breaks walls with, compared against the wall's {@code toolTier} by vanilla. A worm boss's
     * body segment (also a boss mob) uses its head's tier.
     */
    public static float toolTierOf(Mob boss) {
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (boss == null) return config.defaultBossToolTier;
        Mob master = boss.getSpawnPacketMaster();
        if (master != null && master != boss) boss = master;
        return config.toolTierFor(boss.getStringID());
    }
}
