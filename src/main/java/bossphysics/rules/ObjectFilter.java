package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.projectile.Projectile;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

/**
 * Which objects this mod's attacks may damage ({@code BREAKABLE_OBJECTS}). Every object falls in one category: walls,
 * doors, windows, fences, trees, rocks, containers (anything with an inventory: chests, cabinets, stations with
 * storage) or furniture (everything else). All are on by default, so no object makes a free shelter; a destroyed
 * container drops its contents like a mined one.
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
    public static final String CONTAINERS = "containers";
    public static final String FURNITURE = "furniture";

    private ObjectFilter() {
    }

    /** The category of the object at a tile; layer 0 objects with an inventory are containers. */
    public static String categoryOf(Level level, int layer, int tileX, int tileY) {
        GameObject object = level.getObject(layer, tileX, tileY);
        if (object.isDoor) return DOORS;
        if (object.isWall) return WALLS;
        if (object.isWindow) return WINDOWS;
        if (object.isFence) return FENCES;
        if (object.isTree) return TREES;
        if (object.isRock) return ROCKS;
        if (layer == 0) {
            ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
            if (entity != null && entity.implementsOEInventory()) return CONTAINERS;
        }
        return FURNITURE;
    }

    /** Whether this damage comes from one of this mod's attacks and must be refused for the object at this tile. */
    public static boolean refuses(Level level, int layer, int tileX, int tileY, Attacker attacker) {
        if (!isOurs(attacker)) return false;
        return !BossPhysicsConfig.get().breakableObjects.contains(categoryOf(level, layer, tileX, tileY));
    }

    private static boolean isOurs(Attacker attacker) {
        if (attacker instanceof Projectile) return ProjectileRules.ruleFor((Projectile) attacker) != null;
        if (attacker instanceof ExplosionEvent) return ExplosionRules.isEnabled((ExplosionEvent) attacker);
        if (attacker instanceof Mob) return SummonRules.isTagged((Mob) attacker);
        return false;
    }
}
