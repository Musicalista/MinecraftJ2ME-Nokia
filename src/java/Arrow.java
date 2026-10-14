/** Стрела (id 80). */
public final class Arrow extends Entity {
    Entity owner;
    boolean inGround, playerArrow, crit;
    int shakeTime;
    int life, damage = 2;

    public Arrow(Level l) {
        super(l);
        bbW = 0.5f;
        bbH = 0.5f;
        stepHeight = 0;
    }

    public Arrow(Level l, Mob owner) {
        this(l);
        this.owner = owner;
        playerArrow = owner instanceof Player;
        setPos(owner.x, owner.y + owner.headHeight() - 0.1f, owner.z);
    }

    /** Arrow::Arrow(level, mob, power): из глаз игрока со сдвигом 0.16 вбок, скорость power * 1.5. */
    public static Arrow fromPlayer(Level l, Mob p, float power) {
        Arrow a = new Arrow(l);
        a.owner = p;
        a.playerArrow = true;
        float dx = Mth.cos(p.yRot * Mth.DEG), dz = Mth.sin(p.yRot * Mth.DEG);
        a.setPos(p.x - dx * 0.16f, p.y + Player.EYE + 0.12f - 0.1f, p.z - dz * 0.16f);
        a.xo = a.x; a.yo = a.y; a.zo = a.z;
        float c = Mth.cos(p.xRot * Mth.DEG);
        a.shoot(-dz * c, -Mth.sin(p.xRot * Mth.DEG), dx * c, power * 1.5f, 1);
        return a;
    }

    public int typeId() {
        return 80;
    }

    public void shoot(float dx, float dy, float dz, float power, float spread) {
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        java.util.Random r = level.random;
        dx = dx / d + Mth.gaussian(r) * 0.0075f * spread;
        dy = dy / d + Mth.gaussian(r) * 0.0075f * spread;
        dz = dz / d + Mth.gaussian(r) * 0.0075f * spread;
        xd = dx * power;
        yd = dy * power;
        zd = dz * power;
        float h = Mth.sqrt(xd * xd + zd * zd);
        yRot = yRotO = Mth.atan2(xd, zd) * 180f / Mth.PI;
        xRot = xRotO = Mth.atan2(yd, h) * 180f / Mth.PI;
    }

    int xTile = -1, yTile = -1, zTile = -1, lastTile, lastData, flightTime;

    /** Arrow::tick 0.6.1 */
    public void tick() {
        baseTick();
        if (shakeTime > 0) shakeTime--;
        if (inGround) {
            if (level.getTile(xTile, yTile, zTile) != lastTile || level.getData(xTile, yTile, zTile) != lastData) {
                inGround = false;
                xd *= level.random.nextFloat() * 0.2f;
                yd *= level.random.nextFloat() * 0.2f;
                zd *= level.random.nextFloat() * 0.2f;
                life = 0;
                flightTime = 0;
                return;
            }
            if (++life == 60 * 20) remove();
            // Arrow::playerTouch: своя стрела в земле подбирается (pop 0.2 и анимация подбора)
            Player p = level.player;
            if (!level.isClientSide && playerArrow && shakeTime <= 0 && p != null && !p.isDead()) {
                float hw = p.bbW / 2 + bbW / 2;
                if (Math.abs(x - p.x) < hw + 1 && Math.abs(z - p.z) < hw + 1 && y + bbH > p.y && y < p.y + p.bbH && p.inv.add(262, 1, 0) == 0) {
                    if (MC.instance != null) {
                        MC.instance.playSound("random.pop", 0.2f);
                        MC.instance.takeAnim(new ItemEntity(level, x, y, z, 262, 1, 0));
                    }
                    remove();
                }
            }
            return;
        }
        flightTime++;
        boolean hitBlock = level.clip(x, y, z, x + xd, y + yd, z + zd, false, true);
        float ex = x + xd, ey = y + yd, ez = z + zd;
        int bx = level.clipX, by = level.clipY, bz = level.clipZ;
        if (hitBlock) { ex = level.clipPX; ey = level.clipPY; ez = level.clipPZ; }
        Entity hitE = null;
        float nearest = 0;
        java.util.Vector v = level.entities;
        for (int i = -1; i < v.size(); i++) {
            Entity e = i < 0 ? level.player : (Entity) v.elementAt(i);
            if (e == null || e == this || !e.isPickable() || (e == owner && flightTime < 5)) continue;
            float tt = e.rayHitGrow(x, y, z, ex - x, ey - y, ez - z, 0.3f);
            if (tt < 0 || tt > 1) continue;
            float dx = (ex - x) * tt, dy = (ey - y) * tt, dz = (ez - z) * tt;
            float dd = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (dd < nearest || nearest == 0) { hitE = e; nearest = dd; }
        }
        if (hitE != null) {
            if (hitE.hurt(hitDamage(), this)) {
                hitSound();
                remove();
            } else {
                xd *= -0.1f; yd *= -0.1f; zd *= -0.1f;
                yRot += 180; yRotO += 180;
                flightTime = 0;
            }
        } else if (hitBlock) {
            xTile = bx; yTile = by; zTile = bz;
            lastTile = level.getTile(bx, by, bz);
            lastData = level.getData(bx, by, bz);
            xd = ex - x; yd = ey - y; zd = ez - z;
            float dd = Mth.sqrt(xd * xd + yd * yd + zd * zd);
            if (dd > 0) {
                x -= xd / dd * 0.05f; y -= yd / dd * 0.05f; z -= zd / dd * 0.05f;
            }
            hitSound();
            inGround = true;
            shakeTime = 7;
            crit = false;
        }
        // Arrow::tick: след критической стрелы
        if (crit && MC.instance != null)
            for (int i = 0; i < 4; i++) MC.instance.particles.crit(x + xd * i / 4f, y + yd * i / 4f, z + zd * i / 4f, -xd, -yd + 0.2f, -zd);
        x += xd; y += yd; z += zd;
        float h = Mth.sqrt(xd * xd + zd * zd);
        float yr = Mth.atan2(xd, zd) * 180f / Mth.PI, xr = Mth.atan2(yd, h) * 180f / Mth.PI;
        xRot = xRotO + Mth.wrapDegrees(xr - xRotO) * 0.2f;
        yRot = yRotO + Mth.wrapDegrees(yr - yRotO) * 0.2f;
        float inertia = 0.99f;
        if (inWater) {
            if (MC.instance != null)
                for (int i = 0; i < 4; i++) MC.instance.particles.bubble(x - xd * 0.25f, y - yd * 0.25f, z - zd * 0.25f, xd, yd, zd);
            inertia = 0.8f;
        }
        xd *= inertia; yd *= inertia; zd *= inertia;
        yd -= 0.05f;
    }

    /** Стрела в мобе отдаёт урон от имени стрелы (PigZombie разбирает владельца). */
    public boolean isPickable() {
        return false;
    }

    /** Arrow::tick: урон = ceil(скорость * 2), крит — ещё до dmg/2 + 1. */
    private int hitDamage() {
        float pow = Mth.sqrt(xd * xd + yd * yd + zd * zd);
        int dmg = (int) Math.ceil(pow * 2f);
        if (crit) dmg += level.random.nextInt(dmg / 2 + 2);
        return dmg;
    }

    private void hitSound() {
        if (MC.instance != null) MC.instance.playSoundAt("random.bowhit", x, y, z, 1f);
    }

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putShort("life", life);
        t.putBoolean("inGround", inGround);
        t.putBoolean("player", playerArrow);
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        life = t.getShort("life");
        inGround = t.getBoolean("inGround");
        playerArrow = t.getBoolean("player");
    }
}
