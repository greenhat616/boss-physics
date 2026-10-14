package bossphysics.debug;

import bossphysics.rules.ProjectileRules;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import necesse.engine.GameLog;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;

/**
 * Scripted boss runs for the attack audit, driven from {@code Server.tick()}.
 *
 * <p>For each boss: spawn it near the (invincible) player, let it attack, move the player far away halfway through so
 * the boss unloads on the client and comes back (the case where a client may not resolve a projectile's owner), then
 * remove it. When the last boss is done the audit is dumped and, if requested, the game exits.
 *
 * <p>In <b>walled</b> mode the player instead stands inside a closed stone-wall box ({@link TestArena}) for the whole
 * fight, and the summary reports how often each boss projectile hit a wall versus the player.
 *
 * <p>Launching with {@code -Dbossphysics.autotest=boss1,boss2} starts a run as soon as a player is in the world, and
 * exits afterwards; {@code -Dbossphysics.autotest.seconds=N} sets the fight length per boss and
 * {@code -Dbossphysics.autotest.mode=walled} selects walled mode ({@code -Dbossphysics.autotest.wall=<objectid>} picks the
 * wall, default {@code stonewall}).
 */
public final class AutoTest {

    public static final List<String> DEFAULT_BOSSES = Arrays.asList("evilsprotector", "crystaldragon", "swampguardian", "queenspider");

    private static final String PROP_BOSSES = "bossphysics.autotest";
    private static final String PROP_SECONDS = "bossphysics.autotest.seconds";
    private static final String PROP_MODE = "bossphysics.autotest.mode";
    private static final String PROP_WALL = "bossphysics.autotest.wall";
    private static final long SETTLE_MS = 8000;
    // Evils Protector despawns after 100 ticks (5s) with no player within 1280 pixels.
    private static final long AWAY_MS = 4000;
    private static final long GAP_MS = 3000;
    private static final int AWAY_DISTANCE = 4000;
    private static final float SPAWN_DISTANCE = 700;

    private enum Phase { IDLE, WAIT_PLAYER, SETTLE, FIGHT, AWAY, RETURNED, GAP, DONE }

    private static Phase phase = Phase.IDLE;
    private static final Deque<String> queue = new ArrayDeque<>();
    private static long fightMs;
    private static boolean exitWhenDone;
    private static boolean walled;
    private static boolean arenaBuilt;
    private static long phaseStart;
    private static String current;
    private static Mob boss;
    private static int homeX, homeY;
    private static boolean launchChecked;

    private AutoTest() {
    }

    public static boolean isRequestedAtLaunch() {
        return System.getProperty(PROP_BOSSES) != null;
    }

    public static synchronized String start(List<String> bosses, boolean exitAfter, boolean walledMode) {
        queue.clear();
        walled = walledMode;
        queue.addAll(bosses);
        fightMs = Long.getLong(PROP_SECONDS, 45L) * 1000L;
        exitWhenDone = exitAfter;
        AttackAudit.clear();
        AttackAudit.setEnabled(true);
        enter(Phase.WAIT_PLAYER);
        log("started: " + bosses + ", " + fightMs / 1000 + "s per boss" + (walled ? ", walled" : "")
            + (ProjectileRules.isEnabled() ? "" : ", projectile rules OFF"));
        return "Autotest started: " + bosses + (walled ? " (walled)" : "");
    }

    public static synchronized String stop() {
        removeBoss();
        queue.clear();
        enter(Phase.IDLE);
        return "Autotest stopped";
    }

