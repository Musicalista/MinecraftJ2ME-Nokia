/** Базовая сущность: положение, движение со столкновениями, жидкости, падение. */
public abstract class Entity {
    public final Level level;
    public float x, y, z, xo, yo, zo;
    public float xd, yd, zd;
    public float yRot, xRot, yRotO, xRotO;
    public float bbW = 0.6f, bbH = 1.8f;
    public float stepHeight = 0.5f;
    public boolean onGround, horizontalCollision, inWater, inLava, removed;
    public float fallDistance;
    public int tickCount, fire, air = 300, invulnerableTime;
    public static int nextId = 1;
    /** Entity::entityId; у клиента сетевой игры — номер от хоста */
    public int entityId = nextId++;
    /** хост: что последним разослано клиентам (Mob::tick: sentX.., sentXd..) и хэш синхронизируемых данных */
    public float netX, netY, netZ, netYRot, netXRot, netXd, netYd, netZd;
    public int netHash = -1;

    protected Entity(Level level) {
        this.level = level;
    }

    /** Числовой тип для entities.dat (64 — предмет, 10..36 — мобы). */
    public abstract int typeId();

    public void setPos(float x, float y, float z) {
        this.x = xo = x;
        this.y = yo = y;
        this.z = zo = z;
    }

    /** Entity::isStuckInWeb, wasInWater, firstTick */
    public boolean stuckInWeb, wasInWater, firstTick = true;
    /** Entity::walkDist/nextStep — звуки шагов (makeStepSound) */
    public float walkDist, walkDistO;
    protected int nextStep = 1;

    public void tick() {
        baseTick();
    }

    /** Entity::baseTick: вода (всплеск, течение), огонь, лава, выход за мир. */
    public void baseTick() {
        tickCount++;
        walkDistO = walkDist;
        xo = x; yo = y; zo = z;
        xRotO = xRot; yRotO = yRot;
        checkLiquids();
        if (fire > 0) {
            if (fireImmune()) {
                fire -= 4;
                if (fire < 0) fire = 0;
            } else {
                if (fire % 20 == 0) hurt(1, null);
                fire--;
            }
        }
        if (inLava && !fireImmune()) {
            // Entity::lavaHurt
            hurt(4, null);
            fire = 30 * 20;
        }
        if (y < -64) outOfWorld();
        firstTick = false;
    }

    /** Entity::outOfWorld: просто исчезнуть (Mob — урон 4). */
    protected void outOfWorld() {
        remove();
    }

    private static final float[] flowTmp = new float[3];

    /** Level::checkAndHandleWater для рамки [x0..x1) — есть ли вода, и толчок течения 0.004. */
    protected boolean checkWater(float bx0, float by0, float bz0, float bx1, float by1, float bz1) {
        int x0 = Mth.floor(bx0), x1 = Mth.floor(bx1 + 1), y0 = Mth.floor(by0), y1 = Mth.floor(by1 + 1), z0 = Mth.floor(bz0), z1 = Mth.floor(bz1 + 1);
        boolean ok = false;
        float cx = 0, cy = 0, cz = 0;
        for (int xx = x0; xx < x1; xx++)
            for (int yy = y0; yy < y1; yy++)
                for (int zz = z0; zz < z1; zz++) {
                    int t = level.getTile(xx, yy, zz);
                    if (!Tile.isWater(t)) continue;
                    float yt0 = yy + 1 - Liquids.height(level.getData(xx, yy, zz));
                    if (y1 >= yt0) {
                        ok = true;
                        Liquids.flow(level, t, xx, yy, zz, flowTmp);
                        cx += flowTmp[0] * 0.5f; cy += flowTmp[1] * 0.5f; cz += flowTmp[2] * 0.5f;
                    }
                }
        float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (len > 0) {
            float pow = 0.004f / len;
            xd += cx * pow; yd += cy * pow; zd += cz * pow;
        }
        return ok;
    }

    /** Level::containsMaterial(lava) */
    protected boolean containsLava(float bx0, float by0, float bz0, float bx1, float by1, float bz1) {
        int x0 = Mth.floor(bx0), x1 = Mth.floor(bx1 + 1), y0 = Mth.floor(by0), y1 = Mth.floor(by1 + 1), z0 = Mth.floor(bz0), z1 = Mth.floor(bz1 + 1);
        for (int xx = x0; xx < x1; xx++)
            for (int yy = y0; yy < y1; yy++)
                for (int zz = z0; zz < z1; zz++) if (Tile.isLava(level.getTile(xx, yy, zz))) return true;
        return false;
    }

