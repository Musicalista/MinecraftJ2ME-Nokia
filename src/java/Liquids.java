import java.util.Random;

/**
 * Жидкости — LiquidTile / LiquidTileDynamic / LiquidTileStatic 0.6.1.
 * Data: 0 — источник, 1..7 — удаление от источника, бит 8 — падающий поток. Текущая — 8/10, стоячая — 9/11.
 */
public final class Liquids {

    public static int tickDelay(int id) {
        return Tile.isWater(id) ? 5 : 30;
    }

    private static boolean sameMat(Level l, int id, int x, int y, int z) {
        int t = l.getTile(x, y, z);
        return Tile.isWater(id) ? Tile.isWater(t) : Tile.isLava(t);
    }

    /** LiquidTile::getDepth */
    private static int depth(Level l, int id, int x, int y, int z) {
        return sameMat(l, id, x, y, z) ? l.getData(x, y, z) : -1;
    }

    /** LiquidTile::updateLiquid: лава у воды — обсидиан (источник) или булыжник (до 4). */
    public static void updateLiquid(Level l, int id, int x, int y, int z) {
        if (l.getTile(x, y, z) != id || !Tile.isLava(id)) return;
        boolean water = l.getMaterial(x, y, z - 1) == Tile.MT_WATER || l.getMaterial(x, y, z + 1) == Tile.MT_WATER
                || l.getMaterial(x - 1, y, z) == Tile.MT_WATER || l.getMaterial(x + 1, y, z) == Tile.MT_WATER
                || l.getMaterial(x, y + 1, z) == Tile.MT_WATER;
        if (!water) return;
        int d = l.getData(x, y, z);
        if (d == 0) l.setTile(x, y, z, Tile.OBSIDIAN);
        else if (d <= 4) l.setTile(x, y, z, Tile.COBBLE);
        fizz(l, x, y, z);
    }

    /** LiquidTile::fizz */
    static void fizz(Level l, int x, int y, int z) {
        if (MC.instance == null) return;
        for (int i = 0; i < 8; i++) MC.instance.particles.largeSmoke(x + Mth.random(), y + 1.2f, z + Mth.random());
    }

    /** LiquidTileStatic::setDynamic */
    public static void setDynamic(Level l, int id, int x, int y, int z) {
        int d = l.getData(x, y, z);
        l.noNeighborUpdate = true;
        l.setTileAndDataNoUpdate(x, y, z, id - 1, d);
        l.addToTickNextTick(x, y, z, id - 1, tickDelay(id));
        l.noNeighborUpdate = false;
    }

    /** LiquidTileDynamic::setStatic */
    private static void setStatic(Level l, int id, int x, int y, int z) {
        l.setTileAndDataNoUpdate(x, y, z, id + 1, l.getData(x, y, z));
    }

    // поля LiquidTileDynamic общие для всех клеток (как у объекта тайла в оригинале)
    private static int maxCount;
    private static final boolean[] spreadRes = new boolean[4];
    private static final int[] spreadDist = new int[4];

    private static int highest(Level l, int id, int x, int y, int z, int cur) {
        int d = depth(l, id, x, y, z);
        if (d < 0) return cur;
        if (d == 0) maxCount++;
        if (d >= 8) d = 0;
        return cur < 0 || d < cur ? d : cur;
    }

    /** LiquidTileDynamic::isWaterBlocking */
    private static boolean waterBlocking(Level l, int x, int y, int z) {
        int t = l.getTile(x, y, z);
        if (t == 0) return false;
        if (t == Tile.WOOD_DOOR || t == Tile.SIGN || t == Tile.LADDER || t == Tile.REEDS) return true;
        int m = Tile.mat[t];
        return Tile.mBlocksMotion(m) || Tile.mSolid(m);
    }

    private static boolean canSpreadTo(Level l, int id, int x, int y, int z) {
        if (!Level.inside(x, y, z)) return false;
        int m = l.getMaterial(x, y, z);
        if (sameMat(l, id, x, y, z)) return false;
        if (m == Tile.MT_LAVA) return false;
        return !waterBlocking(l, x, y, z);
    }

