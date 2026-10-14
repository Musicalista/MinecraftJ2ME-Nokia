import java.util.Random;

/**
 * Поведение блоков — порт классов *Tile 0.6.1: tick, neighborChanged, onPlace/onRemove, use/attack,
 * выпадение (getResource/spawnResources/playerDestroy), формы выбора (updateShape) и столкновений (getAABB/addAABBs).
 * Стороны: 0 низ, 1 верх, 2 -z, 3 +z, 4 -x, 5 +x.
 */
public final class TileLogic {

    /** Tile::shouldTick — случайные тики Level::tickTiles */
    public static final boolean[] ticking = new boolean[256];

    static {
        int[] t = { Tile.GRASS, Tile.SAPLING, Tile.WATER, Tile.LAVA, Tile.STILL_LAVA, Tile.LEAVES, Tile.TALLGRASS, Tile.FLOWER, Tile.ROSE,
                Tile.BROWN_MUSHROOM, Tile.RED_MUSHROOM, Tile.TORCH, Tile.CROPS, Tile.FARMLAND, Tile.REDSTONE_ORE_LIT, Tile.SNOW_LAYER,
                Tile.ICE, Tile.SNOW, Tile.CACTUS, Tile.REEDS, Tile.MELON_STEM, 254 };
        for (int i = 0; i < t.length; i++) ticking[t[i]] = true;
    }

    public static int fx(int f) { return f == 4 ? -1 : (f == 5 ? 1 : 0); }
    public static int fy(int f) { return f == 0 ? -1 : (f == 1 ? 1 : 0); }
    public static int fz(int f) { return f == 2 ? -1 : (f == 3 ? 1 : 0); }

    /** FurnaceTile::noDrop — смена горящей/погасшей печи без выпадения содержимого */
    static boolean noDrop;

    // ================= tick =================

    public static void tick(Level l, int id, int x, int y, int z, Random r) {
        switch (id) {
            case Tile.GRASS: grassTick(l, x, y, z, r); return;
            case Tile.SAPLING: saplingTick(l, x, y, z, r); return;
            case Tile.FLOWER: case Tile.ROSE: case Tile.TALLGRASS:
                checkAlive(l, id, x, y, z);
                return;
            case Tile.BROWN_MUSHROOM: case Tile.RED_MUSHROOM: mushroomTick(l, id, x, y, z, r); return;
            case Tile.CROPS: cropTick(l, x, y, z, r); return;
            case Tile.MELON_STEM: stemTick(l, x, y, z, r); return;
            case Tile.CACTUS: case Tile.REEDS: growTall(l, id, x, y, z, id == Tile.CACTUS ? 10 : 15); return;
            case Tile.LEAVES: leafTick(l, x, y, z); return;
            case Tile.FARMLAND: farmTick(l, x, y, z); return;
            case Tile.SNOW_LAYER: case Tile.SNOW:
                // TopSnowTile/SnowTile::tick: таяние от света блоков > 11 (ничего не выпадает)
                if (l.getBlockLight(x, y, z) > 11) l.setTile(x, y, z, 0);
                return;
            case Tile.ICE:
                if (l.getBlockLight(x, y, z) > 11 - Tile.lightBlock[Tile.ICE]) l.setTile(x, y, z, Tile.STILL_WATER);
                return;
            case Tile.TORCH:
                if (l.getData(x, y, z) == 0) torchOnPlace(l, x, y, z);
                return;
            case Tile.REDSTONE_ORE_LIT: l.setTile(x, y, z, Tile.REDSTONE_ORE); return;
            case Tile.WATER: case Tile.LAVA: {
                long a = Prof.t();
                Liquids.tick(l, id, x, y, z, r);
                if (Prof.on) Prof.add(Prof.T_LIQ, a);
                return;
            }
            case Tile.SAND: case Tile.GRAVEL: checkSlide(l, id, x, y, z); return;
        }
    }

    /** GrassTile::tick */
    private static void grassTick(Level l, int x, int y, int z, Random r) {
        if (l.getRawBrightness(x, y + 1, z) < 4 && Tile.mBlocksLight(l.getMaterial(x, y + 1, z))) {
            if (r.nextInt(4) != 0) return;
            l.setTile(x, y, z, Tile.DIRT);
        } else if (l.getRawBrightness(x, y + 1, z) >= 9) {
            int xt = x + r.nextInt(3) - 1, yt = y + r.nextInt(5) - 3, zt = z + r.nextInt(3) - 1;
            if (l.getTile(xt, yt, zt) == Tile.DIRT && l.getRawBrightness(xt, yt + 1, zt) >= 4 && !Tile.mBlocksLight(l.getMaterial(xt, yt + 1, zt)))
                l.setTile(xt, yt, zt, Tile.GRASS);
        }
    }

    /** Sapling::tick */
    private static void saplingTick(Level l, int x, int y, int z, Random r) {
        checkAlive(l, Tile.SAPLING, x, y, z);
        if (l.getTile(x, y, z) != Tile.SAPLING) return;
        if (l.getRawBrightness(x, y + 1, z) >= 9 && r.nextInt(7) == 0) {
            int d = l.getData(x, y, z);
            if ((d & 8) == 0) l.setDataNoUpdate(x, y, z, d | 8);
            else growTree(l, x, y, z, r);
        }
    }

    /** Sapling::growTree: при неудаче саженец возвращается без бита возраста. */
    public static void growTree(Level l, int x, int y, int z, Random r) {
        int d = l.getData(x, y, z) & 3;
        l.setTileNoUpdate(x, y, z, 0);
        if (!Gen.growTree(l, x, y, z, d, r)) l.setTileAndDataNoUpdate(x, y, z, Tile.SAPLING, d);
    }

    /** Mushroom::tick: расползание, не больше 5 грибов в окрестности 9x3x9. */
    private static void mushroomTick(Level l, int id, int x, int y, int z, Random r) {
        if (r.nextInt(25) != 0) return;
        int max = 5;
        for (int xx = x - 4; xx <= x + 4; xx++)
            for (int zz = z - 4; zz <= z + 4; zz++)
                for (int yy = y - 1; yy <= y + 1; yy++)
                    if (l.getTile(xx, yy, zz) == id && --max <= 0) return;
        int x2 = x + r.nextInt(3) - 1, y2 = y + r.nextInt(2) - r.nextInt(2), z2 = z + r.nextInt(3) - 1;
        for (int i = 0; i < 4; i++) {
            if (l.isEmptyTile(x2, y2, z2) && canSurvive(l, id, x2, y2, z2)) { x = x2; y = y2; z = z2; }
            x2 = x + r.nextInt(3) - 1;
            y2 = y + r.nextInt(2) - r.nextInt(2);
            z2 = z + r.nextInt(3) - 1;
        }
        if (l.isEmptyTile(x2, y2, z2) && canSurvive(l, id, x2, y2, z2)) l.setTile(x2, y2, z2, id);
    }

    /** CropTile::tick */
    private static void cropTick(Level l, int x, int y, int z, Random r) {
        checkAlive(l, Tile.CROPS, x, y, z);
        if (l.getRawBrightness(x, y, z) >= 9) {
            int age = l.getData(x, y, z);
            if (age < 7 && r.nextInt((int) (25 / growthSpeed(l, Tile.CROPS, x, y, z))) == 0) l.setData(x, y, z, age + 1);
        }
    }

    /** StemTile::tick: дозревший стебель ставит арбуз на первую подходящую сторону (-x, +x, -z, +z). */
    private static void stemTick(Level l, int x, int y, int z, Random r) {
        checkAlive(l, Tile.MELON_STEM, x, y, z);
        if (l.getRawBrightness(x, y + 1, z) < 9) return;
        if (r.nextInt((int) (25 / growthSpeed(l, Tile.MELON_STEM, x, y, z)) + 1) != 0) return;
        int age = l.getData(x, y, z);
        if (age < 7) {
            l.setData(x, y, z, age + 1);
            return;
        }
        if (l.getTile(x - 1, y, z) == Tile.MELON || l.getTile(x + 1, y, z) == Tile.MELON
                || l.getTile(x, y, z - 1) == Tile.MELON || l.getTile(x, y, z + 1) == Tile.MELON) return;
        for (int a = 0; a < 4; a++) {
            int xx = x, zz = z;
            if (a == 0) xx--;
            if (a == 1) xx++;
            if (a == 2) zz--;
            if (a == 3) zz++;
            int below = l.getTile(xx, y - 1, zz);
            if (l.getTile(xx, y, zz) == 0 && (below == Tile.FARMLAND || below == Tile.DIRT || below == Tile.GRASS)) {
                l.setTile(xx, y, zz, Tile.MELON);
                break;
            }
        }
    }