    /** Entity::isInWater (рамка, ужатая на 0.4 по высоте) */
    protected boolean isInWaterNow() {
        float hw = bbW / 2;
        return checkWater(x - hw, y + 0.4f, z - hw, x + hw, y + bbH - 0.4f, z + hw);
    }

    protected void checkLiquids() {
        inWater = isInWaterNow();
        if (inWater) {
            // Entity::baseTick: вход в воду — всплеск и пузыри
            if (!wasInWater && !firstTick && MC.instance != null) {
                float speed = Math.min(1f, Mth.sqrt(xd * xd * 0.2f + yd * yd + zd * zd * 0.2f) * 0.2f);
                MC.instance.playSoundAt("random.splash", x, y, z, speed);
                float yt = Mth.floor(y);
                for (int i = 0; i < 1 + bbW * 20; i++) {
                    float ox = (Mth.random() * 2 - 1) * bbW, oz = (Mth.random() * 2 - 1) * bbW;
                    MC.instance.particles.bubble(x + ox, yt + 1, z + oz, xd, yd - Mth.random() * 0.2f, zd);
                }
            }
            fallDistance = 0;
            wasInWater = true;
            fire = 0;
        } else wasInWater = false;
        float hw = bbW / 2;
        inLava = containsLava(x - hw + 0.1f, y + 0.4f, z - hw + 0.1f, x + hw - 0.1f, y + bbH - 0.4f, z + hw - 0.1f);
    }

    /** Entity::blocksBuilding */
    public boolean blocksBuilding() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    /** Entity::push(Entity): раздвинуться на 0.05 */
    public void push(Entity e) {
        float xa = e.x - x, za = e.z - z;
        float dd = Math.max(Math.abs(xa), Math.abs(za));
        if (dd >= 0.01f) {
            dd = Mth.sqrt(dd);
            xa /= dd;
            za /= dd;
            float pow = 1 / dd;
            if (pow > 1) pow = 1;
            xa *= pow * 0.05f;
            za *= pow * 0.05f;
            xd -= xa; zd -= za;
            e.xd += xa; e.zd += za;
        }
    }

    /** Entity::makeStepSound */
    protected boolean makeStepSound() {
        return false;
    }

    /** Entity::isSneaking (для шагов и края) */
    protected boolean isSneakingNow() {
        return false;
    }

    protected boolean fireImmune() {
        return false;
    }

    public boolean isUnderWater() {
        return isUnderLiquid(true);
    }

    /** Entity::isUnderLiquid: глаза ниже поверхности жидкости в своём блоке (LiquidTile::getHeight - 1/9). */
    public boolean isUnderLiquid(boolean water) {
        // у игрока Pos — глаза, Player::getHeadHeight = 0.12
        float yp = y + (this instanceof Player ? Player.EYE + 0.12f : headHeight());
        int xt = Mth.floor(x), yt = Mth.floor(yp), zt = Mth.floor(z);
        int t = level.getTile(xt, yt, zt);
        if (water ? !Tile.isWater(t) : !Tile.isLava(t)) return false;
        int d = level.getData(xt, yt, zt);
        float hh = (d >= 8 ? 0 : d + 1) / 9f - 1 / 9f;
        return yp < yt + 1 - hh;
    }

    public float headHeight() {
        return bbH * 0.85f;
    }

    // ---- столкновения ----

    private static final float[] boxes = new float[6 * 512];
    private static final int[] boxCount = new int[1];

    private void collect(float x0, float y0, float z0, float x1, float y1, float z1) {
        boxCount[0] = 0;
        int ix0 = Mth.floor(x0), ix1 = Mth.floor(x1), iy0 = Mth.floor(y0) - 1, iy1 = Mth.floor(y1);
        int iz0 = Mth.floor(z0), iz1 = Mth.floor(z1);
        for (int xx = ix0; xx <= ix1; xx++)
            for (int zz = iz0; zz <= iz1; zz++)
                for (int yy = iy0; yy <= iy1; yy++) level.addCollisionBoxes(xx, yy, zz, boxes, boxCount);
    }

