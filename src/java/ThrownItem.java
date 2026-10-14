/** Брошенный снежок (81) или яйцо (82) — Throwable / Snowball / ThrownEgg 0.6.1. */
public final class ThrownItem extends Entity {
    public final int type;
    Entity owner;
    int shakeTime, life, flightTime;
    boolean inGround;
    int xTile = -1, yTile = -1, zTile = -1, lastTile;

    public ThrownItem(Level l, int type) {
        super(l);
        this.type = type;
        bbW = bbH = 0.25f;
        stepHeight = 0;
    }

    /** ThrownItem::ThrownItem(level, mob): из глаз со сдвигом 0.16 вбок, по направлению взгляда, сила 1.5. */
    public static ThrownItem fromPlayer(Level l, Mob p, int type) {
        ThrownItem t = new ThrownItem(l, type);
        t.owner = p;
        float c = Mth.cos(p.yRot * Mth.DEG), s = Mth.sin(p.yRot * Mth.DEG);
        t.setPos(p.x - c * 0.16f, p.y + Player.EYE + 0.12f - 0.1f, p.z - s * 0.16f);
        t.yRot = p.yRot;
        t.xRot = p.xRot;
        float xc = Mth.cos(p.xRot * Mth.DEG);
        t.shoot(-s * xc, -Mth.sin(p.xRot * Mth.DEG), c * xc, 1.5f, 1);
        return t;
    }

    public int typeId() {
        return type;
    }

    public void shoot(float xd, float yd, float zd, float pow, float unc) {
        float d = Mth.sqrt(xd * xd + yd * yd + zd * zd);
        if (d >= 0.001f) { xd /= d; yd /= d; zd /= d; } else xd = yd = zd = 0;
        java.util.Random r = level.random;
        xd += Mth.gaussian(r) * 0.0075f * unc;
        yd += Mth.gaussian(r) * 0.0075f * unc;
        zd += Mth.gaussian(r) * 0.0075f * unc;
        this.xd = xd * pow; this.yd = yd * pow; this.zd = zd * pow;
        float sd = Mth.sqrt(this.xd * this.xd + this.zd * this.zd);
        yRotO = yRot = Mth.atan2(this.xd, this.zd) * 180f / Mth.PI;
        xRotO = xRot = Mth.atan2(this.yd, sd) * 180f / Mth.PI;
        life = 0;
    }

    public void tick() {
        baseTick();
        if (shakeTime > 0) shakeTime--;
        if (inGround) {
            if (level.getTile(xTile, yTile, zTile) != lastTile) {
                inGround = false;
                xd *= level.random.nextFloat() * 0.2f;
                yd *= level.random.nextFloat() * 0.2f;
                zd *= level.random.nextFloat() * 0.2f;
                life = 0;
                flightTime = 0;
            } else {
                if (++life == 20 * 60) remove();
                return;
            }
        } else flightTime++;
        float cy = y;
        boolean hit = level.clip(x, cy, z, x + xd, cy + yd, z + zd, false, false);
        float ex = x + xd, ey = cy + yd, ez = z + zd;
        if (hit) { ex = level.clipPX; ey = level.clipPY; ez = level.clipPZ; }
        // ближайшая задетая сущность (рамка +0.3)
        Entity hitE = null;
        float nearest = 0;
        java.util.Vector v = level.entities;
        for (int i = -1; i < v.size(); i++) {
            Entity e = i < 0 ? level.player : (Entity) v.elementAt(i);
            if (e == null || e == this || !e.isPickable() || (e == owner && flightTime < 5)) continue;
            float tt = e.rayHitGrow(x, cy, z, ex - x, ey - cy, ez - z, 0.3f);
            if (tt < 0 || tt > 1) continue;
            float dx = (ex - x) * tt, dy = (ey - cy) * tt, dz = (ez - z) * tt;
            float dd = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (dd < nearest || nearest == 0) { hitE = e; nearest = dd; }
        }
        if (hitE != null || hit) onHit(hitE);
        x += xd; y += yd; z += zd;
        float sd = Mth.sqrt(xd * xd + zd * zd);
        yRot = Mth.atan2(xd, zd) * 180f / Mth.PI;
        xRot = Mth.atan2(yd, sd) * 180f / Mth.PI;
        xRot = xRotO + Mth.wrapDegrees(xRot - xRotO) * 0.2f;
        yRot = yRotO + Mth.wrapDegrees(yRot - yRotO) * 0.2f;
        float inertia = 0.99f;
        if (inWater) {
            if (MC.instance != null)
                for (int i = 0; i < 4; i++) MC.instance.particles.bubble(x - xd * 0.25f, y - yd * 0.25f, z - zd * 0.25f, xd, yd, zd);
            inertia = 0.8f;
        }
        xd *= inertia; yd *= inertia; zd *= inertia;
        yd -= 0.03f;
    }

    /** Snowball/ThrownEgg::onHit: урон 0 (только толчок), из яйца 1/8 — курица (1/32 — четыре). */
    private void onHit(Entity e) {
        if (e != null) e.hurt(0, this);
        if (type == 82 && level.random.nextInt(8) == 0) {
            int n = level.random.nextInt(32) == 0 ? 4 : 1;
            for (int i = 0; i < n; i++) {
                Mobs.Chicken c = new Mobs.Chicken(level);
                c.setPos(x, y, z);
                c.yRot = yRot;
                level.addEntity(c);
            }
        }
        if (MC.instance != null) for (int i = 0; i < 6; i++) MC.instance.particles.snowballPoof(x, y, z);
        remove();
    }

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putShort("xTile", xTile);
        t.putShort("yTile", yTile);
        t.putShort("zTile", zTile);
        t.putByte("inTile", lastTile);
        t.putByte("shake", shakeTime);
        t.putByte("inGround", inGround ? 1 : 0);
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        xTile = t.getShort("xTile");
        yTile = t.getShort("yTile");
        zTile = t.getShort("zTile");
        lastTile = t.getByte("inTile") & 255;
        shakeTime = t.getByte("shake") & 255;
        inGround = t.getByte("inGround") == 1;
    }
}
