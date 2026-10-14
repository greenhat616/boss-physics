package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import bossphysics.debug.AttackAudit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import necesse.engine.GameLog;
import necesse.entity.ObjectDamageResult;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObjectHit;

/**
 * M2: a whitelisted boss projectile that hits a wall damages it, on the server only and after a short delay. The vanilla
 * {@code doObjectDamage} handles the rest: it syncs to clients, refuses protected tiles, unbreakable objects and walls
 * above the boss's tool tier, drops the wall's items when it breaks, and fires {@code ObjectDamageEvent} so other mods
 * can veto the damage.
 */
public final class WallDamage {

    /**
     * Damage waits {@code wallDamageDelayMs}: the server simulates projectiles ahead of clients, so damaging the wall
     * at once could remove it before a client's copy of the same projectile reaches it; that copy would then fly
     * through on the client. Waiting a little lets clients see the hit first.
     */
    private static final List<Pending> PENDING = new ArrayList<>();

    private WallDamage() {
    }

    /** Called from {@code Projectile.onHit}; {@code mob} is null and {@code object} set when a wall was hit. */
    public static void onProjectileHit(Projectile projectile, Mob mob, LevelObjectHit object) {
        if (mob != null || object == null || !projectile.isServer()) return;
        try {
            damageWall(projectile, object);
        } catch (RuntimeException e) {
            GameLog.err.println("[BossPhysics] wall damage failed for " + projectile.getStringID() + ": " + e);
            e.printStackTrace();
        }
    }

    private static void damageWall(Projectile projectile, LevelObjectHit object) {
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (!config.projectilesBreakWalls) return;
        ProjectileRule rule = ProjectileRules.ruleFor(projectile);
        if (rule == null || !rule.breaksWalls) return;
        if (object.invalidPos()) {
            AttackAudit.recordWallDamageSkip(projectile, "invalidpos");
            return;
        }
        Level level = projectile.getLevel();
        int tileX = object.tileX;
        int tileY = object.tileY;
        if (SettlementGuard.protects(level, tileX, tileY)) {
            AttackAudit.recordWallDamageSkip(projectile, "settlement");
            return;
        }
        int cap = Math.max(1, Math.round(object.getObject().objectHealth * config.maxWallDamageFraction));
        float raw = Math.min(cap, projectile.getDamage().damage * config.wallDamageMultiplier);
        int damage = Math.round(raw * ProjectileRules.wallDamageMultiplierOf(projectile.getOwner()));
        if (damage <= 0) {
            AttackAudit.recordWallDamageSkip(projectile, "nodamage");
            return;
        }
        float toolTier = ProjectileRules.toolTierOf(projectile.getOwner());
        long due = System.currentTimeMillis() + config.wallDamageDelayMs;
        synchronized (PENDING) {
            PENDING.add(new Pending(projectile, level, tileX, tileY, damage, toolTier, due));
        }
    }

    /** Applies the damage that has waited long enough; called every server tick. */
    public static void tick() {
        List<Pending> due = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (PENDING) {
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.dueMillis <= now) {
                    due.add(p);
                    it.remove();
                }
            }
        }
        for (Pending p : due) {
            try {
                String objectID = p.level.getObject(p.tileX, p.tileY).getStringID();
                ObjectDamageResult result = p.level.entityManager.doObjectDamage(
                    0, p.tileX, p.tileY, p.damage, p.toolTier, p.projectile, null, true, p.tileX * 32 + 16, p.tileY * 32 + 16);
                AttackAudit.recordWallDamage(p.projectile, objectID, result);
            } catch (RuntimeException e) {
                GameLog.err.println("[BossPhysics] delayed wall damage failed: " + e);
                e.printStackTrace();
            }
        }
    }

    private static final class Pending {
        final Projectile projectile;
        final Level level;
        final int tileX, tileY, damage;
        final float toolTier;
        final long dueMillis;

        Pending(Projectile projectile, Level level, int tileX, int tileY, int damage, float toolTier, long dueMillis) {
            this.projectile = projectile;
            this.level = level;
            this.tileX = tileX;
            this.tileY = tileY;
            this.damage = damage;
            this.toolTier = toolTier;
            this.dueMillis = dueMillis;
        }
    }
}