    /** CropTile/StemTile::getGrowthSpeed */
    private static float growthSpeed(Level l, int id, int x, int y, int z) {
        float speed = 1;
        int n = l.getTile(x, y, z - 1), s = l.getTile(x, y, z + 1), w = l.getTile(x - 1, y, z), e = l.getTile(x + 1, y, z);
        int d0 = l.getTile(x - 1, y, z - 1), d1 = l.getTile(x + 1, y, z - 1), d2 = l.getTile(x + 1, y, z + 1), d3 = l.getTile(x - 1, y, z + 1);
        boolean horizontal = w == id || e == id, vertical = n == id || s == id;
        boolean diagonal = d0 == id || d1 == id || d2 == id || d3 == id;
        for (int xx = x - 1; xx <= x + 1; xx++)
            for (int zz = z - 1; zz <= z + 1; zz++) {
                float ts = 0;
                if (l.getTile(xx, y - 1, zz) == Tile.FARMLAND) {
                    ts = 1;
                    if (l.getData(xx, y - 1, zz) > 0) ts = 3;
                }
                if (xx != x || zz != z) ts /= 4;
                speed += ts;
            }
        if (diagonal || (horizontal && vertical)) speed /= 2;
        return speed;
    }

    /** CactusTile/ReedTile::tick: до 3 блоков в высоту. */
    private static void growTall(Level l, int id, int x, int y, int z, int maxAge) {
        if (!l.isEmptyTile(x, y + 1, z)) return;
        int h = 1;
        while (l.getTile(x, y - h, z) == id) h++;
        if (h >= 3) return;
        int age = l.getData(x, y, z);
        if (age >= maxAge) {
            l.setTile(x, y + 1, z, id);
            l.setData(x, y, z, 0);
        } else l.setData(x, y, z, age + 1);
    }

    /** куб 16^3 вместо 32^3 оригинала: обход трогает только клетки в радиусе R + 1 от центра */
    private static final byte[] leafBuf = new byte[16 * 16 * 16];

    /** LeafTile::tick: опадание без бревна в радиусе 4 (по связности листвы). */
    private static void leafTick(Level l, int x, int y, int z) {
        int cur = l.getData(x, y, z);
        if ((cur & 4) == 0 || (cur & 8) != 0) return;
        final int R = 4, W = 16, WW = W * W, WO = W / 2;
        byte[] b = leafBuf;
        for (int xo = -R; xo <= R; xo++)
            for (int yo = -R; yo <= R; yo++)
                for (int zo = -R; zo <= R; zo++) {
                    int t = l.getTile(x + xo, y + yo, z + zo);
                    b[(xo + WO) * WW + (yo + WO) * W + (zo + WO)] = (byte) (t == Tile.LOG ? 0 : (t == Tile.LEAVES ? -2 : -1));
                }
        for (int i = 1; i <= R; i++)
            for (int xo = -R; xo <= R; xo++)
                for (int yo = -R; yo <= R; yo++)
                    for (int zo = -R; zo <= R; zo++) {
                        int p = (xo + WO) * WW + (yo + WO) * W + (zo + WO);
                        if (b[p] != i - 1) continue;
                        byte bi = (byte) i;
                        if (b[p - WW] == -2) b[p - WW] = bi;
                        if (b[p + WW] == -2) b[p + WW] = bi;
                        if (b[p - W] == -2) b[p - W] = bi;
                        if (b[p + W] == -2) b[p + W] = bi;
                        if (b[p - 1] == -2) b[p - 1] = bi;
                        if (b[p + 1] == -2) b[p + 1] = bi;
                    }
        if (b[WO * WW + WO * W + WO] >= 0) l.setDataNoUpdate(x, y, z, cur & ~4);
        else {
            spawnResources(l, Tile.LEAVES, x, y, z, l.getData(x, y, z) & 3, 0);
            l.setTile(x, y, z, 0);
        }
    }

    /** FarmTile::tick */
    private static void farmTick(Level l, int x, int y, int z) {
        boolean water = false;
        for (int xx = x - 4; xx <= x + 4 && !water; xx++)
            for (int yy = y; yy <= y + 1 && !water; yy++)
                for (int zz = z - 4; zz <= z + 4; zz++)
                    if (l.getMaterial(xx, yy, zz) == Tile.MT_WATER) { water = true; break; }
        if (water) l.setData(x, y, z, 7);
        else {
            int m = l.getData(x, y, z);
            if (m > 0) l.setData(x, y, z, m - 1);
            else if (l.getTile(x, y + 1, z) != Tile.CROPS) l.setTile(x, y, z, Tile.DIRT);
        }
    }

    // ================= HeavyTile =================

    private static boolean heavyFree(Level l, int x, int y, int z) {
        int t = l.getTile(x, y, z);
        if (t == 0 || t == 51) return true;
        int m = Tile.mat[t];
        return m == Tile.MT_WATER || m == Tile.MT_LAVA;
    }

    /** HeavyTile::checkSlide: падающий блок (FallingTile). */
    private static void checkSlide(Level l, int id, int x, int y, int z) {
        if (heavyFree(l, x, y - 1, z) && y >= 0) {
            FallingTile e = new FallingTile(l, x + 0.5f, y + 0.5f, z + 0.5f, id, l.getData(x, y, z));
            l.addEntity(e);
        }
    }

    // ================= опора и выживание =================

    /** Bush::mayPlaceOn (саженец, цветы, трава), CropTile/StemTile — только грядка. */
    private static boolean bushMayPlaceOn(int id, int below) {
        if (id == Tile.CROPS || id == Tile.MELON_STEM) return below == Tile.FARMLAND;
        if (id == Tile.BROWN_MUSHROOM || id == Tile.RED_MUSHROOM) return Tile.solidT[below];
        return below == Tile.GRASS || below == Tile.DIRT || below == Tile.FARMLAND;
    }

    private static boolean isBush(int id) {
        switch (id) {
            case Tile.SAPLING: case Tile.TALLGRASS: case Tile.FLOWER: case Tile.ROSE: case Tile.BROWN_MUSHROOM: case Tile.RED_MUSHROOM:
            case Tile.CROPS: case Tile.MELON_STEM:
                return true;
        }
        return false;
    }

    /** Tile::canSurvive для блоков, которые его переопределяют. */
    public static boolean canSurvive(Level l, int id, int x, int y, int z) {
        int below = l.getTile(x, y - 1, z);
        switch (id) {
            case Tile.BROWN_MUSHROOM: case Tile.RED_MUSHROOM:
                if (y < 0 || y >= Level.HEIGHT) return false;
                return l.getRawBrightness(x, y, z) < 13 && Tile.solidT[below];
            case Tile.SAPLING: case Tile.TALLGRASS: case Tile.FLOWER: case Tile.ROSE: case Tile.CROPS: case Tile.MELON_STEM:
                return (l.getRawBrightness(x, y, z) >= 8 || l.canSeeSky(x, y, z)) && bushMayPlaceOn(id, below);
            case Tile.REEDS:
                return reedsMayPlace(l, x, y, z);
            case Tile.CACTUS:
                if (Tile.mSolid(l.getMaterial(x - 1, y, z)) || Tile.mSolid(l.getMaterial(x + 1, y, z))
                        || Tile.mSolid(l.getMaterial(x, y, z - 1)) || Tile.mSolid(l.getMaterial(x, y, z + 1))) return false;
                return below == Tile.CACTUS || below == Tile.SAND;
        }
        return true;
    }

    private static boolean reedsMayPlace(Level l, int x, int y, int z) {
        int below = l.getTile(x, y - 1, z);
        if (below == Tile.REEDS) return true;
        if (below != Tile.GRASS && below != Tile.DIRT && below != Tile.SAND) return false;
        return l.getMaterial(x - 1, y - 1, z) == Tile.MT_WATER || l.getMaterial(x + 1, y - 1, z) == Tile.MT_WATER
                || l.getMaterial(x, y - 1, z - 1) == Tile.MT_WATER || l.getMaterial(x, y - 1, z + 1) == Tile.MT_WATER;
    }

    /** Bush::checkAlive: растение без опоры выпадает. */
    private static void checkAlive(Level l, int id, int x, int y, int z) {
        if (!canSurvive(l, id, x, y, z)) {
            spawnResources(l, id, x, y, z, l.getData(x, y, z), 1);
            l.setTile(x, y, z, 0);
        }
    }

    /** TorchTile::isConnection */
    private static boolean torchConnection(Level l, int x, int y, int z) {
        if (l.isSolidBlockingTile(x, y, z)) return true;
        int t = l.getTile(x, y, z);
        return t == Tile.FENCE || t == Tile.GLASS;
    }

    private static boolean torchMayPlace(Level l, int x, int y, int z) {
        return l.isSolidBlockingTile(x - 1, y, z) || l.isSolidBlockingTile(x + 1, y, z) || l.isSolidBlockingTile(x, y, z - 1)
                || l.isSolidBlockingTile(x, y, z + 1) || torchConnection(l, x, y - 1, z);
    }

