import java.util.Random;

/**
 * Появление мобов — MobSpawner::tick 0.6.1 (только выживание): раз в 2 тика монстры по чанкам вокруг игрока,
 * животные — когда время % 400 < 2, по всем чанкам. Кластеры до 4, не ближе 24 блоков к игроку и точке спавна,
 * лимиты: монстров 20, животных 15. Виды и веса — по биому (Biome 0.6.1).
 */
public final class MobSpawner {
    static final int MIN_SPAWN_DISTANCE = 24;
    private static final int[] MONSTER_ID = { Mobs.SPIDER, Mobs.ZOMBIE, Mobs.SKELETON, Mobs.CREEPER };
    private static final int[] MONSTER_W = { 8, 12, 6, 4 }, MONSTER_MIN = { 2, 2, 1, 1 }, MONSTER_MAX = { 3, 4, 3, 1 };
    private static final int[] CREATURE_ID = { Mobs.SHEEP, Mobs.PIG, Mobs.CHICKEN, Mobs.COW };
    private static final int[] CREATURE_W = { 12, 10, 10, 8 }, CREATURE_MIN = { 2, 1, 2, 2 }, CREATURE_MAX = { 3, 3, 4, 3 };
    private static final int MONSTER_TOTAL_W = 30, MAX_MONSTERS = 20, MAX_CREATURES = 15;

    public static void tick(Level l, Player p, Random r, long ticks) {
        if (p == null || l.gameType != 0 || (ticks & 1) != 0) return;
        boolean friendlies = (l.time % 400) < 2;
        boolean enemies = !friendlies && !Options.peaceful;
        int cx0 = 0, cx1 = 15, cz0 = 0, cz1 = 15;
        if (!friendlies) {
            int xx = Mth.floor(p.x / 16), zz = Mth.floor(p.z / 16);
            cx0 = Math.max(0, xx - 8); cx1 = Math.min(15, xx + 8);
            cz0 = Math.max(0, zz - 8); cz1 = Math.min(15, zz + 8);
        }
        if (enemies) spawnCategory(l, p, r, true, cx0, cx1, cz0, cz1);
        if (friendlies) spawnCategory(l, p, r, false, cx0, cx1, cz0, cz1);
    }

    private static boolean solidBlocking(Level l, int x, int y, int z) {
        return l.isSolidBlockingTile(x, y, z);
    }

    private static boolean spawnPositionOk(Level l, int x, int y, int z) {
        return solidBlocking(l, x, y - 1, z) && !solidBlocking(l, x, y, z) && !Tile.isLiquid(l.getTile(x, y, z)) && !solidBlocking(l, x, y + 1, z);
    }

    private static void spawnCategory(Level l, Player p, Random r, boolean monster, int cx0, int cx1, int cz0, int cz1) {
        int num = l.countEntities(monster);
        if (num > (monster ? MAX_MONSTERS : MAX_CREATURES)) return;
        for (int cx = cx0; cx <= cx1; cx++)
            chunks:
            for (int cz = cz0; cz <= cz1; cz++) {
                int xs = cx * 16 + r.nextInt(16), ys = r.nextInt(Level.HEIGHT), zs = cz * 16 + r.nextInt(16);
                if (solidBlocking(l, xs, ys, zs) || l.getTile(xs, ys, zs) != 0) continue;
                int cluster = 0;
                for (int dd = 0; dd < 3; dd++) {
                    int x = xs, y = ys, z = zs;
                    int type = -1, maxCount = 999, curCount = 0;
                    for (int ll = 0; ll < 4; ll++) {
                        if (curCount > maxCount) break;
                        x += r.nextInt(6) - r.nextInt(6);
                        z += r.nextInt(6) - r.nextInt(6);
                        if (!Level.inside(x, y, z) || !spawnPositionOk(l, x, y, z)) continue;
                        float fx = x + 0.5f, fy = y, fz = z + 0.5f;
                        if (p.distanceToSqr(fx, fy, fz) < MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) continue;
                        float sdx = fx - l.spawnX, sdy = fy - l.spawnY, sdz = fz - l.spawnZ;
                        if (sdx * sdx + sdy * sdy + sdz * sdz < MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) continue;
                        if (type < 0) {
                            int biome = Gen.biomeAt(l.seed, x, z);
                            if (!monster && (biome == Gen.B_DESERT || biome == Gen.B_ICE_DESERT)) break;
                            int sel = r.nextInt(monster ? MONSTER_TOTAL_W : 40);
                            int[] w = monster ? MONSTER_W : CREATURE_W;
                            for (int i = 0; i < 4; i++) {
                                sel -= w[i];
                                if (sel < 0) { type = i; break; }
                            }
                            if (monster) {
                                int typeCount = l.countType(MONSTER_ID[type]);
                                int typeMax = (int) (1.5f * MONSTER_W[type] * MAX_MONSTERS) / MONSTER_TOTAL_W;
                                if (typeCount >= typeMax) break;
                            }
                            int mn = monster ? MONSTER_MIN[type] : CREATURE_MIN[type], mx = monster ? MONSTER_MAX[type] : CREATURE_MAX[type];
                            maxCount = mn + r.nextInt(1 + mx - mn);
                        }
                        Entity e = Mobs.create(monster ? MONSTER_ID[type] : CREATURE_ID[type], l);
                        e.setPos(fx, fy, fz);
                        if (!canSpawn(l, e, monster, r)) continue;
                        e.yRot = e.yRotO = r.nextFloat() * 360;
                        // MobSpawner::finalizeMobSettings: цвет овцы
                        if (e instanceof Mobs.Sheep) ((Mobs.Sheep) e).color = Mobs.Sheep.randomColor(l.random);
                        l.addEntity(e);
                        curCount++;
                        if (++cluster >= 4) continue chunks;
                    }
                }
            }
    }

    /** Mob::canSpawn + Animal/Monster::canSpawn. */
    private static boolean canSpawn(Level l, Entity e, boolean monster, Random r) {
        int xt = Mth.floor(e.x), yt = Mth.floor(e.y), zt = Mth.floor(e.z);
        if (monster) {
            if (l.getSky(xt, yt, zt) > r.nextInt(32)) return false;
            if (l.getRawBrightness(xt, yt, zt) > r.nextInt(8)) return false;
        } else {
            if (l.getTile(xt, yt - 1, zt) != Tile.GRASS || l.getRawBrightness(xt, yt, zt) <= 8) return false;
        }
        // Mob::canSpawn: нет блоков, жидкостей и других существ; PathfinderMob: клетка годится для прогулки
        if (!e.isFree(0, 0, 0)) return false;
        java.util.Vector v = l.entities;
        float hw = e.bbW / 2;
        for (int i = -1; i < v.size(); i++) {
            Entity o = i < 0 ? l.player : (Entity) v.elementAt(i);
            if (o == null || o.removed || !o.blocksBuilding()) continue;
            float ow = o.bbW / 2;
            if (o.x + ow > e.x - hw && o.x - ow < e.x + hw && o.y + o.bbH > e.y && o.y < e.y + e.bbH && o.z + ow > e.z - hw && o.z - ow < e.z + hw) return false;
        }
        return ((PathfinderMob) e).walkTargetValue(xt, yt, zt) >= 0;
    }
}
