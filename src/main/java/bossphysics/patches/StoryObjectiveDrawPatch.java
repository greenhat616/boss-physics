package bossphysics.patches;

import bossphysics.debug.Screenshots;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.storyObjectives.StoryObjectiveManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import net.bytebuddy.asm.Advice;

/**
 * Autotest screenshots only: the fresh test character's tutorial hint ("find a tree to chop down") is drawn over the
 * level even with the HUD hidden. While {@link Screenshots} is active this returns null (no hint, as the game itself
 * returns when there is none); otherwise it does nothing.
 */
@ModMethodPatch(target = StoryObjectiveManager.class, name = "getCurrentObjectiveLevelHudDrawOptions",
    arguments = {GameCamera.class, PlayerMob.class})
public class StoryObjectiveDrawPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter() {
        return Screenshots.isActive();
    }
}
