package bossphysics.debug;

import bossphysics.rules.ProjectileRules;
import bossphysics.rules.SummonRules;
import java.awt.Rectangle;
import java.util.Map;
import java.util.TreeMap;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.LevelObject;
import necesse.level.maps.LevelObjectHit;

/**
 * What happened in one autotest fight, for {@link BalanceReport}: every hit the player took (by source, and whether it
 * came from outside the walls while they were still intact), wall hits on each side, and when the walls first broke.
 *
 * <p>Player hits are counted in {@code Mob.isServerHit}, so they include projectiles, explosions, melee and ability
 * events alike. The test player has debug invincibility, which only stops the fatal hit.
 */
public final class FightStats {

    private static FightStats current;

    public final String boss;
    public final TestArena.Layout layout;
    private final Rectangle box;
    private final TestArena.Walls walls;
    private final long startMs;
    public long durationMs;
    /** Ms after the start when the first wall or the door went missing; -1 if the walls held. */
    public long firstBreachMs = -1;
    /** Whether the first thing to break was the door. */
    public boolean doorBrokeFirst;
    public int wallsTotal;
    public int wallsLost;
    public int serverWallHits;
    public int clientWallHits;
    /** Source ("projectile:id", "explosion:id", "mob:id", "event:id") -> {hits, damage, hits through intact walls}. */
    public final Map<String, int[]> playerHits = new TreeMap<>();
    /** Hits through intact walls by whitelisted projectiles: must stay 0. */
    public int whitelistedLeaks;
    public boolean bossLeftEarly;
    /** Projectiles fired by the boss or its summons (server side); 0 together with no player hits = an idle boss. */
    public int bossProjectiles;
    /** Break-down hits by tagged summons: {door, wall, refused}. Vanilla limits damage per tile to about 10 a second. */
    public final int[] summonBreakDowns = new int[3];
    /** Where the boss and player ended up, for diagnosing odd fights. */
    public String endPositions = "";

    private FightStats(String boss, TestArena.Layout layout, Rectangle box, TestArena.Walls walls) {
        this.boss = boss;
        this.layout = layout;
        this.box = box;
        this.walls = walls;
        this.startMs = System.currentTimeMillis();
        this.wallsTotal = walls == null ? 0 : walls.total() + (walls.doorTile != null ? 1 : 0);
    }

    public static synchronized FightStats begin(String boss, TestArena.Layout layout, Rectangle box, TestArena.Walls walls) {
        current = new FightStats(boss, layout, box, walls);
        return current;
    }

    /** Ends the current fight and returns its stats. */
    public static synchronized FightStats end() {
        FightStats stats = current;
        current = null;
        if (stats != null) {
            stats.sample();
            stats.durationMs = System.currentTimeMillis() - stats.startMs;
        }
        return stats;
    }

    /** Counts the walls; called every server tick by {@link AutoTest}. */
    public static synchronized void tick() {
        if (current != null) current.sample();
    }

    private void sample() {
        if (walls == null) return;
        boolean doorLost = walls.doorLost();
        wallsLost = walls.wallsLost() + (doorLost ? 1 : 0);
        if (wallsLost > 0 && firstBreachMs < 0) {
            firstBreachMs = System.currentTimeMillis() - startMs;
            doorBrokeFirst = doorLost;
        }
    }

    private boolean intact() {
        return walls != null && layout != TestArena.Layout.HALFCOVER && firstBreachMs < 0;
    }

    /** Called from {@code Projectile.onHit} on both sides. */
    public static synchronized void onProjectileHit(Projectile projectile, Mob mob, LevelObjectHit object) {
        if (current == null || mob != null || object == null || ProjectileRules.ruleFor(projectile) == null) return;
        if (projectile.isServer()) current.serverWallHits++;
        else current.clientWallHits++;
    }

    /** Called once a projectile is initialised, on both sides. */
    public static synchronized void onProjectileSpawned(Projectile projectile) {
        if (current == null || !projectile.isServer()) return;
        Mob owner = projectile.getOwner();
        if (owner != null && !owner.isPlayer && owner.isHostile) current.bossProjectiles++;
    }

