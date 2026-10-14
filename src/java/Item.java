/** Предметы (id >= 256) — набор Item::initItems() релиза 0.6.1. Блоки как предметы — id < 256. */
public final class Item {
    public static final int N = 512;
    public static final int[] icon = new int[N];
    public static final int[] maxStack = new int[N];
    public static final String[] name = new String[N];
    public static final int[] placesTile = new int[N];
    /** инструмент: 0 нет, 1 лопата, 2 кирка, 3 топор, 4 меч, 5 мотыга, 6 ножницы */
    public static final int[] toolKind = new int[N];
    /** материал: 0 дерево, 1 камень, 2 железо, 3 алмаз, 4 золото */
    public static final int[] toolTier = new int[N];
    public static final int[] maxDamage = new int[N];
    public static final int[] food = new int[N];
    public static final boolean[] exists = new boolean[N];
    /** ItemCategory 0.6.1: 1 строительство, 2 инструменты, 4 еда и броня, 8 декор, 16 механизмы; 0 — нет */
    public static final byte[] category = new byte[N];

    /** броня 298..317: материал (id-298)/4 — ткань, кольчуга, железо, алмаз, золото; слот (id-298)%4 — голова, тело, ноги, ступни */
    static final int[] ARMOR_HEALTH = { 11, 16, 15, 13 }, ARMOR_MULT = { 5, 15, 15, 33, 7 };
    static final int[][] ARMOR_DEF = { { 1, 3, 2, 1 }, { 2, 5, 4, 1 }, { 2, 6, 5, 2 }, { 3, 8, 6, 3 }, { 2, 5, 3, 1 } };

    public static boolean isArmor(int id) {
        return id >= 298 && id <= 317;
    }

    public static int armorSlot(int id) {
        return (id - 298) & 3;
    }

    public static int armorDefense(int id) {
        return isArmor(id) ? ARMOR_DEF[(id - 298) >> 2][(id - 298) & 3] : 0;
    }

    static final float[] TIER_SPEED = { 2f, 4f, 6f, 8f, 12f };
    static final int[] TIER_USES = { 59, 131, 250, 1561, 32 };

