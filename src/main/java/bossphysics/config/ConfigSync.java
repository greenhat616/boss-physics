package bossphysics.config;

import bossphysics.packets.PacketBossPhysicsConfig;
import java.util.ArrayList;
import java.util.List;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.events.ServerStartEvent;
import necesse.engine.network.server.Server;
import necesse.engine.registries.PacketRegistry;

/** Keeps every client on the server's settings; see {@link PacketBossPhysicsConfig} for why. */
public final class ConfigSync {

    private ConfigSync() {
    }

    public static void register() {
        PacketRegistry.registerPacket(PacketBossPhysicsConfig.class);
        GameEvents.addListener(ServerStartEvent.class, new GameEventListener<ServerStartEvent>() {
            @Override
            public void onEvent(ServerStartEvent event) {
                // A world hosted here uses this machine's file, even after playing on a remote server.
                BossPhysicsConfig.useLocal();
            }
        });
        GameEvents.addListener(ServerClientConnectedEvent.class, new GameEventListener<ServerClientConnectedEvent>() {
            @Override
            public void onEvent(ServerClientConnectedEvent event) {
                event.client.sendPacket(new PacketBossPhysicsConfig(BossPhysicsConfig.get()));
            }
        });
    }

    public static void sendToAll(Server server) {
        if (server != null) server.network.sendToAllClients(new PacketBossPhysicsConfig(BossPhysicsConfig.get()));
    }

    public static List<String> describe(BossPhysicsConfig c) {
        List<String> lines = new ArrayList<>();
        lines.add("enabled=" + c.enabled + " blockProjectiles=" + c.blockProjectiles + " projectilesBreakWalls="
            + c.projectilesBreakWalls + " explosionsBreakWalls=" + c.explosionsBreakWalls + " summonsBreakWalls="
            + c.summonsBreakWalls + " damageInSettlements=" + c.damageInSettlements);
        lines.add("wallDamageMultiplier=" + c.wallDamageMultiplier + " explosionWallDamageMultiplier="
            + c.explosionWallDamageMultiplier + " wallDamageDelayMs=" + c.wallDamageDelayMs + " defaultBossToolTier="
            + c.defaultBossToolTier);
        lines.add("bossToolTiers=" + c.bossToolTiers);
        lines.add("projectiles=" + c.projectiles);
        lines.add("explosions=" + c.explosions);
        lines.add("summons=" + c.summons);
        return lines;
    }
}