    /** TorchTile::onPlace: в 0.6.1 факел всегда цепляется к первой опоре по порядку -x, +x, -z, +z, низ. */
    private static void torchOnPlace(Level l, int x, int y, int z) {
        if (l.isSolidBlockingTile(x - 1, y, z)) l.setData(x, y, z, 1);
        else if (l.isSolidBlockingTile(x + 1, y, z)) l.setData(x, y, z, 2);
        else if (l.isSolidBlockingTile(x, y, z - 1)) l.setData(x, y, z, 3);
        else if (l.isSolidBlockingTile(x, y, z + 1)) l.setData(x, y, z, 4);
        else if (torchConnection(l, x, y - 1, z)) l.setData(x, y, z, 5);
        torchCheckCanSurvive(l, x, y, z);
    }

    private static boolean torchCheckCanSurvive(Level l, int x, int y, int z) {
        if (!torchMayPlace(l, x, y, z)) {
            if (l.getTile(x, y, z) == Tile.TORCH) {
                spawnResources(l, Tile.TORCH, x, y, z, l.getData(x, y, z), 1);
                l.setTile(x, y, z, 0);
            }
            return false;
        }
        return true;
    }

    /** TopSnowTile::mayPlace */
    private static boolean topSnowMayPlace(Level l, int x, int y, int z) {
        int t = l.getTile(x, y - 1, z);
        if (t == 0 || !Tile.opaqueRender(t)) return false;
        return Tile.mBlocksMotion(Tile.mat[t]);
    }

    /** TrapDoorTile::attachesTo */
    static boolean trapdoorAttaches(int id) {
        if (id <= 0 || !Tile.exists[id]) return false;
        return Tile.solidBlocking(id) || id == Tile.GLOWSTONE || id == Tile.SLAB || Tile.shape[id] == Tile.S_STAIRS;
    }

    // ================= neighborChanged =================

    public static void neighborChanged(Level l, int id, int x, int y, int z, int type) {
        if (isBush(id)) {
            checkAlive(l, id, x, y, z);
            return;
        }
        switch (id) {
            case Tile.REEDS:
                if (!reedsMayPlace(l, x, y, z)) {
                    popResource(l, x, y, z, 338, 1, 0);
                    l.setTile(x, y, z, 0);
                }
                return;
            case Tile.CACTUS:
                if (!canSurvive(l, id, x, y, z)) {
                    spawnResources(l, id, x, y, z, l.getData(x, y, z), 1);
                    l.setTile(x, y, z, 0);
                }
                return;
            case Tile.SAND: case Tile.GRAVEL:
                l.addToTickNextTick(x, y, z, id, 2);
                return;
            case Tile.WATER: case Tile.LAVA:
                Liquids.updateLiquid(l, id, x, y, z);
                return;
            case Tile.STILL_WATER: case Tile.STILL_LAVA:
                Liquids.updateLiquid(l, id, x, y, z);
                if (l.getTile(x, y, z) == id) Liquids.setDynamic(l, id, x, y, z);
                return;
            case Tile.TORCH: {
                if (!torchCheckCanSurvive(l, x, y, z)) return;
                int dir = l.getData(x, y, z);
                boolean replace = false;
                if (!l.isSolidBlockingTile(x - 1, y, z) && dir == 1) replace = true;
                if (!l.isSolidBlockingTile(x + 1, y, z) && dir == 2) replace = true;
                if (!l.isSolidBlockingTile(x, y, z - 1) && dir == 3) replace = true;
                if (!l.isSolidBlockingTile(x, y, z + 1) && dir == 4) replace = true;
                if (!torchConnection(l, x, y - 1, z) && dir == 5) replace = true;
                if (replace) {
                    spawnResources(l, id, x, y, z, dir, 1);
                    l.setTile(x, y, z, 0);
                }
                return;
            }
            case Tile.LADDER: {
                int face = l.getData(x, y, z);
                boolean ok = (face == 2 && l.isSolidBlockingTile(x, y, z + 1)) || (face == 3 && l.isSolidBlockingTile(x, y, z - 1))
                        || (face == 4 && l.isSolidBlockingTile(x + 1, y, z)) || (face == 5 && l.isSolidBlockingTile(x - 1, y, z));
                if (!ok) {
                    popResource(l, x, y, z, Tile.LADDER, 1, 0);
                    l.setTile(x, y, z, 0);
                }
                return;
            }
            case Tile.SIGN: case Tile.WALL_SIGN: {
                boolean remove;
                if (id == Tile.SIGN) remove = !Tile.mSolid(l.getMaterial(x, y - 1, z));
                else {
                    int face = l.getData(x, y, z);
                    remove = !((face == 2 && Tile.mSolid(l.getMaterial(x, y, z + 1))) || (face == 3 && Tile.mSolid(l.getMaterial(x, y, z - 1)))
                            || (face == 4 && Tile.mSolid(l.getMaterial(x + 1, y, z))) || (face == 5 && Tile.mSolid(l.getMaterial(x - 1, y, z))));
                }
                if (remove) {
                    spawnResources(l, id, x, y, z, l.getData(x, y, z), 0);
                    l.setTile(x, y, z, 0);
                }
                return;
            }
            case Tile.WOOD_DOOR: case Tile.IRON_DOOR: doorNeighborChanged(l, id, x, y, z, type); return;
            case Tile.TRAPDOOR: {
                int d = l.getData(x, y, z);
                int xt = x, zt = z;
                if ((d & 3) == 0) zt++;
                if ((d & 3) == 1) zt--;
                if ((d & 3) == 2) xt++;
                if ((d & 3) == 3) xt--;
                if (!trapdoorAttaches(l.getTile(xt, y, zt))) {
                    l.setTile(x, y, z, 0);
                    popResource(l, x, y, z, Tile.TRAPDOOR, 1, 0);
                }
                // без сигналов (в 0.6.1 нет редстоуна): исчезновение соседа (type 0) закрывает люк
                if (type == 0 && l.getTile(x, y, z) == Tile.TRAPDOOR) trapdoorSetOpen(l, x, y, z, false);
                return;
            }
            case Tile.BED: {
                int d = l.getData(x, y, z);
                int dir = d & 3;
                int[] o = MC.HEAD_DIR[dir];
                if ((d & 8) != 0) {
                    if (l.getTile(x - o[0], y, z - o[1]) != Tile.BED) l.setTile(x, y, z, 0);
                } else if (l.getTile(x + o[0], y, z + o[1]) != Tile.BED) {
                    l.setTile(x, y, z, 0);
                    popResource(l, x, y, z, 355, 1, 0);
                }
                return;
            }
            case Tile.SNOW_LAYER:
                if (!topSnowMayPlace(l, x, y, z)) {
                    spawnResources(l, id, x, y, z, l.getData(x, y, z), 1);
                    l.setTile(x, y, z, 0);
                }
                return;
            case Tile.FARMLAND:
                if (Tile.mSolid(l.getMaterial(x, y + 1, z))) l.setTile(x, y, z, Tile.DIRT);
                return;
            case Tile.CHEST:
                chestRecalcLockDir(l, x, y, z);
                return;
        }
    }

    /** DoorTile::neighborChanged (повтор оригинала: у нижней половины — с выпадением, у верхней — popResource). */
    private static void doorNeighborChanged(Level l, int id, int x, int y, int z, int type) {
        int data = l.getData(x, y, z);
        if ((data & 8) == 0) {
            boolean spawn = false;
            if (l.getTile(x, y + 1, z) != id) {
                l.setTile(x, y, z, 0);
                spawn = true;
            }
            if (!l.isSolidBlockingTile(x, y - 1, z)) {
                l.setTile(x, y, z, 0);
                spawn = true;
                if (l.getTile(x, y + 1, z) == id) l.setTile(x, y + 1, z, 0);
            }
            if (spawn) spawnResources(l, id, x, y, z, data, 0);
            else if (type == 0 && type != id) doorSetOpen(l, x, y, z, false);
        } else {
            if (l.getTile(x, y - 1, z) != id) {
                l.setTile(x, y, z, 0);
                popResource(l, x, y, z, id == Tile.IRON_DOOR ? 330 : 324, 1, 0);
            }
            if (type > 0 && type != id) neighborChanged(l, id, x, y - 1, z, type);
        }
    }

    // ================= onPlace / onRemove =================

    public static void onPlace(Level l, int id, int x, int y, int z) {
        switch (id) {
            case Tile.WATER: case Tile.LAVA:
                Liquids.updateLiquid(l, id, x, y, z);
                if (l.getTile(x, y, z) == id) l.addToTickNextTick(x, y, z, id, Liquids.tickDelay(id));
                return;
            case Tile.STILL_WATER: case Tile.STILL_LAVA:
                Liquids.updateLiquid(l, id, x, y, z);
                return;
            case Tile.SAND: case Tile.GRAVEL:
                l.addToTickNextTick(x, y, z, id, 2);
                return;
            case Tile.TORCH:
                torchOnPlace(l, x, y, z);
                return;
            case Tile.CHEST:
                if (l.getTileEntity(x, y, z) == null || !"Chest".equals(l.getTileEntity(x, y, z).type)) l.setTileEntity(new TileEntity("Chest", x, y, z));
                chestRecalcLockDir(l, x, y, z);
                return;
            case Tile.FURNACE: case Tile.FURNACE_LIT:
                if (!noDrop) {
                    l.setTileEntity(new TileEntity("Furnace", x, y, z));
                    furnaceRecalcLockDir(l, x, y, z);
                }
                return;
            case Tile.SIGN: case Tile.WALL_SIGN:
                l.setTileEntity(new TileEntity("Sign", x, y, z));
                return;
        }
    }