    static {
        for (int i = 0; i < 256; i++) {
            if (Tile.exists[i]) {
                exists[i] = true;
                maxStack[i] = 64;
                name[i] = Tile.name[i];
            }
        }
        tool(256, "shovelIron", 82, 1, 2);
        tool(257, "pickaxeIron", 98, 2, 2);
        tool(258, "hatchetIron", 114, 3, 2);
        item(259, "flintAndSteel", 5, 1); maxDamage[259] = 64;
        food(260, "apple", 10, 4);
        item(261, "bow", 21, 1); maxDamage[261] = 384;
        item(262, "arrow", 37, 64);
        item(263, "coal", 7, 64);
        item(264, "emerald", 55, 64);
        item(265, "ingotIron", 23, 64);
        item(266, "ingotGold", 39, 64);
        tool(267, "swordIron", 66, 4, 2);
        tool(268, "swordWood", 64, 4, 0);
        tool(269, "shovelWood", 80, 1, 0);
        tool(270, "pickaxeWood", 96, 2, 0);
        tool(271, "hatchetWood", 112, 3, 0);
        tool(272, "swordStone", 65, 4, 1);
        tool(273, "shovelStone", 81, 1, 1);
        tool(274, "pickaxeStone", 97, 2, 1);
        tool(275, "hatchetStone", 113, 3, 1);
        tool(276, "swordDiamond", 67, 4, 3);
        tool(277, "shovelDiamond", 83, 1, 3);
        tool(278, "pickaxeDiamond", 99, 2, 3);
        tool(279, "hatchetDiamond", 115, 3, 3);
        item(280, "stick", 53, 64);
        item(281, "bowl", 71, 64);
        food(282, "mushroomStew", 72, 8); maxStack[282] = 1;
        tool(283, "swordGold", 68, 4, 4);
        tool(284, "shovelGold", 84, 1, 4);
        tool(285, "pickaxeGold", 100, 2, 4);
        tool(286, "hatchetGold", 116, 3, 4);
        item(287, "string", 8, 64);
        item(288, "feather", 24, 64);
        item(289, "sulphur", 40, 64);
        tool(290, "hoeWood", 128, 5, 0);
        tool(291, "hoeStone", 129, 5, 1);
        tool(292, "hoeIron", 130, 5, 2);
        tool(293, "hoeDiamond", 131, 5, 3);
        tool(294, "hoeGold", 132, 5, 4);
        item(295, "seeds", 9, 64); placesTile[295] = Tile.CROPS;
        item(296, "wheat", 25, 64);
        food(297, "bread", 41, 5);
        item(298, "helmetCloth", 0, 1);
        item(299, "chestplateCloth", 16, 1);
        item(300, "leggingsCloth", 32, 1);
        item(301, "bootsCloth", 48, 1);
        item(302, "helmetChain", 1, 1);
        item(303, "chestplateChain", 17, 1);
        item(304, "leggingsChain", 33, 1);
        item(305, "bootsChain", 49, 1);
        item(306, "helmetIron", 2, 1);
        item(307, "chestplateIron", 18, 1);
        item(308, "leggingsIron", 34, 1);
        item(309, "bootsIron", 50, 1);
        item(310, "helmetDiamond", 3, 1);
        item(311, "chestplateDiamond", 19, 1);
        item(312, "leggingsDiamond", 35, 1);
        item(313, "bootsDiamond", 51, 1);
        item(314, "helmetGold", 4, 1);
        item(315, "chestplateGold", 20, 1);
        item(316, "leggingsGold", 36, 1);
        item(317, "bootsGold", 52, 1);
        // ArmorItem 0.6.1: прочность = healthPerSlot * множитель материала
        for (int i = 0; i < 20; i++) maxDamage[298 + i] = ARMOR_HEALTH[i & 3] * ARMOR_MULT[i >> 2];
        item(318, "flint", 6, 64);
        food(319, "porkchopRaw", 87, 3);
        food(320, "porkchopCooked", 88, 8);
        item(321, "painting", 26, 64);
        item(323, "sign", 42, 16); placesTile[323] = Tile.SIGN;
        item(324, "doorWood", 43, 1); placesTile[324] = Tile.WOOD_DOOR;
        item(330, "doorIron", 44, 1); placesTile[330] = Tile.IRON_DOOR;
        item(332, "snowball", 14, 16);
        item(334, "leather", 103, 64);
        item(336, "brick", 22, 64);
        item(337, "clay", 57, 64);
        item(338, "reeds", 27, 64); placesTile[338] = Tile.REEDS;
        item(339, "paper", 58, 64);
        item(340, "book", 59, 64);
        item(341, "slimeball", 30, 64);
        item(344, "egg", 12, 16);
        item(345, "compass", 54, 64);
        item(347, "clock", 70, 64);
        item(348, "yellowDust", 73, 64);
        item(351, "dyePowder", 78, 64);
        item(352, "bone", 28, 64);
        item(353, "sugar", 13, 64);
        item(355, "bed", 13 + 16 * 2, 1); placesTile[355] = Tile.BED;
        item(359, "shears", 13 + 16 * 5, 1); toolKind[359] = 6; maxDamage[359] = 238;
        food(360, "melon", 13 + 16 * 6, 2);
        item(362, "seeds_melon", 14 + 16 * 3, 64); placesTile[362] = Tile.MELON_STEM;
        food(363, "beefRaw", 9 + 16 * 6, 3);
        food(364, "beefCooked", 10 + 16 * 6, 8);
        food(365, "chickenRaw", 9 + 16 * 7, 2);
        food(366, "chickenCooked", 10 + 16 * 7, 6);
        item(405, "netherbrickItem", 5 + 16 * 9, 64);
        item(406, "netherquartz", 5 + 16 * 10, 64);
        item(456, "camera", 2 + 16 * 15, 64);
    }

    private static void cat(int c, int[] ids) {
        for (int i = 0; i < ids.length; i++) category[ids[i]] = (byte) c;
    }

