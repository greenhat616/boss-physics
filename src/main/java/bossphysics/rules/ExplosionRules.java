package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import bossphysics.debug.AttackAudit;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Mob;

/**
 * M3: boss explosions listed in {@code EXPLOSIONS} break objects in their blast, the way a player's bomb does.
 *
 * <p>Vanilla already does the work once {@code destroysObjects} is set: {@code ExplosionEvent.serverTick} calls
 * {@code doExplosionDamage} on every object in range whose tier the explosion's {@code toolTier} reaches, which in
 * turn goes through {@code doObjectDamage} (protected tiles refused, drops, client sync). Object damage only happens
 * in {@code serverTick}, so only the server's copy of the event is changed.
 */
public final class ExplosionRules {

    /** Events this mod made destructive, with their object damage multiplier. */
    private static final Map<ExplosionEvent, Float> ENABLED = Collections.synchronizedMap(new WeakHashMap<>());

    private ExplosionRules() {
    }

    /** Called at the end of {@code ExplosionEvent.init()}, where {@code ownerMob} has just been resolved. */
    public static void apply(ExplosionEvent event) {
        if (!event.isServer() || event.destroysObjects) return;
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (!config.enabled || !config.explosionsBreakWalls || !config.explosions.contains(event.getStringID())) return;
        Mob owner = event.ownerMob;
        if (owner == null || !owner.isBoss()) return;
        if (SettlementGuard.protectsArea(event.getLevel(), event.tileX, event.tileY, event.range / 32 + 1)) {
            AttackAudit.recordExplosionRule(event, "skipped:settlement");
            return;
        }
        event.destroysObjects = true;
        event.toolTier = Math.max(event.toolTier, ProjectileRules.toolTierOf(owner));
        ENABLED.put(event, config.explosionWallDamageMultiplier * ProjectileRules.wallDamageMultiplierOf(owner));
        AttackAudit.recordExplosionRule(event, "breaksobjects");
    }

    /** Whether this mod made the event destructive. */
    public static boolean isEnabled(ExplosionEvent event) {
        return ENABLED.containsKey(event);
    }

    /**
     * The object damage multiplier for this event if this mod enabled it: {@code explosionWallDamageMultiplier} x the
     * boss's {@code BOSS_WALL_DAMAGE}.
     */
    public static float objectDamageMultiplier(ExplosionEvent event) {
        Float multiplier = ENABLED.get(event);
        return multiplier != null ? multiplier : 1.0F;
    }
}
