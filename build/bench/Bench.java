/**
 * Headless benchmark of the game logic (no rendering, no M3G).
 * Generates a world from a fixed seed, runs a fixed number of ticks with a player present,
 * and prints timings plus a checksum of the final block state. The checksum must not change
 * between optimizations: that is the determinism check.
 *
 * usage: java -cp <classes>:<bench> Bench [seed] [ticks]
 */
public class Bench {
    /** print a state checkpoint every N ticks (0 = off), used to locate divergence between runs */
    static final int CHECKPOINT = Integer.getInteger("bench.cp", 0);

    static long stateHash(Level l) {
        long h = 1125899906842597L;
        for (int c = 0; c < 256; c++)
            for (int i = 0; i < 16 * 16 * 128; i++) h = 31 * h + l.blocks.get(c, i);
        for (int i = 0; i < l.height.length; i++) h = 31 * h + l.height[i];
        return h;
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        int ticks = args.length > 1 ? Integer.parseInt(args[1]) : 600;

        long t0 = System.currentTimeMillis();
        Level l = new Level();
        l.name = "bench";
        l.seed = seed;
        l.gameType = 0;
        l.isClientSide = false;
        new Gen(l, seed).generate(new Gen.Progress() {
            public void progress(String stage, int pct) {}
        });
        long genMs = System.currentTimeMillis() - t0;

        Player p = new Player(l, false);
        l.player = p;
        l.spawnX = 128; l.spawnY = 64; l.spawnZ = 128;
        p.setPos(128.5f, 70f, 128.5f);

        // Mob.rnd is an unseeded Random per mob: reseed it from the entity index before every tick
        // so runs are reproducible (test-only, via reflection; game code is untouched).
        java.lang.reflect.Field rndField = null;
        try {
            rndField = Mob.class.getDeclaredField("rnd");
            rndField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Mth.random() (used by entities, liquids, TNT) draws from a static unseeded Random: reseed its state.
        java.util.Random mthRnd;
        try {
            java.lang.reflect.Field f = Mth.class.getDeclaredField("RND");
            f.setAccessible(true);
            mthRnd = (java.util.Random) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        t0 = System.currentTimeMillis();
        for (int i = 0; i < ticks; i++) {
            l.random.setSeed(seed * 31 + i);
            mthRnd.setSeed(seed * 17 + i);
            try {
                rndField.set(l.player, new java.util.Random(seed * 53 + i));
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
            for (int e = 0; e < l.entities.size(); e++) {
                Object o = l.entities.elementAt(e);
                if (o instanceof Mob) {
                    try {
                        rndField.set(o, new java.util.Random(seed * 131 + e * 7919L + i));
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            l.tick();
            if (CHECKPOINT > 0 && (i + 1) >= Integer.getInteger("bench.cpFrom", 0) && (i + 1) % CHECKPOINT == 0) System.out.println("cp " + (i + 1) + " " + stateHash(l));
        }
        long tickMs = System.currentTimeMillis() - t0;

        long h = stateHash(l);

        System.out.println("gen_ms=" + genMs);
        System.out.println("tick_ms=" + tickMs + " ticks=" + ticks + " per_tick_us=" + (tickMs * 1000.0 / ticks));
        System.out.println("checksum=" + Long.toHexString(h));
    }
}
