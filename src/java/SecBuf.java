/**
 * Квады секции мира 16x16x16 в сжатом виде — то, что LevelRenderer держит между перестройками (раньше 15 байт
 * на вершину, теперь 6). Цвет не хранится: он считается при склейке столбца из кода света, затенения и оттенка,
 * поэтому смена времени суток — только повторная склейка.
 *
 * Вершина: int — x, y, z в 1/16 блока от угла секции (по 9 бит со сдвигом +32) и u внутри тайла (5 бит),
 * плюс байт v внутри тайла. Квад: int — код света (8), затенение (2), направление грани (3), тайл (8), оттенок (8).
 * Порядок вершин как у MeshBuilder: полоса (a, b, d, c).
 */
public final class SecBuf {
    public int[] p = new int[256];
    public byte[] lv = new byte[256];
    public int[] q = new int[64];
    public int verts;
    /** код света | затенение << 8 | оттенок << 21 для следующих квадов (задаёт LevelRenderer.color) */
    public int curInfo;
    /** зеркальная развёртка по u относительно тайла (TileRenderer::xFlipTexture), -1 — нет */
    public int flipTu = -1;
    public static final int MAX_VERTS = 65532;

    /** затенение граней — одно из четырёх */
    static final int[] SHADES = { 255, 204, 153, 128 };
    /** оттенки (альфа << 24 | RGB) — набор небольшой, в квад пишется номер */
    static int[] tints = new int[16];
    static int nTints;

    static int shadeIndex(int s) {
        return s == 255 ? 0 : s == 204 ? 1 : s == 153 ? 2 : 3;
    }

    static int tintIndex(int t) {
        for (int i = 0; i < nTints; i++) if (tints[i] == t) return i;
        // в Part.info на оттенок 6 бит
        if (nTints == 64) return 0;
        if (nTints == tints.length) {
            int[] n = new int[nTints * 2];
            System.arraycopy(tints, 0, n, 0, nTints);
            tints = n;
        }
        tints[nTints] = t;
        return nTints++;
    }

    public void clear() {
        verts = 0;
    }

    private void ensure(int n) {
        int need = verts + n;
        if (need <= p.length) return;
        int cap = p.length;
        while (cap < need) cap *= 2;
        int[] np = new int[cap];
        System.arraycopy(p, 0, np, 0, verts);
        p = np;
        byte[] nl = new byte[cap];
        System.arraycopy(lv, 0, nl, 0, verts);
        lv = nl;
        int[] nq = new int[cap >> 2];
        System.arraycopy(q, 0, nq, 0, verts >> 2);
        q = nq;
    }

    static int pack(int x, int y, int z) {
        return ((x + 32) & 511) | (((y + 32) & 511) << 9) | (((z + 32) & 511) << 18);
    }

    /** Квад с вершинами a,b,c,d против часовой стрелки; цвета вершин не нужны (см. curInfo). */
    public void quad(int ax, int ay, int az, int au, int av, int ac,
                     int bx, int by, int bz, int bu, int bv, int bc,
                     int cx, int cy, int cz, int cu, int cv, int cc,
                     int dx, int dy, int dz, int du, int dv, int dc) {
        if (verts + 4 > MAX_VERTS) return;
        ensure(4);
        if (flipTu >= 0) {
            int f = 2 * flipTu + 16;
            au = f - au; bu = f - bu; cu = f - cu; du = f - du;
        }
        int tu = Math.min(Math.min(au, bu), Math.min(cu, du)) & ~15;
        int tv = Math.min(Math.min(av, bv), Math.min(cv, dv)) & ~15;
        if (tu > 240) tu = 240;
        if (tv > 240) tv = 240;
        int i = verts;
        put(i, ax, ay, az, au - tu, av - tv);
        put(i + 1, bx, by, bz, bu - tu, bv - tv);
        put(i + 2, dx, dy, dz, du - tu, dv - tv);
        put(i + 3, cx, cy, cz, cu - tu, cv - tv);
        int dir = dir(ax, ay, az, bx, by, bz, dx, dy, dz, cx, cy, cz);
        q[i >> 2] = curInfo | (dir << 10) | (((tu >> 4) | (tv & 0xF0)) << 13);
        verts = i + 4;
    }

    private void put(int i, int x, int y, int z, int u, int v) {
        if (u < 0) u = 0; else if (u > 31) u = 31;
        p[i] = pack(x, y, z) | (u << 27);
        lv[i] = (byte) v;
    }

    /**
     * Направление грани 0..5 (по нормали вдоль оси) или 6 — наклонная. Нормаль (v1 - v0) x (v2 - v0): у полосы
     * (a b d c) — наружу для лицевой стороны.
     */
    static int dir(int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
        boolean sx = x1 == x0 && x2 == x0 && x3 == x0;
        boolean sy = y1 == y0 && y2 == y0 && y3 == y0;
        boolean sz = z1 == z0 && z2 == z0 && z3 == z0;
        if (!sx && !sy && !sz) return 6;
        int ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        int bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        if (sx) return (ay * bz - az * by) > 0 ? 0 : 1;
        if (sy) return (az * bx - ax * bz) > 0 ? 2 : 3;
        return (ax * by - ay * bx) > 0 ? 4 : 5;
    }

    static int px(int p) { return (p & 511) - 32; }
    static int py(int p) { return ((p >> 9) & 511) - 32; }
    static int pz(int p) { return ((p >> 18) & 511) - 32; }

    /** Обратные стороны квадов с вершины from (двусторонние плоскости при отсечении граней). */
    public void backFaces(int from) {
        int end = verts;
        if (end + (end - from) > MAX_VERTS) return;
        ensure(end - from);
        for (int s = from; s < end; s += 4) {
            // полоса (v0 v1 v2 v3) -> (v1 v0 v3 v2): тот же квад с обратным обходом
            int j = verts;
            p[j] = p[s + 1]; lv[j] = lv[s + 1];
            p[j + 1] = p[s]; lv[j + 1] = lv[s];
            p[j + 2] = p[s + 3]; lv[j + 2] = lv[s + 3];
            p[j + 3] = p[s + 2]; lv[j + 3] = lv[s + 2];
            int a = p[j], b = p[j + 1], c = p[j + 2], d = p[j + 3];
            int dir = dir(px(a), py(a), pz(a), px(b), py(b), pz(b), px(c), py(c), pz(c), px(d), py(d), pz(d));
            q[j >> 2] = (q[s >> 2] & ~(7 << 10)) | (dir << 10);
            verts = j + 4;
        }
    }
}
