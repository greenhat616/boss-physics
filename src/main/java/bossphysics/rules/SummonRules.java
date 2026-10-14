package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import bossphysics.debug.AttackAudit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.GameLog;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.LevelObject;

/**
 * M5: boss summons ({@code SUMMONS} in the settings) break their way to a player they cannot otherwise reach.
 *
 * <p>This reuses the vanilla raider mechanics: a mob with {@link BuffModifiers#CAN_BREAK_OBJECTS} plans paths through
 * walls and doors, each costing extra (a door 40, a wall its health x 2.5), so it walks around half cover, breaks a
 * door before a wall, and a wall only when there is no other way. On contact it hits the object for 10 damage once a
 * second ({@code Mob.pathBreakDown}).
 *
 * <p>Vanilla break-downs ignore tool tiers, so {@link #mayBreak} adds the summoning boss's tier and the settlement
 * rule. Protected tiles are refused by {@code doObjectDamage} as usual.
 */
public final class SummonRules {

    /** Summons count only if a boss is this close when they appear (natural spawns of the same mob do not). */
    private static final int BOSS_RANGE = 64 * 32;

    private static final double DOOR_COST = 20.0;
    private static final double WALL_COST_FACTOR = 4.0;

    private static Buff buff;
    private static final List<Mob> PENDING = new ArrayList<>();
    /** Tagged summons and the tool tier of the boss that summoned them. */
    private static final Map<Mob, Float> TIERS = Collections.synchronizedMap(new WeakHashMap<>());

    private SummonRules() {
    }

    public static void registerBuff() {
        buff = BuffRegistry.registerBuff("bossphysicssummon", new SummonBuff());
    }

    /** Called at the end of {@code Mob.init()}; tagging waits for the next server tick, once the mob is in the level. */
    public static void onMobInit(Mob mob) {
        if (!mob.isServer() || mob.isPlayer || mob.isBoss()) return;
        BossPhysicsConfig config = BossPhysicsConfig.get();
        if (!config.enabled || !config.summonsBreakWalls || !config.summons.contains(mob.getStringID())) return;
        synchronized (PENDING) {
            PENDING.add(mob);
        }
    }

    /** Tags the summons that appeared since the last server tick. */
    public static void tick() {
        List<Mob> mobs;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            mobs = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Mob mob : mobs) {
            try {
                tag(mob);
            } catch (RuntimeException e) {
                GameLog.err.println("[BossPhysics] could not tag summon " + mob.getStringID() + ": " + e);
            }
        }
    }

    private static void tag(Mob mob) {
        if (mob.removed() || mob.getLevel() == null) return;
        Mob boss = nearestBoss(mob);
        if (boss == null) return;
        if (SettlementGuard.protects(mob.getLevel(), mob.getTileX(), mob.getTileY())) return;
        float tier = ProjectileRules.toolTierOf(boss);
        TIERS.put(mob, tier);
        mob.buffManager.addBuff(new ActiveBuff(buff, mob, 3600.0F, null), true);
        AttackAudit.recordSummon(mob, boss, tier);
    }

    private static Mob nearestBoss(Mob mob) {
        return mob.getLevel().entityManager.mobs.stream()
            .filter(m -> m.isBoss() && !m.removed() && m.getDistance(mob) < BOSS_RANGE)
            .min((a, b) -> Float.compare(a.getDistance(mob), b.getDistance(mob)))
            .orElse(null);
    }

    /** Whether a tagged summon may break this object; mobs this mod did not tag keep vanilla behaviour. */
    public static boolean mayBreak(Mob mob, LevelObject lo) {
        Float tier = TIERS.get(mob);
        if (tier == null) return true;
        if (!BossPhysicsConfig.get().summonsBreakWalls) return false;
        if (lo.object.toolTier > tier) return false;
        return !SettlementGuard.protects(lo.level, lo.tileX, lo.tileY);
    }

    /**
     * The path cost of breaking through {@code object} for a tagged summon, or -1 for the vanilla cost. Vanilla charges
     * 40 for a door and health x 2.5 (250 for a stone wall) for a wall; walls get {@code WALL_COST_FACTOR} times that,
     * so a summon walks a long way round to a door before it starts on a wall.
     */
    public static double breakDownCost(Mob mob, GameObject object) {
        if (!TIERS.containsKey(mob)) return -1;
        if (object.isDoor) return DOOR_COST;
        return object.objectHealth * 2.5 * WALL_COST_FACTOR;
    }

    public static boolean isTagged(Mob mob) {
        return TIERS.containsKey(mob);
    }

    /** Invisible marker that gives a summon the vanilla object-breaking pathing. */
    public static class SummonBuff extends Buff {
        public SummonBuff() {
            this.shouldSave = false;
            this.isVisible = false;
            this.canCancel = false;
        }

        @Override
        public void init(ActiveBuff activeBuff, BuffEventSubscriber eventSubscriber) {
            activeBuff.setModifier(BuffModifiers.CAN_BREAK_OBJECTS, true);
        }
    }
}
