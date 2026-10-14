package bossphysics.patches;

import bossphysics.rules.SummonRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;

/** Spots boss summons as they are created ({@link SummonRules}). */
@ModMethodPatch(target = Mob.class, name = "init", arguments = {})
public class MobInitPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob) {
        SummonRules.onMobInit(mob);
    }
}