    public static void onRemove(Level l, int id, int x, int y, int z) {
        switch (id) {
            case Tile.LEAVES: {
                // LeafTile::onRemove: соседней листве — бит проверки опадания
                for (int xo = -1; xo <= 1; xo++)
                    for (int yo = -1; yo <= 1; yo++)
                        for (int zo = -1; zo <= 1; zo++)
                            if (l.getTile(x + xo, y + yo, z + zo) == Tile.LEAVES)
                                l.setDataNoUpdate(x + xo, y + yo, z + zo, l.getData(x + xo, y + yo, z + zo) | 4);
                return;
            }
            case Tile.LOG: {
                for (int xo = -4; xo <= 4; xo++)
                    for (int yo = -4; yo <= 4; yo++)
                        for (int zo = -4; zo <= 4; zo++)
                            if (l.getTile(x + xo, y + yo, z + zo) == Tile.LEAVES) {
                                int d = l.getData(x + xo, y + yo, z + zo);
                                if ((d & 4) == 0) l.setDataNoUpdate(x + xo, y + yo, z + zo, d | 4);
                            }
                return;
            }
            case Tile.CHEST: case Tile.FURNACE: case Tile.FURNACE_LIT: {
                if (noDrop) return;
                TileEntity te = l.getTileEntity(x, y, z);
                if (te != null) {
                    // ChestTile/FurnaceTile::onRemove: содержимое кучками по 10..30
                    Random r = l.random;
                    for (int i = 0; i < te.id.length; i++) {
                        if (te.id[i] == 0 || te.count[i] <= 0) continue;
                        float xo = r.nextFloat() * 0.8f + 0.1f, yo = r.nextFloat() * 0.8f + 0.1f, zo = r.nextFloat() * 0.8f + 0.1f;
                        while (te.count[i] > 0) {
                            int c = r.nextInt(21) + 10;
                            if (c > te.count[i]) c = te.count[i];
                            te.count[i] -= c;
                            ItemEntity e = new ItemEntity(l, x + xo, y + yo, z + zo, te.id[i], c, te.aux[i]);
                            float pow = 0.05f;
                            e.xd = Mth.gaussian(r) * pow;
                            e.yd = Mth.gaussian(r) * pow + 0.2f;
                            e.zd = Mth.gaussian(r) * pow;
                            l.addEntity(e);
                        }
                        te.id[i] = 0;
                    }
                }
                l.removeTileEntity(x, y, z);
                return;
            }
            case Tile.SIGN: case Tile.WALL_SIGN:
                l.removeTileEntity(x, y, z);
                return;
        }
    }

    /** ChestTile::recalcLockDir (одиночный сундук): перевернуть, если смотрит в сплошной блок. */
    private static void chestRecalcLockDir(Level l, int x, int y, int z) {
        boolean n = Tile.solidT[l.getTile(x, y, z - 1)], s = Tile.solidT[l.getTile(x, y, z + 1)];
        boolean w = Tile.solidT[l.getTile(x - 1, y, z)], e = Tile.solidT[l.getTile(x + 1, y, z)];
        int d = l.getData(x, y, z);
        if ((d == 2 && n) || (d == 3 && s) || (d == 4 && w) || (d == 5 && e)) {
            if (n && !s) d = 3;
            if (s && !n) d = 2;
            if (w && !e) d = 5;
            if (e && !w) d = 4;
        }
        l.setData(x, y, z, d);
    }

    /** FurnaceTile::recalcLockDir */
    private static void furnaceRecalcLockDir(Level l, int x, int y, int z) {
        boolean n = Tile.solidT[l.getTile(x, y, z - 1)], s = Tile.solidT[l.getTile(x, y, z + 1)];
        boolean w = Tile.solidT[l.getTile(x - 1, y, z)], e = Tile.solidT[l.getTile(x + 1, y, z)];
        int d = 3;
        if (e && !w) d = 4;
        else if (w && !e) d = 5;
        else if (s && !n) d = 2;
        else if (n && !s) d = 3;
        l.setData(x, y, z, d);
    }

    /** FurnaceTile::setLit: смена id без потери содержимого. */
    public static void setFurnaceLit(Level l, boolean lit, int x, int y, int z) {
        int d = l.getData(x, y, z);
        TileEntity te = l.getTileEntity(x, y, z);
        noDrop = true;
        l.setTile(x, y, z, lit ? Tile.FURNACE_LIT : Tile.FURNACE);
        noDrop = false;
        l.setData(x, y, z, d);
        if (te != null) l.setTileEntity(te);
    }

    // ================= установка =================

    /** Tile::mayPlace(level, x, y, z, face) с переопределениями. */
    public static boolean mayPlace(Level l, int id, int x, int y, int z, int face) {
        int t = l.getTile(x, y, z);
        boolean replaceable = t == 0 || Tile.mat[t] == Tile.MT_REPL_PLANT || Tile.mat[t] == Tile.MT_TOPSNOW;
        switch (id) {
            case Tile.SAPLING: case Tile.TALLGRASS: case Tile.FLOWER: case Tile.ROSE: case Tile.CROPS: case Tile.MELON_STEM:
                return bushMayPlaceOn(id, l.getTile(x, y - 1, z));
            case Tile.BROWN_MUSHROOM: case Tile.RED_MUSHROOM:
                return bushMayPlaceOn(id, l.getTile(x, y - 1, z)) && canSurvive(l, id, x, y, z);
            case Tile.REEDS:
                return reedsMayPlace(l, x, y, z);
            case Tile.CACTUS:
                return replaceable && canSurvive(l, id, x, y, z);
            case Tile.TORCH:
                return torchMayPlace(l, x, y, z);
            case Tile.LADDER:
                return l.isSolidBlockingTile(x - 1, y, z) || l.isSolidBlockingTile(x + 1, y, z)
                        || l.isSolidBlockingTile(x, y, z - 1) || l.isSolidBlockingTile(x, y, z + 1);
            case Tile.SNOW_LAYER:
                return topSnowMayPlace(l, x, y, z);
            case Tile.FENCE_GATE:
                return Tile.mSolid(l.getMaterial(x, y - 1, z)) && replaceable;
            case Tile.TRAPDOOR: {
                if (face <= 1) return false;
                int xx = x, zz = z;
                if (face == 2) zz++;
                if (face == 3) zz--;
                if (face == 4) xx++;
                if (face == 5) xx--;
                return trapdoorAttaches(l.getTile(xx, y, zz));
            }
            case Tile.CHEST:
                // ChestTile::mayPlace: двойных сундуков в 0.6.1 нет — рядом с сундуком нельзя
                return l.getTile(x - 1, y, z) != id && l.getTile(x + 1, y, z) != id && l.getTile(x, y, z - 1) != id && l.getTile(x, y, z + 1) != id;
        }
        if (Tile.shape[id] == Tile.S_STAIRS) {
            int base = stairBase(id);
            return mayPlace(l, base, x, y, z, face);
        }
        return replaceable;
    }

    static int stairBase(int id) {
        switch (id) {
            case Tile.WOOD_STAIRS: return Tile.PLANKS;
            case Tile.STONE_STAIRS: return Tile.COBBLE;
            case Tile.BRICK_STAIRS: return Tile.BRICK;
            case Tile.STONE_BRICK_STAIRS: return Tile.STONE_BRICK;
            case Tile.NETHER_BRICK_STAIRS: return Tile.NETHER_BRICK;
            case Tile.SANDSTONE_STAIRS: return Tile.SANDSTONE;
            case Tile.QUARTZ_STAIRS: return Tile.QUARTZ;
        }
        return id;
    }

    /** Level::mayPlace: целевой блок — воздух, жидкость, огонь или слой снега; рамка нового блока не пересекает существ. */
    public static boolean levelMayPlace(Level l, int id, int x, int y, int z, boolean ignoreEntities, int face) {
        if (!Level.inside(x, y, z)) return false;
        int t = l.getTile(x, y, z);
        if (!ignoreEntities && hasCollision(l, id, x, y, z) && !unobstructed(l, id, x, y, z)) return false;
        boolean free = t == 0 || Tile.isLiquid(t) || t == 51 || t == Tile.SNOW_LAYER;
        return id > 0 && free && mayPlace(l, id, x, y, z, face);
    }

