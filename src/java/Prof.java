/** Отладочный профиль (свойство mcj.debug): суммы времени по разделам кадра и тика, раз в 5 с — строка в лог. */
final class Prof {
    static final boolean on = System.getProperty("mcj.debug") != null;

    // кадр
    static final int F_UPD = 0, F_SKY = 1, F_WORLD = 2, F_ENT = 3, F_SIGN = 4, F_PART = 5, F_HAND = 6, F_REL = 7,
            F_HUD = 8, F_FLUSH = 9, F_SCREEN = 10,
    // тик
            T_PLAYER = 11, T_PEND = 12, T_RAND = 13, T_ENT = 14, T_AI = 15, T_LIQ = 16, T_PART = 17, T_SPAWN = 18,
            T_SAVE = 19, T_OTHER = 20,
    // перестройка мешей
            M_TESS = 21, M_COMB = 22, M_LIGHT = 23, N = 24;
    static final String[] NAMES = { "upd", "sky", "world", "ent", "sign", "part", "hand", "rel", "hud", "flush", "scr",
            "pl", "pend", "rand", "ent", "ai", "liq", "part", "spawn", "save", "oth", "tess", "comb", "light" };
    static final long[] acc = new long[N];
    /** отладка: mcj.exp=буквы (d — без текста отладки на HUD, n — без мира) */
    static final String exp = System.getProperty("mcj.exp") == null ? "" : System.getProperty("mcj.exp");
    static boolean x(char c) { return exp.indexOf(c) >= 0; }
    static int calls, bytesVerts, shortVerts;
    static long drawVerts, padVerts;
    /** регрессия времени вызова render() по числу вершин: мс = a + b * вершины */
    static double sx, sy, sxx, sxy;

    static long firstCall;
    static int frameCalls;

    static void call(int verts, long t0) {
        long ms = System.currentTimeMillis() - t0;
        if (frameCalls++ == 0) { firstCall += ms; return; }
        calls++;
        sx += verts; sy += ms; sxx += (double) verts * verts; sxy += (double) verts * ms;
    }
    static int frames, ticks, rebuilds, combines, gcs, hitches, paths;
    static long frameMs, maxFrame, maxTick, lastFree, start;

    static long t() {
        return System.currentTimeMillis();
    }

    static void add(int k, long t0) {
        acc[k] += System.currentTimeMillis() - t0;
    }

    /** Конец кадра: длительность от начала цикла, учёт сборок мусора по скачку свободной памяти. */
    static void frame(long ms) {
        frameCalls = 0;
        frames++;
        frameMs += ms;
        if (ms > maxFrame) maxFrame = ms;
        if (ms >= 100) hitches++;
        long free = Runtime.getRuntime().freeMemory();
        if (free > lastFree + 512 * 1024) gcs++;
        lastFree = free;
    }

    static void tick(long ms) {
        ticks++;
        if (ms > maxTick) maxTick = ms;
    }

    static void report(MC mc) {
        long now = t();
        if (start == 0) { start = now; return; }
        if (now - start < 5000) return;
        float sec = (now - start) / 1000f;
        StringBuffer sb = new StringBuffer();
        sb.append("PROF fps ").append((int) (frames / sec * 10) / 10f).append(" frame ").append(frames > 0 ? frameMs / frames : 0)
                .append(" max ").append(maxFrame).append(" hitch ").append(hitches).append(" |F");
        for (int k = F_UPD; k <= F_SCREEN; k++) sb.append(' ').append(NAMES[k]).append(' ').append(per(acc[k], frames));
        sb.append(" first ").append(per(firstCall, frames)).append(" calls ").append(frames > 0 ? calls / frames : 0);
        firstCall = 0;
        sb.append(" dv ").append(frames > 0 ? drawVerts / frames : 0);
        drawVerts = 0;
        if (calls > 2) {
            double n = calls, b = (n * sxy - sx * sy) / (n * sxx - sx * sx), a = (sy - b * sx) / n;
            sb.append(" verts ").append((int) (sx / Math.max(1, frames))).append(" call ").append((int) (a * 1000)).append("us+")
                    .append((int) (b * 1e6)).append("us/kv");
        }
        sx = sy = sxx = sxy = 0;
        sb.append(" bv ").append(bytesVerts).append(" sv ").append(shortVerts).append(" pad ").append(padVerts);
        sb.append(" |T n ").append(ticks).append(" max ").append(maxTick);
        for (int k = T_PLAYER; k <= T_OTHER; k++) sb.append(' ').append(NAMES[k]).append(' ').append(per(acc[k], ticks));
        sb.append(" |M rb ").append(rebuilds).append(" cb ").append(combines);
        for (int k = M_TESS; k <= M_LIGHT; k++) sb.append(' ').append(NAMES[k]).append(' ').append(acc[k]);
        Runtime r = Runtime.getRuntime();
        sb.append(" |mem ").append((r.totalMemory() - r.freeMemory()) >> 10).append('/').append(r.totalMemory() >> 10)
                .append("K gc ").append(gcs).append(" paths ").append(paths);
        if (mc.level != null) sb.append(" ent ").append(mc.level.entities.size()).append(" secs ").append(mc.lr != null ? mc.lr.lastDrawn : 0)
                .append(" pos ").append((int) mc.player.x).append(',').append((int) mc.player.y).append(',').append((int) mc.player.z);
        Log.i(sb.toString());
        for (int k = 0; k < N; k++) acc[k] = 0;
        frames = calls = ticks = rebuilds = combines = gcs = hitches = paths = 0;
        frameMs = maxFrame = maxTick = 0;
        start = now;
    }

    /** среднее на кадр/тик с одной десятой */
    private static String per(long total, int n) {
        if (n == 0) return "0";
        long v = total * 10 / n;
        return (v / 10) + "." + (v % 10);
    }
}
