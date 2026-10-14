package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.LevelObjectHit;

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

    /** Called after {@code returnToOwner()}: vanilla made the boomerang non-solid; a whitelisted one stays solid. */
    public static void keepSolidOnReturn(Projectile projectile) {
        ProjectileRule rule = ruleFor(projectile);
        if (rule != null && rule.solid) projectile.isSolid = true;
    }

    /**
     * A whitelisted boomerang that hits a wall while returning is removed instead of turning back again (vanilla would
     * call {@code returnToOwner()} at every wall hit). Returns whether the vanilla hit handling must be skipped.
     */
    public static boolean endReturningAtWall(Projectile projectile, Mob mob, LevelObjectHit object) {
        if (mob != null || object == null || !projectile.returningToOwner() || ruleFor(projectile) == null) return false;
        projectile.remove();
        return true;
    }

    /** The boss a projectile or explosion owner counts as: a worm boss's body segment is its head. */
    public static Mob bossOf(Mob mob) {
        if (mob == null) return null;
        Mob master = mob.getSpawnPacketMaster();
        return master != null && master != mob ? master : mob;
    }

    /** {@code BOSS_WALL_DAMAGE} for the boss behind this owner, 1 if unlisted. */
    public static float wallDamageMultiplierOf(Mob owner) {
        Mob boss = bossOf(owner);
        if (boss == null) return 1.0F;
        Float multiplier = BossPhysicsConfig.get().bossWallDamage.get(boss.getStringID());
        return multiplier != null ? multiplier : 1.0F;
    }

    /**
     * The tool tier a boss breaks walls with, compared against the wall's {@code toolTier} by vanilla. A worm boss's
     * body segment (also a boss mob) uses its head's tier.
     */
    public static float toolTierOf(Mob boss) {
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (boss == null) return config.defaultBossToolTier;
        return config.toolTierFor(bossOf(boss).getStringID());
    }
}
