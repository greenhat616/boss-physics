package bossphysics.rules;

/** How a whitelisted boss projectile interacts with walls. */
public final class ProjectileRule {

    /** Stops at the first wall and does not bounce off it. */
    public static final ProjectileRule BLOCKED = new ProjectileRule(true, false);

    /** Whether walls stop the projectile and shield targets behind them. */
    public final boolean solid;
    /** Whether a projectile with bounces left may bounce off walls instead of stopping. */
    public final boolean allowBounce;

    public ProjectileRule(boolean solid, boolean allowBounce) {
        this.solid = solid;
        this.allowBounce = allowBounce;
    }
}
