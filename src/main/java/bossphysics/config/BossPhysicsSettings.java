package bossphysics.config;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * Hooks {@link BossPhysicsConfig} into the vanilla per-mod settings file ({@code cfg/mods/a632079.bossphysics.cfg}),
 * which the game loads at start-up and writes whenever it saves its own settings.
 */
public class BossPhysicsSettings extends ModSettings {

    @Override
    public void addSaveData(SaveData save) {
        // Always this machine's settings: settings received from a remote server must not end up in the local file.
        BossPhysicsConfig.local().addSaveData(save);
    }

    @Override
    public void applyLoadData(LoadData save) {
        BossPhysicsConfig.setLocal(BossPhysicsConfig.fromLoadData(save));
    }
}