    /** LiquidTileDynamic::tick */
    public static void tick(Level l, int id, int x, int y, int z, Random random) {
        int depth = depth(l, id, x, y, z);
        int dropOff = Tile.isLava(id) ? 2 : 1;
        boolean becomeStatic = true;
        if (depth > 0) {
            maxCount = 0;
            int h = -100;
            h = highest(l, id, x - 1, y, z, h);
            h = highest(l, id, x + 1, y, z, h);
            h = highest(l, id, x, y, z - 1, h);
            h = highest(l, id, x, y, z + 1, h);
            int nd = h + dropOff;
            if (nd >= 8 || h < 0) nd = -1;
            if (depth(l, id, x, y + 1, z) >= 0) {
                int above = depth(l, id, x, y + 1, z);
                nd = above >= 8 ? above : above + 8;
            }
            if (maxCount >= 2 && Tile.isWater(id)) {
                if (l.isSolidBlockingTile(x, y - 1, z)) nd = 0;
                else if (sameMat(l, id, x, y - 1, z) && l.getData(x, y, z) == 0) nd = 0;
            }
            if (Tile.isLava(id) && depth < 8 && nd < 8 && nd > depth && random.nextInt(4) != 0) {
                nd = depth;
                becomeStatic = false;
            }
            if (nd != depth) {
                depth = nd;
                if (depth < 0) l.setTile(x, y, z, 0);
                else {
                    l.setData(x, y, z, depth);
                    l.addToTickNextTick(x, y, z, id, tickDelay(id));
                    l.updateNeighborsAt(x, y, z, id);
                }
            } else if (becomeStatic) setStatic(l, id, x, y, z);
        } else setStatic(l, id, x, y, z);
        if (canSpreadTo(l, id, x, y - 1, z)) {
            l.setTileAndData(x, y - 1, z, id, depth >= 8 ? depth : depth + 8);
        } else if (depth >= 0 && (depth == 0 || waterBlocking(l, x, y - 1, z))) {
            boolean[] sp = spread(l, id, x, y, z);
            int neighbor = depth + dropOff;
            if (depth >= 8) neighbor = 1;
            if (neighbor >= 8) return;
            if (sp[0]) trySpreadTo(l, id, x - 1, y, z, neighbor);
            if (sp[1]) trySpreadTo(l, id, x + 1, y, z, neighbor);
            if (sp[2]) trySpreadTo(l, id, x, y, z - 1, neighbor);
            if (sp[3]) trySpreadTo(l, id, x, y, z + 1, neighbor);
        }
    }

    private static void trySpreadTo(Level l, int id, int x, int y, int z, int neighbor) {
        if (!canSpreadTo(l, id, x, y, z)) return;
        int old = l.getTile(x, y, z);
        if (old > 0) {
            if (Tile.isLava(id)) fizz(l, x, y, z);
            else TileLogic.spawnResources(l, old, x, y, z, l.getData(x, y, z), 1);
        }
        l.setTileAndData(x, y, z, id, neighbor);
    }

    private static boolean[] spread(Level l, int id, int x, int y, int z) {
        int[] dist = spreadDist;
        for (int d = 0; d < 4; d++) {
            dist[d] = 1000;
            int xx = x, zz = z;
            if (d == 0) xx--;
            if (d == 1) xx++;
            if (d == 2) zz--;
            if (d == 3) zz++;
            if (waterBlocking(l, xx, y, zz)) continue;
            if (sameMat(l, id, xx, y, zz) && l.getData(xx, y, zz) == 0) continue;
            if (!waterBlocking(l, xx, y - 1, zz)) dist[d] = 0;
            else dist[d] = slopeDistance(l, id, xx, y, zz, 1, d);
        }
        int lowest = dist[0];
        for (int d = 1; d < 4; d++) if (dist[d] < lowest) lowest = dist[d];
        for (int d = 0; d < 4; d++) spreadRes[d] = dist[d] == lowest;
        return spreadRes;
    }

