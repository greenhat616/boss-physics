package bossphysics.rules;

/** How a whitelisted boss projectile interacts with walls. */
public final class ProjectileRule {

    /** Stops at the first wall without bouncing, and damages it. */
    public static final ProjectileRule BLOCKED = new ProjectileRule(true, false, true);

    /** Whether walls stop the projectile and shield targets behind them. */
    public final boolean solid;
    /** Whether a projectile with bounces left may bounce off walls instead of stopping. */
    public final boolean allowBounce;
    /** Whether hitting a wall damages it (M2), subject to the boss's tool tier and protection checks. */
    public final boolean breaksWalls;

    public ProjectileRule(boolean solid, boolean allowBounce, boolean breaksWalls) {
        this.solid = solid;
        this.allowBounce = allowBounce;
        this.breaksWalls = breaksWalls;
    }
}