    private static final float[] tmpBox = new float[6 * 8];
    private static final int[] tmpCount = new int[1];

    /** Level::isUnobstructed для рамки блока: живые существа, TNT и падающие блоки мешают. */
    private static boolean unobstructed(Level l, int id, int x, int y, int z) {
        float[] s = new float[6];
        defaultAabb(l, id, x, y, z, s);
        float x0 = x + s[0], y0 = y + s[1], z0 = z + s[2], x1 = x + s[3], y1 = y + s[4], z1 = z + s[5];
        java.util.Vector v = l.entities;
        for (int i = -1; i < v.size(); i++) {
            Entity e = i < 0 ? l.player : (Entity) v.elementAt(i);
            if (e == null || e.removed || !e.blocksBuilding()) continue;
            float hw = e.bbW / 2;
            if (e.x + hw > x0 && e.x - hw < x1 && e.y + e.bbH > y0 && e.y < y1 && e.z + hw > z0 && e.z - hw < z1) return false;
        }
        return true;
    }

    /** Tile::getPlacedOnFaceDataValue */
    public static int placedData(Level l, int id, int x, int y, int z, int face, float cy, int item) {
        switch (id) {
            case Tile.TORCH: {
                int dir = item;
                if (face == 1 && torchConnection(l, x, y - 1, z)) dir = 5;
                if (face == 2 && l.isSolidBlockingTile(x, y, z + 1)) dir = 4;
                if (face == 3 && l.isSolidBlockingTile(x, y, z - 1)) dir = 3;
                if (face == 4 && l.isSolidBlockingTile(x + 1, y, z)) dir = 2;
                if (face == 5 && l.isSolidBlockingTile(x - 1, y, z)) dir = 1;
                return dir;
            }
            case Tile.LADDER: {
                int dir = item;
                if ((dir == 0 || face == 2) && l.isSolidBlockingTile(x, y, z + 1)) dir = 2;
                if ((dir == 0 || face == 3) && l.isSolidBlockingTile(x, y, z - 1)) dir = 3;
                if ((dir == 0 || face == 4) && l.isSolidBlockingTile(x + 1, y, z)) dir = 4;
                if ((dir == 0 || face == 5) && l.isSolidBlockingTile(x - 1, y, z)) dir = 5;
                return dir;
            }
            case Tile.SLAB:
                if (face == 0 || (face != 1 && cy > 0.5f)) return item | 8;
                return item;
            case Tile.TRAPDOOR:
                return face == 3 ? 1 : (face == 4 ? 2 : (face == 5 ? 3 : 0));
        }
        if (Tile.shape[id] == Tile.S_STAIRS && (face == 0 || (face != 1 && cy > 0.5f))) return item | 4;
        return item;
    }

    /** Tile::setPlacedBy: печь, сундук, ступени, калитка — по взгляду игрока. */
    public static void setPlacedBy(Level l, int id, int x, int y, int z, Mob p) {
        int dir = Mth.floor(p.yRot * 4 / 360f + 0.5f) & 3;
        switch (id) {
            case Tile.FURNACE: case Tile.FURNACE_LIT: {
                int[] m = { 2, 5, 3, 4 };
                l.setData(x, y, z, m[dir]);
                return;
            }
            case Tile.CHEST: {
                int[] m = { 2, 5, 3, 4 };
                if (l.getTile(x, y, z - 1) != id && l.getTile(x, y, z + 1) != id && l.getTile(x - 1, y, z) != id && l.getTile(x + 1, y, z) != id)
                    l.setData(x, y, z, m[dir]);
                return;
            }
            case Tile.FENCE_GATE:
                l.setData(x, y, z, dir);
                return;
        }
        if (Tile.shape[id] == Tile.S_STAIRS) {
            int usd = l.getData(x, y, z) & 4;
            int[] m = { 2, 1, 3, 0 };
            l.setData(x, y, z, m[dir] | usd);
        }
    }

    // ================= использование =================

    /** Tile::use. true — касание поглощено блоком. */
    public static boolean use(Level l, int x, int y, int z, Mob p) {
        int id = l.getTile(x, y, z);
        int d = l.getData(x, y, z);
        switch (id) {
            case Tile.WOOD_DOOR: {
                int comp = doorComposite(l, x, y, z);
                int lower = (comp & 7) ^ 4;
                if ((comp & 8) == 0) l.setData(x, y, z, lower);
                else l.setData(x, y - 1, z, lower);
                doorSound(x, y, z);
                return true;
            }
            // DoorTile/TrapDoorTile::use: железные руками не открываются, но касание поглощают
            case Tile.IRON_DOOR:
                return true;
            case Tile.TRAPDOOR:
                l.setData(x, y, z, d ^ 4);
                doorSound(x, y, z);
                return true;
            case Tile.FENCE_GATE: {
                if ((d & 4) != 0) l.setData(x, y, z, d & ~4);
                else {
                    int dir = (Mth.floor(p.yRot * 4f / 360f + 0.5f)) & 3;
                    if ((d & 3) == ((dir + 2) & 3)) d = dir;
                    l.setData(x, y, z, d | 4);
                }
                doorSound(x, y, z);
                return true;
            }
            case Tile.REDSTONE_ORE: case Tile.REDSTONE_ORE_LIT:
                redstoneInteract(l, id, x, y, z);
                return false;
        }
        return false;
    }

    /** Tile::attack — первый удар по блоку (SurvivalMode::startDestroyBlock). */
    public static void attack(Level l, int x, int y, int z, Mob p) {
        int id = l.getTile(x, y, z);
        if (id == Tile.WOOD_DOOR || id == Tile.IRON_DOOR || id == Tile.TRAPDOOR) use(l, x, y, z, p);
        else if (id == Tile.REDSTONE_ORE || id == Tile.REDSTONE_ORE_LIT) redstoneInteract(l, id, x, y, z);
    }

    /** RedStoneOreTile::interact: искры и подсветка. */
    private static void redstoneInteract(Level l, int id, int x, int y, int z) {
        if (MC.instance != null) MC.instance.particles.redstonePoof(x, y, z);
        if (id == Tile.REDSTONE_ORE) l.setTile(x, y, z, Tile.REDSTONE_ORE_LIT);
    }

    /** Tile::fallOn: грядка вытаптывается. */
    public static void fallOn(Level l, int id, int x, int y, int z, Entity e, float dist) {
        if (id == Tile.FARMLAND && l.random.nextFloat() < dist - 0.5f) l.setTile(x, y, z, Tile.DIRT);
    }

    /** Tile::entityInside: кактус колет, паутина держит. */
    public static void entityInside(Level l, int id, int x, int y, int z, Entity e) {
        if (id == Tile.CACTUS) e.hurt(1, null);
        else if (id == Tile.WEB) {
            e.stuckInWeb = true;
            e.fallDistance = 0;
        }
    }

    /** LevelRenderer::levelEvent SOUND_OPEN_DOOR: случайно door_open или door_close. */
    static void doorSound(int x, int y, int z) {
        if (MC.instance != null) MC.instance.playSoundAt(Mth.random() < 0.5f ? "random.door_open" : "random.door_close", x + 0.5f, y + 0.5f, z + 0.5f, 1f);
        // DoorTile::use: level->levelEvent(SOUND_OPEN_DOOR) — у клиентов сети тот же звук
        if (MC.instance != null && MC.instance.level != null && MC.instance.level.net != null)
            MC.instance.level.net.levelEvent(Net.LEVEL_SOUND_OPEN_DOOR, x, y, z, 0);
    }

    private static void doorSetOpen(Level l, int x, int y, int z, boolean open) {
        int comp = doorComposite(l, x, y, z);
        if (((comp & 4) != 0) == open) return;
        int lower = (comp & 7) ^ 4;
        if ((comp & 8) == 0) l.setData(x, y, z, lower);
        else l.setData(x, y - 1, z, lower);
        doorSound(x, y, z);
    }

    private static void trapdoorSetOpen(Level l, int x, int y, int z, boolean open) {
        int d = l.getData(x, y, z);
        if (((d & 4) != 0) == open) return;
        l.setData(x, y, z, d ^ 4);
        doorSound(x, y, z);
    }

    /** DoorTile::getCompositeData: биты 0-2 нижней половины, 8 — верх, 16 — петли справа. */
    public static int doorComposite(Level l, int x, int y, int z) {
        int d = l.getData(x, y, z);
        boolean upper = (d & 8) != 0;
        int lower = upper ? l.getData(x, y - 1, z) : d;
        int up = upper ? d : l.getData(x, y + 1, z);
        return (lower & 7) | (upper ? 8 : 0) | ((up & 1) != 0 ? 16 : 0);
    }