    private static int slopeDistance(Level l, int id, int x, int y, int z, int pass, int from) {
        int lowest = 1000;
        for (int d = 0; d < 4; d++) {
            if ((d == 0 && from == 1) || (d == 1 && from == 0) || (d == 2 && from == 3) || (d == 3 && from == 2)) continue;
            int xx = x, zz = z;
            if (d == 0) xx--;
            if (d == 1) xx++;
            if (d == 2) zz--;
            if (d == 3) zz++;
            if (waterBlocking(l, xx, y, zz)) continue;
            if (sameMat(l, id, xx, y, zz) && l.getData(xx, y, zz) == 0) continue;
            if (!waterBlocking(l, xx, y - 1, zz)) return pass;
            if (pass < 4) {
                int v = slopeDistance(l, id, xx, y, zz, pass + 1, d);
                if (v < lowest) lowest = v;
            }
        }
        return lowest;
    }

    // ---- течение (LiquidTile::getFlow) для сущностей ----

    /** LiquidTile::getHeight: доля пустоты сверху клетки */
    public static float height(int d) {
        if (d >= 8) d = 0;
        return (d + 1) / 9f;
    }

    private static int renderedDepth(Level l, int id, int x, int y, int z) {
        if (!sameMat(l, id, x, y, z)) return -1;
        int d = l.getData(x, y, z);
        return d >= 8 ? 0 : d;
    }

    private static boolean shouldRenderFace(Level l, int id, int x, int y, int z, int face) {
        int m = l.getMaterial(x, y, z);
        if (m == Tile.mat[id]) return false;
        if (m == Tile.MT_ICE) return false;
        if (face == 1) return true;
        return !Tile.opaqueRender(l.getTile(x, y, z));
    }

    /** LiquidTile::getFlow: нормированный вектор течения, out[0..2]. */
    public static void flow(Level l, int id, int x, int y, int z, float[] out) {
        float fx = 0, fy = 0, fz = 0;
        int mid = renderedDepth(l, id, x, y, z);
        for (int d = 0; d < 4; d++) {
            int xt = x, zt = z;
            if (d == 0) xt--;
            if (d == 1) zt--;
            if (d == 2) xt++;
            if (d == 3) zt++;
            int t = renderedDepth(l, id, xt, y, zt);
            if (t < 0) {
                if (!Tile.mBlocksMotion(l.getMaterial(xt, y, zt))) {
                    t = renderedDepth(l, id, xt, y - 1, zt);
                    if (t >= 0) {
                        int dir = t - (mid - 8);
                        fx += (xt - x) * dir;
                        fz += (zt - z) * dir;
                    }
                }
            } else {
                int dir = t - mid;
                fx += (xt - x) * dir;
                fz += (zt - z) * dir;
            }
        }
        if (l.getData(x, y, z) >= 8) {
            boolean ok = shouldRenderFace(l, id, x, y, z - 1, 2) || shouldRenderFace(l, id, x, y, z + 1, 3)
                    || shouldRenderFace(l, id, x - 1, y, z, 4) || shouldRenderFace(l, id, x + 1, y, z, 5)
                    || shouldRenderFace(l, id, x, y + 1, z - 1, 2) || shouldRenderFace(l, id, x, y + 1, z + 1, 3)
                    || shouldRenderFace(l, id, x - 1, y + 1, z, 4) || shouldRenderFace(l, id, x + 1, y + 1, z, 5);
            if (ok) {
                float len = Mth.sqrt(fx * fx + fy * fy + fz * fz);
                if (len > 1e-4f) { fx /= len; fy /= len; fz /= len; } else { fx = fy = fz = 0; }
                fy -= 6;
            }
        }
        float len = Mth.sqrt(fx * fx + fy * fy + fz * fz);
        if (len < 1e-4f) { out[0] = out[1] = out[2] = 0; return; }
        out[0] = fx / len; out[1] = fy / len; out[2] = fz / len;
    }
}