    /** Called after {@code Mob.pathBreakDown}; only tagged summons are counted. */
    public static synchronized void onBreakDown(Mob mob, LevelObject lo, boolean refused, boolean hit) {
        if (current == null || !SummonRules.isTagged(mob) || mob.isClient()) return;
        if (refused) current.summonBreakDowns[2]++;
        else if (hit) current.summonBreakDowns[lo.object.isDoor ? 0 : 1]++;
    }

    /** Whether the boss did nothing at all: no projectile, no hit. Such a fight says nothing about balance. */
    public boolean bossIdle() {
        return bossProjectiles == 0 && totalPlayerHits() == 0;
    }

    /** Called when {@code Mob.isServerHit} returns for a player. */
    public static synchronized void onPlayerHit(MobWasHitEvent hit, Attacker attacker) {
        if (current == null || hit == null) return;
        current.recordPlayerHit(hit.damage, attacker);
    }

    private void recordPlayerHit(int damage, Attacker attacker) {
        String source;
        boolean outside;
        boolean whitelisted = false;
        if (attacker instanceof Projectile) {
            Projectile p = (Projectile) attacker;
            source = "projectile:" + p.getStringID();
            outside = !AttackAudit.spawnedInside(p.getUniqueID());
            whitelisted = ProjectileRules.ruleFor(p) != null;
        } else if (attacker instanceof ExplosionEvent) {
            ExplosionEvent e = (ExplosionEvent) attacker;
            source = "explosion:" + e.getStringID();
            outside = !box.contains(e.x, e.y);
        } else if (attacker instanceof Mob) {
            Mob m = (Mob) attacker;
            source = "mob:" + m.getStringID();
            outside = !box.contains(m.x, m.y);
        } else if (attacker instanceof LevelEvent) {
            source = "event:" + ((LevelEvent) attacker).getStringID();
            outside = false;
        } else {
            source = attacker == null ? "unknown" : "other:" + attacker.getClass().getSimpleName();
            outside = false;
        }
        boolean leak = outside && intact();
        int[] counts = playerHits.computeIfAbsent(source, k -> new int[3]);
        counts[0]++;
        counts[1] += Math.max(0, damage);
        if (leak) counts[2]++;
        if (leak && whitelisted) whitelistedLeaks++;
    }

    public int totalPlayerHits() {
        int total = 0;
        for (int[] counts : playerHits.values()) total += counts[0];
        return total;
    }

    public int totalLeaks() {
        int total = 0;
        for (int[] counts : playerHits.values()) total += counts[2];
        return total;
    }

    /** Player hits per minute of fight. */
    public double hitsPerMinute() {
        return durationMs <= 0 ? 0 : totalPlayerHits() * 60000.0 / durationMs;
    }

    /** Every number of the fight, for the log. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("fight ").append(boss).append(' ').append(layout.id())
            .append(": seconds=").append(durationMs / 1000)
            .append(" playerHits=").append(totalPlayerHits())
            .append(" leaks=").append(totalLeaks())
            .append(" whitelistedLeaks=").append(whitelistedLeaks)
            .append(" walls=").append(wallsTotal - wallsLost).append('/').append(wallsTotal)
            .append(" firstBreachS=").append(firstBreachMs < 0 ? "-" : String.format("%.1f", firstBreachMs / 1000.0))
            .append(doorBrokeFirst ? " (door first)" : "")
            .append(" wallHits(client/server)=").append(clientWallHits).append('/').append(serverWallHits)
            .append(" bossProjectiles=").append(bossProjectiles)
            .append(" summonBreakDowns(door/wall/refused)=").append(summonBreakDowns[0]).append('/')
            .append(summonBreakDowns[1]).append('/').append(summonBreakDowns[2])
            .append(bossLeftEarly ? " BOSS LEFT EARLY" : "")
            .append(bossIdle() ? " BOSS IDLE" : "")
            .append(endPositions.isEmpty() ? "" : " " + endPositions);
        for (Map.Entry<String, int[]> e : playerHits.entrySet()) {
            int[] c = e.getValue();
            sb.append("\n    ").append(e.getKey()).append(": hits=").append(c[0]).append(" damage=").append(c[1])
                .append(" throughIntactWalls=").append(c[2]);
        }
        return sb.toString();
    }
}
