package bossphysics.patches;

import bossphysics.debug.AttackAudit;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.LevelObjectHit;
import net.bytebuddy.asm.Advice;

/** Counts what boss projectiles hit (wall, player, other mob) for the attack audit. */
@ModMethodPatch(target = Projectile.class, name = "onHit",
    arguments = {Mob.class, LevelObjectHit.class, float.class, float.class, boolean.class, ServerClient.class})
public class ProjectileOnHitPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This Projectile projectile, @Advice.Argument(0) Mob mob, @Advice.Argument(1) LevelObjectHit object) {
        AttackAudit.recordHit(projectile, mob, object);
    }
}
