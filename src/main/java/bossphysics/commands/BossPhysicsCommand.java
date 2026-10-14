package bossphysics.commands;

import bossphysics.debug.AttackAudit;
import bossphysics.debug.AutoTest;
import bossphysics.debug.BossSpawner;
import java.io.IOException;
import java.util.Arrays;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.PresetStringParameterHandler;
import necesse.engine.commands.parameterHandlers.StringParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;

/**
 * Debug tools:
 * <ul>
 * <li>{@code /bp debug <on|off|dump|clear>}: the attack audit used to build the projectile whitelist</li>
 * <li>{@code /bp spawnboss <mobid> [distance]}: spawns a boss near you (default 960 pixels, like summon items)</li>
 * <li>{@code /bp autotest <start|walled|stop> [boss1,boss2,...]}: scripted boss runs feeding the audit; walled
 * mode puts you in a stone-wall box</li>
 * </ul>
 */
public class BossPhysicsCommand extends ModularChatCommand {

    public BossPhysicsCommand() {
        super("bp", "Boss Physics debug tools: debug <on|off|dump|clear> | spawnboss <mobid> [distance] | autotest <start|walled|stop> [bosses]",
            PermissionLevel.ADMIN, false,
            new CmdParameter("subcommand", new PresetStringParameterHandler("debug", "spawnboss", "autotest")),
            new CmdParameter("arg", new StringParameterHandler(null, "on", "off", "dump", "clear", "start", "walled", "stop",
                "evilsprotector", "queenspider", "swampguardian", "crystaldragon"), true),
            new CmdParameter("extra", new StringParameterHandler(), true));
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        String sub = (String) args[0];
        String arg = (String) args[1];
        String extra = (String) args[2];
        switch (sub) {
            case "debug":
                runDebug(arg, logs);
                break;
            case "spawnboss":
                runSpawnBoss(serverClient, arg, extra, logs);
                break;
            case "autotest":
                if ("start".equals(arg) || "walled".equals(arg)) {
                    logs.add(AutoTest.start(extra == null ? AutoTest.DEFAULT_BOSSES : Arrays.asList(extra.split(",")), false,
                        "walled".equals(arg)));
                } else if ("stop".equals(arg)) {
                    logs.add(AutoTest.stop());
                } else {
                    logs.add("Usage: /bp autotest <start|walled|stop> [boss1,boss2,...]");
                }
                break;
            default:
                logs.add("Unknown subcommand: " + sub);
        }
    }

    private static void runDebug(String action, CommandLog logs) {
        if (action == null) action = "";
        switch (action) {
            case "on":
                AttackAudit.setEnabled(true);
                logs.add("Attack audit on (" + AttackAudit.size() + " rows so far)");
                break;
            case "off":
                AttackAudit.setEnabled(false);
                logs.add("Attack audit off (" + AttackAudit.size() + " rows kept)");
                break;
            case "dump":
                try {
                    for (String line : AttackAudit.dump()) logs.add(line);
                } catch (IOException e) {
                    logs.add("Could not write audit: " + e.getMessage());
                }
                break;
            case "clear":
                AttackAudit.clear();
                logs.add("Attack audit cleared");
                break;
            default:
                logs.add("Usage: /bp debug <on|off|dump|clear>");
        }
    }

    private static void runSpawnBoss(ServerClient serverClient, String mobID, String distanceArg, CommandLog logs) {
        if (serverClient == null || serverClient.playerMob == null) {
            logs.add("Must be run by a player in the world");
            return;
        }
        if (mobID == null) {
            logs.add("Usage: /bp spawnboss <mobid> [distance]");
            return;
        }
        float distance = 960;
        if (distanceArg != null) {
            try {
                distance = Integer.parseInt(distanceArg);
            } catch (NumberFormatException e) {
                logs.add("Distance must be a whole number of pixels (32 per tile)");
                return;
            }
        }
        Mob mob = BossSpawner.spawnNear(serverClient, mobID, distance);
        logs.add(mob == null ? "Unknown mob id: " + mobID : "Spawned " + mobID + " " + (int) distance + " pixels away");
    }
}
