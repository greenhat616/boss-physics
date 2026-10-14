package bossphysics.commands;

import bossphysics.debug.AttackAudit;
import java.io.IOException;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.PresetStringParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/** {@code /bp debug <on|off|dump|clear>}: controls the attack audit used to build the projectile whitelist. */
public class BossPhysicsCommand extends ModularChatCommand {

    public BossPhysicsCommand() {
        super("bp", "Boss Physics tools: /bp debug <on|off|dump|clear>", PermissionLevel.ADMIN, false,
            new CmdParameter("subcommand", new PresetStringParameterHandler("debug")),
            new CmdParameter("action", new PresetStringParameterHandler("on", "off", "dump", "clear")));
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        String action = (String) args[1];
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
                logs.add("Unknown action: " + action);
        }
    }
}
