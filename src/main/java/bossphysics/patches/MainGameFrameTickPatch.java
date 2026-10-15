package bossphysics.patches;

import bossphysics.debug.Screenshots;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;

/** Autotest screenshots ({@link Screenshots}); does nothing unless the autotest asks for them. */
@ModMethodPatch(target = MainGame.class, name = "frameTick", arguments = {TickManager.class, GameWindow.class})
public class MainGameFrameTickPatch {

    @Advice.OnMethodExit
    static void onExit() {
        Screenshots.tick();
    }
}
