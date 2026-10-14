package bossphysics.rules;

import bossphysics.config.BossPhysicsConfig;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;

/** Keeps boss attacks from damaging settlements unless {@code damageInSettlements} is on. Server only. */
public final class SettlementGuard {

    private SettlementGuard() {
    }

    /** Whether damage at this tile must be skipped. */
    public static boolean protects(Level level, int tileX, int tileY) {
        if (BossPhysicsConfig.get().damageInSettlements) return false;
        SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(level.getServer());
        return settlements != null && settlements.hasSettlementAtTile(level, tileX, tileY);
    }

    /** Whether any corner or the centre of a square of tiles is protected. Settlements are tracked per region. */
    public static boolean protectsArea(Level level, int centerTileX, int centerTileY, int tileRadius) {
        if (BossPhysicsConfig.get().damageInSettlements) return false;
        for (int dx = -tileRadius; dx <= tileRadius; dx += Math.max(1, tileRadius)) {
            for (int dy = -tileRadius; dy <= tileRadius; dy += Math.max(1, tileRadius)) {
                if (protects(level, centerTileX + dx, centerTileY + dy)) return true;
            }
        }
        return false;
    }
}
