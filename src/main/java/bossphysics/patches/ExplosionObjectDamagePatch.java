package bossphysics.patches;

import bossphysics.rules.ExplosionRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.GameDamage;
import net.bytebuddy.asm.Advice;

/**
 * Scales the object damage of explosions made destructive by {@link ExplosionRules}. Subclasses that override
 * {@code getTotalObjectDamage} call this one first, so the scaling applies to them too.
 */
@ModMethodPatch(target = ExplosionEvent.class, name = "getTotalObjectDamage", arguments = {float.class})
public class ExplosionObjectDamagePatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ExplosionEvent event, @Advice.Return(readOnly = false) GameDamage damage) {
        float multiplier = ExplosionRules.objectDamageMultiplier(event);
        if (multiplier != 1.0F) damage = damage.modDamage(multiplier);
    }
}
