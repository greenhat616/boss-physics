package bossphysics.debug;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.entity.ObjectDamageResult;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.LevelObjectHit;

/**
 * Records every distinct kind of non-player projectile and explosion while {@code /bp debug on} is active.
 *
 * <p>Each kind is keyed by side, class, owner and the collision flags, so one attack shows up once per side. In
 * singleplayer and on a host the server and the local client share this JVM, which is what lets one audit compare
 * the two sides; a remote client's projectiles are not seen.
 */
public final class AttackAudit {

    private static final String CSV_HEADER =
        "kind,side,id,class,superclass,owner,ownerIsBoss,isSolid,canBounce,canHitMobs,canBreakObjects,destroysObjects,destroysTiles,toolTier,target,count";

    private static volatile boolean enabled;
    private static final Map<String, Entry> entries = new LinkedHashMap<>();
    /** Walled autotest box in pixels; projectiles spawned inside it cannot be blocked by its walls. */
    private static volatile Rectangle insideArea;
    private static final Set<Integer> spawnedInside = new HashSet<>();

    private AttackAudit() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static synchronized void clear() {
        entries.clear();
        spawnedInside.clear();
    }

    public static void setInsideArea(Rectangle area) {
        insideArea = area;
    }

    public static synchronized int size() {
        return entries.size();
    }

    /** Called once a projectile is fully initialised, after any subclass {@code init()} has set its flags. */
    public static void recordProjectile(Projectile p) {
        if (!enabled) return;
        Mob owner = p.getOwner();
        if (owner != null && owner.isPlayer) return;
        Rectangle area = insideArea;
        if (area != null && area.contains(p.x, p.y)) {
            synchronized (AttackAudit.class) {
                spawnedInside.add(p.getUniqueID());
            }
        }
        String row = String.join(",",
            "projectile",
            side(p.isServer(), p.isClient()),
            p.getStringID(),
            p.getClass().getSimpleName(),
            p.getClass().getSuperclass().getSimpleName(),
            describeOwner(owner, p.getOwnerID()),
            String.valueOf(owner != null && owner.isBoss()),
            String.valueOf(p.isSolid),
            String.valueOf(p.getTotalBouncing() > 0),
            String.valueOf(p.canHitMobs),
            String.valueOf(p.canBreakObjects),
            "", "", "", "");
        record(row);
    }

    /**
     * Records what a boss projectile hit: {@code wall} (any level object), {@code player}, {@code mob}, or
     * {@code none} (expired). Player hits are usually detected by the client and forwarded to the server, so both
     * sides report them.
     */
    public static void recordHit(Projectile p, Mob target, LevelObjectHit object) {
        if (!enabled) return;
        Mob owner = p.getOwner();
        if (owner == null || !owner.isBoss()) return;
        String hit = target != null ? (target.isPlayer ? "player" : "mob:" + target.getStringID()) : object != null ? "wall:" + object.getObject().getStringID() : "none";
        if (target != null && target.isPlayer) {
            synchronized (AttackAudit.class) {
                if (spawnedInside.contains(p.getUniqueID())) hit = "player:spawned-inside";
            }
        }
        String row = String.join(",",
            "hit",
            side(p.isServer(), p.isClient()),
            p.getStringID(),
            p.getClass().getSimpleName(),
            p.getClass().getSuperclass().getSimpleName(),
            describeOwner(owner, p.getOwnerID()),
            "true",
            String.valueOf(p.isSolid),
            "", "", "", "", "", "",
            hit);
        record(row);
    }

    public static void recordExplosion(ExplosionEvent e) {
        if (!enabled) return;
        Mob owner = e.ownerMob;
        if (owner != null && owner.isPlayer) return;
        String row = String.join(",",
            "explosion",
            side(e.isServer(), e.isClient()),
            e.getStringID(),
            e.getClass().getSimpleName(),
            e.getClass().getSuperclass().getSimpleName(),
            describeOwner(owner, e.owner),
            String.valueOf(owner != null && owner.isBoss()),
            "", "", "", "",
            String.valueOf(e.destroysObjects),
            String.valueOf(e.destroysTiles),
            String.valueOf(e.toolTier),
            "");
        record(row);
    }

