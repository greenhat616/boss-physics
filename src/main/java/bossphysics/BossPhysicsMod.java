package bossphysics;

import bossphysics.commands.BossPhysicsCommand;
import bossphysics.config.BossPhysicsSettings;
import bossphysics.config.ConfigSync;
import bossphysics.debug.AttackAudit;
import bossphysics.debug.AutoTest;
import bossphysics.debug.TestCharacter;
import bossphysics.rules.SummonRules;
import necesse.engine.commands.CommandsManager;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;

/**
 * Makes boss attacks obey walls: whitelisted direct-fire projectiles are blocked by walls and damage them, and boss
 * explosions and summons can break walls. See {@code docs/ROADMAP.md}; settings live in {@code cfg/mods/a632079.bossphysics.cfg}.
 */
@ModEntry
public class BossPhysicsMod {

    public ModSettings initSettings() {
        return new BossPhysicsSettings();
    }

    public void init() {
        ConfigSync.register();
        SummonRules.registerBuff();
        CommandsManager.registerServerCommand(new BossPhysicsCommand());
    }

    public void postInit() {
        if (AutoTest.isRequestedAtLaunch()) {
            TestCharacter.ensureExists();
            AttackAudit.startAutosave();
        }
    }
}
