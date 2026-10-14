package bossphysics.patches;

import bossphysics.debug.AutoTest;
import bossphysics.rules.SummonRules;
import bossphysics.rules.WallDamage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = Server.class, name = "tick", arguments = {})
public class ServerTickPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Server server) {
        WallDamage.tick();
        SummonRules.tick();
        AutoTest.tick(server);
    }
}
