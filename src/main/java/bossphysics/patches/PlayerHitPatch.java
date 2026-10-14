package bossphysics.patches;

import bossphysics.debug.FightStats;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import net.bytebuddy.asm.Advice;

/** Autotest only: counts every hit a player takes, whatever the attack type ({@link FightStats}). */
@ModMethodPatch(target = Mob.class, name = "isServerHit",
    arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class PlayerHitPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Argument(4) Attacker attacker, @Advice.Return MobWasHitEvent hit) {
        if (mob.isPlayer) FightStats.onPlayerHit(hit, attacker);
    }
}
