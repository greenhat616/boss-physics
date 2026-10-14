package bossphysics.rules;

/** How a whitelisted boss projectile interacts with walls; the names are the values used in the settings file. */
public enum ProjectileRule {

    /** Keeps vanilla behaviour; lets a settings file switch off a default entry. */
    VANILLA(false, false, false),
    /** Stops at the first wall without bouncing, and damages it. */
    BLOCKED(true, false, true),
    /** Stops at the first wall without bouncing, but leaves it intact. */
    SHIELDED(true, false, false),
    /** Bounces off walls while it has bounces left, damaging each wall it hits. */
    BOUNCE(true, true, true);

    /** Whether walls stop the projectile and shield targets behind them. */
    public final boolean solid;
    /** Whether a projectile with bounces left may bounce off walls instead of stopping. */
    public final boolean allowBounce;
    /** Whether hitting a wall damages it (M2), subject to the boss's tool tier and protection checks. */
    public final boolean breaksWalls;

    ProjectileRule(boolean solid, boolean allowBounce, boolean breaksWalls) {
        this.solid = solid;
        this.allowBounce = allowBounce;
        this.breaksWalls = breaksWalls;
    }

    /** The rule with this name, ignoring case, or null. */
    public static ProjectileRule parse(String name) {
        if (name == null) return null;
        for (ProjectileRule rule : values()) {
            if (rule.name().equalsIgnoreCase(name.trim())) return rule;
        }
        return null;
    }
}
