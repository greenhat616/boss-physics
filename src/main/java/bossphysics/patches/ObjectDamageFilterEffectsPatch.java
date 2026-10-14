package bossphysics.patches;

import bossphysics.rules.ObjectFilter;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.manager.EntityManager;
import necesse.entity.mobs.Attacker;
import net.bytebuddy.asm.Advice;

/** Same as {@link ObjectDamageFilterPatch}, for the overload with hit effects. */
@ModMethodPatch(target = EntityManager.class, name = "doObjectDamage",
    arguments = {int.class, int.class, int.class, int.class, float.class, Attacker.class, ServerClient.class, boolean.class,
        int.class, int.class})
public class ObjectDamageFilterEffectsPatch {

    /** Returning true skips the original, which then returns null: no damage done. */
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This EntityManager manager, @Advice.Argument(0) int layer, @Advice.Argument(1) int tileX,
                           @Advice.Argument(2) int tileY, @Advice.Argument(5) Attacker attacker) {
        return ObjectFilter.refuses(manager.level, layer, tileX, tileY, attacker);
    }
}