    /** DoorItem::place: петли — со стороны соседней двери или где меньше сплошных блоков. */
    public static void placeDoor(Level l, int x, int y, int z, int dir, int id) {
        int xra = 0, zra = 0;
        if (dir == 0) zra = 1;
        if (dir == 1) xra = -1;
        if (dir == 2) zra = -1;
        if (dir == 3) xra = 1;
        int solidLeft = (l.isSolidBlockingTile(x - xra, y, z - zra) ? 1 : 0) + (l.isSolidBlockingTile(x - xra, y + 1, z - zra) ? 1 : 0);
        int solidRight = (l.isSolidBlockingTile(x + xra, y, z + zra) ? 1 : 0) + (l.isSolidBlockingTile(x + xra, y + 1, z + zra) ? 1 : 0);
        boolean doorLeft = l.getTile(x - xra, y, z - zra) == id || l.getTile(x - xra, y + 1, z - zra) == id;
        boolean doorRight = l.getTile(x + xra, y, z + zra) == id || l.getTile(x + xra, y + 1, z + zra) == id;
        boolean flip = false;
        if (doorLeft && !doorRight) flip = true;
        else if (solidRight > solidLeft) flip = true;
        l.noNeighborUpdate = true;
        l.setTileAndData(x, y, z, id, dir);
        l.setTileAndData(x, y + 1, z, id, 8 | (flip ? 1 : 0));
        l.noNeighborUpdate = false;
        l.updateNeighborsAt(x, y, z, id);
        l.updateNeighborsAt(x, y + 1, z, id);
    }

    // ================= выпадение =================

    /** Tile::getResource (0 и меньше — ничего). */
    public static int resource(int id, int data, Random r) {
        switch (id) {
            case Tile.STONE: return Tile.COBBLE;
            case Tile.GRASS: case Tile.FARMLAND: return Tile.DIRT;
            case Tile.GRAVEL: return r.nextInt(10) == 0 ? 318 : Tile.GRAVEL;
            case Tile.COAL_ORE: return 263;
            case Tile.DIAMOND_ORE: return 264;
            case Tile.LAPIS_ORE: return 351;
            case Tile.REDSTONE_ORE: case Tile.REDSTONE_ORE_LIT: return 0;
            case Tile.LEAVES: return Tile.SAPLING;
            case Tile.WEB: return 287;
            case Tile.TALLGRASS: return r.nextInt(8) == 0 ? 295 : -1;
            case Tile.DOUBLE_SLAB: return Tile.SLAB;
            case Tile.BOOKSHELF: return 340;
            case Tile.GLOWING_OBSIDIAN: return Tile.OBSIDIAN;
            case Tile.CROPS: return data == 7 ? 296 : -1;
            case Tile.FURNACE_LIT: return Tile.FURNACE;
            case Tile.SIGN: case Tile.WALL_SIGN: return 323;
            case Tile.WOOD_DOOR: return (data & 8) != 0 ? 0 : 324;
            case Tile.IRON_DOOR: return (data & 8) != 0 ? 0 : 330;
            case Tile.SNOW_LAYER: case Tile.SNOW: return 332;
            case Tile.CLAY: return 337;
            case Tile.REEDS: return 338;
            case Tile.GLOWSTONE: return 348;
            case Tile.MELON: return 360;
            case Tile.MELON_STEM: return -1;
            case Tile.BED: return (data & 8) != 0 ? 0 : 355;
            case Tile.WATER: case Tile.STILL_WATER: case Tile.LAVA: case Tile.STILL_LAVA: case 51: case Tile.GRASS_CARRIED:
                return 0;
        }
        return id;
    }

    /** Tile::getResourceCount */
    public static int resourceCount(int id, Random r) {
        switch (id) {
            case Tile.LAPIS_ORE: return 4 + r.nextInt(5);
            case Tile.REDSTONE_ORE: case Tile.REDSTONE_ORE_LIT: return 4 + r.nextInt(2);
            case Tile.GLOWSTONE: return 2 + r.nextInt(3);
            case Tile.MELON: return 3 + r.nextInt(5);
            case Tile.CLAY: case Tile.SNOW: return 4;
            case Tile.BOOKSHELF: return 3;
            case Tile.DOUBLE_SLAB: return 2;
            case Tile.GLASS: case Tile.GLASS_PANE: case Tile.ICE: case Tile.SNOW_LAYER: case 51: case Tile.GRASS_CARRIED:
            case Tile.WATER: case Tile.STILL_WATER: case Tile.LAVA: case Tile.STILL_LAVA:
                return 0;
            case Tile.LEAVES: return r.nextInt(20) == 0 ? 1 : 0;
        }
        return 1;
    }

    /** Tile::getSpawnResourcesAuxValue */
    public static int resourceAux(int id, int data) {
        switch (id) {
            case Tile.LAPIS_ORE: return 4;
            case Tile.SAPLING: case Tile.LEAVES: return data & 3;
            case Tile.SLAB: case Tile.DOUBLE_SLAB: return data & 7;
            case Tile.LOG: case Tile.WOOL: case Tile.STONE_BRICK: case Tile.SANDSTONE: case Tile.QUARTZ: return data;
        }
        return 0;
    }

    /** Tile::spawnResources(odds): каждая единица — отдельный предмет со случайным сдвигом. */
    public static void spawnResources(Level l, int id, int x, int y, int z, int data, float odds) {
        Random r = l.random;
        switch (id) {
            case Tile.LEAVES: {
                // LeafTile::spawnResources: саженец 1/20, яблоко 1/200 у обычной листвы
                if (r.nextInt(20) == 0) popResource(l, x, y, z, Tile.SAPLING, 1, data & 3);
                if ((data & 3) == 0 && r.nextInt(200) == 0) popResource(l, x, y, z, 260, 1, 0);
                return;
            }
            case Tile.BED:
                if ((data & 8) == 0) popResource(l, x, y, z, 355, 1, 0);
                return;
        }
        int count = resourceCount(id, r);
        for (int i = 0; i < count; i++) {
            if (r.nextFloat() > odds) continue;
            int type = resource(id, data, r);
            if (type <= 0) continue;
            dropAt(l, x, y, z, type, 1, resourceAux(id, data));
        }
        if (id == Tile.CROPS) {
            // CropTile::spawnResources: три попытки семян nextInt(15) <= data
            for (int i = 0; i < 3; i++) {
                if (r.nextInt(15) > data) continue;
                dropAt(l, x, y, z, 295, 1, 0);
            }
        } else if (id == Tile.MELON_STEM) {
            for (int i = 0; i < 3; i++) {
                if (r.nextInt(15) > data) continue;
                popResource(l, x, y, z, 362, 1, 0);
            }
        }
    }

    private static void dropAt(Level l, int x, int y, int z, int id, int count, int aux) {
        Random r = l.random;
        float s = 0.7f;
        float xo = r.nextFloat() * s + (1 - s) * 0.5f, yo = r.nextFloat() * s + (1 - s) * 0.5f, zo = r.nextFloat() * s + (1 - s) * 0.5f;
        l.addEntity(new ItemEntity(l, x + xo, y + yo, z + zo, id, count, aux));
    }

    /** Tile::popResource: в творческом ничего не выпадает. */
    public static void popResource(Level l, int x, int y, int z, int id, int count, int aux) {
        if (l.gameType == 1) return;
        dropAt(l, x, y, z, id, count, aux);
    }

    /** Tile::playerDestroy (SurvivalMode::destroyBlock, если инструмент подходит). */
    public static void playerDestroy(Level l, Player p, int id, int x, int y, int z, int data) {
        playerDestroy(l, p.inv.selId(), id, x, y, z, data);
    }

    /** sel — предмет в руке разрушившего (у игрока сети — из PlayerEquipmentPacket) */
    public static void playerDestroy(Level l, int sel, int id, int x, int y, int z, int data) {
        switch (id) {
            case Tile.LEAVES:
                if (sel == 359) {
                    popResource(l, x, y, z, Tile.LEAVES, 1, data & 3);
                    return;
                }
                break;
            case Tile.TALLGRASS:
                if (sel == 359) {
                    popResource(l, x, y, z, Tile.TALLGRASS, 1, data);
                    return;
                }
                break;
            case Tile.SNOW_LAYER:
                dropAt(l, x, y, z, 332, 1, 0);
                l.setTile(x, y, z, 0);
                return;
            case Tile.ICE: {
                int below = l.getMaterial(x, y - 1, z);
                if (Tile.mBlocksMotion(below) || Tile.mLiquid(below)) l.setTile(x, y, z, Tile.WATER);
                return;
            }
        }
        spawnResources(l, id, x, y, z, data, 1);
    }

    /** Tile::wasExploded: TNT становится подожжённым с короткой задержкой. */
    public static void wasExploded(Level l, int id, int x, int y, int z) {
        if (id != Tile.TNT) return;
        PrimedTnt p = new PrimedTnt(l, x + 0.5f, y + 0.01f, z + 0.5f);
        p.fuse = l.random.nextInt(p.fuse / 4) + p.fuse / 8;
        l.addEntity(p);
    }

    // ================= формы =================

