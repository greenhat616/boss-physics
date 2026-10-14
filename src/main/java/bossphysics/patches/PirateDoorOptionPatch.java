package bossphysics.patches;

import bossphysics.rules.SummonRules;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.hostile.pirates.PirateMob;
import net.bytebuddy.asm.Advice;

/**
 * Pirates always path as door openers and ignore {@code CAN_BREAK_OBJECTS}, so the Pirate Captain's summoned recruits
 * and parrots get the object-breaking path option here instead.
 */
@ModMethodPatch(target = PirateMob.class, name = "getPathDoorOption", arguments = {})
public class PirateDoorOptionPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This PirateMob mob, @Advice.Return(readOnly = false) PathDoorOption option) {
        if (option != null && SummonRules.isTagged(mob)) option = mob.getLevel().regionManager.CAN_BREAK_OBJECTS_OPTIONS;
    }
}
