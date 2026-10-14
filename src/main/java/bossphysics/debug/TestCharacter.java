package bossphysics.debug;

import java.io.File;
import necesse.engine.GameLog;
import necesse.engine.save.CharacterSave;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.HumanLook;

/**
 * Creates the autotest character before the main menu loads characters.
 *
 * <p>The vanilla {@code -character <name>} launch option only creates a missing character when at least one other
 * character already exists ({@code CharacterSelectForm}), so a fresh {@code -datadir} would stop at the character
 * screen. This does the same creation up front, only when an autotest run was requested.
 */
public final class TestCharacter {

    public static final String NAME = "BossTester";

    private TestCharacter() {
    }

    public static void ensureExists() {
        File[] existing = new File(CharacterSave.getCharacterSavesPath()).listFiles();
        if (existing != null && existing.length > 0) return;
        PlayerMob player = new PlayerMob(0L, null);
        player.look = new HumanLook(GameRandom.globalRandom, false);
        player.playerName = NAME;
        player.getInv().giveStarterItems();
        File file = CharacterSave.saveCharacter(CharacterSave.newCharacter(player, 0L), null, false);
        GameLog.out.println("[BossPhysics] autotest created character " + NAME + " at " + file.getName());
    }
}
