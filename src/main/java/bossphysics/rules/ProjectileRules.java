package bossphysics.rules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;

/**
 * The projectile whitelist: which boss projectiles are made solid (blocked by walls, unable to hit targets behind
 * walls). Everything not listed keeps its vanilla behaviour.
 *
 * <p>Only direct-fire projectiles belong here. Lobbed and summoning projectiles ({@code canHitMobs = false}, e.g.
 * spider spit, eggs, boulders) fly over walls, and {@code *Warning*} markers must never be blocked. See
 * {@code docs/projectile-audit.md}.
 */
public final class ProjectileRules {

    /** {@code -Dbossphysics.rules=off} disables every rule, for A/B comparisons in autotest runs. */
    private static final boolean ENABLED = !"off".equals(System.getProperty("bossphysics.rules"));

    private static final Map<String, ProjectileRule> RULES;

    static {
        Map<String, ProjectileRule> rules = new HashMap<>();
        rules.put("evilsprotector1", ProjectileRule.BLOCKED);
        rules.put("evilsprotector2", ProjectileRule.BLOCKED);
        rules.put("crystaldragonshard", ProjectileRule.BLOCKED);
        RULES = Collections.unmodifiableMap(rules);
    }

    private ProjectileRules() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** The rule for this projectile, or null when it should keep vanilla behaviour. */
    public static ProjectileRule ruleFor(Projectile projectile) {
        if (!ENABLED) return null;
        ProjectileRule rule = RULES.get(projectile.getStringID());
        if (rule == null) return null;
        Mob owner = projectile.getOwner();
        if (owner != null) return owner.isBoss() ? rule : null;
        // Owner known by ID but not loaded on this side: decide by type alone so both sides agree. Never observed in
        // the M0 audit; kept as a defensive branch.
        return projectile.getOwnerID() != 0 && projectile.getOwnerID() != -1 ? rule : null;
    }

    /**
     * Applies the rule once the projectile is fully initialised. Returns the new {@code canBounce} value, since that
     * field is protected and is written back by the patch.
     */
    public static boolean apply(Projectile projectile, boolean canBounce) {
        ProjectileRule rule = ruleFor(projectile);
        if (rule == null) return canBounce;
        projectile.isSolid = rule.solid;
        return canBounce && rule.allowBounce;
    }
}
