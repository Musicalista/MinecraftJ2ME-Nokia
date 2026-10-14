/** Картина (id 83) — HangingEntity + Painting 0.6.1: висит на стене, сюжет случайный из тех, что помещаются. */
public final class Painting extends Entity {
    // Motive 0.6.1 в порядке getAllMotivesAsList(): имя, ширина, высота (пиксели), u, v в art/kz.png, публичный
    static final String[] NAME = { "Kebab", "Aztec2", "Alban", "Bomb", "Plant", "Wasteland", "Pool", "Courbet", "Sea",
            "Sunset", "Creebet", "Wanderer", "Graham", "Match", "Bust", "Stage", "Void", "SkullAndRoses", "Fighters",
            "Pointer", "Pigscene", "BurningSkull", "Skeleton", "DonkeyKong", "Earth", "Wind", "Fire", "Water" };
    static final int[] W = { 16, 16, 16, 16, 16, 16, 32, 32, 32, 32, 32, 16, 16, 32, 32, 32, 32, 32, 64, 64, 64, 64, 64, 64, 32, 32, 32, 32 };
    static final int[] H = { 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 32, 32, 32, 32, 32, 32, 32, 32, 64, 64, 64, 48, 48, 32, 32, 32, 32 };
    static final int[] U = { 0, 48, 32, 64, 80, 96, 0, 32, 64, 96, 128, 0, 16, 0, 32, 64, 96, 128, 0, 0, 64, 128, 192, 192, 0, 32, 64, 96 };
    static final int[] V = { 0, 0, 0, 0, 0, 0, 32, 32, 32, 32, 32, 64, 64, 128, 128, 128, 128, 128, 96, 192, 192, 192, 64, 112, 160, 160, 160, 160 };
    static final int PUBLIC = 24;

    /** Direction: 0 юг, 1 запад, 2 север, 3 восток; для грани блока 2..5 */
    static final int[] FACING_DIRECTION = { -1, -1, 2, 0, 1, 3 };
    static final int[] OPPOSITE = { 2, 3, 0, 1 };

    public int motive, dir, xTile, yTile, zTile;
    private int checkInterval;
    /** коробка картины в мире */
    public float bx0, by0, bz0, bx1, by1, bz1;

    public Painting(Level l) {
        super(l);
        bbW = 0.5f;
        bbH = 0.5f;
        stepHeight = 0;
    }

    public Painting(Level l, int x, int y, int z, int dir) {
        this(l);
        xTile = x; yTile = y; zTile = z;
        setRandomMotive(dir);
    }

    public int typeId() {
        return 83;
    }

    private void setRandomMotive(int dir) {
        int[] ok = new int[PUBLIC];
        int n = 0;
        for (int i = 0; i < PUBLIC; i++) {
            motive = i;
            setDir(dir);
            if (survives()) ok[n++] = i;
        }
        motive = n > 0 ? ok[level.random.nextInt(n)] : 0;
        setDir(dir);
    }

    private static float offs(int w) {
        return w == 32 || w == 64 ? 0.5f : 0f;
    }

    public void setDir(int dir) {
        this.dir = dir;
        yRot = yRotO = dir * 90;
        float w = W[motive], h = H[motive], d = W[motive];
        if (dir == 2 || dir == 0) {
            d = 2f;
            yRot = yRotO = OPPOSITE[dir] * 90;
        } else w = 2f;
        w /= 32f; h /= 32f; d /= 32f;
        float x = xTile + 0.5f, y = yTile + 0.5f, z = zTile + 0.5f;
        float off = 0.5f + 1f / 16f;
        if (dir == 2) z -= off;
        if (dir == 1) x -= off;
        if (dir == 0) z += off;
        if (dir == 3) x += off;
        if (dir == 2) x -= offs(W[motive]);
        if (dir == 1) z += offs(W[motive]);
        if (dir == 0) x += offs(W[motive]);
        if (dir == 3) z -= offs(W[motive]);
        y += offs(H[motive]);
        setPos(x, y, z);
        float ss = -(0.5f / 16f);
        bx0 = x - w - ss; by0 = y - h - ss; bz0 = z - d - ss;
        bx1 = x + w + ss; by1 = y + h + ss; bz1 = z + d + ss;
    }

    public boolean survives() {
        // внутри картины не должно быть блоков
        for (int xx = Mth.floor(bx0); xx <= Mth.floor(bx1); xx++)
            for (int yy = Mth.floor(by0); yy <= Mth.floor(by1); yy++)
                for (int zz = Mth.floor(bz0); zz <= Mth.floor(bz1); zz++)
                    if (TileLogic.hasCollision(level, level.getTile(xx, yy, zz), xx, yy, zz)) return false;
        int ws = Math.max(1, W[motive] / 16), hs = Math.max(1, H[motive] / 16);
        int xt = xTile, zt = zTile;
        if (dir == 2 || dir == 0) xt = Mth.floor(x - W[motive] / 32f);
        else zt = Mth.floor(z - W[motive] / 32f);
        int yt = Mth.floor(y - H[motive] / 32f);
        for (int s = 0; s < ws; s++)
            for (int yy = 0; yy < hs; yy++) {
                int t = (dir == 2 || dir == 0) ? level.getTile(xt + s, yt + yy, zTile) : level.getTile(xTile, yt + yy, zt + s);
                if (!Tile.mSolid(Tile.mat[t])) return false;
            }
        java.util.Vector v = level.entities;
        for (int i = 0; i < v.size(); i++) {
            Object o = v.elementAt(i);
            if (o == this || !(o instanceof Painting)) continue;
            Painting p = (Painting) o;
            if (!p.removed && p.bx1 > bx0 && p.bx0 < bx1 && p.by1 > by0 && p.by0 < by1 && p.bz1 > bz0 && p.bz0 < bz1) return false;
        }
        return true;
    }

    public void tick() {
        xo = x; yo = y; zo = z;
        tickCount++;
        if (checkInterval++ == 20 * 5) {
            checkInterval = 0;
            if (!removed && !survives()) {
                remove();
                dropItem();
            }
        }
    }

    private void dropItem() {
        if (level.gameType != 1) level.addEntity(new ItemEntity(level, x, y, z, 321, 1, 0));
    }

    public boolean hurt(int dmg, Entity source) {
        if (!removed) {
            remove();
            dropItem();
        }
        return true;
    }

    public boolean isPickable() {
        return true;
    }

    public float rayHit(float ox, float oy, float oz, float dx, float dy, float dz) {
        float tmin = -1e9f, tmax = 1e9f;
        float[] o = { ox, oy, oz }, d = { dx, dy, dz }, lo = { bx0, by0, bz0 }, hi = { bx1, by1, bz1 };
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

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putString("Motive", NAME[motive]);
        t.putByte("Direction", dir);
        t.putInt("TileX", xTile);
        t.putInt("TileY", yTile);
        t.putInt("TileZ", zTile);
        // как в оригинале — старое поле Dir для совместимости
        t.putByte("Dir", dir == 2 ? 0 : (dir == 1 ? 1 : (dir == 0 ? 2 : 3)));
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        String m = t.getString("Motive");
        motive = 0;
        for (int i = 0; i < NAME.length; i++) if (NAME[i].equals(m)) motive = i;
        if (t.contains("Direction")) dir = t.getByte("Direction");
        else {
            int d = t.getByte("Dir");
            dir = d == 0 ? 2 : (d == 1 ? 1 : (d == 2 ? 0 : 3));
        }
        dir &= 3;
        xTile = t.getInt("TileX");
        yTile = t.getInt("TileY");
        zTile = t.getInt("TileZ");
        setDir(dir);
    }
}
