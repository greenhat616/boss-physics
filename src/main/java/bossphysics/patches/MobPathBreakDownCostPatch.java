package bossphysics.patches;

import bossphysics.rules.SummonRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

/** Makes boss summons plan around walls toward a door when there is one ({@link SummonRules#breakDownCost}). */
@ModMethodPatch(target = Mob.class, name = "getPathBreakDownCost", arguments = {Level.class, int.class, int.class, GameObject.class})
public class MobPathBreakDownCostPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Argument(3) GameObject object, @Advice.Return(readOnly = false) double cost) {
        double override = SummonRules.breakDownCost(mob, object);
        if (override >= 0) cost = override;
    }
}
