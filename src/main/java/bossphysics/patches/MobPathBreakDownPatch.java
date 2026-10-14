package bossphysics.patches;

import bossphysics.debug.FightStats;
import bossphysics.rules.SummonRules;
import java.util.function.Consumer;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;

/** Stops a boss summon from breaking what its boss's tier or the settlement rule forbids ({@link SummonRules}). */
@ModMethodPatch(target = Mob.class, name = "pathBreakDown", arguments = {LevelObject.class, boolean.class, Consumer.class})
public class MobPathBreakDownPatch {

    /** Returning true skips the original, which then returns false: nothing was broken. */
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo) {
        return !SummonRules.mayBreak(mob, lo);
    }

    /** {@code true} from the original means a hit landed (it returns false while on cooldown). */
    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo, @Advice.Enter boolean refused,
                       @Advice.Return boolean hit) {
        if (hit) SummonRules.onBreakDownHit(mob, lo);
        FightStats.onBreakDown(mob, lo, refused, hit);
    }
}
