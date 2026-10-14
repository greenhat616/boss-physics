package bossphysics.patches;

import bossphysics.debug.AttackAudit;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.projectile.Projectile;
import net.bytebuddy.asm.Advice;

/**
 * Hooks {@code Projectile.postInit()} rather than {@code init()}: boss projectiles set {@code isSolid = false} in
 * their own {@code init()} after calling {@code super.init()}, and {@code EntityList.add} calls {@code postInit()}
 * right after {@code init()}. No projectile subclass overrides {@code postInit()}.
 */
@ModMethodPatch(target = Projectile.class, name = "postInit", arguments = {})
public class ProjectilePostInitPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Projectile projectile) {
        AttackAudit.recordProjectile(projectile);
    }
}