    public boolean isFree(float dx, float dy, float dz) {
        float hw = bbW / 2;
        float ax0 = x - hw + dx, ay0 = y + dy, az0 = z - hw + dz, ax1 = x + hw + dx, ay1 = y + bbH + dy, az1 = z + hw + dz;
        collect(ax0, ay0, az0, ax1, ay1, az1);
        for (int i = 0; i < boxCount[0]; i++) {
            int o = i * 6;
            if (ax1 > boxes[o] && ax0 < boxes[o + 3] && ay1 > boxes[o + 1] && ay0 < boxes[o + 4] && az1 > boxes[o + 2] && az0 < boxes[o + 5])
                return false;
        }
        for (int xx = Mth.floor(ax0); xx <= Mth.floor(ax1); xx++)
            for (int yy = Mth.floor(ay0); yy <= Mth.floor(ay1); yy++)
                for (int zz = Mth.floor(az0); zz <= Mth.floor(az1); zz++)
                    if (Tile.isLiquid(level.getTile(xx, yy, zz))) return false;
        return true;
    }

    protected boolean avoidEdges() {
        return false;
    }

    public void move(float dx, float dy, float dz) {
        float hw = bbW / 2;
        float startX = x, startZ = z;
        if (stuckInWeb) {
            stuckInWeb = false;
            dx *= 0.25f; dy *= 0.05f; dz *= 0.25f;
            xd = yd = zd = 0;
        }
        if (onGround && avoidEdges()) {
            float step = 0.05f;
            while (dx != 0 && freeBelow(dx, 0)) {
                if (dx < step && dx >= -step) dx = 0; else if (dx > 0) dx -= step; else dx += step;
            }
            while (dz != 0 && freeBelow(0, dz)) {
                if (dz < step && dz >= -step) dz = 0; else if (dz > 0) dz -= step; else dz += step;
            }
        }
        float odx = dx, ody = dy, odz = dz;
        float bx0 = x - hw, by0 = y, bz0 = z - hw, bx1 = x + hw, by1 = y + bbH, bz1 = z + hw;
        collect(Math.min(bx0, bx0 + dx), Math.min(by0, by0 + dy), Math.min(bz0, bz0 + dz),
                Math.max(bx1, bx1 + dx), Math.max(by1, by1 + dy), Math.max(bz1, bz1 + dz));
        int n = boxCount[0];
        float[] b = boxes;
        for (int i = 0; i < n; i++) dy = clipY(b, i * 6, bx0, by0, bz0, bx1, by1, bz1, dy);
        by0 += dy; by1 += dy;
        for (int i = 0; i < n; i++) dx = clipX(b, i * 6, bx0, by0, bz0, bx1, by1, bz1, dx);
        bx0 += dx; bx1 += dx;
        for (int i = 0; i < n; i++) dz = clipZ(b, i * 6, bx0, by0, bz0, bx1, by1, bz1, dz);
        bz0 += dz; bz1 += dz;

        boolean wasOnGround = onGround || (ody != dy && ody < 0);
        if (stepHeight > 0 && wasOnGround && (odx != dx || odz != dz)) {
            float sx0 = x - hw, sy0 = y, sz0 = z - hw, sx1 = x + hw, sy1 = y + bbH, sz1 = z + hw;
            float sdx = odx, sdy = stepHeight, sdz = odz;
            collect(Math.min(sx0, sx0 + sdx), sy0, Math.min(sz0, sz0 + sdz), Math.max(sx1, sx1 + sdx), sy1 + sdy, Math.max(sz1, sz1 + sdz));
            int m = boxCount[0];
            for (int i = 0; i < m; i++) sdy = clipY(b, i * 6, sx0, sy0, sz0, sx1, sy1, sz1, sdy);
            sy0 += sdy; sy1 += sdy;
            for (int i = 0; i < m; i++) sdx = clipX(b, i * 6, sx0, sy0, sz0, sx1, sy1, sz1, sdx);
            sx0 += sdx; sx1 += sdx;
            for (int i = 0; i < m; i++) sdz = clipZ(b, i * 6, sx0, sy0, sz0, sx1, sy1, sz1, sdz);
            sz0 += sdz; sz1 += sdz;
            float down = -sdy;
            for (int i = 0; i < m; i++) down = clipY(b, i * 6, sx0, sy0, sz0, sx1, sy1, sz1, down);
            sy0 += down; sy1 += down;
            if (sdx * sdx + sdz * sdz > dx * dx + dz * dz) {
                dx = sdx; dz = sdz; dy = sy0 - y;
                bx0 = sx0; bx1 = sx1; by0 = sy0; by1 = sy1; bz0 = sz0; bz1 = sz1;
            }
        }

        horizontalCollision = odx != dx || odz != dz;
        boolean vert = ody != dy;
        onGround = vert && ody < 0;
        x = (bx0 + bx1) / 2;
        y = by0;
        z = (bz0 + bz1) / 2;
        // Entity::checkFallDamage: Tile::fallOn (у мобов) и урон от падения
        if (onGround) {
            if (fallDistance > 0) {
                if (this instanceof Mob) {
                    int xt = Mth.floor(x), yt = Mth.floor(y - 0.2f), zt = Mth.floor(z);
                    int t = level.getTile(xt, yt, zt);
                    if (t == 0 && level.getTile(xt, yt - 1, zt) == Tile.FENCE) t = Tile.FENCE;
                    if (t > 0) TileLogic.fallOn(level, t, xt, yt, zt, this, fallDistance);
                }
                causeFall(fallDistance);
                fallDistance = 0;
            }
        } else if (dy < 0) fallDistance -= dy;
        if (odx != dx) xd = 0;
        if (vert) yd = 0;
        if (odz != dz) zd = 0;
        // Entity::move: шаг каждый пройденный блок (*0.6) — звук блока под ногами
        if (makeStepSound() && !(onGround && isSneakingNow())) {
            float mx = x - startX, mz = z - startZ;
            walkDist += Mth.sqrt(mx * mx + mz * mz) * 0.6f;
            int xt = Mth.floor(x), yt = Mth.floor(y - 0.2f), zt = Mth.floor(z);
            int t = level.getTile(xt, yt, zt);
            if (t == 0) {
                int u = level.getTile(xt, yt - 1, zt);
                if (u == Tile.FENCE || u == Tile.FENCE_GATE) t = u;
            }
            if (walkDist > nextStep && t > 0) {
                nextStep = (int) walkDist + 1;
                playStepSound(xt, yt, zt, t);
            }
        }
        // Tile::entityInside: кактус, паутина
        int ix0 = Mth.floor(x - hw), iy0 = Mth.floor(y), iz0 = Mth.floor(z - hw);
        int ix1 = Mth.floor(x + hw), iy1 = Mth.floor(y + bbH), iz1 = Mth.floor(z + hw);
        for (int xx = ix0; xx <= ix1; xx++)
            for (int yy = iy0; yy <= iy1; yy++)
                for (int zz = iz0; zz <= iz1; zz++) {
                    int t = level.getTile(xx, yy, zz);
                    if (t == Tile.CACTUS || t == Tile.WEB) TileLogic.entityInside(level, t, xx, yy, zz, this);
                }
    }

