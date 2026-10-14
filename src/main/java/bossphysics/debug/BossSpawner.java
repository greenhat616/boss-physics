package bossphysics.debug;

import necesse.engine.network.packet.PacketPlayerMovement;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.MobRegistry;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;

/** Debug helpers for spawning bosses and moving the player, mirroring what the vanilla summon items and /tp do. */
public final class BossSpawner {

    private BossSpawner() {
    }

    /**
     * Spawns {@code mobID} at a random angle {@code distance} pixels from the player, like the vanilla summon items
     * do (they use 960). Returns null if the mob ID is unknown.
     */
    public static Mob spawnNear(ServerClient client, String mobID, float distance) {
        if (!MobRegistry.mobExists(mobID)) return null;
        Level level = client.getLevel();
        Mob mob = MobRegistry.getMob(mobID, level);
        float angle = GameRandom.globalRandom.nextInt(360);
        int x = client.playerMob.getX() + (int) (GameMath.cos(angle) * distance);
        int y = client.playerMob.getY() + (int) (GameMath.sin(angle) * distance);
        level.entityManager.addMob(mob, x, y);
        return mob;
    }

    /** Spawns {@code mobID} at a level position; returns null if the mob ID is unknown. */
    public static Mob spawnAt(ServerClient client, String mobID, float x, float y) {
        if (!MobRegistry.mobExists(mobID)) return null;
        Level level = client.getLevel();
        Mob mob = MobRegistry.getMob(mobID, level);
        level.entityManager.addMob(mob, x, y);
        return mob;
    }

    /** Same-level teleport, as {@code TeleportServerCommand} does it. */
    public static void teleport(ServerClient client, int x, int y) {
        client.playerMob.dx = 0.0F;
        client.playerMob.dy = 0.0F;
        client.playerMob.setPos(x, y, true);
        client.playerMob.nextMovementTeleportBuffer();
        client.getServer().network.sendToClientsWithEntity(new PacketPlayerMovement(client, true), client.playerMob);
    }
}
