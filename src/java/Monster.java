/** Monster 0.6.1: цель — видимый игрок ближе 16, удар вплотную раз в getAttackTime, исчезает в мирном режиме. */
public abstract class Monster extends PathfinderMob {
    protected int attackDamage = 2;
    public boolean noAi;

    protected Monster(Level l) {
        super(l);
    }

    public int maxHealth() {
        return 20;
    }

    public void tick() {
        super.tick();
        if (Options.peaceful) remove();
    }

    /** Monster::aiStep: на свету бездействие копится быстрее */
    protected void aiStep() {
        if (brightness() > 0.5f) noActionTime += 2;
        super.aiStep();
    }

    protected void updateAi() {
        if (noAi) { moveForward = moveStrafe = 0; jumping = false; return; }
        super.updateAi();
    }

    /** Monster::findAttackTarget */
    protected Entity findAttackTarget() {
        Mob p = nearestPlayer(16);
        if (p != null && canSee(p)) return p;
        return null;
    }

    /** Level::getNearestPlayer(source, maxDist) */
    protected Mob nearestPlayer(float maxDist) {
        return level.nearestPlayer(x, y, z, maxDist);
    }

    protected int attackTimeTicks() {
        return 20;
    }

    protected int attackDamage(Entity target) {
        return attackDamage;
    }

    /** Monster::checkHurtTarget: ближе 2 и рамки пересекаются по высоте */
    protected void checkHurtTarget(Entity t, float d) {
        if (attackTime <= 0 && d < 2f && t.y + t.bbH > y && t.y < y + bbH) {
            attackTime = attackTimeTicks();
            swing();
            t.hurt(attackDamage(t), this);
        }
    }

    /** Monster::getWalkTargetValue: темнее — лучше */
    public float walkTargetValue(int x, int y, int z) {
        return 0.5f - level.getBrightness(x, y, z);
    }

    public boolean hurt(int dmg, Entity source) {
        if (super.hurt(dmg, source)) {
            if (source != this) {
                attackTarget = source;
            }
            return true;
        }
        return false;
    }

    /** Zombie/Skeleton::aiStep: днём на открытом небе — урон 1 с облачками (не горят). */
    protected void sunHurt() {
        if (level.isDay()) {
            float br = brightness();
            if (br > 0.5f && level.canSeeSky(Mth.floor(x), Mth.floor(y), Mth.floor(z)) && rnd.nextFloat() * 3.5f < br - 0.4f) {
                hurt(1, null);
                if (MC.instance != null)
                    for (int i = 0; i < 5; i++) {
                        float xa = (2 * rnd.nextFloat() - 1) * (2 * rnd.nextFloat() - 1) * 0.02f;
                        float ya = (2 * rnd.nextFloat() - 1) * (2 * rnd.nextFloat() - 1) * 0.02f;
                        float za = (2 * rnd.nextFloat() - 1) * (2 * rnd.nextFloat() - 1) * 0.02f;
                        MC.instance.particles.explode(x + rnd.nextFloat() * bbW * 2 - bbW, y + rnd.nextFloat() * bbH, z + rnd.nextFloat() * bbW * 2 - bbW, xa, ya, za);
                    }
            }
        }
    }
}
