package bossphysics.debug;

import bossphysics.config.BossPhysicsConfig;
import bossphysics.config.ConfigSync;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.GlobalData;

/**
 * Judges the settings in force against the balance criteria in {@code docs/balance.md}, from the fights of one autotest
 * run, and writes {@code <appdata>/bossphysics-balance.md}. A boss may only be in the shipped defaults when all its
 * checks pass.
 *
 * <p>Thresholds can be overridden with {@code -Dbossphysics.balance.<name>=<value>}.
 */
public final class BalanceReport {

    /** Walls must hold at least this long before the first one breaks (cover must mean something). */
    static final double MIN_BREACH_SECONDS = threshold("minBreachSeconds", 5);
    /** Hiding is not free if the boss breaks in within this time... */
    static final double MAX_BREACH_SECONDS = threshold("maxBreachSeconds", 60);
    /** ...or still lands this share of its open-field hit rate on a boxed-in player. */
    static final double MIN_BOX_PRESSURE = threshold("minBoxPressure", 0.25);
    /** Half cover must not stop a boss: it keeps at least this share of its open-field hit rate. */
    static final double MIN_HALFCOVER_PRESSURE = threshold("minHalfCoverPressure", 0.25);
    /** Client and server wall hit counts may differ by this share (or 2 hits) before it counts as a desync. */
    static final double MAX_SYNC_DIFFERENCE = threshold("maxSyncDifference", 0.10);

    private BalanceReport() {
    }