    /** Tile::mayPick: жидкость — только источник и только при liquid. */
    public static boolean mayPick(int id, int data, boolean liquid) {
        if (Tile.isLiquid(id)) return liquid && data == 0;
        return true;
    }

    /** Форма выбора (updateShape / getTileAABB) в долях блока: x0 y0 z0 x1 y1 z1. */
    public static void shape(Level l, int id, int x, int y, int z, float[] o) {
        int d = l.getData(x, y, z);
        set(o, 0, 0, 0, 1, 1, 1);
        switch (id) {
            case Tile.SAPLING: case Tile.TALLGRASS: set(o, 0.1f, 0, 0.1f, 0.9f, 0.8f, 0.9f); return;
            case Tile.FLOWER: case Tile.ROSE: set(o, 0.3f, 0, 0.3f, 0.7f, 0.6f, 0.7f); return;
            case Tile.BROWN_MUSHROOM: case Tile.RED_MUSHROOM: set(o, 0.3f, 0, 0.3f, 0.7f, 0.4f, 0.7f); return;
            case Tile.CROPS: set(o, 0, 0, 0, 1, 0.25f, 1); return;
            case Tile.MELON_STEM: set(o, 0.375f, 0, 0.375f, 0.625f, (d * 2 + 2) / 16f, 0.625f); return;
            case Tile.REEDS: set(o, 0.125f, 0, 0.125f, 0.875f, 1, 0.875f); return;
            case Tile.TORCH: {
                float r = 0.15f;
                if (d == 1) set(o, 0, 0.2f, 0.5f - r, r * 2, 0.8f, 0.5f + r);
                else if (d == 2) set(o, 1 - r * 2, 0.2f, 0.5f - r, 1, 0.8f, 0.5f + r);
                else if (d == 3) set(o, 0.5f - r, 0.2f, 0, 0.5f + r, 0.8f, r * 2);
                else if (d == 4) set(o, 0.5f - r, 0.2f, 1 - r * 2, 0.5f + r, 0.8f, 1);
                else set(o, 0.4f, 0, 0.4f, 0.6f, 0.6f, 0.6f);
                return;
            }
            case Tile.SLAB:
                if ((d & 8) != 0) set(o, 0, 0.5f, 0, 1, 1, 1);
                else set(o, 0, 0, 0, 1, 0.5f, 1);
                return;
            case Tile.SNOW_LAYER: set(o, 0, 0, 0, 1, 1 / 8f, 1); return;
            case Tile.FARMLAND: set(o, 0, 0, 0, 1, 15 / 16f, 1); return;
            case Tile.CACTUS: set(o, 1 / 16f, 0, 1 / 16f, 15 / 16f, 1, 15 / 16f); return;
            case Tile.BED: set(o, 0, 0, 0, 1, 9 / 16f, 1); return;
            case Tile.CHEST: set(o, 0.025f, 0, 0.025f, 0.975f, 0.95f, 0.975f); return;
            case Tile.SIGN: set(o, 0.25f, 0, 0.25f, 0.75f, 1, 0.75f); return;
            case Tile.WALL_SIGN: {
                // SignTile::updateShape (настенная): толщина 2/16, высота 4.5..12.5 /16
                float h0 = 4.5f / 16, h1 = 12.5f / 16, d0 = 2 / 16f;
                if (d == 2) set(o, 0, h0, 1 - d0, 1, h1, 1);
                if (d == 3) set(o, 0, h0, 0, 1, h1, d0);
                if (d == 4) set(o, 1 - d0, h0, 0, 1, h1, 1);
                if (d == 5) set(o, 0, h0, 0, d0, h1, 1);
                return;
            }
            case Tile.LADDER: ladderShape(d, o); return;
            case Tile.WOOD_DOOR: case Tile.IRON_DOOR: doorShape(doorComposite(l, x, y, z), o); return;
            case Tile.TRAPDOOR: trapdoorShape(d, o); return;
            case Tile.FENCE: {
                boolean n = fenceConnects(l, x, y, z - 1), s = fenceConnects(l, x, y, z + 1), w = fenceConnects(l, x - 1, y, z), e = fenceConnects(l, x + 1, y, z);
                set(o, w ? 0 : 6 / 16f, 0, n ? 0 : 6 / 16f, e ? 1 : 10 / 16f, 1, s ? 1 : 10 / 16f);
                return;
            }
            case Tile.GLASS_PANE: {
                boolean n = paneAttaches(l.getTile(x, y, z - 1)), s = paneAttaches(l.getTile(x, y, z + 1));
                boolean w = paneAttaches(l.getTile(x - 1, y, z)), e = paneAttaches(l.getTile(x + 1, y, z));
                float minX = 7 / 16f, maxX = 9 / 16f, minZ = 7 / 16f, maxZ = 9 / 16f;
                boolean none = !w && !e && !n && !s;
                if ((w && e) || none) { minX = 0; maxX = 1; } else if (w) minX = 0; else if (e) maxX = 1;
                if ((n && s) || none) { minZ = 0; maxZ = 1; } else if (n) minZ = 0; else if (s) maxZ = 1;
                set(o, minX, 0, minZ, maxX, 1, maxZ);
                return;
            }
        }
    }

    private static void set(float[] o, float a, float b, float c, float d, float e, float f) {
        o[0] = a; o[1] = b; o[2] = c; o[3] = d; o[4] = e; o[5] = f;
    }

    private static void ladderShape(int d, float[] o) {
        float r = 2 / 16f;
        if (d == 2) set(o, 0, 0, 1 - r, 1, 1, 1);
        if (d == 3) set(o, 0, 0, 0, 1, 1, r);
        if (d == 4) set(o, 1 - r, 0, 0, 1, 1, 1);
        if (d == 5) set(o, 0, 0, 0, r, 1, 1);
    }

    /** DoorTile::setShape(compositeData) */
    public static void doorShape(int comp, float[] o) {
        float r = 3 / 16f;
        int dir = comp & 3;
        boolean open = (comp & 4) != 0, right = (comp & 16) != 0;
        set(o, 0, 0, 0, 1, 1, 1);
        if (dir == 0) {
            if (open) { if (!right) set(o, 0, 0, 0, 1, 1, r); else set(o, 0, 0, 1 - r, 1, 1, 1); }
            else set(o, 0, 0, 0, r, 1, 1);
        } else if (dir == 1) {
            if (open) { if (!right) set(o, 1 - r, 0, 0, 1, 1, 1); else set(o, 0, 0, 0, r, 1, 1); }
            else set(o, 0, 0, 0, 1, 1, r);
        } else if (dir == 2) {
            if (open) { if (!right) set(o, 0, 0, 1 - r, 1, 1, 1); else set(o, 0, 0, 0, 1, 1, r); }
            else set(o, 1 - r, 0, 0, 1, 1, 1);
        } else {
            if (open) { if (!right) set(o, 0, 0, 0, r, 1, 1); else set(o, 1 - r, 0, 0, 1, 1, 1); }
            else set(o, 0, 0, 1 - r, 1, 1, 1);
        }
    }

    /** TrapDoorTile::setShape */
    public static void trapdoorShape(int d, float[] o) {
        float r = 3 / 16f;
        set(o, 0, 0, 0, 1, r, 1);
        if ((d & 4) != 0) {
            if ((d & 3) == 0) set(o, 0, 0, 1 - r, 1, 1, 1);
            if ((d & 3) == 1) set(o, 0, 0, 0, 1, 1, r);
            if ((d & 3) == 2) set(o, 1 - r, 0, 0, 1, 1, 1);
            if ((d & 3) == 3) set(o, 0, 0, 0, r, 1, 1);
        }
    }

    /** FenceTile::connectsTo */
    public static boolean fenceConnects(Level l, int x, int y, int z) {
        int t = l.getTile(x, y, z);
        if (t == Tile.FENCE || t == Tile.FENCE_GATE) return true;
        return Tile.solidBlocking(t) && Tile.mat[t] != Tile.MT_VEGETABLE;
    }

    /** ThinFenceTile::attachsTo */
    public static boolean paneAttaches(int t) {
        return Tile.solidT[t] || t == Tile.GLASS_PANE || t == Tile.GLASS;
    }

    /** Есть ли у блока рамка столкновения (getAABB != NULL). */
    public static boolean hasCollision(Level l, int id, int x, int y, int z) {
        if (id == 0 || Tile.isLiquid(id) || isBush(id)) return false;
        switch (id) {
            case Tile.REEDS: case Tile.TORCH: case Tile.SIGN: case Tile.WALL_SIGN: case Tile.WEB: case Tile.SNOW_LAYER: case 51:
                return false;
            case Tile.FENCE_GATE:
                return (l.getData(x, y, z) & 4) == 0;
        }
        return true;
    }

