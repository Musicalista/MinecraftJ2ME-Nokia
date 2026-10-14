/** PathfinderMob 0.6.1: цель атаки, путь A*, прогулки к лучшей клетке, бегство после удара. */
public abstract class PathfinderMob extends Mob {
    static final int MAX_TURN = 30;
    protected Entity attackTarget;
    protected boolean holdGround;
    protected int fleeTime;
    protected final PathFinder.Path path = new PathFinder.Path();
    private static PathFinder finder;
    private static Level finderLevel;

    protected PathfinderMob(Level l) {
        super(l);
    }

    protected PathFinder finder() {
        if (finder == null || finderLevel != level) {
            finder = new PathFinder(level);
            finderLevel = level;
        }
        return finder;
    }

    public boolean isPathFinding() {
        return !path.isEmpty();
    }

    protected Entity findAttackTarget() {
        return null;
    }

    protected void checkHurtTarget(Entity t, float d) {
    }

    protected void checkCantSeeTarget(Entity t, float d) {
        if (d > 32) attackTarget = null;
    }

    public float walkTargetValue(int x, int y, int z) {
        return 0;
    }

    protected boolean shouldHoldGround() {
        return false;
    }

    protected float walkingSpeedModifier() {
        float s = super.walkingSpeedModifier();
        if (fleeTime > 0) s *= 2;
        return s;
    }

    static boolean alive(Entity e) {
        return e != null && !e.removed && (!(e instanceof Mob) || ((Mob) e).health > 0);
    }

    /** PathfinderMob::updateAi */
    protected void updateAi() {
        if (fleeTime > 0) fleeTime--;
        holdGround = shouldHoldGround();
        float maxDist = 16;
        Entity target = null;
        if (attackTarget == null) {
            target = findAttackTarget();
            if (target != null) {
                finder().findPath(path, this, target, maxDist);
                attackTarget = target;
            }
        } else {
            target = attackTarget;
            if (!alive(target)) {
                attackTarget = null;
                target = null;
            } else {
                float d = distTo(target);
                if (canSee(target)) checkHurtTarget(target, d);
                else checkCantSeeTarget(target, d);
            }
        }
        boolean stroll = false;
        if (!holdGround && target != null && (path.isEmpty() || rnd.nextInt(20) == 0)) {
            finder().findPath(path, this, target, maxDist);
        } else if (!holdGround) {
            if (path.isEmpty() && rnd.nextInt(180) == 0) stroll = true;
            else if (rnd.nextInt(120) == 0) stroll = true;
            else if (fleeTime > 0 && (fleeTime & 7) == 1) stroll = true;
        }
        if (stroll && noActionTime < 20 * 5) findRandomStrollLocation();
        int yFloor = Mth.floor(y + 0.5f);
        xRot = 0;
        if (path.isEmpty() || rnd.nextInt(100) == 0) {
            super.updateAi();
            return;
        }
        float r = bbW * 2;
        boolean looping = true;
        float tx = path.posX(this), ty = path.posY(), tz = path.posZ(this);
        while (looping && (tx - x) * (tx - x) + (tz - z) * (tz - z) < r * r) {
            path.next();
            if (path.isDone()) {
                looping = false;
                path.destroy();
            } else {
                tx = path.posX(this); ty = path.posY(); tz = path.posZ(this);
            }
        }
        jumping = false;
        if (looping) {
            float xd = tx - x, zd = tz - z, yd = ty - yFloor;
            float yRotD = Mth.atan2(zd, xd) * 180f / Mth.PI - 90;
            float rot = Mth.wrapDegrees(yRotD - yRot);
            moveForward = runSpeed;
            if (rot > MAX_TURN) rot = MAX_TURN;
            if (rot < -MAX_TURN) rot = -MAX_TURN;
            yRot += rot;
            if (holdGround && target != null) {
                float xd2 = target.x - x, zd2 = target.z - z;
                float old = yRot;
                yRot = Mth.atan2(zd2, xd2) * 180f / Mth.PI - 90;
                rot = (old - yRot + 90) * Mth.PI / 180;
                moveStrafe = -Mth.sin(rot) * moveForward;
                moveForward = Mth.cos(rot) * moveForward;
            }
            if (yd > 0) jumping = true;
        }
        if (target != null) lookAt(target, 30, 30);
        if (horizontalCollision && !isPathFinding()) jumping = true;
        if (rnd.nextFloat() < 0.8f && (inWater || inLava)) jumping = true;
    }

    /** Entity::distanceTo (по позициям как в оригинале) */
    protected float distTo(Entity e) {
        float dx = e.x - x, dy = Level.origY(e) - y, dz = e.z - z;
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** PathfinderMob::findRandomStrollLocation: лучшая из 10 случайных клеток рядом. */
    protected void findRandomStrollLocation() {
        boolean has = false;
        int bx = -1, by = -1, bz = -1;
        float best = -99999;
        for (int i = 0; i < 10; i++) {
            int xt = Mth.floor(x + rnd.nextInt(13) - 6), yt = Mth.floor(y + rnd.nextInt(7) - 3), zt = Mth.floor(z + rnd.nextInt(13) - 6);
            float v = walkTargetValue(xt, yt, zt);
            if (v > best) {
                best = v;
                bx = xt; by = yt; bz = zt;
                has = true;
            }
        }
        if (has) finder().findPath(path, this, bx, by, bz, 10);
    }
}
