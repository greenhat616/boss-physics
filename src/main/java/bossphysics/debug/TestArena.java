package bossphysics.debug;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import necesse.engine.network.server.Server;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;

/**
 * Builds the autotest layouts around a tile: a cleared grass square with a wall ring of {@link #WALL_RADIUS} around the
 * centre, where the player stands. Bosses are spawned east of the player ({@link AutoTest}), so "away from the boss"
 * is west.
 */
public final class TestArena {

    /** Half-size of the cleared square, in tiles. */
    public static final int CLEAR_RADIUS = 24;
    /** Chebyshev distance of the wall ring from the centre: 3 gives a 7x7 box with a 5x5 inside. */
    public static final int WALL_RADIUS = 3;

    public enum Layout {
        /** No walls: the baseline a box fight is compared against. */
        OPEN,
        /** A closed ring: nothing gets in until a wall breaks. */
        BOX,
        /** The ring without its west side: a boss or summon can walk around to the opening. */
        HALFCOVER,
        /** A closed ring whose west side has a door in the middle. */
        DOORBOX;

        public static Layout parse(String name) {
            for (Layout layout : values()) {
                if (layout.name().equalsIgnoreCase(name.trim())) return layout;
            }
            return null;
        }

        public String id() {
            return name().toLowerCase();
        }
    }

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
     * Clears the square and builds {@code layout}; returns the placed wall and door tiles.
     *
     * @param protectWalls marks the wall tiles protected the way boss arena presets do
     *                     ({@code regionManager.setTileProtected}), so boss attacks must not be able to damage them
     */
    public static Walls build(Level level, int centerTileX, int centerTileY, String wallID, Layout layout, boolean protectWalls) {
        Server server = level.getServer();
        int grass = TileRegistry.getTileID("grasstile");
        int wall = ObjectRegistry.getObjectID(wallID);
        int door = ObjectRegistry.getObjectID(wallID.endsWith("wall") ? wallID.substring(0, wallID.length() - 4) + "door" : wallID);
        List<Point> wallTiles = new ArrayList<>();
        Point doorTile = null;
        for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                int x = centerTileX + dx;
                int y = centerTileY + dy;
                int object = objectAt(layout, dx, dy, wall, door);
                level.setTile(x, y, grass);
                level.sendTileChangePacket(server, x, y, grass);
                level.setObject(x, y, object);
                level.sendObjectChangePacket(server, x, y, object);
                level.regionManager.setTileProtected(x, y, protectWalls && object != 0);
                if (object == wall) wallTiles.add(new Point(x, y));
                else if (object != 0) doorTile = new Point(x, y);
            }
        }
        return new Walls(level, wallTiles, doorTile);
    }

    private static int objectAt(Layout layout, int dx, int dy, int wall, int door) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) != WALL_RADIUS) return 0;
        switch (layout) {
            case BOX:
                return wall;
            case HALFCOVER:
                return dx == -WALL_RADIUS && Math.abs(dy) < WALL_RADIUS ? 0 : wall;
            case DOORBOX:
                return dx == -WALL_RADIUS && dy == 0 ? door : wall;
            default:
                return 0;
        }
    }

    /** The placed wall and door tiles, for counting how many are left. */
    public static final class Walls {
        private final Level level;
        public final List<Point> wallTiles;
        /** Null unless the layout has a door. */
        public final Point doorTile;
        private final List<Integer> originalIDs = new ArrayList<>();

        Walls(Level level, List<Point> wallTiles, Point doorTile) {
            this.level = level;
            this.wallTiles = Collections.unmodifiableList(wallTiles);
            this.doorTile = doorTile;
            for (Point p : wallTiles) originalIDs.add(level.getObjectID(p.x, p.y));
        }

        public int total() {
            return wallTiles.size();
        }

        public int wallsLost() {
            int lost = 0;
            for (int i = 0; i < wallTiles.size(); i++) {
                Point p = wallTiles.get(i);
                if (level.getObjectID(p.x, p.y) != originalIDs.get(i)) lost++;
            }
            return lost;
        }

        /** A door counts as lost once it is gone; an open door is still there. */
        public boolean doorLost() {
            if (doorTile == null) return false;
            return !level.getObject(doorTile.x, doorTile.y).getStringID().contains("door");
        }
    }
}