    static {
        cat(1, new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 20, 21, 24, 26, 35, 43, 44, 45, 48, 49, 51, 53, 54, 58, 59, 60, 61, 62, 64, 65, 67, 71, 78, 79, 80, 81, 82, 83, 85, 89, 96, 98, 102, 107, 108, 109, 112, 114, 128, 155, 156, 245, 246, 247, 248, 249, 254, 280, 324, 330, 336, 337, 355, 405 });
        cat(2, new int[] { 46, 50, 256, 257, 258, 259, 261, 262, 263, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294, 318, 334, 345, 347, 348, 352, 359 });
        cat(4, new int[] { 103, 105, 260, 281, 282, 295, 296, 297, 298, 299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 319, 320, 338, 344, 353, 360, 362, 363, 364, 365, 366 });
        cat(8, new int[] { 14, 15, 22, 30, 31, 37, 38, 39, 40, 41, 42, 47, 56, 57, 63, 68, 264, 265, 266, 321, 323, 332, 339, 340, 341, 351, 456 });
        cat(16, new int[] { 73, 74, 406 });
    }

    private static void item(int id, String n, int ic, int stack) {
        exists[id] = true;
        name[id] = n;
        icon[id] = ic;
        maxStack[id] = stack;
    }

    private static void tool(int id, String n, int ic, int kind, int tier) {
        item(id, n, ic, 1);
        toolKind[id] = kind;
        toolTier[id] = tier;
        maxDamage[id] = TIER_USES[tier];
    }

    private static void food(int id, String n, int ic, int f) {
        item(id, n, ic, 64);
        food[id] = f;
    }

    public static boolean isBlock(int id) {
        return id > 0 && id < 256;
    }

    /** Иконка красителя по data (как в атласе: столбцы 14-15). */
    public static int iconFor(int id, int aux) {
        if (id == 351) return (14 + ((aux >> 3) & 1)) + (4 + (aux & 7)) * 16;
        return icon[id];
    }

    /** Tier::getLevel / getAttackDamageBonus (золото — как дерево) */
    static final int[] TIER_LEVEL = { 0, 1, 2, 3, 0 }, TIER_BONUS = { 0, 1, 2, 3, 0 };

    /** ShovelItem / PickaxeItem / HatchetItem: списки блоков DiggerItem */
    static final boolean[] SHOVEL = new boolean[256], PICK = new boolean[256], AXE = new boolean[256];

    static {
        int[] sh = { Tile.GRASS, Tile.DIRT, Tile.SAND, Tile.GRAVEL, Tile.SNOW_LAYER, Tile.SNOW, Tile.CLAY, Tile.FARMLAND };
        for (int i = 0; i < sh.length; i++) SHOVEL[sh[i]] = true;
        int[] pk = { Tile.COBBLE, Tile.DOUBLE_SLAB, Tile.SLAB, Tile.STONE, Tile.SANDSTONE, Tile.MOSSY, Tile.IRON_ORE, Tile.IRON_BLOCK,
                Tile.COAL_ORE, Tile.GOLD_BLOCK, Tile.GOLD_ORE, Tile.DIAMOND_ORE, Tile.DIAMOND_BLOCK, Tile.ICE, Tile.LAPIS_ORE,
                Tile.LAPIS_BLOCK, Tile.REDSTONE_ORE, Tile.REDSTONE_ORE_LIT };
        for (int i = 0; i < pk.length; i++) PICK[pk[i]] = true;
        int[] ax = { Tile.PLANKS, Tile.BOOKSHELF, Tile.LOG, Tile.CHEST, Tile.DOUBLE_SLAB, Tile.SLAB };
        for (int i = 0; i < ax.length; i++) AXE[ax[i]] = true;
    }

    /** Item::getDestroySpeed */
    public static float destroySpeed(int item, int tile) {
        if (item < 256) return 1f;
        int k = toolKind[item];
        int m = Tile.mat[tile];
        float sp = TIER_SPEED[toolTier[item]];
        switch (k) {
            case 1:
                return SHOVEL[tile] ? sp : 1f;
            case 2:
                if (m == Tile.MT_METAL || m == Tile.MT_STONE) return sp;
                return PICK[tile] ? sp : 1f;
            case 3:
                if (m == Tile.MT_WOOD) return sp;
                return AXE[tile] ? sp : 1f;
            case 4:
                return tile == Tile.WEB ? 15f : 1.5f;
            case 6:
                if (tile == Tile.LEAVES || tile == Tile.WEB) return 15f;
                if (tile == Tile.WOOL) return 5f;
                return 1f;
        }
        return 1f;
    }

    /** Inventory::canDestroy: материал всегда добываем, иначе Item::canDestroySpecial. */
    public static boolean canHarvest(int item, int tile) {
        if (Tile.mAlwaysDestroyable(Tile.mat[tile])) return true;
        if (item < 256) return false;
        switch (toolKind[item]) {
            case 1:
                return tile == Tile.SNOW_LAYER || tile == Tile.SNOW;
            case 2: {
                int lvl = TIER_LEVEL[toolTier[item]];
                if (tile == Tile.OBSIDIAN) return lvl == 3;
                if (tile == Tile.DIAMOND_BLOCK || tile == Tile.DIAMOND_ORE || tile == Tile.GOLD_BLOCK || tile == Tile.GOLD_ORE) return lvl >= 2;
                if (tile == Tile.IRON_BLOCK || tile == Tile.IRON_ORE || tile == Tile.LAPIS_BLOCK || tile == Tile.LAPIS_ORE) return lvl >= 1;
                if (tile == Tile.REDSTONE_ORE || tile == Tile.REDSTONE_ORE_LIT) return lvl >= 2;
                int m = Tile.mat[tile];
                return m == Tile.MT_STONE || m == Tile.MT_METAL;
            }
            case 4: case 6:
                return tile == Tile.WEB;
        }
        return false;
    }

    /** Item::getAttackDamage: меч 4, кирка 2, топор 3, лопата 1 (+ бонус материала), прочее 1 */
    public static int attackDamage(int item) {
        if (item < 256) return 1;
        int b = TIER_BONUS[toolTier[item]];
        switch (toolKind[item]) {
            case 1: return 1 + b;
            case 2: return 2 + b;
            case 3: return 3 + b;
            case 4: return 4 + b;
        }
        return 1;
    }
}
