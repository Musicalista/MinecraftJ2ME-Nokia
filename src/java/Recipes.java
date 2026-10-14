import java.util.Vector;

/**
 * Рецепты MCPE 0.6.1 (список и порядок — как в Recipes::Recipes() релиза, сверено с libminecraftpe.so).
 * Крафт списком: результат + набор ингредиентов. Форма нужна только для размера сетки:
 * влезает в 2x2 — доступен с рук, иначе — верстак.
 * Станок: 0 — руки (2x2), 1 — верстак, 2 — камнерез (CraftingFilters::isStonecutterItem).
 */
public final class Recipes {
    public static final class Recipe {
        public int id, count, aux, station;
        public int[] ing; // тройки: id, aux (-1 — любой), количество

        Recipe(int station, int id, int count, int aux, int[] ing) {
            this.station = station; this.id = id; this.count = count; this.aux = aux; this.ing = ing;
        }
    }

    public static final Vector all = new Vector();
    static final int ANY = -1;

    /** Recipe::isAnyAuxValue: у блоков data не важна, кроме шерсти, плит и песчаника; у предметов — важна. */
    private static int ingAux(int id, int aux) {
        if (id < 256 && id != Tile.WOOL && id != Tile.SLAB && id != Tile.SANDSTONE) return ANY;
        return aux;
    }

    /** Результат делается на камнерезе: каменный/песчаный блок, кроме лазурита, печи, камнереза и деревянной плиты. */
    static boolean stonecutterItem(int id, int aux) {
        if (id <= 0 || id >= 256 || !Tile.exists[id]) return false;
        if (id == Tile.LAPIS_BLOCK || id == Tile.FURNACE || id == Tile.STONECUTTER) return false;
        if (id == Tile.SLAB && aux == 2) return false;
        int m = Tile.material[id];
        return m == Tile.M_STONE || m == Tile.M_SAND;
    }

    private static void add(int id, int cnt, int aux, boolean small, int[] ing) {
        int st = stonecutterItem(id, aux) ? 2 : (small ? 0 : 1);
        all.addElement(new Recipe(st, id, cnt, aux, ing));
    }

    /**
     * Рецепт с формой: строки через '/', key — тройки {символ, id, aux}.
     * Ингредиенты считаются по числу символов в форме.
     */
    private static void shaped(int id, int cnt, int aux, String shape, int[] key) {
        int w = 0, h = 1, cur = 0;
        int[] n = new int[key.length / 3];
        for (int i = 0; i < shape.length(); i++) {
            char c = shape.charAt(i);
            if (c == '/') { h++; cur = 0; continue; }
            if (++cur > w) w = cur;
            for (int k = 0; k < n.length; k++) if (key[k * 3] == c) n[k]++;
        }
        int[] ing = new int[n.length * 3];
        for (int k = 0; k < n.length; k++) {
            ing[k * 3] = key[k * 3 + 1];
            ing[k * 3 + 1] = ingAux(key[k * 3 + 1], key[k * 3 + 2]);
            ing[k * 3 + 2] = n[k];
        }
        add(id, cnt, aux, w <= 2 && h <= 2, ing);
    }

    private static void shaped(int id, int cnt, int aux, String shape, int a, int aa) {
        shaped(id, cnt, aux, shape, new int[] { '#', a, aa });
    }

    /** Бесформенный рецепт: пары {id, aux}; одинаковые складываются. Больше 4 штук — только верстак. */
    private static void shapeless(int id, int cnt, int aux, int[] pairs) {
        Vector v = new Vector();
        for (int i = 0; i < pairs.length; i += 2) {
            int a = ingAux(pairs[i], pairs[i + 1]);
            boolean found = false;
            for (int k = 0; k < v.size(); k++) {
                int[] t = (int[]) v.elementAt(k);
                if (t[0] == pairs[i] && t[1] == a) { t[2]++; found = true; }
            }
            if (!found) v.addElement(new int[] { pairs[i], a, 1 });
        }
        int[] ing = new int[v.size() * 3];
        for (int k = 0; k < v.size(); k++) System.arraycopy((int[]) v.elementAt(k), 0, ing, k * 3, 3);
        add(id, cnt, aux, pairs.length / 2 <= 4, ing);
    }