    /** Writes the audit to {@code <appdata>/bossphysics-audit.csv} and returns the lines for the command log. */
    /**
     * Records a wall-damage attempt by a whitelisted projectile: {@code <object>:destroyed}, {@code :damaged},
     * {@code :refused} (0 damage applied: protected tile, tier too low, or vetoed by another mod) or {@code :none}
     * (no object left to damage).
     */
    public static void recordWallDamage(Projectile p, String objectID, ObjectDamageResult result) {
        if (!enabled) return;
        String outcome = result == null ? "none" : result.destroyed ? "destroyed" : result.addedDamage > 0 ? "damaged" : "refused";
        String row = String.join(",",
            "walldamage",
            side(p.isServer(), p.isClient()),
            p.getStringID(),
            p.getClass().getSimpleName(),
            p.getClass().getSuperclass().getSimpleName(),
            describeOwner(p.getOwner(), p.getOwnerID()),
            "true",
            String.valueOf(p.isSolid),
            "", "", "", "", "", "",
            objectID + ":" + outcome);
        record(row);
    }

    /** Records why a whitelisted projectile's wall hit did not even try to damage the wall. */
    public static void recordWallDamageSkip(Projectile p, String reason) {
        if (!enabled) return;
        record(String.join(",", "walldamage", side(p.isServer(), p.isClient()), p.getStringID(), p.getClass().getSimpleName(),
            p.getClass().getSuperclass().getSimpleName(), describeOwner(p.getOwner(), p.getOwnerID()), "true",
            String.valueOf(p.isSolid), "", "", "", "", "", "", "skipped:" + reason));
    }

    /** One line per projectile and object type: how its wall-damage attempts turned out. */
    public static synchronized List<String> summarizeWallDamage() {
        Map<String, int[]> totals = new TreeMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            String[] cols = e.getKey().split(",", -1);
            if (!cols[0].equals("walldamage")) continue;
            String[] target = cols[14].split(":", 2);
            int[] t = totals.computeIfAbsent(cols[2] + " -> " + target[0], k -> new int[4]);
            int slot = target[1].equals("damaged") ? 0 : target[1].equals("destroyed") ? 1 : target[1].equals("refused") ? 2 : 3;
            t[slot] += e.getValue().count;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, int[]> e : totals.entrySet()) {
            int[] t = e.getValue();
            lines.add("walldamage " + e.getKey() + ": damaged=" + t[0] + " destroyed=" + t[1] + " refused=" + t[2] + " none=" + t[3]);
        }
        return lines;
    }

    /** One line per projectile and side: how many times it hit a wall, a player, another mob, or nothing. */
    public static synchronized List<String> summarizeHits() {
        Map<String, int[]> totals = new TreeMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            String[] cols = e.getKey().split(",", -1);
            if (!cols[0].equals("hit")) continue;
            int[] t = totals.computeIfAbsent(cols[2] + " (" + cols[1] + ", solid=" + cols[7] + ")", k -> new int[5]);
            String target = cols[14];
            int slot = target.startsWith("wall") ? 0 : target.equals("player") ? 1 : target.startsWith("player") ? 2
                : target.startsWith("mob") ? 3 : 4;
            t[slot] += e.getValue().count;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, int[]> e : totals.entrySet()) {
            int[] t = e.getValue();
            lines.add("hits " + e.getKey() + ": wall=" + t[0] + " player=" + t[1] + " player(spawned inside box)=" + t[2]
                + " mob=" + t[3] + " none=" + t[4]);
        }
        return lines;
    }

    public static synchronized List<String> dump() throws IOException {
        File file = new File(GlobalData.appDataPath(), "bossphysics-audit.csv");
        List<String> lines = new ArrayList<>();
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            out.println(CSV_HEADER);
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                out.println(e.getKey() + "," + e.getValue().count);
                lines.add(e.getKey() + " x" + e.getValue().count);
            }
        }
        lines.add("Wrote " + entries.size() + " rows to " + file.getAbsolutePath());
        return lines;
    }

    private static synchronized void record(String row) {
        Entry entry = entries.get(row);
        if (entry == null) {
            entries.put(row, new Entry());
            GameLog.debug.println("[BossPhysics] new " + row);
        } else {
            entry.count++;
        }
    }

    private static String side(boolean server, boolean client) {
        return server ? "server" : client ? "client" : "unknown";
    }

    /** {@code UNRESOLVED} means the owner's unique ID is known but the mob is not loaded on this side. */
    private static String describeOwner(Mob owner, int ownerID) {
        if (owner != null) return owner.getStringID();
        return ownerID != 0 && ownerID != -1 ? "UNRESOLVED" : "none";
    }

    private static final class Entry {
        int count = 1;
    }
}