    /** Entity::playStepSound: снег сверху — его звук, жидкости молчат. */
    protected void playStepSound(int xt, int yt, int zt, int t) {
        if (MC.instance == null) return;
        if (level.getTile(xt, yt + 1, zt) == Tile.SNOW_LAYER) t = Tile.SNOW_LAYER;
        if (!Tile.isLiquid(t)) MC.instance.playSoundAt(Tile.stepSound(t), x, y, z, Tile.soundVolume(t) * 0.25f);
    }

    private boolean freeBelow(float dx, float dz) {
        float hw = bbW / 2;
        float ax0 = x - hw + dx, ay0 = y - 1f, az0 = z - hw + dz, ax1 = x + hw + dx, ay1 = y, az1 = z + hw + dz;
        collect(ax0, ay0, az0, ax1, ay1, az1);
        for (int i = 0; i < boxCount[0]; i++) {
            int o = i * 6;
            if (ax1 > boxes[o] && ax0 < boxes[o + 3] && ay1 > boxes[o + 1] && ay0 < boxes[o + 4] && az1 > boxes[o + 2] && az0 < boxes[o + 5])
                return false;
        }
        return true;
    }

    static float clipY(float[] b, int o, float x0, float y0, float z0, float x1, float y1, float z1, float d) {
        if (x1 <= b[o] || x0 >= b[o + 3] || z1 <= b[o + 2] || z0 >= b[o + 5]) return d;
        if (d > 0 && y1 <= b[o + 1]) { float m = b[o + 1] - y1; if (m < d) d = m; }
        else if (d < 0 && y0 >= b[o + 4]) { float m = b[o + 4] - y0; if (m > d) d = m; }
        return d;
    }

    static float clipX(float[] b, int o, float x0, float y0, float z0, float x1, float y1, float z1, float d) {
        if (y1 <= b[o + 1] || y0 >= b[o + 4] || z1 <= b[o + 2] || z0 >= b[o + 5]) return d;
        if (d > 0 && x1 <= b[o]) { float m = b[o] - x1; if (m < d) d = m; }
        else if (d < 0 && x0 >= b[o + 3]) { float m = b[o + 3] - x0; if (m > d) d = m; }
        return d;
    }

