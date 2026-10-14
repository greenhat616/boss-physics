package bossphysics.packets;

import bossphysics.config.BossPhysicsConfig;
import necesse.engine.GameLog;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

/**
 * Server to client: the server's settings, sent on connect and after {@code /bp reload}.
 *
 * <p>Clients simulate boss projectiles too, so which ones walls stop must be the same on both sides, or a projectile
 * the server stops at a wall would fly on through it on the client.
 */
public class PacketBossPhysicsConfig extends Packet {

    public final String script;

    public PacketBossPhysicsConfig(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.script = reader.getNextStringLong();
    }

    public PacketBossPhysicsConfig(BossPhysicsConfig config) {
        this.script = config.toScript();
        PacketWriter writer = new PacketWriter(this);
        writer.putNextStringLong(this.script);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // A host or singleplayer client shares the server's JVM, and with it the settings already in force.
        if (client.getLocalServer() != null) return;
        try {
            BossPhysicsConfig.setFromServer(BossPhysicsConfig.fromScript(this.script));
        } catch (RuntimeException e) {
            GameLog.err.println("[BossPhysics] could not apply the server's settings: " + e);
        }
    }
}
