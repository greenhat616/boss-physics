package bossphysics.patches;

import bossphysics.debug.AttackAudit;
import bossphysics.rules.ExplosionRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import net.bytebuddy.asm.Advice;

/** {@code ExplosionEvent.init()} resolves {@code ownerMob}, so this is the earliest point the owner is known. */
@ModMethodPatch(target = ExplosionEvent.class, name = "init", arguments = {})
public class ExplosionEventInitPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ExplosionEvent event) {
        ExplosionRules.apply(event);
        AttackAudit.recordExplosion(event);
    }
}