    /** Tile::getAABB для одной рамки (рамка самого блока). */
    private static void defaultAabb(Level l, int id, int x, int y, int z, float[] o) {
        switch (id) {
            case Tile.FARMLAND: set(o, 0, 0, 0, 1, 1, 1); return;
            case Tile.CACTUS: set(o, 1 / 16f, 0, 1 / 16f, 15 / 16f, 15 / 16f, 15 / 16f); return;
            case Tile.FENCE: shape(l, id, x, y, z, o); o[4] = 1.5f; return;
            case Tile.FENCE_GATE: {
                int d = l.getData(x, y, z);
                if (d == 0 || d == 2) set(o, 0, 0, 6 / 16f, 1, 1.5f, 10 / 16f);
                else set(o, 6 / 16f, 0, 0, 10 / 16f, 1.5f, 1);
                return;
            }
        }
        if (Tile.shape[id] == Tile.S_STAIRS || id == Tile.GLASS_PANE) {
            set(o, 0, 0, 0, 1, 1, 1);
            return;
        }
        shape(l, id, x, y, z, o);
    }

    private static final float[] sb = new float[6];

    /** Tile::addAABBs: рамки столкновений блока в мировых координатах. */
    public static void addAABBs(Level l, int x, int y, int z, float[] out, int[] count) {
        int id = l.getTile(x, y, z);
        if (!hasCollision(l, id, x, y, z)) return;
        float[] s = sb;
        if (Tile.shape[id] == Tile.S_STAIRS) {
            int d = l.getData(x, y, z);
            if ((d & 4) != 0) set(s, 0, 0.5f, 0, 1, 1, 1);
            else set(s, 0, 0, 0, 1, 0.5f, 1);
            add(out, count, x, y, z, s);
            boolean inner = stairStepShape(l, x, y, z, s);
            add(out, count, x, y, z, s);
            if (inner && stairInnerShape(l, x, y, z, s)) add(out, count, x, y, z, s);
            return;
        }
        if (id == Tile.GLASS_PANE) {
            boolean n = paneAttaches(l.getTile(x, y, z - 1)), so = paneAttaches(l.getTile(x, y, z + 1));
            boolean w = paneAttaches(l.getTile(x - 1, y, z)), e = paneAttaches(l.getTile(x + 1, y, z));
            boolean none = !w && !e && !n && !so;
            if ((w && e) || none) { set(s, 0, 0, 7 / 16f, 1, 1, 9 / 16f); add(out, count, x, y, z, s); }
            else if (w) { set(s, 0, 0, 7 / 16f, 0.5f, 1, 9 / 16f); add(out, count, x, y, z, s); }
            else if (e) { set(s, 0.5f, 0, 7 / 16f, 1, 1, 9 / 16f); add(out, count, x, y, z, s); }
            if ((n && so) || none) { set(s, 7 / 16f, 0, 0, 9 / 16f, 1, 1); add(out, count, x, y, z, s); }
            else if (n) { set(s, 7 / 16f, 0, 0, 9 / 16f, 1, 0.5f); add(out, count, x, y, z, s); }
            else if (so) { set(s, 7 / 16f, 0, 0.5f, 9 / 16f, 1, 1); add(out, count, x, y, z, s); }
            return;
        }
        defaultAabb(l, id, x, y, z, s);
        add(out, count, x, y, z, s);
    }

    private static void add(float[] out, int[] count, int x, int y, int z, float[] s) {
        int n = count[0];
        if (n * 6 + 6 > out.length) return;
        out[n * 6] = x + s[0]; out[n * 6 + 1] = y + s[1]; out[n * 6 + 2] = z + s[2];
        out[n * 6 + 3] = x + s[3]; out[n * 6 + 4] = y + s[4]; out[n * 6 + 5] = z + s[5];
        count[0] = n + 1;
    }

    // ---- StairTile: угловые ступени ----

    static boolean isStairs(int id) {
        return id > 0 && Tile.shape[id] == Tile.S_STAIRS;
    }

    /** StairTile::isLockAttached */
    private static boolean lockAttached(Level l, int x, int y, int z, int data) {
        int t = l.getTile(x, y, z);
        return isStairs(t) && l.getData(x, y, z) == data;
    }

    /** StairTile::setStepShape (DIR_EAST 0, WEST 1, SOUTH 2, NORTH 3). true — проверять внутренний угол. */
    public static boolean stairStepShape(Level l, int x, int y, int z, float[] o) {
        int data = l.getData(x, y, z), dir = data & 3;
        float bottom = 0.5f, top = 1;
        if ((data & 4) != 0) { bottom = 0; top = 0.5f; }
        float west = 0, east = 1, north = 0, south = 0.5f;
        boolean inner = true;
        if (dir == 0) {
            west = 0.5f; south = 1;
            int bt = l.getTile(x + 1, y, z), bd = l.getData(x + 1, y, z);
            if (isStairs(bt) && (data & 4) == (bd & 4)) {
                int b = bd & 3;
                if (b == 3 && !lockAttached(l, x, y, z + 1, data)) { south = 0.5f; inner = false; }
                else if (b == 2 && !lockAttached(l, x, y, z - 1, data)) { north = 0.5f; inner = false; }
            }
        } else if (dir == 1) {
            east = 0.5f; south = 1;
            int bt = l.getTile(x - 1, y, z), bd = l.getData(x - 1, y, z);
            if (isStairs(bt) && (data & 4) == (bd & 4)) {
                int b = bd & 3;
                if (b == 3 && !lockAttached(l, x, y, z + 1, data)) { south = 0.5f; inner = false; }
                else if (b == 2 && !lockAttached(l, x, y, z - 1, data)) { north = 0.5f; inner = false; }
            }
        } else if (dir == 2) {
            north = 0.5f; south = 1;
            int bt = l.getTile(x, y, z + 1), bd = l.getData(x, y, z + 1);
            if (isStairs(bt) && (data & 4) == (bd & 4)) {
                int b = bd & 3;
                if (b == 1 && !lockAttached(l, x + 1, y, z, data)) { east = 0.5f; inner = false; }
                else if (b == 0 && !lockAttached(l, x - 1, y, z, data)) { west = 0.5f; inner = false; }
            }
        } else {
            int bt = l.getTile(x, y, z - 1), bd = l.getData(x, y, z - 1);
            if (isStairs(bt) && (data & 4) == (bd & 4)) {
                int b = bd & 3;
                if (b == 1 && !lockAttached(l, x + 1, y, z, data)) { east = 0.5f; inner = false; }
                else if (b == 0 && !lockAttached(l, x - 1, y, z, data)) { west = 0.5f; inner = false; }
            }
        }
        set(o, west, bottom, north, east, top, south);
        return inner;
    }

    /** StairTile::setInnerPieceShape: четвертинка внутреннего угла. */
    public static boolean stairInnerShape(Level l, int x, int y, int z, float[] o) {
        int data = l.getData(x, y, z), dir = data & 3;
        float bottom = 0.5f, top = 1;
        if ((data & 4) != 0) { bottom = 0; top = 0.5f; }
        float west = 0, east = 0.5f, north = 0.5f, south = 1;
        boolean has = false;
        if (dir == 0) {
            int ft = l.getTile(x - 1, y, z), fd = l.getData(x - 1, y, z);
            if (isStairs(ft) && (data & 4) == (fd & 4)) {
                int f = fd & 3;
                if (f == 3 && !lockAttached(l, x, y, z - 1, data)) { north = 0; south = 0.5f; has = true; }
                else if (f == 2 && !lockAttached(l, x, y, z + 1, data)) { north = 0.5f; south = 1; has = true; }
            }
        } else if (dir == 1) {
            int ft = l.getTile(x + 1, y, z), fd = l.getData(x + 1, y, z);
            if (isStairs(ft) && (data & 4) == (fd & 4)) {
                west = 0.5f; east = 1;
                int f = fd & 3;
                if (f == 3 && !lockAttached(l, x, y, z - 1, data)) { north = 0; south = 0.5f; has = true; }
                else if (f == 2 && !lockAttached(l, x, y, z + 1, data)) { north = 0.5f; south = 1; has = true; }
            }
        } else if (dir == 2) {
            int ft = l.getTile(x, y, z - 1), fd = l.getData(x, y, z - 1);
            if (isStairs(ft) && (data & 4) == (fd & 4)) {
                north = 0; south = 0.5f;
                int f = fd & 3;
                if (f == 1 && !lockAttached(l, x - 1, y, z, data)) has = true;
                else if (f == 0 && !lockAttached(l, x + 1, y, z, data)) { west = 0.5f; east = 1; has = true; }
            }
        } else {
            int ft = l.getTile(x, y, z + 1), fd = l.getData(x, y, z + 1);
            if (isStairs(ft) && (data & 4) == (fd & 4)) {
                int f = fd & 3;
                if (f == 1 && !lockAttached(l, x - 1, y, z, data)) has = true;
                else if (f == 0 && !lockAttached(l, x + 1, y, z, data)) { west = 0.5f; east = 1; has = true; }
            }
        }
        if (has) set(o, west, bottom, north, east, top, south);
        return has;
    }
}