    static {
        final int STICK = 280, IRON = 265, GOLD = 266, DIAMOND = 264, DYE = 351, COAL = 263;
        int[] mats = { Tile.PLANKS, Tile.COBBLE, IRON, DIAMOND, GOLD };

        // ToolRecipes
        int[][] tools = { { 270, 274, 257, 278, 285 }, { 269, 273, 256, 277, 284 },
                { 271, 275, 258, 279, 286 }, { 290, 291, 292, 293, 294 } };
        String[] toolShapes = { "XXX/ # / # ", "X/#/#", "XX/X#/ #", "XX/ #/ #" };
        for (int m = 0; m < 5; m++)
            for (int t = 0; t < 4; t++)
                shaped(tools[t][m], 1, 0, toolShapes[t], new int[] { '#', STICK, 0, 'X', mats[m], 0 });
        shaped(359, 1, 0, " #/# ", IRON, 0);

        // WeaponRecipes
        int[] swords = { 268, 272, 267, 276, 283 };
        for (int m = 0; m < 5; m++) shaped(swords[m], 1, 0, "X/X/#", new int[] { '#', STICK, 0, 'X', mats[m], 0 });
        shaped(261, 1, 0, " #X/# X/ #X", new int[] { 'X', 287, 0, '#', STICK, 0 });
        shaped(262, 4, 0, "X/#/Y", new int[] { 'Y', 288, 0, 'X', 318, 0, '#', STICK, 0 });

        // OreRecipes
        int[] blocks = { Tile.GOLD_BLOCK, Tile.IRON_BLOCK, Tile.DIAMOND_BLOCK, Tile.LAPIS_BLOCK };
        int[] ores = { GOLD, IRON, DIAMOND, DYE };
        for (int i = 0; i < 4; i++) {
            int oa = ores[i] == DYE ? 4 : 0;
            shaped(blocks[i], 1, 0, "###/###/###", ores[i], oa);
            shaped(ores[i], 9, oa, "#", blocks[i], 0);
        }

        // FoodRecipes
        shapeless(282, 1, 0, new int[] { Tile.BROWN_MUSHROOM, 0, Tile.RED_MUSHROOM, 0, 281, 0 });
        shaped(Tile.MELON, 1, 0, "MMM/MMM/MMM", new int[] { 'M', 360, 0 });
        shaped(362, 1, 0, "M", new int[] { 'M', 360, 0 });

        // StructureRecipes
        shaped(Tile.CHEST, 1, 0, "###/# #/###", Tile.PLANKS, 0);
        shaped(Tile.FURNACE, 1, 0, "###/# #/###", Tile.COBBLE, 0);
        shaped(Tile.WORKBENCH, 1, 0, "##/##", Tile.PLANKS, 0);
        shaped(Tile.STONECUTTER, 1, 0, "##/##", Tile.COBBLE, 0);
        shaped(Tile.SANDSTONE, 1, 0, "##/##", Tile.SAND, 0);
        shaped(Tile.SANDSTONE, 4, 2, "##/##", Tile.SANDSTONE, 0);
        shaped(Tile.SANDSTONE, 1, 1, "#/#", Tile.SLAB, 1);
        shaped(Tile.STONE_BRICK, 4, 0, "##/##", Tile.STONE, 0);
        shaped(Tile.GLASS_PANE, 16, 0, "###/###", Tile.GLASS, 0);
        shaped(Tile.NETHER_BRICK, 1, 0, "NN/NN", new int[] { 'N', 405, 0 });
        shaped(Tile.QUARTZ, 1, 0, "NN/NN", new int[] { 'N', 406, 0 });

        // ArmorRecipes: кожа, железо, алмаз, золото (кольчужной нет)
        int[] amats = { 334, IRON, DIAMOND, GOLD };
        String[] armorShapes = { "XXX/X X", "X X/XXX/XXX", "XXX/X X/X X", "X X/X X" };
        for (int m = 0; m < 4; m++)
            for (int t = 0; t < 4; t++)
                shaped(298 + m * 4 + (m > 0 ? 4 : 0) + t, 1, 0, armorShapes[t], new int[] { 'X', amats[m], 0 });

        // ClothDyeRecipes: шерсть красится всеми красителями, кроме чёрного, коричневого, серых и белого
        for (int i = 0; i < 16; i++) {
            if (i == 0 || i == 3 || i == 7 || i == 8 || i == 15) continue;
            shapeless(Tile.WOOL, 1, 15 - i, new int[] { DYE, i, Tile.WOOL, 0 });
        }
        shapeless(DYE, 2, 11, new int[] { Tile.FLOWER, 0 });
        shapeless(DYE, 3, 15, new int[] { 352, 0 });
        shapeless(DYE, 2, 9, new int[] { DYE, 1, DYE, 15 });
        shapeless(DYE, 2, 14, new int[] { DYE, 1, DYE, 11 });
        shapeless(DYE, 2, 10, new int[] { DYE, 2, DYE, 15 });
        shapeless(DYE, 2, 12, new int[] { DYE, 4, DYE, 15 });
        shapeless(DYE, 2, 6, new int[] { DYE, 4, DYE, 2 });
        shapeless(DYE, 2, 5, new int[] { DYE, 4, DYE, 1 });
        shapeless(DYE, 4, 13, new int[] { DYE, 4, DYE, 1, DYE, 1, DYE, 15 });

        // Recipes::Recipes()
        shaped(339, 3, 0, "###", 338, 0);
        shaped(340, 1, 0, "#/#/#", 339, 0);
        shaped(Tile.FENCE, 2, 0, "###/###", STICK, 0);
        shaped(Tile.FENCE_GATE, 1, 0, "#W#/#W#", new int[] { '#', STICK, 0, 'W', Tile.PLANKS, 0 });
        shaped(Tile.BOOKSHELF, 1, 0, "###/XXX/###", new int[] { '#', Tile.PLANKS, 0, 'X', 340, 0 });
        shaped(Tile.SNOW, 1, 0, "##/##", 332, 0);
        shaped(Tile.CLAY, 1, 0, "##/##", 337, 0);
        shaped(Tile.BRICK, 1, 0, "##/##", 336, 0);
        shaped(Tile.GLOWSTONE, 1, 0, "##/##", 348, 0);
        shaped(Tile.WOOL, 1, 0, "##/##", 287, 0);
        shaped(Tile.TNT, 1, 0, "X#X/#X#/X#X", new int[] { 'X', 289, 0, '#', Tile.SAND, 0 });
        shaped(Tile.SLAB, 6, 3, "###", Tile.COBBLE, 0);
        shaped(Tile.SLAB, 6, 0, "###", Tile.STONE, 0);
        shaped(Tile.SLAB, 6, 1, "###", Tile.SANDSTONE, 0);
        shaped(Tile.SLAB, 6, 2, "###", Tile.PLANKS, 0);
        shaped(Tile.SLAB, 6, 4, "###", Tile.BRICK, 0);
        shaped(Tile.SLAB, 6, 5, "###", Tile.STONE_BRICK, 0);
        shaped(Tile.LADDER, 2, 0, "# #/###/# #", STICK, 0);
        shaped(324, 1, 0, "##/##/##", Tile.PLANKS, 0);
        shaped(Tile.TRAPDOOR, 2, 0, "###/###", Tile.PLANKS, 0);
        shaped(323, 1, 0, "###/###/ X ", new int[] { '#', Tile.PLANKS, 0, 'X', STICK, 0 });
        shaped(353, 1, 0, "#", 338, 0);
        shaped(Tile.PLANKS, 4, 0, "#", Tile.LOG, 0);
        shaped(STICK, 4, 0, "#/#", Tile.PLANKS, 0);
        shaped(Tile.TORCH, 4, 0, "X/#", new int[] { 'X', COAL, 0, '#', STICK, 0 });
        shaped(Tile.TORCH, 4, 0, "X/#", new int[] { 'X', COAL, 1, '#', STICK, 0 });
        shaped(281, 4, 0, "# #/ # ", Tile.PLANKS, 0);
        shaped(259, 1, 0, "A / B", new int[] { 'A', IRON, 0, 'B', 318, 0 });
        shaped(297, 1, 0, "###", 296, 0);
        shaped(Tile.WOOD_STAIRS, 4, 0, "#  /## /###", Tile.PLANKS, 0);
        shaped(Tile.STONE_STAIRS, 4, 0, "#  /## /###", Tile.COBBLE, 0);
        shaped(Tile.BRICK_STAIRS, 4, 0, "#  /## /###", Tile.BRICK, 0);
        shaped(Tile.STONE_BRICK_STAIRS, 4, 0, "#  /## /###", Tile.STONE_BRICK, 0);
        shaped(Tile.NETHER_BRICK_STAIRS, 4, 0, "#  /## /###", Tile.NETHER_BRICK, 0);
        shaped(321, 1, 0, "###/#X#/###", new int[] { '#', STICK, 0, 'X', Tile.WOOL, 0 });
        shaped(355, 1, 0, "###/XXX", new int[] { '#', Tile.WOOL, 0, 'X', Tile.PLANKS, 0 });
        shaped(Tile.NETHER_REACTOR, 1, 0, "X#X/X#X/X#X", new int[] { '#', DIAMOND, 0, 'X', IRON, 0 });
    }

