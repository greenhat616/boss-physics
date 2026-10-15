package bossphysics.debug;

import bossphysics.config.BossPhysicsConfig;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import necesse.engine.GameLog;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;

/**
 * Scripted boss fights, driven from {@code Server.tick()}. Two kinds of run:
 *
 * <ul>
 * <li><b>audit</b> (M0): spawn each boss near the (invincible) player, move the player far away halfway through so the
 * boss unloads on the client and comes back (where a client may fail to resolve a projectile's owner), then remove
 * it.</li>
 * <li><b>layouts</b>: each boss fights the player once per {@link TestArena.Layout}, each fight on a fresh arena east
 * of the last one. {@link FightStats} records every fight and {@link BalanceReport} judges them at the end.</li>
 * </ul>
 *
 * <p>Launch properties: {@code -Dbossphysics.autotest=boss1,boss2} starts a run as soon as a player is in the world and
 * exits afterwards; {@code .seconds=N} is the fight length; {@code .mode=audit|walled|balance} ({@code walled} = the
 * {@code box} layout, {@code balance} = every layout); {@code .layouts=open,box,...} overrides the
 * layouts; {@code .wall=<objectid>} picks the wall (default {@code stonewall}); {@code .protectWalls=true} marks it
 * protected.
 */
public final class AutoTest {

    public static final List<String> DEFAULT_BOSSES = Arrays.asList("evilsprotector", "crystaldragon", "swampguardian", "queenspider");

    private static final String PROP_BOSSES = "bossphysics.autotest";
    private static final String PROP_SECONDS = "bossphysics.autotest.seconds";
    private static final String PROP_MODE = "bossphysics.autotest.mode";
    private static final String PROP_LAYOUTS = "bossphysics.autotest.layouts";
    private static final String PROP_WALL = "bossphysics.autotest.wall";
    private static final String PROP_PROTECT = "bossphysics.autotest.protectWalls";
    private static final long SETTLE_MS = 8000;
    // Evils Protector despawns after 100 ticks (5s) with no player within 1280 pixels.
    private static final long AWAY_MS = 4000;
    private static final long GAP_MS = 3000;
    private static final int AWAY_DISTANCE = 4000;
    private static final float SPAWN_DISTANCE = 700;
    /** Arenas are this many tiles apart, so a fight never starts among the previous fight's rubble. */
    private static final int ARENA_SPACING = 64;
    /** Mobs left within this many pixels of the arena after a fight (summons, night spawns) are removed. */
    private static final int CLEANUP_RANGE = 1600;

    private enum Phase { IDLE, WAIT_PLAYER, MOVE, SETTLE, FIGHT, AWAY, RETURNED, GAP, DONE }

    /** An open-field fight whose boss was idle, or any fight whose boss left early, is retried this many times. */
    private static final int IDLE_RETRIES = 2;

    private static final class Job {
        final String boss;
        /** Null in audit runs. */
        final TestArena.Layout layout;
        final int attempt;

        Job(String boss, TestArena.Layout layout, int attempt) {
            this.boss = boss;
            this.layout = layout;
            this.attempt = attempt;
        }
    }

    private static Phase phase = Phase.IDLE;
    private static final Deque<Job> queue = new ArrayDeque<>();
    private static final List<FightStats> results = new ArrayList<>();
    private static long fightMs;
    private static boolean exitWhenDone;
    private static long phaseStart;
    private static Job current;
    private static Mob boss;
    private static int homeX, homeY;
    private static int nextArenaTileX = Integer.MIN_VALUE;
    private static int arenaTileY;
    private static boolean launchChecked;
    private static boolean pausedWarned;

    private AutoTest() {
    }

    public static boolean isRequestedAtLaunch() {
        return System.getProperty(PROP_BOSSES) != null;
    }

    /** @param layouts null for an audit run */
    public static synchronized String start(List<String> bosses, boolean exitAfter, List<TestArena.Layout> layouts) {
        queue.clear();
        results.clear();
        for (String b : bosses) {
            if (layouts == null) queue.add(new Job(b, null, 0));
            else for (TestArena.Layout layout : layouts) queue.add(new Job(b, layout, 0));
        }
        fightMs = Long.getLong(PROP_SECONDS, 45L) * 1000L;
        exitWhenDone = exitAfter;
        nextArenaTileX = Integer.MIN_VALUE;
        AttackAudit.clear();
        AttackAudit.setEnabled(true);
        enter(Phase.WAIT_PLAYER);
        String what = bosses + (layouts == null ? " (audit)" : " x " + layouts);
        log("started: " + what + ", " + fightMs / 1000 + "s per fight"
            + (BossPhysicsConfig.get().blockProjectiles ? "" : ", projectile rules OFF"));
        return "Autotest started: " + what;
    }

