package bossphysics;

import bossphysics.commands.BossPhysicsCommand;
import bossphysics.debug.AttackAudit;
import bossphysics.debug.AutoTest;
import bossphysics.debug.TestCharacter;
import necesse.engine.commands.CommandsManager;
import necesse.engine.modLoader.annotations.ModEntry;

/**
 * Makes boss attacks obey walls: whitelisted direct-fire projectiles are blocked by walls and damage them, and boss
 * explosions can break walls. See {@code docs/ROADMAP.md}.
 *
 * <p>M0 only ships the audit tooling ({@code /bp debug}, {@code /bp spawnboss}, {@code /bp autotest}) used to build
 * the projectile whitelist.
 */
@ModEntry
public class BossPhysicsMod {

    public void init() {
        CommandsManager.registerServerCommand(new BossPhysicsCommand());
    }

    public void postInit() {
        if (AutoTest.isRequestedAtLaunch()) {
            TestCharacter.ensureExists();
            AttackAudit.startAutosave();
        }
    }
}