    /** Сколько предметов (id, aux или ANY) у игрока. */
    public static int countOf(Inventory inv, int id, int aux) {
        int n = 0;
        for (int i = 0; i < Inventory.SLOTS; i++)
            if (inv.id[i] == id && (aux == ANY || inv.aux[i] == aux)) n += inv.count[i];
        return n;
    }

    public static boolean canCraft(Inventory inv, Recipe r) {
        if (inv.creative) return true;
        for (int k = 0; k < r.ing.length; k += 3)
            if (countOf(inv, r.ing[k], r.ing[k + 1]) < r.ing[k + 2]) return false;
        return true;
    }

    /** Списать ингредиенты и выдать результат. */
    public static boolean craft(Inventory inv, Recipe r) {
        if (!canCraft(inv, r)) return false;
        if (!inv.creative) {
            for (int k = 0; k < r.ing.length; k += 3) {
                int need = r.ing[k + 2];
                for (int i = 0; i < Inventory.SLOTS && need > 0; i++) {
                    if (inv.id[i] == r.ing[k] && (r.ing[k + 1] == ANY || inv.aux[i] == r.ing[k + 1])) {
                        int take = Math.min(need, inv.count[i]);
                        inv.count[i] -= take;
                        need -= take;
                        if (inv.count[i] <= 0) { inv.id[i] = 0; inv.count[i] = 0; inv.aux[i] = 0; }
                    }
                }
            }
        }
        int left = inv.add(r.id, r.count, r.aux);
        if (left > 0 && MC.instance != null && MC.instance.level != null) {
            Player p = MC.instance.player;
            MC.instance.level.addEntity(new ItemEntity(MC.instance.level, p.x, p.y + 1f, p.z, r.id, left, r.aux));
        }
        return true;
    }

    /** Рецепты, доступные на станке (верстак показывает и рецепты 2x2; камнерез — только свои). */
    public static Vector forStation(int station) {
        Vector v = new Vector();
        for (int i = 0; i < all.size(); i++) {
            Recipe r = (Recipe) all.elementAt(i);
            if (r.station == station || (station == 1 && r.station == 0)) v.addElement(r);
        }
        return v;
    }
}