    public static synchronized String stop() {
        removeBoss();
        FightStats.end();
        queue.clear();
        enter(Phase.IDLE);
        return "Autotest stopped";
    }

    /** Parses the run kind from {@code mode} and {@code layouts}; null means an audit run. */
    public static List<TestArena.Layout> parseLayouts(String mode, String layouts) {
        if (layouts != null && !layouts.isEmpty()) {
            List<TestArena.Layout> out = new ArrayList<>();
            for (String name : layouts.split(",")) {
                TestArena.Layout layout = TestArena.Layout.parse(name);
                if (layout != null) out.add(layout);
            }
            return out;
        }
        if ("walled".equals(mode)) return Collections.singletonList(TestArena.Layout.BOX);
        if ("balance".equals(mode)) return Arrays.asList(TestArena.Layout.values());
        return null;
    }

    public static synchronized void tick(Server server) {
        if (!launchChecked) {
            launchChecked = true;
            String prop = System.getProperty(PROP_BOSSES);
            if (prop != null) {
                start(prop.isEmpty() ? DEFAULT_BOSSES : Arrays.asList(prop.split(",")), true,
                    parseLayouts(System.getProperty(PROP_MODE), System.getProperty(PROP_LAYOUTS)));
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
        if (server.isPaused() != pausedWarned) {
            pausedWarned = server.isPaused();
            // The world does not tick while paused, so the fight in progress is worthless (it shows up as an idle boss).
            log(pausedWarned ? "WARNING: world paused (singleplayer pauses when the window loses focus)" : "world resumed");
        }

        switch (phase) {
            case WAIT_PLAYER:
                client.playerMob.hasInvincibility = true;
                enter(Phase.MOVE);
                break;
            case MOVE:
                if (queue.isEmpty()) {
                    finish(server);
                    break;
                }
                current = queue.poll();
                moveToArena(client, current.layout != null);
                enter(Phase.SETTLE);
                break;
            case SETTLE:
                if (elapsed < SETTLE_MS) break;
                startFight(server, client);
                break;
            case FIGHT:
                FightStats.tick();
                if (boss.removed()) {
                    log(current.boss + " removed itself (died or despawned)");
                    endFight(client, true);
                } else if (current.layout != null && elapsed >= fightMs) {
                    endFight(client, false);
                } else if (current.layout == null && elapsed >= fightMs / 2) {
                    BossSpawner.teleport(client, homeX + AWAY_DISTANCE, homeY);
                    log("player moved away from " + current.boss);
                    enter(Phase.AWAY);
                }
                break;
            case AWAY:
                if (elapsed >= AWAY_MS) {
                    BossSpawner.teleport(client, (int) boss.x + 300, (int) boss.y);
                    log("player returned to " + current.boss + (boss.removed() ? " (boss already removed)" : ""));
                    enter(Phase.RETURNED);
                }
                break;
            case RETURNED:
                if (boss.removed() || elapsed >= fightMs / 2) endFight(client, false);
                break;
            case GAP:
                if (elapsed >= GAP_MS) enter(Phase.MOVE);
                break;
            default:
                break;
        }
    }

    /** Layout fights get a fresh arena outside settlements; audit fights stay where the player is. */
    private static void moveToArena(ServerClient client, boolean arena) {
        Level level = client.getLevel();
        int tileY = client.playerMob.getTileY();
        int tileX = client.playerMob.getTileX();
        if (arena) {
            if (nextArenaTileX == Integer.MIN_VALUE) {
                nextArenaTileX = tileX;
                arenaTileY = tileY;
            }
            tileY = arenaTileY;
            // New worlds put the player in the elder's village, a settlement, where walls are protected by default.
            tileX = TestArena.findSpotOutsideSettlements(level, nextArenaTileX, tileY);
            nextArenaTileX = tileX + ARENA_SPACING;
        }
        homeX = tileX * 32 + 16;
        homeY = tileY * 32 + 16;
        BossSpawner.teleport(client, homeX, homeY);
    }

    private static void startFight(Server server, ServerClient client) {
        // Screenshots want daylight; Evils Protector still needs the night (it leaves during the day).
        if (DAYTIME && !"evilsprotector".equals(current.boss)) ensureDay(server);
        else ensureNight(server);
        BossSpawner.teleport(client, homeX, homeY);
        if (current.layout != null) {
            // Built after SETTLE so the regions around the new spot are generated and will not overwrite it.
            String wall = System.getProperty(PROP_WALL, "stonewall");
            boolean protect = Boolean.getBoolean(PROP_PROTECT);
            int tileX = homeX / 32;
            int tileY = homeY / 32;
            TestArena.Walls walls = TestArena.build(client.getLevel(), tileX, tileY, wall, current.layout, protect);
            AttackAudit.setInsideArea(TestArena.wallBounds(tileX, tileY));
            FightStats.begin(current.boss, current.layout, TestArena.wallBounds(tileX, tileY),
                current.layout == TestArena.Layout.OPEN ? null : walls);
            log("built " + current.layout.id() + " arena at tile " + tileX + "," + tileY + " with " + wall + (protect ? " (protected)" : ""));
            boss = BossSpawner.spawnAt(client, current.boss, homeX + SPAWN_DISTANCE, homeY);
        } else {
            boss = BossSpawner.spawnNear(client, current.boss, SPAWN_DISTANCE);
        }
        if (boss == null) {
            log("unknown mob id: " + current.boss);
            FightStats.end();
            enter(Phase.GAP);
        } else {
            log("spawned " + current.boss + (current.layout != null ? " for " + current.layout.id() : ""));
            enter(Phase.FIGHT);
        }
    }

    private static void endFight(ServerClient client, boolean bossLeft) {
        String positions = boss == null ? "" : String.format("(boss at %+d,%+d px from home, player at %+d,%+d)",
            (int) boss.x - homeX, (int) boss.y - homeY, client.playerMob.getX() - homeX, client.playerMob.getY() - homeY);
        removeBoss();
        removeLeftoverMobs(client.getLevel());
        FightStats stats = FightStats.end();
        if (stats != null) {
            stats.bossLeftEarly = bossLeft;
            stats.endPositions = positions;
            log(stats.describe());
            // An idle boss behind walls is a result (the walls stopped it), not a broken fight; only the open field
            // must show the boss fighting.
            boolean idleInOpen = stats.bossIdle() && current.layout == TestArena.Layout.OPEN;
            if ((idleInOpen || bossLeft) && current.layout != null && current.attempt < IDLE_RETRIES) {
                log("boss was " + (bossLeft ? "gone early" : "idle") + ", retrying " + current.boss + " " + current.layout.id()
                    + " on a new arena");
                queue.addFirst(new Job(current.boss, current.layout, current.attempt + 1));
            } else {
                results.add(stats);
            }
        }
        log("finished " + current.boss + (current.layout != null ? " " + current.layout.id() : ""));
        enter(Phase.GAP);
    }

    /** Summons and night spawns would otherwise carry over into the next fight. */
    private static void removeLeftoverMobs(Level level) {
        List<Mob> leftovers = new ArrayList<>();
        level.entityManager.mobs.stream()
            .filter(m -> !m.isPlayer && m.isHostile && m.getDistance(homeX, homeY) < CLEANUP_RANGE)
            .forEach(leftovers::add);
        for (Mob m : leftovers) m.remove();
    }

    /** {@code -Dbossphysics.autotest.daytime=true} (screenshots): fight at midday when the boss allows it. */
    private static final boolean DAYTIME = Boolean.getBoolean("bossphysics.autotest.daytime");

    private static void ensureDay(Server server) {
        if (!server.world.worldEntity.isNight()) return;
        long oldTime = server.world.worldEntity.getWorldTime();
        server.world.setMidday();
        server.world.simulateWorldTime(server.world.worldEntity.getWorldTime() - oldTime, true);
        log("time set to midday");
    }

    /** Some bosses (Evils Protector) leave during the day; jump to midnight the way {@code /time midnight} does. */
    private static void ensureNight(Server server) {
        if (server.world.worldEntity.isNight()) return;
        long oldTime = server.world.worldEntity.getWorldTime();
        server.world.setMidnight();
        server.world.simulateWorldTime(server.world.worldEntity.getWorldTime() - oldTime, true);
        log("time set to midnight");
    }

    private static void finish(Server server) {
        enter(Phase.DONE);
        try {
            for (String line : AttackAudit.dump()) log(line);
            for (String line : AttackAudit.summarizeHits()) log(line);
            for (String line : AttackAudit.summarizeWallDamage()) log(line);
            if (!results.isEmpty()) {
                boolean localClient = server.isSingleplayer() || server.isHosted();
                for (String line : BalanceReport.write(results, localClient)) log(line);
            }
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
