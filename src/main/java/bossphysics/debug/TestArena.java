package bossphysics.debug;

import java.awt.Rectangle;
import necesse.engine.network.server.Server;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;

/**
 * Builds the M1 test layout around a tile: a cleared grass square, with a closed stone-wall ring around the centre.
 * A player standing in the middle can only be hit by boss projectiles that pass through (or spawn inside) the walls.
 */
public final class TestArena {

    /** Half-size of the cleared square, in tiles. */
    public static final int CLEAR_RADIUS = 24;
    /** Chebyshev distance of the wall ring from the centre: 3 gives a 7x7 box with a 5x5 inside. */
    public static final int WALL_RADIUS = 3;

    private TestArena() {
    }

    /**
     * Returns a tile X, east of {@code tileX} on the same row, whose whole cleared square lies outside every
     * settlement (settlements are tracked per region, so every corner is checked).
     */
    public static int findSpotOutsideSettlements(Level level, int tileX, int tileY) {
        SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(level.getServer());
        if (settlements == null) return tileX;
        for (int x = tileX; x < tileX + 64 * 50; x += 64) {
            boolean clear = true;
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS && clear; dx += CLEAR_RADIUS) {
                for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS && clear; dy += CLEAR_RADIUS) {
                    if (settlements.hasSettlementAtTile(level, x + dx, tileY + dy)) clear = false;
                }
            }
            if (clear) return x;
        }
        return tileX;
    }

    /** The wall ring and everything inside it, in level pixels. */
    public static Rectangle wallBounds(int centerTileX, int centerTileY) {
        int size = (WALL_RADIUS * 2 + 1) * 32;
        return new Rectangle((centerTileX - WALL_RADIUS) * 32, (centerTileY - WALL_RADIUS) * 32, size, size);
    }

    /**
     * @param protectWalls marks the wall tiles protected the way boss arena presets do
     *                     ({@code regionManager.setTileProtected}), so boss attacks must not be able to damage them
     */
    public static void build(Level level, int centerTileX, int centerTileY, String wallID, boolean protectWalls) {
        Server server = level.getServer();
        int grass = TileRegistry.getTileID("grasstile");
        int wall = ObjectRegistry.getObjectID(wallID);
        for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                int x = centerTileX + dx;
                int y = centerTileY + dy;
                int object = Math.max(Math.abs(dx), Math.abs(dy)) == WALL_RADIUS ? wall : 0;
                level.setTile(x, y, grass);
                level.sendTileChangePacket(server, x, y, grass);
                level.setObject(x, y, object);
                level.sendObjectChangePacket(server, x, y, object);
                level.regionManager.setTileProtected(x, y, protectWalls && object != 0);
            }
        }
    }
}
