package bossphysics.debug;

import necesse.engine.GameLog;
import necesse.engine.Settings;
import necesse.gfx.Renderer;

/**
 * Autotest only: takes a game screenshot (the game's own F2 screenshot, saved to {@code <data dir>/screenshots/})
 * every {@code -Dbossphysics.autotest.screenshots=<seconds>} seconds, for the Workshop page. Window capture from
 * outside the game (tools/screenshot.ps1) can come back blank for the OpenGL window; this reads the frame buffer.
 */
public final class Screenshots {

    private static final int INTERVAL_SECONDS = Integer.getInteger("bossphysics.autotest.screenshots", 0);
    private static long next;
    private static boolean errorLogged;

    private Screenshots() {
    }

    public static boolean isActive() {
        return INTERVAL_SECONDS > 0;
    }

    /** On the render thread, once per frame of the in-game state. */
    public static void tick() {
        if (INTERVAL_SECONDS <= 0) return;
        // A fresh test character shows tutorial hints over everything: hide the HUD for clean shots.
        Settings.hideUI = true;
        long now = System.currentTimeMillis();
        if (next == 0) next = now + INTERVAL_SECONDS * 1000L;
        if (now < next) return;
        next = now + INTERVAL_SECONDS * 1000L;
        try {
            Renderer.takeScreenshot(null);
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                GameLog.warn.println("[BossPhysics] autotest screenshot failed: " + e);
            }
        }
    }
}
