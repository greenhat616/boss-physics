package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.gameObject.GameObject;

/**
 * Which objects this mod's attacks may damage ({@code BREAKABLE_OBJECTS}): by default barriers (walls, doors, windows,
 * fences) and natural cover (trees, rocks). Furniture, chests and crafting stations still stop projectiles but are never
 * damaged, so a boss fight near a base cannot shred its contents.
 *
 * <p>Applied in {@code EntityManager.doObjectDamage}, where every path ends up: projectile wall damage, vanilla
 * explosion damage (including containers' own {@code doExplosionDamage}) and summon break-downs.
 */
public final class ObjectFilter {

    public static final String WALLS = "walls";
    public static final String DOORS = "doors";
    public static final String WINDOWS = "windows";
    public static final String FENCES = "fences";
    public static final String TREES = "trees";
    public static final String ROCKS = "rocks";

    private ObjectFilter() {
    }

    /** The category name of an object, or null if it belongs to none (furniture, containers, stations, ...). */
    public static String categoryOf(GameObject object) {
        if (object.isDoor) return DOORS;
        if (object.isWall) return WALLS;
        if (object.isWindow) return WINDOWS;
        if (object.isFence) return FENCES;
        if (object.isTree) return TREES;
        if (object.isRock) return ROCKS;
        return null;
    }

    /** Whether this damage comes from one of this mod's attacks and must be refused for this object. */
    public static boolean refuses(GameObject object, Attacker attacker) {
        if (!isOurs(attacker)) return false;
        String category = categoryOf(object);
        return category == null || !BossPhysicsConfig.get().breakableObjects.contains(category);
    }

    private static boolean isOurs(Attacker attacker) {
        if (attacker instanceof Projectile) return ProjectileRules.ruleFor((Projectile) attacker) != null;
        if (attacker instanceof ExplosionEvent) return ExplosionRules.isEnabled((ExplosionEvent) attacker);
        if (attacker instanceof Mob) return SummonRules.isTagged((Mob) attacker);
        return false;
    }
}