    public static synchronized void tick(Server server) {
        if (!launchChecked) {
            launchChecked = true;
            String prop = System.getProperty(PROP_BOSSES);
            if (prop != null) {
                start(prop.isEmpty() ? DEFAULT_BOSSES : Arrays.asList(prop.split(",")), true, "walled".equals(System.getProperty(PROP_MODE)));
            }
        }
        if (phase == Phase.IDLE || phase == Phase.DONE) return;

        ServerClient client = server.streamClients()
            .filter(c -> c.playerMob != null && c.getLevel() != null && c.hasAnyLoadedRegionAtLevel(c.getLevel().getIdentifier()))
            .findFirst().orElse(null);
        if (client == null) {
            if (phase != Phase.WAIT_PLAYER) log("player gone, waiting");
            enter(Phase.WAIT_PLAYER);
            return;
        }
        long elapsed = System.currentTimeMillis() - phaseStart;

        switch (phase) {
            case WAIT_PLAYER:
                client.playerMob.hasInvincibility = true;
                int tileX = client.playerMob.getTileX();
                int tileY = client.playerMob.getTileY();
                if (walled) {
                    // Spawn is inside the elder's village, a settlement, where walls are protected by default.
                    tileX = TestArena.findSpotOutsideSettlements(client.getLevel(), tileX, tileY);
                    arenaBuilt = false;
                }
                homeX = tileX * 32 + 16;
                homeY = tileY * 32 + 16;
                BossSpawner.teleport(client, homeX, homeY);
                enter(Phase.SETTLE);
                break;
            case SETTLE:
            case GAP:
                if (elapsed < (phase == Phase.SETTLE ? SETTLE_MS : GAP_MS)) break;
                if (queue.isEmpty()) {
                    finish();
                    break;
                }
                if (walled && !arenaBuilt) {
                    // Built after SETTLE so the regions around the new spot are generated and will not overwrite it.
                    String wall = System.getProperty(PROP_WALL, "stonewall");
                    int arenaX = homeX / 32;
                    int arenaY = homeY / 32;
                    TestArena.build(client.getLevel(), arenaX, arenaY, wall);
                    AttackAudit.setInsideArea(TestArena.wallBounds(arenaX, arenaY));
                    arenaBuilt = true;
                    log("built walled arena at tile " + arenaX + "," + arenaY + " with " + wall);
                }
                current = queue.poll();
                ensureNight(server);
                BossSpawner.teleport(client, homeX, homeY);
                boss = BossSpawner.spawnNear(client, current, SPAWN_DISTANCE);
                if (boss == null) {
                    log("unknown mob id: " + current);
                    enter(Phase.GAP);
                } else {
                    log("spawned " + current);
                    enter(Phase.FIGHT);
                }
                break;
            case FIGHT:
                if (boss.removed()) {
                    log(current + " removed itself (died or despawned)");
                    enter(Phase.GAP);
                } else if (walled && elapsed >= fightMs) {
                    removeBoss();
                    log("finished " + current);
                    enter(Phase.GAP);
                } else if (!walled && elapsed >= fightMs / 2) {
                    BossSpawner.teleport(client, homeX + AWAY_DISTANCE, homeY);
                    log("player moved away from " + current);
                    enter(Phase.AWAY);
                }
                break;
            case AWAY:
                if (elapsed >= AWAY_MS) {
                    BossSpawner.teleport(client, (int) boss.x + 300, (int) boss.y);
                    log("player returned to " + current + (boss.removed() ? " (boss already removed)" : ""));
                    enter(Phase.RETURNED);
                }
                break;
            case RETURNED:
                if (boss.removed() || elapsed >= fightMs / 2) {
                    removeBoss();
                    log("finished " + current);
                    enter(Phase.GAP);
                }
                break;
            default:
                break;
        }
    }

    /** Some bosses (Evils Protector) leave during the day; jump to midnight the way {@code /time midnight} does. */
    private static void ensureNight(Server server) {
        if (server.world.worldEntity.isNight()) return;
        long oldTime = server.world.worldEntity.getWorldTime();
        server.world.setMidnight();
        server.world.simulateWorldTime(server.world.worldEntity.getWorldTime() - oldTime, true);
        log("time set to midnight");
    }

    private static void finish() {
        enter(Phase.DONE);
        try {
            for (String line : AttackAudit.dump()) log(line);
            for (String line : AttackAudit.summarizeHits()) log(line);
            for (String line : AttackAudit.summarizeWallDamage()) log(line);
        } catch (IOException e) {
            log("could not write audit: " + e.getMessage());
        }
        log("DONE");
        if (exitWhenDone) {
            Thread exit = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                System.exit(0);
            }, "BossPhysics-autotest-exit");
            exit.setDaemon(true);
            exit.start();
        }
    }

    private static void removeBoss() {
        if (boss != null && !boss.removed()) boss.remove();
        boss = null;
    }

    private static void enter(Phase next) {
        phase = next;
        phaseStart = System.currentTimeMillis();
    }

    private static void log(String message) {
        GameLog.out.println("[BossPhysics] autotest " + message);
    }
}