    /**
     * @param localClient whether a client runs in this JVM (singleplayer or host), so client-side counts exist
     */
    public static List<String> write(List<FightStats> fights, boolean localClient) throws IOException {
        Map<String, List<FightStats>> byBoss = new LinkedHashMap<>();
        for (FightStats f : fights) byBoss.computeIfAbsent(f.boss, k -> new ArrayList<>()).add(f);

        List<String> log = new ArrayList<>();
        List<String> md = new ArrayList<>();
        md.add("# Boss Physics balance report");
        md.add("");
        md.add("Settings: " + String.join("; ", ConfigSync.describe(BossPhysicsConfig.get())));
        md.add("");
        md.add(String.format("Criteria: breach after >= %.0f s; hiding not free (breach <= %.0f s or box pressure >= %.2f); "
            + "half-cover pressure >= %.2f; client/server wall hits within %.0f%%.", MIN_BREACH_SECONDS, MAX_BREACH_SECONDS,
            MIN_BOX_PRESSURE, MIN_HALFCOVER_PRESSURE, MAX_SYNC_DIFFERENCE * 100));
        md.add("");
        md.add("| Boss | Verdict | Check | Result | Detail |");
        md.add("|---|---|---|---|---|");
        for (Map.Entry<String, List<FightStats>> e : byBoss.entrySet()) {
            List<String[]> checks = evaluate(e.getValue(), localClient);
            boolean pass = true;
            for (String[] c : checks) pass &= !c[1].equals("FAIL") && !c[1].equals("INVALID");
            String verdict = pass ? "PASS" : "FAIL";
            log.add("balance " + e.getKey() + ": " + verdict);
            for (String[] c : checks) {
                md.add("| " + e.getKey() + " | " + verdict + " | " + c[0] + " | " + c[1] + " | " + c[2] + " |");
                log.add("balance   " + c[0] + " " + c[1] + ": " + c[2]);
            }
        }
        md.add("");
        md.add("## Fights");
        md.add("");
        md.add("```");
        for (FightStats f : fights) md.add(f.describe());
        md.add("```");

        File file = new File(GlobalData.appDataPath(), "bossphysics-balance.md");
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            for (String line : md) out.println(line);
        }
        log.add("balance report written to " + file.getAbsolutePath());
        return log;
    }

    /** Each check as {name, PASS|FAIL|INFO|INVALID|N/A, detail}. */
    static List<String[]> evaluate(List<FightStats> fights, boolean localClient) {
        List<String[]> checks = new ArrayList<>();
        FightStats open = find(fights, TestArena.Layout.OPEN);
        FightStats box = find(fights, TestArena.Layout.BOX);
        FightStats half = find(fights, TestArena.Layout.HALFCOVER);
        FightStats door = find(fights, TestArena.Layout.DOORBOX);

        for (FightStats f : fights) {
            if (f.bossLeftEarly) checks.add(new String[]{"fight-valid", "INVALID", f.layout.id() + ": the boss left before the fight ended"});
            if (f.bossIdle() && f.layout == TestArena.Layout.OPEN) {
                checks.add(new String[]{"fight-valid", "INVALID", "open: the boss never attacked, even after retries"});
            }
        }
        for (FightStats f : new FightStats[]{box, door}) {
            if (f == null) continue;
            checks.add(new String[]{"walls-stop-whitelist(" + f.layout.id() + ")", f.whitelistedLeaks == 0 ? "PASS" : "FAIL",
                f.whitelistedLeaks + " whitelisted projectile hits through intact walls"});
            String leaks = hitsThroughIntactWalls(f);
            if (!leaks.isEmpty()) checks.add(new String[]{"hits-through-walls(" + f.layout.id() + ")", "INFO", leaks});
        }
        if (box != null) {
            double breach = box.firstBreachMs < 0 ? -1 : box.firstBreachMs / 1000.0;
            checks.add(new String[]{"cover-holds", breach < 0 || breach >= MIN_BREACH_SECONDS ? "PASS" : "FAIL",
                breach < 0 ? "walls never broke" : String.format("first wall broke after %.1f s", breach)});
            double pressure = pressure(box, open);
            boolean breachedInTime = breach >= 0 && breach <= MAX_BREACH_SECONDS;
            boolean pressured = pressure >= MIN_BOX_PRESSURE;
            checks.add(new String[]{"hiding-not-free", breachedInTime || pressured ? "PASS" : "FAIL",
                String.format("breach %s, box pressure %s", breach < 0 ? "never" : String.format("%.1f s", breach),
                    pressure < 0 ? "n/a (no open fight)" : String.format("%.2f", pressure))});
            checks.add(syncCheck(box, localClient));
        }
        if (half != null) {
            double pressure = pressure(half, open);
            boolean pass = pressure < 0 ? half.totalPlayerHits() > 0 : pressure >= MIN_HALFCOVER_PRESSURE;
            checks.add(new String[]{"halfcover-reachable", pass ? "PASS" : "FAIL",
                pressure < 0 ? half.totalPlayerHits() + " hits (no open fight)" : String.format("pressure %.2f", pressure)});
        }
        if (door != null) {
            int[] b = door.summonBreakDowns;
            String broke = door.firstBreachMs < 0 ? "nothing broke" : door.doorBrokeFirst ? "the door broke first" : "a wall broke first";
            checks.add(new String[]{"door-first", "INFO", broke + "; summon break-down hits: door " + b[0] + ", wall " + b[1]
                + ", refused " + b[2]});
        }
        return checks;
    }

    private static String[] syncCheck(FightStats f, boolean localClient) {
        if (!localClient) return new String[]{"client-server-sync", "N/A", "no client in this JVM"};
        int c = f.clientWallHits;
        int s = f.serverWallHits;
        boolean pass = Math.abs(c - s) <= Math.max(2, MAX_SYNC_DIFFERENCE * Math.max(c, s));
        return new String[]{"client-server-sync", pass ? "PASS" : "FAIL", "wall hits client " + c + " / server " + s};
    }

    /** Box or half-cover hit rate as a share of the open-field rate; -1 when there is nothing to compare with. */
    private static double pressure(FightStats f, FightStats open) {
        if (open == null || open.hitsPerMinute() <= 0) return -1;
        return f.hitsPerMinute() / open.hitsPerMinute();
    }

    /** Every source that hit the player from outside intact walls: whitelist candidates, or untouched attack types. */
    private static String hitsThroughIntactWalls(FightStats f) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> e : f.playerHits.entrySet()) {
            if (e.getValue()[2] == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append(" x").append(e.getValue()[2]);
        }
        return sb.toString();
    }

    private static FightStats find(List<FightStats> fights, TestArena.Layout layout) {
        for (FightStats f : fights) {
            if (f.layout == layout) return f;
        }
        return null;
    }

    private static double threshold(String name, double fallback) {
        String value = System.getProperty("bossphysics.balance." + name);
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
