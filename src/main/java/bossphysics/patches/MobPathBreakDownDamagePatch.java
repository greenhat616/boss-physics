package bossphysics.patches;

import bossphysics.rules.SummonRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;

/** Boss summons hit walls harder than vanilla raiders ({@code summonWallDamageMultiplier}). */
@ModMethodPatch(target = Mob.class, name = "getPathBreakDownDamage", arguments = {LevelObject.class})
public class MobPathBreakDownDamagePatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo, @Advice.Return(readOnly = false) int damage) {
        damage = SummonRules.breakDownDamage(mob, lo, damage);
    }
}
