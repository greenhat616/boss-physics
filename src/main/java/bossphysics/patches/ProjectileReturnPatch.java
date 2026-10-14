package bossphysics.patches;

import bossphysics.rules.ProjectileRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.projectile.Projectile;
import net.bytebuddy.asm.Advice;

/**
 * Vanilla makes a boomerang non-solid once it turns back, so a whitelisted one (Reaper's scythe, Spider Empress's web
 * ball) could fly back through a wall into a shelter. Keep it solid; {@link ProjectileOnHitPatch} ends it at the wall.
 */
@ModMethodPatch(target = Projectile.class, name = "returnToOwner", arguments = {})
public class ProjectileReturnPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Projectile projectile) {
        ProjectileRules.keepSolidOnReturn(projectile);
    }
}
