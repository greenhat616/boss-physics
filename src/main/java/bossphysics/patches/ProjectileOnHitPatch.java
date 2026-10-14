package bossphysics.patches;

import bossphysics.debug.AttackAudit;
import bossphysics.debug.FightStats;
import bossphysics.rules.WallDamage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.LevelObjectHit;
import net.bytebuddy.asm.Advice;

/**
 * Wall hits by whitelisted boss projectiles damage the wall (server only, see {@link WallDamage}); every boss
 * projectile hit is also counted for the attack audit. Runs on entry, while the hit wall is still in place.
 */
@ModMethodPatch(target = Projectile.class, name = "onHit",
    arguments = {Mob.class, LevelObjectHit.class, float.class, float.class, boolean.class, ServerClient.class})
public class ProjectileOnHitPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This Projectile projectile, @Advice.Argument(0) Mob mob, @Advice.Argument(1) LevelObjectHit object) {
        AttackAudit.recordHit(projectile, mob, object);
        FightStats.onProjectileHit(projectile, mob, object);
        WallDamage.onProjectileHit(projectile, mob, object);
    }
}