    static float clipZ(float[] b, int o, float x0, float y0, float z0, float x1, float y1, float z1, float d) {
        if (x1 <= b[o] || x0 >= b[o + 3] || y1 <= b[o + 1] || y0 >= b[o + 4]) return d;
        if (d > 0 && z1 <= b[o + 2]) { float m = b[o + 2] - z1; if (m < d) d = m; }
        else if (d < 0 && z0 >= b[o + 5]) { float m = b[o + 5] - z0; if (m > d) d = m; }
        return d;
    }

    /** Движение по горизонтали относительно взгляда. */
    protected void moveRelative(float str, float fwd, float speed) {
        float d = Mth.sqrt(str * str + fwd * fwd);
        if (d < 0.01f) return;
        if (d < 1f) d = 1f;
        d = speed / d;
        str *= d;
        fwd *= d;
        float s = Mth.sin(yRot * Mth.DEG), c = Mth.cos(yRot * Mth.DEG);
        xd += str * c - fwd * s;
        zd += fwd * c + str * s;
    }

    protected void causeFall(float dist) {
    }

    /** Урон; source — кто нанёс (может быть null). true — урон прошёл. */
    public boolean hurt(int dmg, Entity source) {
        return false;
    }

    public float distanceToSqr(Entity e) {
        float dx = x - e.x, dy = y - e.y, dz = z - e.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distanceToSqr(float px, float py, float pz) {
        float dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public void remove() {
        removed = true;
    }

    /** Можно ли ударить/ткнуть (луч касания). */
    public boolean isPickable() {
        return false;
    }

    /** Коробка в мире для попадания лучом. */
    public float rayHit(float ox, float oy, float oz, float dx, float dy, float dz) {
        float hw = bbW / 2 + 0.1f;
        return rayBox(ox, oy, oz, dx, dy, dz, x - hw, y, z - hw, x + hw, y + bbH + 0.1f, z + hw);
    }

    /** луч против рамки, расширенной на g во все стороны (AABB::grow + clip) */
    public float rayHitGrow(float ox, float oy, float oz, float dx, float dy, float dz, float g) {
        float hw = bbW / 2 + g;
        return rayBox(ox, oy, oz, dx, dy, dz, x - hw, y - g, z - hw, x + hw, y + bbH + g, z + hw);
    }

    static float rayBox(float ox, float oy, float oz, float dx, float dy, float dz, float x0, float y0, float z0, float x1, float y1, float z1) {
        float tmin = -1e9f, tmax = 1e9f;
        float[] o = { ox, oy, oz }, d = { dx, dy, dz }, lo = { x0, y0, z0 }, hi = { x1, y1, z1 };
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-7f) {
                if (o[a] < lo[a] || o[a] > hi[a]) return -1;
                continue;
            }
            float t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1;
        }
        if (tmax < 0) return -1;
        return tmin < 0 ? 0 : tmin;
    }

    // ---- NBT (общая часть, как в MCPE) ----

    /** Смещение Pos.y относительно ног при сохранении. */
    protected float savedYOffset() {
        return 0f;
    }

    public float savedYOffsetPublic() {
        return savedYOffset();
    }

    public void saveBase(Nbt.Compound t) {
        t.putInt("id", typeId());
        t.putList("Pos", Nbt.NList.floats(x, y + savedYOffset(), z));
        t.putList("Motion", Nbt.NList.floats(xd, yd, zd));
        t.putList("Rotation", Nbt.NList.floats(yRot, xRot));
        t.putFloat("FallDistance", fallDistance);
        t.putShort("Fire", Math.max(0, fire));
        t.putShort("Air", air);
        t.putBoolean("OnGround", onGround);
    }

    public void loadBase(Nbt.Compound t) {
        Nbt.NList pos = t.getList("Pos");
        if (pos != null && pos.size() >= 3) setPos(pos.getFloat(0), pos.getFloat(1) - savedYOffset(), pos.getFloat(2));
        Nbt.NList mot = t.getList("Motion");
        if (mot != null && mot.size() >= 3) {
            xd = mot.getFloat(0); yd = mot.getFloat(1); zd = mot.getFloat(2);
        }
        Nbt.NList rot = t.getList("Rotation");
        if (rot != null && rot.size() >= 2) {
            yRot = yRotO = rot.getFloat(0);
            xRot = xRotO = rot.getFloat(1);
        }
        fallDistance = t.getFloat("FallDistance");
        if (t.contains("Fire")) fire = t.getShort("Fire");
        if (t.contains("Air")) air = t.getShort("Air");
        onGround = t.getBoolean("OnGround");
    }

    public abstract void save(Nbt.Compound t);

    public abstract void load(Nbt.Compound t);
}
