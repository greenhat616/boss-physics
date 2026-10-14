package bossphysics.debug;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.entity.levelEvent.explosionEvent.ExplosionEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;

/**
 * Records every distinct kind of non-player projectile and explosion while {@code /bp debug on} is active.
 *
 * <p>Each kind is keyed by side, class, owner and the collision flags, so one attack shows up once per side. In
 * singleplayer and on a host the server and the local client share this JVM, which is what lets one audit compare
 * the two sides; a remote client's projectiles are not seen.
 */
public final class AttackAudit {

    private static final String CSV_HEADER =
        "kind,side,id,class,superclass,owner,ownerIsBoss,isSolid,canBounce,canHitMobs,canBreakObjects,destroysObjects,destroysTiles,toolTier,count";

    private static volatile boolean enabled;
    private static final Map<String, Entry> entries = new LinkedHashMap<>();

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
    }

    public static synchronized int size() {
        return entries.size();
    }

    /** Called once a projectile is fully initialised, after any subclass {@code init()} has set its flags. */
    public static void recordProjectile(Projectile p) {
        if (!enabled) return;
        Mob owner = p.getOwner();
        if (owner != null && owner.isPlayer) return;
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
            "", "", "");
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
            String.valueOf(e.toolTier));
        record(row);
    }

    /** Writes the audit to {@code <appdata>/bossphysics-audit.csv} and returns the lines for the command log. */
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
